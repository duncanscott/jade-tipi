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

import org.jadetipi.dto.util.Constants;

import java.util.Objects;

public record Group(
        String org,
        String grp
) {
    public String getId() {
        return org + Constants.ID_SEPARATOR + grp;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Group(String organization1, String group2))) return false;
        return Objects.equals(grp, group2) && Objects.equals(org, organization1);
    }

    @Override
    public int hashCode() {
        return Objects.hash(org, grp);
    }
}
