/**
 * Part of Jade-Tipi — an open scientific metadata framework.
 *
 * Copyright (c) 2025 Duncan Scott and Jade-Tipi contributors
 * SPDX-License-Identifier: AGPL-3.0-only OR Commercial
 *
 * This file is part of a dual-licensed distribution:
 * - Under AGPL-3.0 for open-source use (see LICENSE)
 * - Under Commercial License for proprietary use (see DUAL-LICENSE.txt or contact licensing@jade-tipi.org)
 *
 * https://jade-tipi.org/license
 */
package org.jadetipi.jadetipi.importer

import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.ExitCodeGenerator
import org.springframework.boot.SpringApplication
import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.util.Assert

import java.time.Duration

/**
 * The production import trigger (TASK-066): a deliberate, scriptable CLI —
 * no always-on import service, and the trigger stays inside this excisable
 * module. Run via the Gradle application plugin:
 *
 * <pre>
 * ./gradlew :importers:jgi-import:run --args='\
 *     --jgi-import.mode=import \
 *     --jgi-import.process=processes_24-35613 \
 *     --jgi-import.org=lbl-gov --jgi-import.grp=jgi-pps \
 *     --jgi-import.kafka.bootstrap-servers=localhost:9092 \
 *     --jgi-import.kafka.topic=jdtp-txn \
 *     --spring.data.mongodb.uri=mongodb://localhost:27017/jadetipi'
 * </pre>
 *
 * <p>Modes: {@code plan} fills the dependency-ordered queue for the named
 * process document(s); {@code drive} drains whatever is pending;
 * {@code import} does both. Planning/driving is resumable — rerunning
 * continues from pending rows and reuses recorded ids (see
 * {@link ClarityImportDriver}). The exit code is non-zero when any item
 * failed.
 *
 * <p>This class rides the shared integration-test classpath, so
 * everything here is inert unless explicitly configured: the runner
 * exists only when {@code jgi-import.mode} is set, the publisher only
 * when {@code jgi-import.kafka.topic} is set, and the CLI boots with its
 * own {@code spring.config.name} so its YAML never collides with the
 * application's.
 */
@Slf4j
@SpringBootApplication
class JgiImportCliApplication {

    static void main(String[] args) {
        ConfigurableApplicationContext context = new SpringApplicationBuilder(JgiImportCliApplication)
                .web(WebApplicationType.NONE)
                .properties(['spring.config.name': 'jgi-import-cli'] as Map<String, Object>)
                .run(args)
        System.exit(SpringApplication.exit(context))
    }

    @Bean(destroyMethod = 'close')
    @ConditionalOnProperty(name = 'jgi-import.kafka.topic')
    ImportMessagePublisher importMessagePublisher(
            @Value('${jgi-import.kafka.bootstrap-servers:localhost:9092}') String bootstrapServers,
            @Value('${jgi-import.kafka.topic}') String topic) {
        return new ImportMessagePublisher(bootstrapServers, topic)
    }

    @Bean
    JgiImportExitCode jgiImportExitCode() {
        return new JgiImportExitCode()
    }

    @Bean
    @ConditionalOnProperty(name = 'jgi-import.mode')
    ApplicationRunner jgiImportRunner(ClarityAliquotImportPlanner planner,
                                      EspEntityImportPlanner espPlanner,
                                      ClarityImportDriver driver,
                                      CouchDbDocumentReader reader,
                                      org.springframework.beans.factory.ObjectProvider<ImportMessagePublisher> publisherProvider,
                                      JgiImportExitCode exitCode,
                                      @Value('${jgi-import.mode}') String mode,
                                      @Value('${jgi-import.process:}') String processDocIds,
                                      @Value('${jgi-import.process-type:}') String processType,
                                      @Value('${jgi-import.files:false}') boolean planFiles,
                                      @Value('${jgi-import.esp-entity:}') String espEntityUuids,
                                      @Value('${jgi-import.esp-type-name:}') String espTypeName,
                                      @Value('${jgi-import.limit:0}') int limit,
                                      @Value('${jgi-import.org:}') String org,
                                      @Value('${jgi-import.grp:}') String grp,
                                      @Value('${jgi-import.user:jgi-import}') String user,
                                      @Value('${jgi-import.batch-size:200}') int batchSize) {
        return { ApplicationArguments args ->
            Assert.isTrue(mode in ['types', 'plan', 'drive', 'import'],
                    "jgi-import.mode must be types, plan, drive, or import (got '${mode}')")

            if (mode == 'types') {
                Map<String, Long> counts = reader
                        .processTypeCounts(ClarityAliquotImportPlanner.DATABASE)
                        .block(Duration.ofMinutes(1))
                counts.sort { -it.value }.each { String name, Long count ->
                    log.info('{}  {}', String.format('%7d', count), name)
                }
                log.info('{} process type(s), {} process(es) total',
                        counts.size(), counts.values().sum() ?: 0)
                return
            }

            if (mode in ['plan', 'import']) {
                List<String> docIds = processDocIds.tokenize(',')*.trim().findAll { it }
                List<String> espUuids = espEntityUuids.tokenize(',')*.trim().findAll { it }
                Assert.isTrue(!docIds.isEmpty() || processType.trim() || planFiles ||
                        !espUuids.isEmpty() || espTypeName.trim(),
                        'jgi-import.process, jgi-import.process-type, jgi-import.files, ' +
                                'jgi-import.esp-entity, or jgi-import.esp-type-name is required to plan')
                docIds.each { String docId ->
                    Long inserted = planner.planProcess(docId).block(Duration.ofMinutes(5))
                    log.info('Planned {}: {} newly enqueued row(s)', docId, inserted)
                }
                if (processType.trim()) {
                    Long inserted = planner.planProcessesByType(processType.trim(),
                            limit > 0 ? limit : null).block(Duration.ofHours(4))
                    log.info('Planned process type "{}"{}: {} newly enqueued row(s)',
                            processType.trim(), limit > 0 ? " (limit ${limit})" : '', inserted)
                }
                if (planFiles) {
                    Long inserted = planner.planFiles(limit > 0 ? limit : null)
                            .block(Duration.ofHours(4))
                    log.info('Files pass planned{}: {} newly enqueued row(s)',
                            limit > 0 ? " (limit ${limit})" : '', inserted)
                }
                espUuids.each { String uuid ->
                    Long inserted = espPlanner.planEspEntity(uuid).block(Duration.ofMinutes(5))
                    log.info('Planned esp entity {}: {} newly enqueued row(s)', uuid, inserted)
                }
                if (espTypeName.trim()) {
                    Long inserted = espPlanner.planEspEntitiesByTypeName(espTypeName.trim(),
                            limit > 0 ? limit : null).block(Duration.ofHours(4))
                    log.info('Planned esp type "{}"{}: {} newly enqueued row(s)',
                            espTypeName.trim(), limit > 0 ? " (limit ${limit})" : '', inserted)
                }
            }

            if (mode in ['drive', 'import']) {
                Assert.hasText(org, 'jgi-import.org is required to drive (ids are minted under it)')
                Assert.hasText(grp, 'jgi-import.grp is required to drive (ids are minted under it)')
                ImportMessagePublisher publisher = publisherProvider.getIfAvailable()
                Assert.notNull(publisher,
                        'jgi-import.kafka.topic (and bootstrap-servers) are required to drive')
                ImportDriveReport report = driver.drive(publisher, org, grp, user, batchSize)
                log.info('Import drive complete: batches={}, itemsDone={}, itemsFailed={}, ' +
                        'messagesPublished={}, txnIds={}',
                        report.batches, report.itemsDone, report.itemsFailed,
                        report.messagesPublished, report.txnIds)
                if (report.itemsFailed > 0) {
                    log.error('{} item(s) failed — inspect import_queue rows with state "failed"',
                            report.itemsFailed)
                    exitCode.code = 1
                }
            }
        } as ApplicationRunner
    }

    /** Non-zero when any drive item failed, surfaced as the process exit code. */
    static class JgiImportExitCode implements ExitCodeGenerator {
        int code = 0

        @Override
        int getExitCode() {
            return code
        }
    }
}
