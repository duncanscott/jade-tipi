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
package org.jadetipi.dto.message;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.f4b6a3.uuid.UuidCreator;
import org.jadetipi.dto.util.Constants;

import java.util.Objects;

/**
 * Transaction identifier containing UUIDv7, group, and client.
 *
 * <p>JSON structure:
 * <pre>
 * {
 *   "uuid": "018fd849-2a40-7abc-8a45-111111111111",
 *   "group": { "org": "...", "grp": "..." },
 *   "client": "jade-cli"
 * }
 * </pre>
 *
 * <p>Transaction ID string format: {@code <uuid>~<org>~<grp>~<client>}
 */
public record Transaction(
        @JsonProperty("uuid") String uuid,
        @JsonProperty("group") Group group,
        @JsonProperty("client") String client
) {
    public static Transaction newInstance(String org, String grp, String client) {
        return newInstance(new Group(org, grp), client);
    }

    public static Transaction newInstance(Group group, String client) {
        return new Transaction(UuidCreator.getTimeOrderedEpoch().toString(), group, client);
    }

    @JsonIgnore
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
