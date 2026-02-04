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
package org.jadetipi.dto.message;

import org.jadetipi.dto.util.Constants;

import java.util.Objects;

public record Transaction(
        String uuid,
        Group group,
        String client
) {
    public String getId() {
        return uuid + Constants.ID_SEPARATOR + group.getId() + Constants.ID_SEPARATOR + client;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Transaction(String uuid1, Group group1, String client1))) return false;
        return Objects.equals(uuid, uuid1) && Objects.equals(group, group1) && Objects.equals(client, client1);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid, group, client);
    }
}
