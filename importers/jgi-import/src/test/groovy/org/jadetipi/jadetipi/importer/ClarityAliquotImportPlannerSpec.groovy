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

import com.fasterxml.jackson.databind.ObjectMapper
import reactor.core.publisher.Mono
import spock.lang.Specification

/**
 * TASK-059 coverage for the dependency-ordered planner: bootstrap types
 * first, each artifact's container before the artifact, inputs before
 * outputs, process last — and dedup across repeated planning.
 */
class ClarityAliquotImportPlannerSpec extends Specification {

    static final ObjectMapper JSON = new ObjectMapper()

    CouchDbDocumentReader reader
    ImportQueueService queue
    ClarityAliquotImportPlanner planner
    List<String> enqueued
    Set<String> existing

    def setup() {
        reader = Mock(CouchDbDocumentReader)
        queue = Mock(ImportQueueService)
        planner = new ClarityAliquotImportPlanner(reader, queue)
        enqueued = []
        existing = [] as Set
        queue.enqueue('clarity', _ as String, _ as String) >> { String _s, String key, String _k ->
            enqueued << key
            return Mono.just(existing.add(key))
        }
        reader.findDocument('clarity', _ as String) >> { String _db, String docId ->
            InputStream stream = ClarityAliquotImportPlannerSpec.getResourceAsStream(
                    "/importfixtures/clarity-aliquot/${docId.replaceFirst('^processes_', 'process_').replaceFirst('^artifacts_', 'artifact_').replaceFirst('^containers_', 'container_')}.json")
            return stream == null ? Mono.empty() : Mono.just(JSON.readValue(stream, Map))
        }
    }

    def 'planProcess enqueues bootstrap and procedure types, containers and samples before artifacts, inputs first, process last'() {
        when:
        Long inserted = planner.planProcess('processes_24-35613').block()

        then: '8 bootstrap types + the procedure type + 2 containers + 1 shared sample + 4 artifacts + process'
        inserted == (enqueued as Set).size()
        inserted == 17L

        and: 'bootstrap types lead, then the process type discovered from the document (TASK-067)'
        enqueued.take(8) == ClarityAliquotImportMapper.BOOTSTRAP_KEYS
        enqueued[8] == ClarityAliquotImportMapper.processTypeKey('AC Sample Aliquot Creation')

        and: 'the input artifact is preceded by its container and sample, and precedes every output'
        int inputIdx = enqueued.indexOf('artifacts_DES439A6PA1')
        enqueued.indexOf('containers_27-8528') == inputIdx - 2
        enqueued.indexOf('samples_DES439A6') == inputIdx - 1
        ['artifacts_2-79367', 'artifacts_92-79368', 'artifacts_92-79369'].every {
            enqueued.indexOf(it) > inputIdx
        }

        and: 'the located output is preceded by its container (the shared sample re-enqueue is a dedup no-op)'
        enqueued.indexOf('containers_27-8546') == enqueued.indexOf('artifacts_2-79367') - 2

        and: 'the process is last'
        enqueued.last() == 'processes_24-35613'
    }

    def 'planProcessesByType discovers process documents through the reader and plans each (TASK-067)'() {
        given: 'the view returns the fixture process twice — the replan dedups to zero'
        reader.processDocIdsByType('clarity', 'AC Sample Aliquot Creation', 5) >>
                reactor.core.publisher.Flux.just('processes_24-35613', 'processes_24-35613')

        when:
        Long inserted = planner.planProcessesByType('AC Sample Aliquot Creation', 5).block()

        then: 'the first plan inserts the full row set; the second inserts nothing new'
        inserted == 17L
        enqueued.count { it == 'processes_24-35613' } == 2
    }

    def 'replanning the same process inserts nothing new'() {
        when:
        planner.planProcess('processes_24-35613').block()
        Long secondRun = planner.planProcess('processes_24-35613').block()

        then:
        secondRun == 0L
    }

    def 'a missing process document errors'() {
        when:
        planner.planProcess('processes_no-such').block()

        then:
        thrown(IllegalArgumentException)
    }

    def 'the files pass enqueues property declarations once, then eligible files, skipping unimported artifacts (TASK-068)'() {
        given: 'three file-doc fixtures: two attached to imported artifacts, one to a stranger'
        reader.docIdsByPrefix('clarity', 'files_') >> reactor.core.publisher.Flux.just(
                'files_40-1', 'files_40-2', 'files_40-3')
        queue.jdtpIdOf(ImportQueueService.rowId('clarity', 'artifacts_92-79368')) >>
                Mono.just('recorded-1')
        queue.jdtpIdOf(ImportQueueService.rowId('clarity', 'artifacts_92-79369')) >>
                Mono.just('recorded-2')
        queue.jdtpIdOf(_) >> Mono.empty()

        when:
        Long inserted = planner.planFiles(null).block()

        then: 'property rows ride the first eligible file only; the stranger is skipped'
        inserted == ClarityAliquotImportMapper.FILE_PROPERTY_KEYS.size() + 2
        enqueued.take(ClarityAliquotImportMapper.FILE_PROPERTY_KEYS.size()) ==
                ClarityAliquotImportMapper.FILE_PROPERTY_KEYS
        enqueued.drop(ClarityAliquotImportMapper.FILE_PROPERTY_KEYS.size()) ==
                ['files_40-1', 'files_40-3']
    }

    def 'the files pass limit bounds the document scan (TASK-068)'() {
        given:
        reader.docIdsByPrefix('clarity', 'files_') >> reactor.core.publisher.Flux.just(
                'files_40-1', 'files_40-3')
        queue.jdtpIdOf(_) >> Mono.just('recorded')

        when:
        Long inserted = planner.planFiles(1).block()

        then: 'exactly the property rows plus the first file'
        inserted == ClarityAliquotImportMapper.FILE_PROPERTY_KEYS.size() + 1
        enqueued.last() == 'files_40-1'
        !enqueued.contains('files_40-3')
    }
}
