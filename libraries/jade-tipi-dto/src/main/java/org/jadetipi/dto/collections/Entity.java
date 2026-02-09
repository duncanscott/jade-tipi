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
package org.jadetipi.dto.collections;

import org.jadetipi.dto.util.Constants;

public record Entity(
        Transaction transactionId, // tansactionId of message that created Entity
        String messageUuid, // UUID of message that created Entity
        String type,
        String subtype
) {
    public String idString() {
        if (subtype != null) {
            return transactionId.getId() + Constants.ID_SEPARATOR + messageUuid + Constants.ID_SEPARATOR + type + Constants.ID_SEPARATOR + subtype;
        }
        return transactionId.getId() + Constants.ID_SEPARATOR + messageUuid + Constants.ID_SEPARATOR + type;
    }
}
