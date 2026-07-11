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
package org.jadetipi.jadetipi.importer

import spock.lang.Specification

/**
 * TASK-070: the config service reads the vendored esplims workflow snapshot
 * and answers the protocol count (sheet-merge key), input types, and
 * lab/administrative flag per workflow — with conservative handling of
 * workflows absent from the snapshot.
 */
class EspWorkflowConfigServiceSpec extends Specification {

    EspWorkflowConfigService service = new EspWorkflowConfigService()

    def 'the vendored snapshot answers protocol count, input types, and lab flag'() {
        expect: 'lab workflows with their real protocol counts (the sheet-merge key)'
        service.isKnown('Aliquot Creation')
        service.protocolCount('Aliquot Creation') == 2
        service.inputTypes('Aliquot Creation') == ['SOW Item']
        service.isLabProcedure('Aliquot Creation')

        and: 'higher-protocol workflows'
        service.protocolCount('Illumina Sequencing') == 3
        service.protocolCount('qPCR') == 4

        and: 'single-sheet workflows'
        service.protocolCount('Sample QC') == 1
        service.inputTypes('Sample QC') == ['Nucleic Acid']
        service.isLabProcedure('Sample QC')

        and: 'administrative workflows are excluded'
        !service.isLabProcedure('Sequencing Project Edit')
        !service.isLabProcedure('SOW Item Edit')
        !service.isLabProcedure('Final Deliverable Add Create')
    }

    def 'an unknown workflow is conservatively non-procedure with a single protocol'() {
        expect:
        !service.isKnown('Some Brand New Workflow')
        service.protocolCount('Some Brand New Workflow') == 1
        service.inputTypes('Some Brand New Workflow') == []
        !service.isLabProcedure('Some Brand New Workflow')
    }

    def 'a missing resource yields an empty, all-unknown config (never throws)'() {
        when:
        EspWorkflowConfigService empty = new EspWorkflowConfigService((InputStream) null)

        then:
        !empty.isKnown('Aliquot Creation')
        empty.protocolCount('Aliquot Creation') == 1
        !empty.isLabProcedure('Aliquot Creation')
    }
}
