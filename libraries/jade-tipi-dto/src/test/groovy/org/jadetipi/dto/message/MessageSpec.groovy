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
package org.jadetipi.dto.message

import org.jadetipi.dto.collections.Group
import org.jadetipi.dto.collections.Transaction
import org.jadetipi.dto.util.JsonMapper
import org.jadetipi.dto.util.ValidationException
import spock.lang.Specification
import spock.lang.Unroll

class MessageSpec extends Specification {

    private static final List<String> EXAMPLE_PATHS = [
            '/example/message/01-open-transaction.json',
            '/example/message/02-create-property-definition-text.json',
            '/example/message/03-create-property-definition-numeric.json',
            '/example/message/04-create-entity-type.json',
            '/example/message/05-update-entity-type-add-property.json',
            '/example/message/06-create-entity.json',
            '/example/message/07-assign-property-value-text.json',
            '/example/message/08-assign-property-value-number.json',
            '/example/message/09-commit-transaction.json',
            '/example/message/10-create-location.json',
            '/example/message/11-create-contents-type.json',
            '/example/message/12-create-contents-link-plate-sample.json',
            '/example/message/13-create-group.json'
    ]

    private static String readResource(String path) {
        def stream = MessageSpec.getResourceAsStream(path)
        assert stream != null: "Resource not found: ${path}"
        stream.getText('UTF-8')
    }

    def "newInstance constructs a Message with the given collection and action"() {
        given:
        def txn = new Transaction(
                '018fd849-2a40-7abc-8a45-111111111111',
                new Group('jade-tipi-org', 'dev'),
                'kli',
                '0000-0002-1825-0097'
        )

        when:
        def message = Message.newInstance(txn, Collection.PROPERTY, Action.CREATE, [kind: 'definition'])

        then:
        message.txn() == txn
        message.collection() == Collection.PROPERTY
        message.action() == Action.CREATE
        message.uuid() != null
        message.data() == [kind: 'definition']
    }

    def "Message JSON includes the collection field"() {
        given:
        def txn = new Transaction(
                '018fd849-2a40-7abc-8a45-111111111111',
                new Group('jade-tipi-org', 'dev'),
                'kli',
                null
        )
        def message = Message.newInstance(txn, Collection.ENTITY, Action.CREATE, [id: 'jade-tipi-org~dev~uuid~en~plate_a'])

        when:
        String json = JsonMapper.toJson(message)
        Map parsed = JsonMapper.fromJson(json, Map)

        then:
        parsed.collection == 'ent'
        parsed.action == 'create'
    }

    @Unroll
    def "example #examplePath round-trips through JsonMapper preserving collection"() {
        given:
        String json = readResource(examplePath)

        when:
        Message message = JsonMapper.fromJson(json, Message)

        then:
        message.collection() != null
        message.action() != null

        when:
        String roundTripped = JsonMapper.toJson(message)
        Message reparsed = JsonMapper.fromJson(roundTripped, Message)

        then:
        reparsed.collection() == message.collection()
        reparsed.action() == message.action()
        reparsed.txn() == message.txn()
        reparsed.uuid() == message.uuid()

        where:
        examplePath << EXAMPLE_PATHS
    }

    @Unroll
    def "example #examplePath validates against the schema"() {
        given:
        String json = readResource(examplePath)
        Message message = JsonMapper.fromJson(json, Message)

        when:
        message.validate()

        then:
        noExceptionThrown()

        where:
        examplePath << EXAMPLE_PATHS
    }

    def "schema rejects messages missing the collection field"() {
        given:
        String json = '''
            {
              "txn": {
                "uuid": "018fd849-2a40-7abc-8a45-111111111111",
                "group": { "org": "jade-tipi-org", "grp": "dev" },
                "client": "kli"
              },
              "uuid": "018fd849-2a40-7def-8b56-222222222222",
              "action": "open",
              "data": { "description": "missing collection" }
            }
        '''.trim()
        Message message = JsonMapper.fromJson(json, Message)

        when:
        message.validate()

        then:
        ValidationException ex = thrown()
        ex.message.toLowerCase().contains('collection')
    }

    def "fromJson rejects unknown collection abbreviations"() {
        given:
        String json = '''
            {
              "txn": {
                "uuid": "018fd849-2a40-7abc-8a45-111111111111",
                "group": { "org": "jade-tipi-org", "grp": "dev" },
                "client": "kli"
              },
              "uuid": "018fd849-2a40-7def-8b56-222222222222",
              "collection": "xyz",
              "action": "open",
              "data": {}
            }
        '''.trim()

        when:
        JsonMapper.fromJson(json, Message)

        then:
        thrown(Exception)
    }

    def "schema rejects collection=txn paired with a data action"() {
        given:
        def txn = new Transaction(
                '018fd849-2a40-7abc-8a45-111111111111',
                new Group('jade-tipi-org', 'dev'),
                'kli',
                null
        )
        def message = Message.newInstance(txn, Collection.TRANSACTION, Action.CREATE, [id: 'x'])

        when:
        message.validate()

        then:
        ValidationException ex = thrown()
        ex.message.toLowerCase().contains('action')
    }

    def "schema rejects collection=ppy paired with a transaction-control action"() {
        given:
        def txn = new Transaction(
                '018fd849-2a40-7abc-8a45-111111111111',
                new Group('jade-tipi-org', 'dev'),
                'kli',
                null
        )
        def message = Message.newInstance(txn, Collection.PROPERTY, Action.OPEN, [:])

        when:
        message.validate()

        then:
        ValidationException ex = thrown()
        ex.message.toLowerCase().contains('action')
    }

    def "Collection.fromJson('loc') returns LOCATION and serializes back as 'loc'"() {
        expect:
        Collection.fromJson('loc') == Collection.LOCATION
        Collection.fromJson('location') == Collection.LOCATION
        Collection.LOCATION.toJson() == 'loc'
        Collection.LOCATION.abbreviation == 'loc'
        Collection.LOCATION.name == 'location'
        Collection.LOCATION.actions == [Action.CREATE, Action.UPDATE, Action.DELETE]
    }

    def "schema accepts collection=loc paired with action=create"() {
        given:
        def txn = new Transaction(
                '018fd849-2a40-7abc-8a45-111111111111',
                new Group('jade-tipi-org', 'dev'),
                'kli',
                '0000-0002-1825-0097'
        )
        def message = Message.newInstance(
                txn,
                Collection.LOCATION,
                Action.CREATE,
                [
                        id: 'jade-tipi-org~dev~018fd849-2a47-7777-8f01-aaaaaaaaaaaa~loc~freezer_a',
                        name: 'freezer_a'
                ]
        )

        when:
        message.validate()

        then:
        noExceptionThrown()
    }

    @Unroll
    def "schema rejects collection=loc paired with transaction-control action #action"() {
        given:
        def txn = new Transaction(
                '018fd849-2a40-7abc-8a45-111111111111',
                new Group('jade-tipi-org', 'dev'),
                'kli',
                null
        )
        def message = Message.newInstance(txn, Collection.LOCATION, action, [:])

        when:
        message.validate()

        then:
        ValidationException ex = thrown()
        ex.message.toLowerCase().contains('action')

        where:
        action << [Action.OPEN, Action.COMMIT, Action.ROLLBACK]
    }

    def "Message id remains <txn>~<uuid>~<action> and excludes collection"() {
        given:
        def txn = new Transaction(
                '018fd849-2a40-7abc-8a45-111111111111',
                new Group('jade-tipi-org', 'dev'),
                'kli',
                null
        )
        def message = new Message(
                txn,
                '018fd849-2a40-7def-8b56-222222222222',
                Collection.PROPERTY,
                Action.CREATE,
                [:]
        )

        expect:
        message.getId() == "${txn.getId()}~018fd849-2a40-7def-8b56-222222222222~create"
    }

    def "Message equality stays based on txn and uuid only"() {
        given:
        def txn = new Transaction(
                '018fd849-2a40-7abc-8a45-111111111111',
                new Group('jade-tipi-org', 'dev'),
                'kli',
                null
        )
        def a = new Message(txn, '018fd849-2a40-7def-8b56-222222222222', Collection.PROPERTY, Action.CREATE, [:])
        def b = new Message(txn, '018fd849-2a40-7def-8b56-222222222222', Collection.ENTITY, Action.UPDATE, [k: 'v'])

        expect:
        a == b
        a.hashCode() == b.hashCode()
    }

    def "loc create example uses the human-readable data.properties / data.links shape"() {
        given:
        String json = readResource('/example/message/10-create-location.json')

        when:
        Message message = JsonMapper.fromJson(json, Message)

        then:
        message.collection() == Collection.LOCATION
        message.action() == Action.CREATE

        and: 'data.id is the materialized object id; type_id is omitted on this minimal example'
        Map data = message.data()
        data.id == 'jade-tipi-org~dev~018fd849-2a47-7777-8f01-aaaaaaaaaaaa~loc~freezer_a'
        !data.containsKey('type_id')

        and: 'human-authored properties live under data.properties, not at the data root'
        Map properties = data.properties as Map
        properties.name == 'freezer_a'
        properties.description == 'minus-80 freezer in room 110'
        !data.containsKey('name')
        !data.containsKey('description')

        and: 'data.links is present and explicitly empty on a simple create'
        data.links == [:]
    }

    def "contents typ example declares the canonical link-type facts"() {
        given:
        String json = readResource('/example/message/11-create-contents-type.json')

        when:
        Message message = JsonMapper.fromJson(json, Message)

        then:
        message.collection() == Collection.TYPE
        message.action() == Action.CREATE

        and:
        Map data = message.data()
        data.kind == 'link_type'
        data.id == 'jade-tipi-org~dev~018fd849-2a49-7999-8a09-aaaaaaaaaaab~typ~contents'
        data.name == 'contents'
        data.left_role == 'container'
        data.right_role == 'content'
        data.left_to_right_label == 'contains'
        data.right_to_left_label == 'contained_by'
        data.allowed_left_collections == ['loc']
        data.allowed_right_collections == ['loc', 'ent']
    }

    def "grp create example carries a permissions map keyed by world-unique grp ids"() {
        given:
        String json = readResource('/example/message/13-create-group.json')

        when:
        Message message = JsonMapper.fromJson(json, Message)

        then:
        message.collection() == Collection.GROUP
        message.action() == Action.CREATE

        and:
        Map data = message.data()
        data.id == 'jade-tipi-org~dev~018fd849-2a4d-7d0d-8d0d-cccccccccccc~grp~analytics'
        data.name == 'analytics'
        data.description == 'analytics team'

        and: 'permissions is a map whose keys are peer grp ids and whose values are exactly rw or r'
        Map permissions = data.permissions as Map
        permissions.size() == 2
        permissions['jade-tipi-org~dev~018fd849-2a4d-7d0d-8d0d-aaaaaaaaaaaa~grp~lab_ops'] == 'rw'
        permissions['jade-tipi-org~dev~018fd849-2a4d-7d0d-8d0d-bbbbbbbbbbbb~grp~viewers'] == 'r'
    }

    def "schema rejects a grp create whose permissions value is not 'rw' or 'r'"() {
        given:
        def txn = new Transaction(
                '018fd849-2a40-7abc-8a45-111111111111',
                new Group('jade-tipi-org', 'dev'),
                'kli',
                '0000-0002-1825-0097'
        )
        def message = Message.newInstance(
                txn,
                Collection.GROUP,
                Action.CREATE,
                [
                        id         : 'jade-tipi-org~dev~018fd849-2a4d-7d0d-8d0d-cccccccccccc~grp~analytics',
                        name       : 'analytics',
                        permissions: [
                                'jade-tipi-org~dev~018fd849-2a4d-7d0d-8d0d-aaaaaaaaaaaa~grp~lab_ops': 'admin'
                        ]
                ]
        )

        when:
        message.validate()

        then:
        ValidationException ex = thrown()
        ex.message.toLowerCase().contains('permissions') ||
                ex.message.toLowerCase().contains('rw') ||
                ex.message.toLowerCase().contains('enum')
    }

    def "schema rejects a non-grp message whose data has a non-snake_case key"() {
        given:
        def txn = new Transaction(
                '018fd849-2a40-7abc-8a45-111111111111',
                new Group('jade-tipi-org', 'dev'),
                'kli',
                '0000-0002-1825-0097'
        )
        def message = Message.newInstance(
                txn,
                Collection.LOCATION,
                Action.CREATE,
                [
                        id                                                             : 'jade-tipi-org~dev~018fd849-2a47-7777-8f01-aaaaaaaaaaaa~loc~freezer_a',
                        'jade-tipi-org~dev~018fd849-2a4d-7d0d-8d0d-aaaaaaaaaaaa~grp~x': 'rw'
                ]
        )

        when:
        message.validate()

        then:
        thrown(ValidationException)
    }

    def "contents lnk example references the contents type and carries a position property"() {
        given:
        String json = readResource('/example/message/12-create-contents-link-plate-sample.json')

        when:
        Message message = JsonMapper.fromJson(json, Message)

        then:
        message.collection() == Collection.LINK
        message.action() == Action.CREATE

        and:
        Map data = message.data()
        data.id.endsWith('~lnk~plate_b1_sample_x1')
        data.type_id.endsWith('~typ~contents')
        data.left.endsWith('~loc~plate_b1')
        data.right.endsWith('~ent~sample_x1')

        and:
        Map position = data.properties.position
        position.kind == 'plate_well'
        position.label == 'A1'
        position.row == 'A'
        position.column == 1
    }

    def "contents transaction example trio shares one txn id and pairs typ link-type with a referencing lnk create"() {
        given: 'open, contents-type create, contents-link create, and commit examples'
        Message open = JsonMapper.fromJson(
                readResource('/example/message/01-open-transaction.json'), Message)
        Message typCreate = JsonMapper.fromJson(
                readResource('/example/message/11-create-contents-type.json'), Message)
        Message lnkCreate = JsonMapper.fromJson(
                readResource('/example/message/12-create-contents-link-plate-sample.json'), Message)
        Message commit = JsonMapper.fromJson(
                readResource('/example/message/09-commit-transaction.json'), Message)

        expect: 'all four messages chain through the same transaction uuid'
        String txnUuid = open.txn().uuid()
        typCreate.txn().uuid() == txnUuid
        lnkCreate.txn().uuid() == txnUuid
        commit.txn().uuid() == txnUuid

        and: 'open and commit are transaction-control messages on the txn collection'
        open.collection() == Collection.TRANSACTION
        open.action() == Action.OPEN
        commit.collection() == Collection.TRANSACTION
        commit.action() == Action.COMMIT

        and: 'the lnk create points to the typ link-type by id (the in-resource cross-reference)'
        Map typData = typCreate.data()
        Map lnkData = lnkCreate.data()
        typData.kind == 'link_type'
        typData.name == 'contents'
        lnkData.type_id == typData.id

        and: 'lnk endpoints respect the typ allowed_*_collections declarations'
        typData.allowed_left_collections.contains('loc')
        typData.allowed_right_collections.contains('ent')
        lnkData.left.contains('~loc~')
        lnkData.right.contains('~ent~')
    }
}
