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

import com.mongodb.MongoWriteException
import com.mongodb.ServerAddress
import com.mongodb.WriteError
import org.bson.BsonDocument
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

/**
 * TASK-059 coverage for the persistent import queue: counter-driven
 * sequencing, dedup that preserves the original seq, pending-in-order
 * drain shape, and the emit/done/failed transitions.
 */
class ImportQueueServiceSpec extends Specification {

    ReactiveMongoTemplate mongoTemplate
    ImportQueueService service

    def setup() {
        mongoTemplate = Mock(ReactiveMongoTemplate)
        service = new ImportQueueService(mongoTemplate)
    }

    private static DuplicateKeyException springDuplicate() {
        def writeError = new WriteError(11000, 'duplicate key', new BsonDocument())
        def cause = new MongoWriteException(writeError, new ServerAddress('localhost'))
        return new DuplicateKeyException('duplicate key', cause)
    }

    def 'enqueue assigns the next counter seq and inserts a pending row'() {
        given:
        Map captured = null
        mongoTemplate.findAndModify(_ as Query, _ as Update, _ as FindAndModifyOptions,
                Map.class, 'import_queue') >> Mono.just([_id: 'seq_counter', value: 42L] as Map)
        mongoTemplate.insert(_ as Map, 'import_queue') >> { Map row, String _c ->
            captured = row
            return Mono.just(row)
        }

        when:
        Boolean inserted = service.enqueue('clarity', 'artifacts_2-79367', 'artifact').block()

        then:
        inserted
        captured._id == 'clarity~artifacts_2-79367'
        captured.source == 'clarity'
        captured.key == 'artifacts_2-79367'
        captured.kind == 'artifact'
        captured.state == 'pending'
        captured.seq == 42L
        captured.enqueued_at != null
    }

    def 'a duplicate enqueue is a no-op that reports false and never rewrites the row'() {
        given:
        mongoTemplate.findAndModify(_ as Query, _ as Update, _ as FindAndModifyOptions,
                Map.class, 'import_queue') >> Mono.just([_id: 'seq_counter', value: 43L] as Map)
        mongoTemplate.insert(_ as Map, 'import_queue') >> Mono.error(springDuplicate())

        when:
        Boolean inserted = service.enqueue('clarity', 'artifacts_2-79367', 'artifact').block()

        then: 'the original row (and its lower seq) is preserved'
        !inserted
        0 * mongoTemplate.updateFirst(_, _, _)
    }

    def 'pendingInOrder drains pending rows by ascending seq with the requested limit'() {
        given:
        Query captured = null
        mongoTemplate.find(_ as Query, Map.class, 'import_queue') >> { Query q, Class _t, String _c ->
            captured = q
            return Flux.just([
                    _id: 'clarity~a', source: 'clarity', key: 'a', kind: 'artifact',
                    state: 'pending', seq: 7L
            ] as Map)
        }

        when:
        List<ImportQueueItem> items = service.pendingInOrder(50).collectList().block()

        then:
        items.size() == 1
        items[0].id == 'clarity~a'
        items[0].seq == 7L
        captured.getQueryObject().get('state') == 'pending'
        captured.getSortObject().get('seq') == 1
        captured.getLimit() == 50
    }

    def 'emit, done, and failed transitions update the expected fields'() {
        given:
        List<Update> updates = []
        mongoTemplate.updateFirst(_ as Query, _ as Update, 'import_queue') >> { Query q, Update u, String _c ->
            updates << u
            return Mono.empty()
        }

        when:
        service.recordJdtpId('clarity~a', 'org~grp~u~ent~clarity_a').block()
        service.markDone('clarity~a', 'txn-1').block()
        service.markFailed('clarity~b', 'boom').block()

        then:
        (updates[0].getUpdateObject().get('$set') as Map).jdtp_id == 'org~grp~u~ent~clarity_a'
        (updates[1].getUpdateObject().get('$set') as Map).state == 'done'
        (updates[1].getUpdateObject().get('$set') as Map).txn_id == 'txn-1'
        (updates[2].getUpdateObject().get('$set') as Map).state == 'failed'
        (updates[2].getUpdateObject().get('$set') as Map).error == 'boom'
    }
}
