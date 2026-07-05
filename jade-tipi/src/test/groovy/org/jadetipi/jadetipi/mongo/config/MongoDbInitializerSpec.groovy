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
package org.jadetipi.jadetipi.mongo.config

import org.jadetipi.dto.message.Collection
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import reactor.core.publisher.Mono
import spock.lang.Specification

class MongoDbInitializerSpec extends Specification {

    def "run() creates the loc collection when it does not yet exist"() {
        given:
        ReactiveMongoTemplate mongoTemplate = Mock(ReactiveMongoTemplate)
        MongoDbInitializer initializer = new MongoDbInitializer(mongoTemplate)

        when:
        initializer.run()

        then:
        1 * mongoTemplate.collectionExists('loc') >> Mono.just(false)
        1 * mongoTemplate.createCollection('loc') >> Mono.empty()
        0 * mongoTemplate.createCollection({ String name -> name != 'loc' })
        _ * mongoTemplate.collectionExists(_ as String) >> Mono.just(true)
        _ * mongoTemplate.getCollection(_ as String) >> Mono.empty()
    }

    def "run() does not recreate the loc collection when it already exists"() {
        given:
        ReactiveMongoTemplate mongoTemplate = Mock(ReactiveMongoTemplate)
        MongoDbInitializer initializer = new MongoDbInitializer(mongoTemplate)

        when:
        initializer.run()

        then:
        0 * mongoTemplate.createCollection(_ as String)
        _ * mongoTemplate.collectionExists(_ as String) >> Mono.just(true)
        _ * mongoTemplate.getCollection(_ as String) >> Mono.empty()
    }

    def "run() creates the backend-internal usr collection alongside the wire vocabulary"() {
        given:
        ReactiveMongoTemplate mongoTemplate = Mock(ReactiveMongoTemplate)
        MongoDbInitializer initializer = new MongoDbInitializer(mongoTemplate)

        when:
        initializer.run()

        then:
        1 * mongoTemplate.collectionExists('usr') >> Mono.just(false)
        1 * mongoTemplate.createCollection('usr') >> Mono.empty()
        _ * mongoTemplate.collectionExists(_ as String) >> Mono.just(true)
        _ * mongoTemplate.getCollection(_ as String) >> Mono.empty()
    }

    def "run() touches exactly the wire collections plus usr — no tipi, no msg"() {
        given:
        ReactiveMongoTemplate mongoTemplate = Mock(ReactiveMongoTemplate)
        MongoDbInitializer initializer = new MongoDbInitializer(mongoTemplate)
        Set<String> checked = [] as Set

        when:
        initializer.run()

        then:
        _ * mongoTemplate.collectionExists(_ as String) >> { String name ->
            checked << name
            return Mono.just(true)
        }
        _ * mongoTemplate.getCollection(_ as String) >> Mono.empty()

        and: 'every wire abbreviation and usr is ensured; retired/planned names are not'
        checked == (Collection.values().collect { it.abbreviation } + ['usr']) as Set
        !checked.contains('tipi')
        !checked.contains('msg')
    }
}
