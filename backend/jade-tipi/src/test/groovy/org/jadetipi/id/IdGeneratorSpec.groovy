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
package org.jadetipi.id

import org.jadetipi.id.IdGenerator
import spock.lang.Ignore
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Ignore
class IdGeneratorSpec extends Specification {

    static final String STEM_REGEX = '^[a-z]{6}[0-9]+[a-z]{3}$'

    @Ignore
    def "generates matching stem"() {
        given:
        def gen = new IdGenerator()

        when:
        String id = gen.nextId()

        then:
        id ==~ STEM_REGEX
    }

    @Ignore
    def "concurrent uniqueness and format"() {
        given:
        def gen = new IdGenerator()
        int tasks = 50
        int perTask = 1_000
        def exec = Executors.newVirtualThreadPerTaskExecutor()
        def all = ConcurrentHashMap.newKeySet(tasks * perTask)

        when:
        def futures = (0..<tasks).collect { t ->
            CompletableFuture.runAsync({
                for (int i = 0; i < perTask; i++) {
                    String id = gen.nextId()
                    assert id ==~ STEM_REGEX : "stem must match regex"
                    boolean added = all.add(id)
                    assert added : "duplicate id: $id"
                }
            }, exec)
        }
        CompletableFuture.allOf((CompletableFuture[]) futures.toArray(new CompletableFuture[0])).join()
        exec.shutdown()
        exec.awaitTermination(10, TimeUnit.SECONDS)

        then:
        all.size() == tasks * perTask

        cleanup:
        if (!exec.isShutdown()) {
            exec.shutdownNow()
            exec.awaitTermination(5, TimeUnit.SECONDS)
        }
    }
}
