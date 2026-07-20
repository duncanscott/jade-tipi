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
            '/example/message/05a-update-entity-type-add-property-volume.json',
            '/example/message/06-create-entity.json',
            '/example/message/07-assign-property-value-text.json',
            '/example/message/08-assign-property-value-number.json',
            '/example/message/09-commit-transaction.json',
            '/example/message/10-create-location.json',
            '/example/message/11-create-contents-type.json',
            '/example/message/12-create-contents-link-plate-sample.json',
            '/example/message/13-create-group.json',
            '/example/message/14-create-plate-type-extends-container.json',
            '/example/message/15-assign-object-property-value.json',
            '/example/message/16-create-procedure-type.json',
            '/example/message/17-create-task-type.json',
            '/example/message/18-create-task.json',
            '/example/message/19-create-task-input-link-type.json',
            '/example/message/19a-create-task-input-link.json',
            '/example/message/20-create-procedure-with-output-input.json',
            '/example/message/21-create-fulfills-link-type.json',
            '/example/message/21a-create-fulfills-link.json',
            '/example/message/22-create-produced-by-link-type.json',
            '/example/message/22a-create-produced-by-link.json',
            '/example/message/23-create-file-type.json',
            '/example/message/24-create-property-definition-retrieval-url.json',
            '/example/message/25-update-file-type-add-property.json',
            '/example/message/26-create-file.json',
            '/example/message/27-assign-file-property-value.json'
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
        def message = Message.newInstance(txn, Collection.ENTITY, Action.CREATE, [id: 'jade-tipi-org~dev~uuid~ent~plate_a'])

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
                        id: '018fd849-2a47-7777-8f01-aaaaaaaaaaaa~jade-tipi-org~dev~loc~freezer_a',
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
        data.id == '018fd849-2a47-7777-8f01-aaaaaaaaaaaa~jade-tipi-org~dev~loc~freezer_a'
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
        data.id == '018fd849-2a49-7999-8a09-aaaaaaaaaaab~jade-tipi-org~dev~typ~contents'
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
        data.id == '018fd849-2a4d-7d0d-8d0d-cccccccccccc~jade-tipi-org~dev~grp~analytics'
        data.name == 'analytics'
        data.description == 'analytics team'

        and: 'permissions is a map whose keys are peer grp ids and whose values are exactly rw or r'
        Map permissions = data.permissions as Map
        permissions.size() == 2
        permissions['018fd849-2a4d-7d0d-8d0d-aaaaaaaaaaaa~jade-tipi-org~dev~grp~lab_ops'] == 'rw'
        permissions['018fd849-2a4d-7d0d-8d0d-bbbbbbbbbbbb~jade-tipi-org~dev~grp~viewers'] == 'r'
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
                        id         : '018fd849-2a4d-7d0d-8d0d-cccccccccccc~jade-tipi-org~dev~grp~analytics',
                        name       : 'analytics',
                        permissions: [
                                '018fd849-2a4d-7d0d-8d0d-aaaaaaaaaaaa~jade-tipi-org~dev~grp~lab_ops': 'admin'
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
                        id                                                             : '018fd849-2a47-7777-8f01-aaaaaaaaaaaa~jade-tipi-org~dev~loc~freezer_a',
                        '018fd849-2a4d-7d0d-8d0d-aaaaaaaaaaaa~jade-tipi-org~dev~grp~x': 'rw'
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

    def "bare entity-type typ create example uses the human-readable data.id, data.name, optional data.description, and no data.kind/data.links shape"() {
        given:
        String json = readResource('/example/message/04-create-entity-type.json')

        when:
        Message message = JsonMapper.fromJson(json, Message)

        then:
        message.collection() == Collection.TYPE
        message.action() == Action.CREATE

        and: 'data.id is the materialized entity-type object id ending with the ~typ~ segment'
        Map data = message.data()
        data.id == '018fd849-2a43-7333-8c03-cccccccccccc~jade-tipi-org~dev~typ~plate_96'

        and: 'human-authored facts live flat under data, with no link-type kind discriminator and no data.links block'
        data.name == 'plate_96'
        data.description == '96-well sample plate'
        !data.containsKey('kind')
        !data.containsKey('links')

        and: 'no other facts leak onto the data root'
        data.keySet() == ['id', 'name', 'description'] as Set
    }

    def "ent create example uses the human-readable data.id, data.type_id, and explicit empty data.properties / data.links shape"() {
        given:
        String json = readResource('/example/message/06-create-entity.json')

        when:
        Message message = JsonMapper.fromJson(json, Message)

        then:
        message.collection() == Collection.ENTITY
        message.action() == Action.CREATE

        and: 'data.id is the materialized entity object id; data.type_id references the entity type'
        Map data = message.data()
        data.id == '018fd849-2a45-7555-8e05-eeeeeeeeeeee~jade-tipi-org~dev~ent~plate_a'
        data.type_id == '018fd849-2a43-7333-8c03-cccccccccccc~jade-tipi-org~dev~typ~plate_96'

        and: 'data.properties and data.links are present and explicitly empty on a simple create'
        data.properties == [:]
        data.links == [:]

        and: 'no human-authored facts leak to the data root next to id and type_id'
        data.keySet() == ['id', 'type_id', 'properties', 'links'] as Set
    }

    def "entity transaction example sequence shares one txn id and the entity references the entity-type by id"() {
        given: 'open, entity-type create, entity create, and commit examples'
        Message open = JsonMapper.fromJson(
                readResource('/example/message/01-open-transaction.json'), Message)
        Message typCreate = JsonMapper.fromJson(
                readResource('/example/message/04-create-entity-type.json'), Message)
        Message entCreate = JsonMapper.fromJson(
                readResource('/example/message/06-create-entity.json'), Message)
        Message commit = JsonMapper.fromJson(
                readResource('/example/message/09-commit-transaction.json'), Message)

        expect: 'all four messages chain through the same transaction uuid'
        String txnUuid = open.txn().uuid()
        typCreate.txn().uuid() == txnUuid
        entCreate.txn().uuid() == txnUuid
        commit.txn().uuid() == txnUuid

        and: 'open and commit are transaction-control messages on the txn collection'
        open.collection() == Collection.TRANSACTION
        open.action() == Action.OPEN
        commit.collection() == Collection.TRANSACTION
        commit.action() == Action.COMMIT

        and: 'the entity create points to the entity type by id (the in-resource cross-reference)'
        Map typData = typCreate.data()
        Map entData = entCreate.data()
        typCreate.collection() == Collection.TYPE
        typCreate.action() == Action.CREATE
        entCreate.collection() == Collection.ENTITY
        entCreate.action() == Action.CREATE
        entData.type_id == typData.id
    }

    def "typ + update add_property example uses the human-readable data.id, data.operation, data.property_id, and optional data.required shape"() {
        given:
        String json = readResource('/example/message/05-update-entity-type-add-property.json')

        when:
        Message message = JsonMapper.fromJson(json, Message)

        then:
        message.collection() == Collection.TYPE
        message.action() == Action.UPDATE

        and: 'data.id targets the existing bare entity-type root by id'
        Map data = message.data()
        data.id == '018fd849-2a43-7333-8c03-cccccccccccc~jade-tipi-org~dev~typ~plate_96'

        and: 'data.operation names the bounded supported variant'
        data.operation == 'add_property'

        and: 'data.property_id references the property-definition by id; data.required carries the wire-shape boolean'
        data.property_id == '018fd849-2a41-7111-8a01-aaaaaaaaaaaa~jade-tipi-org~dev~ppy~barcode'
        data.required == true

        and: 'no other facts leak onto the data root next to id, operation, property_id, and required'
        data.keySet() == ['id', 'operation', 'property_id', 'required'] as Set
    }

    def "typ + update add_property volume example registers the numeric property-definition with required=false"() {
        given:
        String json = readResource('/example/message/05a-update-entity-type-add-property-volume.json')

        when:
        Message message = JsonMapper.fromJson(json, Message)

        then:
        message.collection() == Collection.TYPE
        message.action() == Action.UPDATE

        and: 'data.id targets the same bare entity-type root as 05'
        Map data = message.data()
        data.id == '018fd849-2a43-7333-8c03-cccccccccccc~jade-tipi-org~dev~typ~plate_96'

        and: 'data.operation names the bounded supported variant'
        data.operation == 'add_property'

        and: 'data.property_id references the numeric property-definition; required carries the wire-shape false'
        data.property_id == '018fd849-2a42-7222-8b02-bbbbbbbbbbbb~jade-tipi-org~dev~ppy~volume'
        data.required == false

        and: 'no other facts leak onto the data root next to id, operation, property_id, and required'
        data.keySet() == ['id', 'operation', 'property_id', 'required'] as Set
    }

    def "every canonical assignment example targets a property registered on the entity type within the same transaction"() {
        given: 'the type-update registrations and the assignment examples'
        Message typUpdateBarcode = JsonMapper.fromJson(
                readResource('/example/message/05-update-entity-type-add-property.json'), Message)
        Message typUpdateVolume = JsonMapper.fromJson(
                readResource('/example/message/05a-update-entity-type-add-property-volume.json'), Message)
        Message entCreate = JsonMapper.fromJson(
                readResource('/example/message/06-create-entity.json'), Message)
        Message assignText = JsonMapper.fromJson(
                readResource('/example/message/07-assign-property-value-text.json'), Message)
        Message assignNumber = JsonMapper.fromJson(
                readResource('/example/message/08-assign-property-value-number.json'), Message)

        expect: 'both registrations target the entity type referenced by the entity the assignments point at'
        String typeId = entCreate.data().type_id
        typUpdateBarcode.data().id == typeId
        typUpdateVolume.data().id == typeId

        and: 'each assignment property_id is registered by one of the type updates'
        Set registeredPropertyIds = [typUpdateBarcode.data().property_id,
                                     typUpdateVolume.data().property_id] as Set
        registeredPropertyIds.contains(assignText.data().property_id)
        registeredPropertyIds.contains(assignNumber.data().property_id)

        and: 'both assignments target the canonical entity through the object-targeted form'
        assignText.data().object_collection == 'ent'
        assignNumber.data().object_collection == 'ent'
        assignText.data().object_id == entCreate.data().id
        assignNumber.data().object_id == entCreate.data().id

        and: 'all five share the same transaction uuid'
        String txnUuid = entCreate.txn().uuid()
        typUpdateBarcode.txn().uuid() == txnUuid
        typUpdateVolume.txn().uuid() == txnUuid
        assignText.txn().uuid() == txnUuid
        assignNumber.txn().uuid() == txnUuid
    }

    def "entity-type-with-property example sequence shares one txn id and the type-update references the property-definition by id"() {
        given: 'open, property-definition create, entity-type create, type-update add_property, and commit examples'
        Message open = JsonMapper.fromJson(
                readResource('/example/message/01-open-transaction.json'), Message)
        Message ppyCreate = JsonMapper.fromJson(
                readResource('/example/message/02-create-property-definition-text.json'), Message)
        Message typCreate = JsonMapper.fromJson(
                readResource('/example/message/04-create-entity-type.json'), Message)
        Message typUpdate = JsonMapper.fromJson(
                readResource('/example/message/05-update-entity-type-add-property.json'), Message)
        Message commit = JsonMapper.fromJson(
                readResource('/example/message/09-commit-transaction.json'), Message)

        expect: 'all five messages chain through the same transaction uuid'
        String txnUuid = open.txn().uuid()
        ppyCreate.txn().uuid() == txnUuid
        typCreate.txn().uuid() == txnUuid
        typUpdate.txn().uuid() == txnUuid
        commit.txn().uuid() == txnUuid

        and: 'open and commit are transaction-control messages on the txn collection'
        open.collection() == Collection.TRANSACTION
        open.action() == Action.OPEN
        commit.collection() == Collection.TRANSACTION
        commit.action() == Action.COMMIT

        and: 'the in-resource collections and actions match the human-readable type-with-property sequence'
        ppyCreate.collection() == Collection.PROPERTY
        ppyCreate.action() == Action.CREATE
        typCreate.collection() == Collection.TYPE
        typCreate.action() == Action.CREATE
        typUpdate.collection() == Collection.TYPE
        typUpdate.action() == Action.UPDATE

        and: 'the type-update targets the freshly-created bare entity-type by id and references the freshly-created property-definition by id'
        Map ppyData = ppyCreate.data()
        Map typData = typCreate.data()
        Map updateData = typUpdate.data()
        updateData.id == typData.id
        updateData.property_id == ppyData.id
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

    def "Collection.fromJson resolves prc and tsk and serializes back to the abbreviations"() {
        expect:
        Collection.fromJson('prc') == Collection.PROCEDURE
        Collection.fromJson('procedure') == Collection.PROCEDURE
        Collection.PROCEDURE.toJson() == 'prc'
        Collection.PROCEDURE.actions == [Action.CREATE, Action.UPDATE, Action.DELETE]
        Collection.fromJson('tsk') == Collection.TASK
        Collection.fromJson('task') == Collection.TASK
        Collection.TASK.toJson() == 'tsk'
        Collection.TASK.actions == [Action.CREATE, Action.UPDATE, Action.DELETE]
    }

    def "Collection.fromJson resolves fil and serializes back to the abbreviation"() {
        expect:
        Collection.fromJson('fil') == Collection.FILE
        Collection.fromJson('file') == Collection.FILE
        Collection.FILE.toJson() == 'fil'
        Collection.FILE.abbreviation == 'fil'
        Collection.FILE.name == 'file'
        Collection.FILE.actions == [Action.CREATE, Action.UPDATE, Action.DELETE]
    }

    def "file example sequence shares one txn id and wires the typed file and its assignment by id"() {
        given: 'the file type, retrieval_url definition, registration, file, and assignment examples'
        Message fileType = JsonMapper.fromJson(
                readResource('/example/message/23-create-file-type.json'), Message)
        Message urlDefinition = JsonMapper.fromJson(
                readResource('/example/message/24-create-property-definition-retrieval-url.json'), Message)
        Message addProperty = JsonMapper.fromJson(
                readResource('/example/message/25-update-file-type-add-property.json'), Message)
        Message file = JsonMapper.fromJson(
                readResource('/example/message/26-create-file.json'), Message)
        Message assignment = JsonMapper.fromJson(
                readResource('/example/message/27-assign-file-property-value.json'), Message)

        expect: 'all five messages chain through the same transaction uuid'
        String txnUuid = fileType.txn().uuid()
        [urlDefinition, addProperty, file, assignment].every { it.txn().uuid() == txnUuid }

        and: 'the file is a fil create typed by the ordinary file type — no kind discriminator, no hoisted structure'
        file.collection() == Collection.FILE
        file.action() == Action.CREATE
        !fileType.data().containsKey('kind')
        file.data().type_id == fileType.data().id
        file.data().keySet() == ['id', 'type_id', 'name', 'description'] as Set

        and: 'the registration joins the retrieval_url definition to the file type'
        addProperty.data().id == fileType.data().id
        addProperty.data().property_id == urlDefinition.data().id

        and: 'the assignment targets the fil root through the object-targeted form'
        assignment.data().kind == 'assignment'
        assignment.data().object_collection == 'fil'
        assignment.data().object_id == file.data().id
        assignment.data().property_id == urlDefinition.data().id
        (assignment.data().value as Map).containsKey('url')
    }

    def "prc + create example carries a top-level output_input map keyed by output and input ids"() {
        given:
        Message message = JsonMapper.fromJson(
                readResource('/example/message/20-create-procedure-with-output-input.json'), Message)

        expect:
        message.collection() == Collection.PROCEDURE
        message.action() == Action.CREATE

        and: 'the root facts are the id, the procedure type, a name, and output_input'
        Map data = message.data()
        data.id == '018fd849-3c15-7666-8a06-202020202020~jade-tipi-org~dev~prc~pool_run_1'
        data.type_id == '018fd849-3c10-7111-8a01-161616161616~jade-tipi-org~dev~typ~dna_pooling'
        data.keySet() == ['id', 'type_id', 'name', 'output_input'] as Set

        and: 'output_input maps each output ent to its inputs and open contribution objects'
        Map outputInput = data.output_input as Map
        outputInput.size() == 1
        String outputId = outputInput.keySet().first()
        outputId.contains('~ent~')
        Map inputs = outputInput[outputId] as Map
        inputs.size() == 2
        inputs.keySet().every { it.contains('~ent~') }
        inputs.values().every { (it as Map).containsKey('volume') }
    }

    def "procedure-task example sequence shares one txn id and wires the full provenance loop by id"() {
        given: 'the procedure type, task type, task, input link, procedure, fulfillment, and produced-by examples'
        Message procedureType = JsonMapper.fromJson(
                readResource('/example/message/16-create-procedure-type.json'), Message)
        Message taskType = JsonMapper.fromJson(
                readResource('/example/message/17-create-task-type.json'), Message)
        Message task = JsonMapper.fromJson(
                readResource('/example/message/18-create-task.json'), Message)
        Message inputLinkType = JsonMapper.fromJson(
                readResource('/example/message/19-create-task-input-link-type.json'), Message)
        Message inputLink = JsonMapper.fromJson(
                readResource('/example/message/19a-create-task-input-link.json'), Message)
        Message procedure = JsonMapper.fromJson(
                readResource('/example/message/20-create-procedure-with-output-input.json'), Message)
        Message fulfillsType = JsonMapper.fromJson(
                readResource('/example/message/21-create-fulfills-link-type.json'), Message)
        Message fulfills = JsonMapper.fromJson(
                readResource('/example/message/21a-create-fulfills-link.json'), Message)
        Message producedByType = JsonMapper.fromJson(
                readResource('/example/message/22-create-produced-by-link-type.json'), Message)
        Message producedBy = JsonMapper.fromJson(
                readResource('/example/message/22a-create-produced-by-link.json'), Message)

        expect: 'all ten messages chain through the same transaction uuid'
        String txnUuid = procedureType.txn().uuid()
        [taskType, task, inputLinkType, inputLink, procedure,
         fulfillsType, fulfills, producedByType, producedBy].every {
            it.txn().uuid() == txnUuid
        }

        and: 'the type kinds mirror the link_type discriminator convention'
        procedureType.data().kind == 'procedure_type'
        taskType.data().kind == 'task_type'
        [inputLinkType, fulfillsType, producedByType].every { it.data().kind == 'link_type' }

        and: 'the task type carries the procedure type by id, so instances need no procedure pointer'
        taskType.data().procedure_type_id == procedureType.data().id
        taskType.data().procedure_name == procedureType.data().name
        task.data().type_id == taskType.data().id
        !task.data().containsKey('procedure_type_id')

        and: 'the procedure instance is typed by the procedure type, not the task type'
        procedure.collection() == Collection.PROCEDURE
        task.collection() == Collection.TASK
        procedure.data().type_id == procedureType.data().id

        and: 'the input link joins the task to an input ent under the task_input type'
        inputLink.data().type_id == inputLinkType.data().id
        inputLink.data().left == task.data().id
        String inputEntId = inputLink.data().right
        inputEntId.contains('~ent~')

        and: 'the fulfillment link records the procedure that fulfilled the task'
        fulfills.data().type_id == fulfillsType.data().id
        fulfills.data().left == procedure.data().id
        fulfills.data().right == task.data().id

        and: 'the produced-by link joins an output_input output to the procedure'
        producedBy.data().type_id == producedByType.data().id
        producedBy.data().right == procedure.data().id
        Map outputInput = procedure.data().output_input as Map
        outputInput.containsKey(producedBy.data().left)

        and: 'the linked input ent contributed to that output'
        (outputInput[producedBy.data().left] as Map).containsKey(inputEntId)
    }

    def "schema rejects a prc create whose output_input contribution is not an object"() {
        given: 'a contribution value that is a bare number instead of an open contribution object'
        String json = '''
            {
              "txn": {
                "uuid": "018fd849-2a40-7abc-8a45-111111111111",
                "group": { "org": "jade-tipi-org", "grp": "dev" },
                "client": "kli",
                "user": "0000-0002-1825-0097"
              },
              "uuid": "018fd849-3c15-7666-8a06-202020202020",
              "collection": "prc",
              "action": "create",
              "data": {
                "id": "018fd849-3c15-7666-8a06-202020202020~jade-tipi-org~dev~prc~pool_run_1",
                "output_input": {
                  "018fd849-3c22-7ccc-8a0c-c3c3c3c3c3c3~jade-tipi-org~dev~ent~pool_1": {
                    "018fd849-3c20-7aaa-8a0a-a1a1a1a1a1a1~jade-tipi-org~dev~ent~sample_a": 5.0
                  }
                }
              }
            }
        '''
        Message message = JsonMapper.fromJson(json, Message)

        when:
        message.validate()

        then:
        thrown(ValidationException)
    }

    def "prc + create accepts a top-level inputs map keyed by input ids with task_id back-references"() {
        given: 'a pooling procedure whose inputs map carries two library inputs, each delivered by a task'
        String json = '''
            {
              "txn": {
                "uuid": "018fd849-2a40-7abc-8a45-111111111111",
                "group": { "org": "jade-tipi-org", "grp": "dev" },
                "client": "kli",
                "user": "0000-0002-1825-0097"
              },
              "uuid": "018fd849-3c15-7666-8a06-202020202020",
              "collection": "prc",
              "action": "create",
              "data": {
                "id": "018fd849-3c15-7666-8a06-202020202020~jade-tipi-org~dev~prc~pool_run_1",
                "type_id": "018fd849-3c10-7111-8a01-161616161616~jade-tipi-org~dev~typ~dna_pooling",
                "inputs": {
                  "018fd849-3c20-7aaa-8a0a-a1a1a1a1a1a1~jade-tipi-org~dev~ent~lib_a": {
                    "task_id": "018fd849-3c31-7bbb-8a1b-b2b2b2b2b2b2~jade-tipi-org~dev~tsk~sow_a"
                  },
                  "018fd849-3c21-7bbb-8a0b-b2b2b2b2b2b2~jade-tipi-org~dev~ent~lib_b": {}
                }
              }
            }
        '''
        Message message = JsonMapper.fromJson(json, Message)

        when:
        message.validate()

        then:
        noExceptionThrown()

        and: 'the inputs map is keyed by input object ids'
        Map inputs = message.data().inputs as Map
        inputs.size() == 2
        inputs.keySet().every { it.contains('~ent~') }

        and: 'a task-carried input back-references its delivering task; a direct input omits task_id'
        (inputs.values().find { (it as Map).task_id } as Map).task_id.contains('~tsk~')
        inputs.values().any { (it as Map).isEmpty() }
    }

    def "schema rejects a prc create whose inputs value is not an object"() {
        given: 'an inputs entry whose value is a bare string instead of an open object'
        String json = '''
            {
              "txn": {
                "uuid": "018fd849-2a40-7abc-8a45-111111111111",
                "group": { "org": "jade-tipi-org", "grp": "dev" },
                "client": "kli",
                "user": "0000-0002-1825-0097"
              },
              "uuid": "018fd849-3c15-7666-8a06-202020202020",
              "collection": "prc",
              "action": "create",
              "data": {
                "id": "018fd849-3c15-7666-8a06-202020202020~jade-tipi-org~dev~prc~pool_run_1",
                "inputs": {
                  "018fd849-3c20-7aaa-8a0a-a1a1a1a1a1a1~jade-tipi-org~dev~ent~lib_a": "sow_a"
                }
              }
            }
        '''
        Message message = JsonMapper.fromJson(json, Message)

        when:
        message.validate()

        then:
        thrown(ValidationException)
    }

    def "schema rejects a non-prc message whose data carries object-id keys under output_input"() {
        given: 'the same output_input map on an ent create, where snake_case keys are required'
        String json = '''
            {
              "txn": {
                "uuid": "018fd849-2a40-7abc-8a45-111111111111",
                "group": { "org": "jade-tipi-org", "grp": "dev" },
                "client": "kli",
                "user": "0000-0002-1825-0097"
              },
              "uuid": "018fd849-3c23-7ddd-8a0d-d4d4d4d4d4d4",
              "collection": "ent",
              "action": "create",
              "data": {
                "id": "018fd849-3c23-7ddd-8a0d-d4d4d4d4d4d4~jade-tipi-org~dev~ent~pool_1",
                "output_input": {
                  "018fd849-3c22-7ccc-8a0c-c3c3c3c3c3c3~jade-tipi-org~dev~ent~pool_1": {
                    "018fd849-3c20-7aaa-8a0a-a1a1a1a1a1a1~jade-tipi-org~dev~ent~sample_a": { "volume": 5.0 }
                  }
                }
              }
            }
        '''
        Message message = JsonMapper.fromJson(json, Message)

        when:
        message.validate()

        then:
        thrown(ValidationException)
    }

    def "ppy + create definition example uses the human-readable kind, id, name, and value_schema shape"() {
        given:
        Message message = JsonMapper.fromJson(
                readResource('/example/message/02-create-property-definition-text.json'), Message)

        expect:
        message.collection() == Collection.PROPERTY
        message.action() == Action.CREATE

        and:
        Map data = message.data()
        data.kind == 'definition'
        data.id == '018fd849-2a41-7111-8a01-aaaaaaaaaaaa~jade-tipi-org~dev~ppy~barcode'
        data.name == 'barcode'

        and: 'value_schema is preserved verbatim as a JSON-object value contract'
        Map valueSchema = data.value_schema as Map
        valueSchema.type == 'object'
        valueSchema.required == ['text']
        Map vsProperties = valueSchema.properties as Map
        Map textProperty = vsProperties.text as Map
        textProperty.type == 'string'

        and: 'no other facts leak onto the data root next to kind, id, name, and value_schema'
        data.keySet() == ['kind', 'id', 'name', 'value_schema'] as Set
    }

    def "ppy + create numeric definition example uses kind=definition with a multi-key value_schema"() {
        given:
        Message message = JsonMapper.fromJson(
                readResource('/example/message/03-create-property-definition-numeric.json'), Message)

        expect:
        message.collection() == Collection.PROPERTY
        message.action() == Action.CREATE

        and:
        Map data = message.data()
        data.kind == 'definition'
        data.id == '018fd849-2a42-7222-8b02-bbbbbbbbbbbb~jade-tipi-org~dev~ppy~volume'
        data.name == 'volume'

        and: 'value_schema accepts multi-key required arrays for compound JSON-object values'
        Map valueSchema = data.value_schema as Map
        valueSchema.type == 'object'
        valueSchema.required == ['number', 'unit_id']
        Map vsProperties = valueSchema.properties as Map
        ((Map) vsProperties.number).type == 'number'
        ((Map) vsProperties.unit_id).type == 'string'

        and: 'no other facts leak onto the data root'
        data.keySet() == ['kind', 'id', 'name', 'value_schema'] as Set
    }

    def "ppy + create assignment example uses the object-targeted kind, object_collection, object_id, property_id, and value shape"() {
        given:
        Message message = JsonMapper.fromJson(
                readResource('/example/message/07-assign-property-value-text.json'), Message)

        expect: 'assignment messages share the ppy collection and create action with definition messages'
        message.collection() == Collection.PROPERTY
        message.action() == Action.CREATE

        and:
        Map data = message.data()
        data.kind == 'assignment'
        data.object_collection == 'ent'
        data.object_id == '018fd849-2a45-7555-8e05-eeeeeeeeeeee~jade-tipi-org~dev~ent~plate_a'
        data.property_id == '018fd849-2a41-7111-8a01-aaaaaaaaaaaa~jade-tipi-org~dev~ppy~barcode'
        data.value == [text: 'barcode-1']

        and: 'no other facts leak onto the data root next to the canonical assignment keys'
        data.keySet() == ['kind', 'object_collection', 'object_id', 'property_id', 'value'] as Set
    }

    def "property-definition transaction example sequence shares one txn id and the assignment example references the property-definition and entity by id"() {
        given: 'open, ppy create, entity-type create, ent create, ppy assignment, and commit examples'
        Message open = JsonMapper.fromJson(
                readResource('/example/message/01-open-transaction.json'), Message)
        Message ppyCreate = JsonMapper.fromJson(
                readResource('/example/message/02-create-property-definition-text.json'), Message)
        Message typCreate = JsonMapper.fromJson(
                readResource('/example/message/04-create-entity-type.json'), Message)
        Message entCreate = JsonMapper.fromJson(
                readResource('/example/message/06-create-entity.json'), Message)
        Message ppyAssign = JsonMapper.fromJson(
                readResource('/example/message/07-assign-property-value-text.json'), Message)
        Message commit = JsonMapper.fromJson(
                readResource('/example/message/09-commit-transaction.json'), Message)

        expect: 'all six messages chain through the same transaction uuid'
        String txnUuid = open.txn().uuid()
        ppyCreate.txn().uuid() == txnUuid
        typCreate.txn().uuid() == txnUuid
        entCreate.txn().uuid() == txnUuid
        ppyAssign.txn().uuid() == txnUuid
        commit.txn().uuid() == txnUuid

        and: 'open and commit are transaction-control messages on the txn collection'
        open.collection() == Collection.TRANSACTION
        open.action() == Action.OPEN
        commit.collection() == Collection.TRANSACTION
        commit.action() == Action.COMMIT

        and: 'the object-targeted assignment cross-references resolve verbatim to the entity and property-definition ids'
        Map ppyData = ppyCreate.data()
        Map entData = entCreate.data()
        Map assignData = ppyAssign.data()
        ppyData.kind == 'definition'
        assignData.kind == 'assignment'
        assignData.object_collection == 'ent'
        assignData.object_id == entData.id
        assignData.property_id == ppyData.id
    }

    def "schema rejects a data.id that does not follow the object identifier convention"() {
        given: 'the pre-TASK-044 runbook drift shape: a literal segment where the UUIDv7 belongs'
        def txn = new Transaction(
                '018fd849-2a40-7abc-8a45-111111111111',
                new Group('jade-tipi-org', 'dev'),
                'kli',
                '0000-0002-1825-0097'
        )
        def message = Message.newInstance(txn, Collection.LOCATION, Action.CREATE,
                [id: 'jade-tipi-org~dev~plate96-demo~loc~plate_0001'])

        when:
        message.validate()

        then:
        ValidationException ex = thrown()
        ex.message.toLowerCase().contains('id')
    }

    @Unroll
    def "schema accepts a conforming data.id: #description"() {
        given:
        def txn = new Transaction(
                '018fd849-2a40-7abc-8a45-111111111111',
                new Group('jade-tipi-org', 'dev'),
                'kli',
                '0000-0002-1825-0097'
        )
        def message = Message.newInstance(txn, collection, Action.CREATE, data)

        when:
        message.validate()

        then:
        noExceptionThrown()

        where:
        description                                     | collection          | data
        'message-UUID form'                             | Collection.LOCATION | [id: '018fd849-2a47-7777-8f01-aaaaaaaaaaaa~jade-tipi-org~dev~loc~freezer_01']
        'transaction-UUID form, source-derived suffix'  | Collection.LOCATION | [id: '018fd849-c0c0-7000-8a01-c1a141e5e501~jade-tipi-org~dev~loc~esp_bin_019a3a60-9628']
        'deprecated legacy composite alias id'          | Collection.PROPERTY | [kind: 'assignment', id: '018fd849-2a45-7555-8e05-eeeeeeeeeeee~lbl_gov~jgi_pps~ent~plate_a~018fd849-2a41-7111-8a01-aaaaaaaaaaaa~lbl_gov~jgi_pps~ppy~barcode', entity_id: '018fd849-2a45-7555-8e05-eeeeeeeeeeee~lbl_gov~jgi_pps~ent~plate_a', property_id: '018fd849-2a41-7111-8a01-aaaaaaaaaaaa~lbl_gov~jgi_pps~ppy~barcode', value: [text: 'barcode-1']]
    }
}
