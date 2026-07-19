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

import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

import java.time.Duration

/**
 * Fetches workflow-INSTANCE-enriched esp entities from the pps-esp-entity
 * service's {@code GET /api/v2/entities/{uuid}} endpoint (TASK-072).
 *
 * <p>The bulk esp-entity CouchDB replica is a point-in-time snapshot whose
 * sample sheets predate the {@code workflow_instance_uuid} field — the id that
 * ties the sheets of one workflow run together. The importer needs that id to
 * group co-processed carriers into a single procedure, so where a locally-read
 * sheet lacks it, this client pulls the enriched entity from the service. The
 * V2 route is the right one: it is cache-first (regenerating a stale doc only
 * once, then saving it back so the replica converges) rather than the V1
 * always-regenerate route, so it does not hammer the production database.
 *
 * <p>Disabled unless {@code jadetipi.import.esp-api.base-url} is set. When
 * disabled, {@link #enrichedSampleSheets} returns an empty list and the
 * importer falls back to the local (workflow-instance-less) sheets.
 */
@Slf4j
@Service
class EspEnrichedEntityClient {

    private final WebClient webClient
    private final boolean enabled

    EspEnrichedEntityClient(WebClient.Builder webClientBuilder,
                            @Value('${jadetipi.import.esp-api.base-url:}') String baseUrl,
                            @Value('${jadetipi.import.esp-api.auth-header:}') String authHeader,
                            @Value('${jadetipi.import.esp-api.max-in-memory-mb:64}') int maxInMemoryMb) {
        this.enabled = baseUrl != null && !baseUrl.trim().isEmpty()
        if (!enabled) {
            this.webClient = null
            return
        }
        WebClient.Builder b = webClientBuilder
                .baseUrl(baseUrl.trim())
                .codecs { it.defaultCodecs().maxInMemorySize(maxInMemoryMb * 1024 * 1024) }
        if (authHeader != null && !authHeader.trim().isEmpty()) {
            // The configured value may be the full header ("Authorization: Bearer x")
            // or just the token value; normalize to a bare Authorization value.
            String value = authHeader.trim()
            if (value.toLowerCase().startsWith('authorization:')) {
                value = value.substring('authorization:'.length()).trim()
            }
            b = b.defaultHeader('Authorization', value)
        }
        this.webClient = b.build()
    }

    boolean isEnabled() {
        return enabled
    }

    /**
     * The enriched entity's {@code sample_sheets}, each carrying
     * {@code workflow_instance_uuid} (and name/state/window). Empty when the
     * client is disabled, the entity is unknown, or the fetch fails — the
     * caller then falls back to local sheets.
     */
    List<Map<String, Object>> enrichedSampleSheets(String uuid) {
        if (!enabled || !uuid) {
            return []
        }
        try {
            Map<String, Object> doc = webClient.get()
                    .uri('/api/v2/entities/{uuid}', uuid)
                    .retrieve()
                    .bodyToMono(Map)
                    .block(Duration.ofSeconds(90)) as Map<String, Object>
            Object sheets = doc?.get('sample_sheets')
            return (sheets instanceof List) ? (List<Map<String, Object>>) sheets : []
        } catch (Exception e) {
            log.warn('esp enriched fetch failed for {}: {} — falling back to local sheets',
                    uuid, e.message)
            return []
        }
    }
}
