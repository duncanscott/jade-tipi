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

import spock.lang.Specification

/**
 * TASK-044 coverage for the object identifier convention predicate:
 * {@code <uuidv7>~<org>~<grp>~<collection>~<suffix>}, with {@code genesis}
 * as the single sanctioned non-UUID segment and composite legacy assignment
 * IDs conforming in both halves. The materializer only warns on
 * nonconforming IDs; this spec pins the structural check itself.
 */
class CommittedTransactionMaterializerIdConventionSpec extends Specification {

    static final String OBJ = '018fd849-2a47-7777-8f01-aaaaaaaaaaaa~jade-tipi-org~dev~loc~freezer_01'
    static final String PPY = '018fd849-2a41-7123-8c67-333333333333~lbl_gov~jgi_pps~ppy~barcode'

    def 'conforming identifiers pass'() {
        expect:
        CommittedTransactionMaterializer.isConformingObjectId(id)

        where:
        id << [
                OBJ,
                PPY,
                // transaction-UUID form with a source-derived suffix (D4)
                '018fd849-c0c0-7000-8a01-c1a141e5e501~jade-tipi-org~dev~loc~esp_bin_019a3a60-9628',
                // the sanctioned genesis exception
                'genesis~jade-tipi-org~dev~usr~jdtp-admin',
                // procedure and task collections (TASK-048)
                '018fd849-3b03-7333-8a03-cccccccccccc~jade-tipi-org~dev~prc~pool_run_1',
                '018fd849-3b04-7444-8a04-dddddddddddd~jade-tipi-org~dev~tsk~pool_batch_7',
                // file collection (TASK-049)
                '018fd849-3e02-7222-8a02-bbbbbbbbbbbb~jade-tipi-org~dev~fil~run42_r1_fastq',
                // composite legacy assignment id: object + property
                OBJ + '~' + PPY
        ]
    }

    def 'nonconforming identifiers are rejected'() {
        expect:
        !CommittedTransactionMaterializer.isConformingObjectId(id)

        where:
        id << [
                null,
                '',
                // too few segments (the pre-TASK-044 itest fixture shape)
                'jadetipi-itest-plate96~typ~container_abc',
                // literal segment where the UUIDv7 belongs (the runbook drift)
                'jade-tipi-org~dev~plate96-demo~ppy~barcode',
                // UUID version 4 in the UUID position
                'jade-tipi-org~dev~7c2f8f60-4c1e-4d0a-9d2e-1f2e3d4c5b6a~grp~analytics',
                // unknown collection segment
                '018fd849-2a47-7777-8f01-aaaaaaaaaaaa~jade-tipi-org~dev~xyz~thing',
                // composite whose second half does not conform
                OBJ + '~org~grp~not-a-uuid~ppy~barcode',
                // over the 235-character single-form size limit (suffix > 128)
                '018fd849-2a47-7777-8f01-aaaaaaaaaaaa~jade-tipi-org~dev~ent~' + ('a' * 200)
        ]
    }
}
