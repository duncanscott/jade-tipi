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
package org.jadetipi.jadetipi.controller

import com.fasterxml.jackson.databind.node.ObjectNode
import groovy.util.logging.Slf4j
import org.jadetipi.jadetipi.service.DocumentService
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

import static org.jadetipi.jadetipi.util.Constants.FIELD_DELETED_COUNT
import static org.jadetipi.jadetipi.util.Constants.MAX_DOCUMENT_SIZE_BYTES

@Slf4j
@RestController
@RequestMapping('/api/documents')
class DocumentController {

    private final DocumentService documentService

    DocumentController(DocumentService mongoDbService) {
        this.documentService = mongoDbService
    }

    /**
     * GET /api/documents - List all documents (ID and name only)
     */
    @GetMapping
    Flux<ObjectNode> listDocuments() {
        log.debug('Listing all documents (summary)')
        return documentService.findAllSummary()
                .doOnComplete { log.debug('Document listing complete') }
    }

    /**
     * GET /api/documents/{id} - Retrieve a document by ID
     */
    @GetMapping('/{id}')
    Mono<ResponseEntity<ObjectNode>> getDocument(@PathVariable('id') String id) {
        log.debug('Retrieving document: id={}', id)
        return documentService.findById(id)
                .doOnNext { log.info('Document retrieved: id={}', id) }
                .map(document -> ResponseEntity.ok(document))
                .defaultIfEmpty(ResponseEntity.notFound().build())
    }

    /**
     * POST /api/documents/{id} - Create a new document
     */
    @PostMapping(value = '/{id}', consumes = MediaType.APPLICATION_JSON_VALUE)
    Mono<ResponseEntity<ObjectNode>> createDocument(
            @PathVariable('id') String id,
            @RequestBody ObjectNode document) {

        log.debug('Creating document: id={}', id)

        // Validate document content
        if (document == null || document.isEmpty()) {
            return Mono.error(new ResponseStatusException(
                HttpStatus.BAD_REQUEST, 'Document cannot be empty'))
        }

        // Check document size
        String json = document.toString()
        if (json.length() > MAX_DOCUMENT_SIZE_BYTES) {
            log.warn('Document size exceeds limit: id={}, size={} bytes', id, json.length())
            return Mono.error(new ResponseStatusException(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "Document exceeds ${MAX_DOCUMENT_SIZE_BYTES} byte limit"))
        }

        return documentService.create(id, document)
                .doOnSuccess { log.info('Document created: id={}', id) }
                .doOnError { ex -> log.error('Failed to create document: id={}', id, ex) }
                .map(created -> ResponseEntity.status(HttpStatus.CREATED).body(created))
    }

    /**
     * PUT /api/documents/{id} - Update an existing document
     */
    @PutMapping(value = '/{id}', consumes = MediaType.APPLICATION_JSON_VALUE)
    Mono<ResponseEntity<ObjectNode>> updateDocument(
            @PathVariable('id') String id,
            @RequestBody ObjectNode document) {

        log.debug('Updating document: id={}', id)

        // Validate document content
        if (document == null || document.isEmpty()) {
            return Mono.error(new ResponseStatusException(
                HttpStatus.BAD_REQUEST, 'Document cannot be empty'))
        }

        // Check document size
        String json = document.toString()
        if (json.length() > MAX_DOCUMENT_SIZE_BYTES) {
            log.warn('Document size exceeds limit: id={}, size={} bytes', id, json.length())
            return Mono.error(new ResponseStatusException(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "Document exceeds ${MAX_DOCUMENT_SIZE_BYTES} byte limit"))
        }

        return documentService.update(id, document)
                .doOnSuccess { log.info('Document updated: id={}', id) }
                .doOnError { ex -> log.error('Failed to update document: id={}', id, ex) }
                .map(updated -> ResponseEntity.ok(updated))
    }

    /**
     * DELETE /api/documents/{id} - Delete a document
     */
    @DeleteMapping('/{id}')
    Mono<ResponseEntity<Void>> deleteDocument(@PathVariable('id') String id) {
        log.debug('Deleting document: id={}', id)
        return documentService.delete(id)
                .doOnNext { deleted ->
                    if (deleted) {
                        log.info('Document deleted: id={}', id)
                    } else {
                        log.warn('Document not found for deletion: id={}', id)
                    }
                }
                .map(deleted -> deleted ?
                        ResponseEntity.noContent().<Void> build() :
                        ResponseEntity.notFound().<Void> build())
    }

    /**
     * DELETE /api/documents/cleanup/corrupted - Delete all corrupted documents
     */
    @DeleteMapping('/cleanup/corrupted')
    Mono<ResponseEntity<Map<String, Long>>> cleanupCorruptedDocuments() {
        log.info('Starting corrupted documents cleanup')
        return documentService.deleteCorruptedDocuments()
                .doOnSuccess { count -> log.info('Cleaned up {} corrupted documents', count) }
                .map(count -> ResponseEntity.ok([(FIELD_DELETED_COUNT): count] as Map<String, Long>))
    }
}
