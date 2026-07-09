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

import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

/**
 * TASK-069 coverage for the esp planner: begat ancestry first
 * (recursively), the container before the entity, the dynamic type row
 * before each entity, link-type bootstrap once, and discovery through
 * the by_type_name view.
 */
class EspEntityImportPlannerSpec extends Specification {

    static final String CHILD = '019a0000-0000-7000-8000-000000000001'
    static final String PARENT = '019a0000-0000-7000-8000-000000000002'
    static final String GRANDPARENT = '019a0000-0000-7000-8000-000000000003'
    static final String CONTAINER = '019a0000-0000-7000-8000-000000000004'

    CouchDbDocumentReader reader
    ImportQueueService queue
    EspEntityImportPlanner planner
    List<String> enqueued
    Set<String> existing

    def setup() {
        reader = Mock(CouchDbDocumentReader)
        queue = Mock(ImportQueueService)
        planner = new EspEntityImportPlanner(reader, queue)
        enqueued = []
        existing = [] as Set
        queue.enqueue('esp', _ as String, _ as String) >> { String _s, String key, String _k ->
            enqueued << key
            return Mono.just(existing.add(key))
        }
        reader.findDocument('esp-entity', CHILD) >> Mono.just([
                _id: CHILD, uuid: CHILD, class_name: 'Sample', type_name: 'Aliquot',
                parents: [[relationship: 'begat', uuid: PARENT]],
                container: [uuid: CONTAINER]] as Map)
        reader.findDocument('esp-entity', PARENT) >> Mono.just([
                _id: PARENT, uuid: PARENT, class_name: 'Sample', type_name: 'SOW Item',
                parents: [[relationship: 'begat', uuid: GRANDPARENT]]] as Map)
        reader.findDocument('esp-entity', GRANDPARENT) >> Mono.just([
                _id: GRANDPARENT, uuid: GRANDPARENT, class_name: 'Sample',
                type_name: 'Proposal'] as Map)
        reader.findDocument('esp-entity', CONTAINER) >> Mono.just([
                _id: CONTAINER, uuid: CONTAINER, class_name: 'Container',
                type_name: '96W Plate'] as Map)
    }

    def 'ancestry plans before the container, the container before the entity, types before each'() {
        when:
        Long inserted = planner.planEspEntity(CHILD).block()

        then: '2 link types + 4 type rows... minus dedup (Sample types shared) + 4 entities'
        inserted == (enqueued as Set).size()

        and: 'link-type bootstrap leads'
        enqueued.take(2) == EspEntityImportMapper.BOOTSTRAP_KEYS

        and: 'depth-first ancestry: grandparent, parent, container, child — each after its type row'
        List<String> entityOrder = enqueued.findAll { it in [CHILD, PARENT, GRANDPARENT, CONTAINER] }
        entityOrder == [GRANDPARENT, PARENT, CONTAINER, CHILD]
        enqueued[enqueued.indexOf(CHILD) - 1] ==
                EspEntityImportMapper.typeKey('Sample', 'Aliquot')
        enqueued[enqueued.indexOf(CONTAINER) - 1] ==
                EspEntityImportMapper.typeKey('Container', '96W Plate')
    }

    def 'replanning inserts nothing new'() {
        when:
        planner.planEspEntity(CHILD).block()
        Long second = planner.planEspEntity(CHILD).block()

        then:
        second == 0L
    }

    def 'discovery plans every entity the by_type_name view returns'() {
        given:
        reader.docIdsByViewKey('esp-entity', 'entity_views', 'by_type_name', 'Aliquot', 3) >>
                Flux.just(CHILD)

        when:
        Long inserted = planner.planEspEntitiesByTypeName('Aliquot', 3).block()

        then:
        inserted == (enqueued as Set).size()
        enqueued.contains(CHILD)
    }

    def 'a missing entity document is skipped with its subtree, not an error'() {
        given:
        reader.findDocument('esp-entity', 'no-such') >> Mono.empty()

        when:
        Long inserted = planner.planEspEntity('no-such').block()

        then: 'only the link-type bootstrap rows'
        inserted == 2L
    }
}
