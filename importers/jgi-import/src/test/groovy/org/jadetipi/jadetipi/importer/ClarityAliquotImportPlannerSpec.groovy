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

    def 'planProcess enqueues bootstrap types, containers before artifacts, inputs first, process last'() {
        when:
        Long inserted = planner.planProcess('processes_24-35613').block()

        then: '7 types + input container + input + 3 outputs (one shares the input container path, two have none) + process'
        inserted == enqueued.size()

        and: 'bootstrap types lead'
        enqueued.take(7) == ClarityAliquotImportMapper.BOOTSTRAP_KEYS

        and: 'the input artifact is preceded by its container and precedes every output'
        int inputIdx = enqueued.indexOf('artifacts_DES439A6PA1')
        int inputContainerIdx = enqueued.indexOf('containers_27-8528')
        inputContainerIdx >= 0 && inputContainerIdx == inputIdx - 1
        ['artifacts_2-79367', 'artifacts_92-79368', 'artifacts_92-79369'].every {
            enqueued.indexOf(it) > inputIdx
        }

        and: 'the located output is preceded by its container'
        enqueued.indexOf('containers_27-8546') == enqueued.indexOf('artifacts_2-79367') - 1

        and: 'the process is last'
        enqueued.last() == 'processes_24-35613'
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
}
