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
package org.jadetipi.tipi.cli

import org.jadetipi.cli.JadeTipiCli

class TipiCli {

    private static final String DEFAULT_CLIENT_ID = 'tipi-cli'
    private static final String DEFAULT_CLIENT_SECRET = '7e8d5df5-5afb-4cc0-8d56-9f3f5c7cc5fd'

    static void main(String[] args) {
        new JadeTipiCli(
                'tipi',
                'TIPI',
                DEFAULT_CLIENT_ID,
                DEFAULT_CLIENT_SECRET
        ).run(args)
    }
}
