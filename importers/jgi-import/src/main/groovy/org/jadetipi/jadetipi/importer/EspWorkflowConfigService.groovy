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

import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j
import org.springframework.stereotype.Service

/**
 * Reads the vendored ESP workflow-configuration snapshot (TASK-070),
 * generated from the esplims repo by
 * {@code scripts/regen_esp_workflow_config.py} into
 * {@code /esp-workflow-config.json} on the classpath. The importer carries
 * no build- or run-time dependency on esplims.
 *
 * <p>Per workflow name the snapshot gives: the ordered {@code protocols},
 * a {@code protocol_count} (how many sample sheets form one workflow
 * INSTANCE — the sheet-merge count for procedure reconstruction), the
 * {@code input_types} (accepted input entity types), and a
 * {@code lab_procedure} flag (administrative CRUD workflows are excluded).
 *
 * <p>An unknown workflow (absent from the snapshot — e.g. added to esplims
 * after the last regeneration) is treated conservatively as NOT a lab
 * procedure with a single protocol, and logged, so it does not fabricate a
 * procedure until the snapshot is regenerated.
 */
@Slf4j
@Service
class EspWorkflowConfigService {

    private static final String RESOURCE = '/esp-workflow-config.json'

    private final Map<String, Map<String, Object>> workflows

    EspWorkflowConfigService() {
        this(EspWorkflowConfigService.getResourceAsStream(RESOURCE))
    }

    /** Test seam: load from an explicit stream. */
    EspWorkflowConfigService(InputStream stream) {
        if (stream == null) {
            log.warn('esp-workflow-config.json not found on classpath; all workflows treated as unknown')
            this.workflows = [:]
            return
        }
        Object parsed = new JsonSlurper().parse(stream)
        Map raw = (parsed instanceof Map ? ((Map) parsed).get('workflows') : null) as Map ?: [:]
        Map<String, Map<String, Object>> byName = new LinkedHashMap<>()
        raw.each { Object name, Object body ->
            if (body instanceof Map) {
                byName.put(name as String, (Map<String, Object>) body)
            }
        }
        this.workflows = byName
        log.info('Loaded {} esp workflow config(s) from the vendored snapshot', byName.size())
    }

    /** True when the workflow name is present in the snapshot. */
    boolean isKnown(String workflowName) {
        return workflows.containsKey(workflowName)
    }

    /**
     * How many sample sheets constitute one instance of this workflow (its
     * protocol count). Unknown → 1 (each sheet stands alone).
     */
    int protocolCount(String workflowName) {
        Map<String, Object> wf = workflows.get(workflowName)
        if (wf == null) {
            return 1
        }
        Object n = wf.get('protocol_count')
        return n instanceof Number ? Math.max(1, ((Number) n).intValue()) : 1
    }

    /** The accepted input entity types for this workflow (empty when unknown). */
    List<String> inputTypes(String workflowName) {
        Map<String, Object> wf = workflows.get(workflowName)
        Object types = wf?.get('input_types')
        return types instanceof List ? new ArrayList<>((List<String>) types) : []
    }

    /**
     * Whether this workflow is a real lab procedure (vs administrative
     * CRUD). Unknown workflows are conservatively NOT procedures, and the
     * first encounter is warned so the stale snapshot is visible.
     */
    boolean isLabProcedure(String workflowName) {
        Map<String, Object> wf = workflows.get(workflowName)
        if (wf == null) {
            log.warn('esp workflow "{}" is not in the vendored config snapshot — treating as non-procedure; ' +
                    'regenerate esp-workflow-config.json if it is a real lab workflow', workflowName)
            return false
        }
        return wf.get('lab_procedure') == true
    }
}
