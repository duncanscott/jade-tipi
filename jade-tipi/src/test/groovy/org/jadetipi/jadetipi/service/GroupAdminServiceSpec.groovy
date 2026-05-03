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

import com.mongodb.MongoWriteException
import com.mongodb.ServerAddress
import com.mongodb.WriteError
import org.bson.BsonDocument
import org.jadetipi.jadetipi.dto.GroupCreateRequest
import org.jadetipi.jadetipi.dto.GroupRecord
import org.jadetipi.jadetipi.dto.GroupUpdateRequest
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import spock.lang.Specification

import java.time.Instant

class GroupAdminServiceSpec extends Specification {

    static final String EXISTING_ID = 'jade-tipi-org~dev~018fd849-2a4d-7d0d-8d0d-cccccccccccc~grp~analytics'
    static final String PEER_RW = 'jade-tipi-org~dev~018fd849-2a4d-7d0d-8d0d-aaaaaaaaaaaa~grp~lab_ops'
    static final String PEER_R = 'jade-tipi-org~dev~018fd849-2a4d-7d0d-8d0d-bbbbbbbbbbbb~grp~viewers'

    ReactiveMongoTemplate mongoTemplate
    GroupAdminService service

    def setup() {
        mongoTemplate = Mock(ReactiveMongoTemplate)
        service = new GroupAdminService(mongoTemplate)
    }

    private static GroupCreateRequest createRequest(Map overrides = [:]) {
        GroupCreateRequest req = new GroupCreateRequest()
        req.id = overrides.containsKey('id') ? overrides.id : EXISTING_ID
        req.name = overrides.containsKey('name') ? overrides.name : 'Analytics'
        req.description = overrides.containsKey('description') ?
                overrides.description : 'Analytics group'
        req.permissions = overrides.containsKey('permissions') ?
                overrides.permissions : [(PEER_RW): 'rw', (PEER_R): 'r']
        return req
    }

    private static GroupUpdateRequest updateRequest(Map overrides = [:]) {
        GroupUpdateRequest req = new GroupUpdateRequest()
        req.name = overrides.containsKey('name') ? overrides.name : 'Analytics renamed'
        req.description = overrides.containsKey('description') ?
                overrides.description : 'updated description'
        req.permissions = overrides.containsKey('permissions') ?
                overrides.permissions : [(PEER_RW): 'rw']
        return req
    }

    def 'create writes a root-shaped grp document with admin sentinel provenance'() {
        given:
        Map<String, Object> captured = null
        mongoTemplate.insert(_ as Map, 'grp') >> { Map doc, String coll ->
            captured = doc
            return Mono.just(doc)
        }

        when:
        GroupRecord record = service.create(createRequest()).block()

        then:
        record != null
        record.id == EXISTING_ID
        record.collection == 'grp'
        record.name == 'Analytics'
        record.description == 'Analytics group'
        record.permissions == [(PEER_RW): 'rw', (PEER_R): 'r']
        record.head != null
        record.head.provenance != null
        record.head.provenance.collection == 'grp'
        record.head.provenance.action == 'create'
        record.head.provenance.txnId.startsWith('admin~')
        record.head.provenance.commitId == record.head.provenance.txnId
        record.head.provenance.msgUuid != null

        and:
        captured != null
        captured['_id'] == EXISTING_ID
        captured['id'] == EXISTING_ID
        captured['collection'] == 'grp'
        captured['properties']['name'] == 'Analytics'
        captured['properties']['description'] == 'Analytics group'
        captured['properties']['permissions'] == [(PEER_RW): 'rw', (PEER_R): 'r']
        captured['links'] == [:]
        captured['_head']['schema_version'] == 1
        captured['_head']['document_kind'] == 'root'
        captured['_head']['root_id'] == EXISTING_ID
        captured['_head']['provenance']['collection'] == 'grp'
        captured['_head']['provenance']['action'] == 'create'
        captured['_head']['provenance']['txn_id'].startsWith('admin~')
        captured['_head']['provenance']['committed_at'] instanceof Instant
        captured['_head']['provenance']['materialized_at'] instanceof Instant
    }

    def 'create synthesizes id when none supplied'() {
        given:
        GroupCreateRequest req = createRequest(id: null)
        Map<String, Object> captured = null
        mongoTemplate.insert(_ as Map, 'grp') >> { Map doc, String coll ->
            captured = doc
            return Mono.just(doc)
        }

        when:
        GroupRecord record = service.create(req).block()

        then:
        record != null
        record.id != null
        record.id.startsWith('jade-tipi-org~dev~')
        record.id.endsWith('~grp~analytics')
        captured['_id'] == record.id
    }

    def 'create rejects blank name with 400'() {
        when:
        service.create(createRequest(name: '   ')).block()

        then:
        ResponseStatusException ex = thrown(ResponseStatusException)
        ex.statusCode == HttpStatus.BAD_REQUEST
    }

    def 'create rejects permissions with non rw/r value'() {
        when:
        service.create(createRequest(permissions: [(PEER_RW): 'admin'])).block()

        then:
        ResponseStatusException ex = thrown(ResponseStatusException)
        ex.statusCode == HttpStatus.BAD_REQUEST
    }

    def 'create rejects permissions with blank key'() {
        when:
        service.create(createRequest(permissions: ['   ': 'rw'])).block()

        then:
        ResponseStatusException ex = thrown(ResponseStatusException)
        ex.statusCode == HttpStatus.BAD_REQUEST
    }

    def 'create surfaces duplicate id as 409'() {
        given:
        WriteError dupError = new WriteError(11000, 'duplicate', new BsonDocument())
        DuplicateKeyException dup = new DuplicateKeyException(
                'duplicate', new MongoWriteException(dupError, new ServerAddress()))
        mongoTemplate.insert(_ as Map, 'grp') >> Mono.error(dup)

        when:
        service.create(createRequest()).block()

        then:
        ResponseStatusException ex = thrown(ResponseStatusException)
        ex.statusCode == HttpStatus.CONFLICT
    }

    def 'list returns projected records'() {
        given:
        Map<String, Object> doc = sampleStoredDoc()
        mongoTemplate.find(_ as Query, Map.class, 'grp') >> Flux.just(doc)

        when:
        List<GroupRecord> records = service.list().collectList().block()

        then:
        records.size() == 1
        records[0].id == EXISTING_ID
        records[0].collection == 'grp'
        records[0].name == 'Analytics'
        records[0].permissions == [(PEER_RW): 'rw']
    }

    def 'findById returns mapped record'() {
        given:
        mongoTemplate.findById(EXISTING_ID, Map.class, 'grp') >> Mono.just(sampleStoredDoc())

        expect:
        StepVerifier.create(service.findById(EXISTING_ID))
                .assertNext { GroupRecord record ->
                    assert record.id == EXISTING_ID
                    assert record.name == 'Analytics'
                    assert record.head.provenance.action == 'create'
                }
                .verifyComplete()
    }

    def 'findById returns empty when id not present'() {
        given:
        mongoTemplate.findById('missing', Map.class, 'grp') >> Mono.empty()

        expect:
        StepVerifier.create(service.findById('missing'))
                .verifyComplete()
    }

    def 'update rewrites editable fields and provenance to action update'() {
        given:
        Update capturedUpdate = null
        mongoTemplate.findAndModify(_ as Query, _ as Update, Map.class, 'grp') >> {
            Query q, Update u, Class c, String coll ->
                capturedUpdate = u
                return Mono.just([_id: EXISTING_ID])
        }
        Map<String, Object> updatedDoc = sampleStoredDoc()
        ((Map) updatedDoc['properties']).put('name', 'Analytics renamed')
        ((Map) updatedDoc['properties']).put('description', 'updated description')
        ((Map) updatedDoc['_head']['provenance']).put('action', 'update')
        mongoTemplate.findById(EXISTING_ID, Map.class, 'grp') >> Mono.just(updatedDoc)

        when:
        GroupRecord record = service.update(EXISTING_ID, updateRequest()).block()

        then:
        record != null
        record.id == EXISTING_ID
        record.name == 'Analytics renamed'
        record.description == 'updated description'
        record.head.provenance.action == 'update'

        and:
        capturedUpdate != null
        Map<String, Object> setOps = capturedUpdate.getUpdateObject().get('$set')
        setOps['properties']['name'] == 'Analytics renamed'
        setOps['properties']['description'] == 'updated description'
        setOps['properties']['permissions'] == [(PEER_RW): 'rw']
        setOps['_head.provenance']['action'] == 'update'
        setOps['_head.provenance']['txn_id'].startsWith('admin~')
        setOps['_head.provenance']['collection'] == 'grp'
    }

    def 'update returns empty when id not present'() {
        given:
        mongoTemplate.findAndModify(_ as Query, _ as Update, Map.class, 'grp') >> Mono.empty()

        when:
        def result = service.update(EXISTING_ID, updateRequest()).block()

        then:
        result == null
        0 * mongoTemplate.findById(_, _, _)
    }

    def 'update rejects non rw/r permissions value'() {
        when:
        service.update(EXISTING_ID, updateRequest(permissions: [(PEER_RW): 'maybe'])).block()

        then:
        ResponseStatusException ex = thrown(ResponseStatusException)
        ex.statusCode == HttpStatus.BAD_REQUEST
    }

    def 'update rejects blank name with 400'() {
        when:
        service.update(EXISTING_ID, updateRequest(name: '')).block()

        then:
        ResponseStatusException ex = thrown(ResponseStatusException)
        ex.statusCode == HttpStatus.BAD_REQUEST
    }

    private static Map<String, Object> sampleStoredDoc() {
        Map<String, Object> properties = new LinkedHashMap<>()
        properties.put('name', 'Analytics')
        properties.put('description', 'Analytics group')
        properties.put('permissions', [(PEER_RW): 'rw'])

        Map<String, Object> provenance = new LinkedHashMap<>()
        provenance.put('txn_id', 'admin~11111111-1111-7111-8111-111111111111')
        provenance.put('commit_id', 'admin~11111111-1111-7111-8111-111111111111')
        provenance.put('msg_uuid', '22222222-2222-7222-8222-222222222222')
        provenance.put('collection', 'grp')
        provenance.put('action', 'create')
        provenance.put('committed_at', Instant.parse('2026-05-03T00:00:00Z'))
        provenance.put('materialized_at', Instant.parse('2026-05-03T00:00:00Z'))

        Map<String, Object> head = new LinkedHashMap<>()
        head.put('schema_version', 1)
        head.put('document_kind', 'root')
        head.put('root_id', EXISTING_ID)
        head.put('provenance', provenance)

        Map<String, Object> doc = new LinkedHashMap<>()
        doc.put('_id', EXISTING_ID)
        doc.put('id', EXISTING_ID)
        doc.put('collection', 'grp')
        doc.put('properties', properties)
        doc.put('links', [:])
        doc.put('_head', head)
        return doc
    }
}
