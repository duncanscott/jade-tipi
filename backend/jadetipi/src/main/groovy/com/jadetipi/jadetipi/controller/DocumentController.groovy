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
package com.jadetipi.jadetipi.controller

import com.fasterxml.jackson.databind.node.ObjectNode
import com.jadetipi.jadetipi.service.DocumentService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono
import reactor.core.publisher.Flux

@RestController
@RequestMapping("/api/documents")
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
        return documentService.findAllSummary()
    }

    /**
     * GET /api/documents/{id} - Retrieve a document by ID
     */
    @GetMapping("/{id}")
    Mono<ResponseEntity<ObjectNode>> getDocument(@PathVariable("id") String id) {
        return documentService.findById(id)
                .map(document -> ResponseEntity.ok(document))
                .defaultIfEmpty(ResponseEntity.notFound().build())
    }

    /**
     * POST /api/documents/{id} - Create a new document
     */
    @PostMapping("/{id}")
    Mono<ResponseEntity<ObjectNode>> createDocument(
            @PathVariable("id") String id,
            @RequestBody ObjectNode document) {
        return documentService.create(id, document)
                .map(created -> ResponseEntity.status(HttpStatus.CREATED).body(created))
                .onErrorResume(e -> Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).build()))
    }

    /**
     * PUT /api/documents/{id} - Update an existing document
     */
    @PutMapping("/{id}")
    Mono<ResponseEntity<ObjectNode>> updateDocument(
            @PathVariable("id") String id,
            @RequestBody ObjectNode document) {
        return documentService.update(id, document)
                .map(updated -> ResponseEntity.ok(updated))
    }

    /**
     * DELETE /api/documents/{id} - Delete a document
     */
    @DeleteMapping("/{id}")
    Mono<ResponseEntity<Void>> deleteDocument(@PathVariable("id") String id) {
        return documentService.delete(id)
                .map(deleted -> deleted ?
                        ResponseEntity.noContent().<Void>build() :
                        ResponseEntity.notFound().<Void>build())
    }

    /**
     * DELETE /api/documents/cleanup/corrupted - Delete all corrupted documents
     */
    @DeleteMapping("/cleanup/corrupted")
    Mono<ResponseEntity<Map<String, Long>>> cleanupCorruptedDocuments() {
        return documentService.deleteCorruptedDocuments()
                .map(count -> ResponseEntity.ok([deletedCount: count] as Map<String, Long>))
    }
}
