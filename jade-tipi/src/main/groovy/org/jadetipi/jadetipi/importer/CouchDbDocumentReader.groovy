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
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.util.Assert
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFilterFunctions
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

/**
 * Minimal read-only client for the locally replicated CouchDB databases
 * (TASK-043). Fetches one document by ID; a 404 resolves empty. The reader
 * never writes to CouchDB and carries no retry/synchronization behavior —
 * the narrow import loop reads a small selected set on demand.
 *
 * <p>Configuration (`jadetipi.import.couchdb.*`) defaults to the local
 * docker-compose CouchDB with its loopback-only development credentials;
 * override via {@code JADETIPI_IMPORT_COUCHDB_URL} /
 * {@code JADETIPI_IMPORT_COUCHDB_USERNAME} /
 * {@code JADETIPI_IMPORT_COUCHDB_PASSWORD}.
 */
@Slf4j
@Service
class CouchDbDocumentReader {

    private final WebClient webClient

    CouchDbDocumentReader(WebClient.Builder webClientBuilder,
                          @Value('${jadetipi.import.couchdb.url:http://localhost:5984}') String url,
                          @Value('${jadetipi.import.couchdb.username:admin}') String username,
                          @Value('${jadetipi.import.couchdb.password:admin}') String password) {
        this.webClient = webClientBuilder
                .baseUrl(url)
                .filter(ExchangeFilterFunctions.basicAuthentication(username, password))
                .build()
    }

    /**
     * Fetch one CouchDB document. Empty {@link Mono} when the document (or
     * database) does not exist; other non-2xx responses surface as errors.
     */
    Mono<Map<String, Object>> findDocument(String database, String documentId) {
        Assert.hasText(database, 'database must not be blank')
        Assert.hasText(documentId, 'documentId must not be blank')
        return webClient.get()
                .uri('/{db}/{id}', database, documentId)
                .exchangeToMono({ ClientResponse response ->
                    if (response.statusCode() == HttpStatus.NOT_FOUND) {
                        log.debug('CouchDB document not found: db={}, id={}', database, documentId)
                        return response.releaseBody().then(Mono.empty())
                    }
                    if (response.statusCode().isError()) {
                        return response.createException().flatMap({ ex -> Mono.error(ex) })
                    }
                    return response.bodyToMono(Map)
                }) as Mono<Map<String, Object>>
    }
}
