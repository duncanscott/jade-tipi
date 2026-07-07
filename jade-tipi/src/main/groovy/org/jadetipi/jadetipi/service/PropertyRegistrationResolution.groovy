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

import groovy.transform.Immutable

/**
 * Outcome of the inheritance-aware property registration walk (TASK-061):
 * whether the property is registered on the target's type chain, and
 * whether the {@code history: false} opt-out convention leaves history
 * enabled for this (object type, property) pair.
 */
@Immutable
class PropertyRegistrationResolution {
    boolean registered
    boolean historyEnabled

    static PropertyRegistrationResolution unregistered() {
        return new PropertyRegistrationResolution(registered: false, historyEnabled: false)
    }

    static PropertyRegistrationResolution registered(boolean historyEnabled) {
        return new PropertyRegistrationResolution(registered: true, historyEnabled: historyEnabled)
    }
}
