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
package org.jadetipi.jade.cli

import org.jadetipi.cli.KeycloakTokenCli

class JadeCli {

    private static final String DEFAULT_CLIENT_ID = 'jade-cli'
    private static final String DEFAULT_CLIENT_SECRET = '62ba4c1e-7f7e-46c7-9793-f752c63f2e10'

    static void main(String[] args) {
        new KeycloakTokenCli(
                'jade',
                'JADE',
                DEFAULT_CLIENT_ID,
                DEFAULT_CLIENT_SECRET
        ).run(args)
    }
}
