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

import spock.lang.Specification

class IdGeneratorSpec extends Specification {

    private final IdGenerator generator = new IdGenerator()

    def "nextKey returns a 256-bit url-safe token without padding"() {
        when:
        def key = generator.nextKey()

        then:
        noExceptionThrown()
        key.length() == 43
        key ==~ /[A-Za-z0-9_-]{43}/
    }

    def "nextKey produces unique values for each invocation"() {
        when:
        def keys = (1..200).collect { generator.nextKey() }

        then:
        noExceptionThrown()
        keys.toSet().size() == keys.size()
    }
}
