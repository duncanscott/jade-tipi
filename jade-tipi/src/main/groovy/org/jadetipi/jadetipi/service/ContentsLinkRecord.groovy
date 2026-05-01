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
 * One materialized {@code contents} link returned by
 * {@link ContentsLinkReadService}.
 *
 * <p>Carries the materialized {@code lnk} document fields verbatim: the link
 * id (Mongo {@code _id}, equal to the original {@code data.id}), the
 * {@code type_id}, the {@code left} and {@code right} endpoint id strings,
 * the instance {@code properties} (e.g. {@code properties.position} for a
 * plate-well placement), and the reserved {@code _jt_provenance}
 * sub-document written by {@link CommittedTransactionMaterializer}.
 *
 * <p>Endpoint resolution against {@code loc} or {@code ent} is intentionally
 * not performed at this boundary; callers receive the raw id strings.
 */
@Immutable
class ContentsLinkRecord {
    String linkId
    String typeId
    String left
    String right
    Map<String, Object> properties
    Map<String, Object> provenance
}
