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

import groovy.json.JsonOutput
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.util.Assert
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFilterFunctions
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Flux
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
     * The clarity process-type histogram from the replica's own
     * {@code _design/processes/_view/process-type-count} view (TASK-067):
     * display name → process count.
     */
    Mono<Map<String, Long>> processTypeCounts(String database) {
        Assert.hasText(database, 'database must not be blank')
        return webClient.get()
                .uri('/{db}/_design/processes/_view/process-type-count?group=true', database)
                .retrieve()
                .bodyToMono(Map)
                .map { Map body ->
                    Map<String, Long> counts = new LinkedHashMap<>()
                    (body.get('rows') as List ?: []).each { Object row ->
                        Map r = row as Map
                        counts.put(r.get('key') as String, ((Number) r.get('value')).longValue())
                    }
                    return counts
                } as Mono<Map<String, Long>>
    }

    /**
     * Process document ids for one clarity process type, via the replica's
     * {@code _design/processes/_view/process-type} view (keys are
     * {@code [type name, index]}; each row's id is the process document
     * id). Paged with startkey/startkey_docid so any type size streams in
     * constant memory; {@code limit} (when positive) caps the total.
     */
    Flux<String> processDocIdsByType(String database, String processTypeName, Integer limit) {
        Assert.hasText(database, 'database must not be blank')
        Assert.hasText(processTypeName, 'processTypeName must not be blank')
        int remaining = (limit == null || limit <= 0) ? Integer.MAX_VALUE : limit
        return pageProcessDocIds(database, processTypeName, null, null, remaining)
    }

    private static final int VIEW_PAGE_SIZE = 1000

    private Flux<String> pageProcessDocIds(String database, String processTypeName,
                                           Object startkey, String startkeyDocid, int remaining) {
        int pageSize = Math.min(remaining, VIEW_PAGE_SIZE)
        // resuming from the last row of the previous page: fetch one extra
        // and drop the duplicate first row
        int requestLimit = startkeyDocid == null ? pageSize : pageSize + 1
        String startkeyJson = startkey != null
                ? JsonOutput.toJson(startkey)
                : JsonOutput.toJson([processTypeName])
        String endkeyJson = JsonOutput.toJson([processTypeName, [:]])

        return webClient.get()
                .uri({ uriBuilder ->
                    def builder = uriBuilder.path('/{db}/_design/processes/_view/process-type')
                            .queryParam('startkey', '{startkey}')
                            .queryParam('endkey', '{endkey}')
                            .queryParam('limit', requestLimit)
                    if (startkeyDocid != null) {
                        builder = builder.queryParam('startkey_docid', '{startkeyDocid}')
                        return builder.build(database, startkeyJson, endkeyJson, startkeyDocid)
                    }
                    return builder.build(database, startkeyJson, endkeyJson)
                })
                .retrieve()
                .bodyToMono(Map)
                .flatMapMany { Map body ->
                    List rows = (body.get('rows') as List) ?: []
                    if (startkeyDocid != null && !rows.isEmpty()) {
                        rows = rows.drop(1)
                    }
                    List<String> ids = rows.collect { Object row -> (row as Map).get('id') as String }
                    if (ids.size() > remaining) {
                        ids = ids.take(remaining)
                    }
                    boolean lastPage = rows.size() < pageSize || ids.size() >= remaining
                    Flux<String> page = Flux.fromIterable(ids)
                    if (lastPage || rows.isEmpty()) {
                        return page
                    }
                    Map lastRow = rows.last() as Map
                    int stillWanted = remaining - ids.size()
                    return page.concatWith(Flux.defer {
                        pageProcessDocIds(database, processTypeName,
                                lastRow.get('key'), lastRow.get('id') as String, stillWanted)
                    })
                }
    }

    /**
     * Every row id a view emits under one scalar key (e.g. esp
     * {@code entity_views/by_type_name} keyed by type name), paged with
     * startkey_docid; {@code limit} (when positive) caps the total
     * (TASK-069).
     */
    Flux<String> docIdsByViewKey(String database, String designDoc, String viewName,
                                 String key, Integer limit) {
        Assert.hasText(database, 'database must not be blank')
        Assert.hasText(designDoc, 'designDoc must not be blank')
        Assert.hasText(viewName, 'viewName must not be blank')
        Assert.hasText(key, 'key must not be blank')
        int remaining = (limit == null || limit <= 0) ? Integer.MAX_VALUE : limit
        return pageDocIdsByViewKey(database, designDoc, viewName, key, null, remaining)
    }

    private Flux<String> pageDocIdsByViewKey(String database, String designDoc, String viewName,
                                             String key, String startkeyDocid, int remaining) {
        int pageSize = Math.min(remaining, VIEW_PAGE_SIZE)
        int requestLimit = startkeyDocid == null ? pageSize : pageSize + 1
        String keyJson = JsonOutput.toJson(key)

        return webClient.get()
                .uri({ uriBuilder ->
                    def builder = uriBuilder.path('/{db}/_design/{design}/_view/{view}')
                            .queryParam('key', '{key}')
                            .queryParam('limit', requestLimit)
                    if (startkeyDocid != null) {
                        builder = builder.queryParam('startkey_docid', '{startkeyDocid}')
                        return builder.build(database, designDoc, viewName, keyJson, startkeyDocid)
                    }
                    return builder.build(database, designDoc, viewName, keyJson)
                })
                .retrieve()
                .bodyToMono(Map)
                .flatMapMany { Map body ->
                    List rows = (body.get('rows') as List) ?: []
                    if (startkeyDocid != null && !rows.isEmpty()) {
                        rows = rows.drop(1)
                    }
                    List<String> ids = rows.collect { Object row -> (row as Map).get('id') as String }
                    if (ids.size() > remaining) {
                        ids = ids.take(remaining)
                    }
                    boolean lastPage = rows.size() < pageSize || ids.size() >= remaining
                    Flux<String> page = Flux.fromIterable(ids)
                    if (lastPage || rows.isEmpty()) {
                        return page
                    }
                    return page.concatWith(Flux.defer {
                        pageDocIdsByViewKey(database, designDoc, viewName, key,
                                ids.last(), remaining - ids.size())
                    })
                }
    }

    /**
     * Every document id under an id prefix (e.g. {@code files_}), streamed
     * through {@code _all_docs} with startkey/startkey_docid paging —
     * constant memory at any prefix size (TASK-068). Downstream
     * cancellation stops the paging.
     */
    Flux<String> docIdsByPrefix(String database, String prefix) {
        Assert.hasText(database, 'database must not be blank')
        Assert.hasText(prefix, 'prefix must not be blank')
        return pageDocIdsByPrefix(database, prefix, null)
    }

    private Flux<String> pageDocIdsByPrefix(String database, String prefix, String startkeyDocid) {
        int requestLimit = startkeyDocid == null ? VIEW_PAGE_SIZE : VIEW_PAGE_SIZE + 1
        String startkeyJson = startkeyDocid == null
                ? JsonOutput.toJson(prefix)
                : JsonOutput.toJson(startkeyDocid)
        String endkeyJson = JsonOutput.toJson(prefix + '￰')

        return webClient.get()
                .uri({ uriBuilder ->
                    uriBuilder.path('/{db}/_all_docs')
                            .queryParam('startkey', '{startkey}')
                            .queryParam('endkey', '{endkey}')
                            .queryParam('limit', requestLimit)
                            .build(database, startkeyJson, endkeyJson)
                })
                .retrieve()
                .bodyToMono(Map)
                .flatMapMany { Map body ->
                    List rows = (body.get('rows') as List) ?: []
                    if (startkeyDocid != null && !rows.isEmpty()) {
                        rows = rows.drop(1)
                    }
                    List<String> ids = rows.collect { Object row -> (row as Map).get('id') as String }
                    Flux<String> page = Flux.fromIterable(ids)
                    if (ids.size() < VIEW_PAGE_SIZE) {
                        return page
                    }
                    String lastId = ids.last()
                    return page.concatWith(Flux.defer {
                        pageDocIdsByPrefix(database, prefix, lastId)
                    })
                }
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
