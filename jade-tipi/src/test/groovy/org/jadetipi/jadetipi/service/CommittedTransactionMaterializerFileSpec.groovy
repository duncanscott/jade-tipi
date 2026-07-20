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
package org.jadetipi.jadetipi.service

import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.time.Instant

/**
 * TASK-049 coverage for file ({@code fil}) roots: {@code fil + create}
 * materializes a standard typed root — deliberately no hoisted structure
 * (content identity and dedup are deferred director rulings) — and
 * object-targeted {@code ppy} assignments accept {@code fil} targets.
 */
class CommittedTransactionMaterializerFileSpec extends Specification {

    static final String TXN_ID = 'aaaaaaaa-bbbb-7ccc-8ddd-eeeeeeeeeeee'
    static final String COMMIT_ID = 'COMMIT-001'
    static final Instant OPENED_AT = Instant.parse('2026-01-01T00:00:00Z')
    static final Instant COMMITTED_AT = Instant.parse('2026-01-01T00:00:05Z')

    static final String TYP_FASTQ = '018fd849-3e01-7111-8a01-aaaaaaaaaaaa~jade-tipi-org~dev~typ~fastq'
    static final String FIL_ID = '018fd849-3e02-7222-8a02-bbbbbbbbbbbb~jade-tipi-org~dev~fil~run42_r1_fastq'
    static final String PPY_RETRIEVAL_URL = '018fd849-3e03-7333-8a03-cccccccccccc~jade-tipi-org~dev~ppy~retrieval_url'
    static final String FIL_MSG_UUID = '018fd849-3e02-7222-8a02-bbbbbbbbbbbb'
    static final String ASSIGN_MSG_UUID = '018fd849-3e04-7444-8f04-dddddddddddd'

    ReactiveMongoTemplate mongoTemplate
    CommittedTransactionReadService readService
    CommittedTransactionMaterializer materializer

    def setup() {
        mongoTemplate = Mock(ReactiveMongoTemplate)
        readService = Mock(CommittedTransactionReadService)
        materializer = new CommittedTransactionMaterializer(mongoTemplate, readService)
        // apply_state stamps (TASK-056) write to the txn WAL rows
        mongoTemplate.updateFirst(_ as Query, _ as Update, 'txn') >> Mono.empty()
    }

    private static CommittedTransactionSnapshot snapshot(List<CommittedTransactionMessage> messages) {
        return new CommittedTransactionSnapshot(
                txnId: TXN_ID,
                state: 'committed',
                commitId: COMMIT_ID,
                openedAt: OPENED_AT,
                committedAt: COMMITTED_AT,
                openData: [hint: 'open'],
                commitData: [reason: 'done'],
                messages: messages
        )
    }

    private static CommittedTransactionMessage fileCreateMessage() {
        return new CommittedTransactionMessage(
                msgUuid: FIL_MSG_UUID,
                collection: 'fil',
                action: 'create',
                data: [
                        id         : FIL_ID,
                        type_id    : TYP_FASTQ,
                        name       : 'run42_r1.fastq',
                        description: 'forward reads for run 42'
                ] as Map<String, Object>,
                receivedAt: Instant.parse('2026-01-01T00:00:02Z'),
                kafka: null
        )
    }

    private static CommittedTransactionMessage fileAssignment() {
        return new CommittedTransactionMessage(
                msgUuid: ASSIGN_MSG_UUID,
                collection: 'ppy',
                action: 'create',
                data: [
                        kind             : 'assignment',
                        object_collection: 'fil',
                        object_id        : FIL_ID,
                        property_id      : PPY_RETRIEVAL_URL,
                        value            : [url: 'https://data.example.org/run42/r1.fastq']
                ] as Map<String, Object>,
                receivedAt: Instant.parse('2026-01-01T00:00:03Z'),
                kafka: null
        )
    }

    def 'materializes a fil create as a standard typed root with no hoisted structure'() {
        given:
        Map<String, Object> captured = null
        mongoTemplate.insert(_ as Map, 'fil') >> { Map doc, String _coll ->
            captured = doc
            return Mono.just(doc)
        }

        when:
        MaterializeResult result = materializer.materialize(snapshot([fileCreateMessage()])).block()

        then:
        result.materialized == 1
        result.skippedUnsupported == 0
        result.skippedInvalid == 0

        and:
        captured._id == FIL_ID
        captured.id == FIL_ID
        captured.collection == 'fil'
        captured.type_id == TYP_FASTQ
        (captured.properties as Map).name == 'run42_r1.fastq'
        (captured.properties as Map).description == 'forward reads for run 42'
        captured.links == [:]

        and: 'the root carries only the standard contract keys — nothing file-specific is hoisted'
        captured.keySet() == ['_id', 'id', 'collection', 'type_id', 'properties', 'links', '_head'] as Set

        and: '_head carries the standard provenance'
        Map head = captured._head as Map
        head.root_id == FIL_ID
        (head.provenance as Map).collection == 'fil'
        (head.provenance as Map).msg_uuid == FIL_MSG_UUID
    }

    def 'projects a fil-targeted assignment onto property_values against the fil collection'() {
        given:
        Update capturedUpdate = null
        String capturedCollection = null
        mongoTemplate.findById(FIL_ID, Map.class, 'fil') >> Mono.just([
                _id: FIL_ID, id: FIL_ID, collection: 'fil', type_id: TYP_FASTQ,
                properties: [:], links: [:]
        ] as Map)
        mongoTemplate.findById(TYP_FASTQ, Map.class, 'typ') >> Mono.just([
                _id: TYP_FASTQ, id: TYP_FASTQ, collection: 'typ', type_id: null,
                properties: [name: 'fastq', property_refs: [(PPY_RETRIEVAL_URL): [:]]],
                links: [:]
        ] as Map)
        mongoTemplate.updateFirst(_ as Query, _ as Update, 'fil') >> {
            Query q, Update u, String coll ->
                capturedUpdate = u
                capturedCollection = coll
                return Mono.empty()
        }

        when:
        MaterializeResult result = materializer.materialize(snapshot([fileAssignment()])).block()

        then:
        result.materialized == 1
        result.skippedInvalid == 0
        result.skippedUnregisteredProperty == 0
        capturedCollection == 'fil'
        Map entry = (capturedUpdate.updateObject.get('$set') as Map)
                .get("property_values.${PPY_RETRIEVAL_URL}" as String) as Map
        entry.value == [url: 'https://data.example.org/run42/r1.fastq']
        entry.txn_id == TXN_ID
    }
}
