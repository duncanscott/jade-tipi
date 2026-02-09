/**
 * Part of Jade-Tipi — an open scientific metadata framework.
 * <p>
 * Copyright (c) 2025 Duncan Scott and Jade-Tipi contributors
 * SPDX-License-Identifier: AGPL-3.0-only OR Commercial
 * <p>
 * This file is part of a dual-licensed distribution:
 * - Under AGPL-3.0 for open-source use (see LICENSE)
 * - Under Commercial License for proprietary use (see DUAL-LICENSE.txt or contact licensing@jade-tipi.org)
 * <p>
 * https://jade-tipi.org/license
 */
package org.jadetipi.id;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class IdGeneratorTest {

    private static final String STEM_REGEX = "^[a-z]{16}~[0-9]+~[a-z]{1,16}$";

    private IdGenerator idGenerator;

    @BeforeEach
    void setUp() {
        idGenerator = new IdGenerator();
    }

    @Test
    void generatesMatchingStem() {
        String id = idGenerator.nextId();
        System.out.println(id);
        assertTrue(id.matches(STEM_REGEX), "stem must match regex");
    }

    @Test
    void concurrentUniquenessAndFormat() throws Exception {
        int tasks = 50;
        int perTask = 1_000;
        ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor();
        Set<String> all = ConcurrentHashMap.newKeySet(tasks * perTask);
        try {
            List<CompletableFuture<Void>> futures = new ArrayList<>(tasks);
            for (int t = 0; t < tasks; t++) {
                futures.add(CompletableFuture.runAsync(() -> {
                    for (int i = 0; i < perTask; i++) {
                        String id = idGenerator.nextId();
                        assertTrue(id.matches(STEM_REGEX), "stem must match regex");
                        boolean added = all.add(id);
                        if (!added) fail("duplicate id: " + id);
                    }
                }, exec));
            }
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        } finally {
            exec.shutdown();
            assert exec.awaitTermination(10, TimeUnit.SECONDS);
        }
        assertEquals(tasks * perTask, all.size(), "all IDs must be unique");
    }
}
