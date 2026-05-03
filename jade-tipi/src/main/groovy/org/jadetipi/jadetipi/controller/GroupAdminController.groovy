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

import groovy.util.logging.Slf4j
import org.jadetipi.jadetipi.dto.GroupCreateRequest
import org.jadetipi.jadetipi.dto.GroupRecord
import org.jadetipi.jadetipi.dto.GroupUpdateRequest
import org.jadetipi.jadetipi.service.GroupAdminService
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * Admin-only group management endpoints. Authorization is enforced upstream
 * in {@code SecurityConfig} via {@code hasRole('jade-tipi-admin')} on
 * {@code /api/admin/**}.
 *
 * <p>This controller is intentionally narrow:
 * <ul>
 *   <li>{@code POST /api/admin/groups} — create a {@code grp} root.</li>
 *   <li>{@code GET /api/admin/groups} — list all {@code grp} roots.</li>
 *   <li>{@code GET /api/admin/groups/{id}} — read one.</li>
 *   <li>{@code PUT /api/admin/groups/{id}} — full-replacement update.</li>
 * </ul>
 * All persisted documents follow the {@code TASK-020} root-shape contract.
 * Permission enforcement on non-admin paths is intentionally not implemented.
 */
@Slf4j
@RestController
@RequestMapping('/api/admin/groups')
class GroupAdminController {

    private final GroupAdminService groupAdminService

    GroupAdminController(GroupAdminService groupAdminService) {
        this.groupAdminService = groupAdminService
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    Mono<ResponseEntity<GroupRecord>> createGroup(
            @RequestBody GroupCreateRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        log.debug('Admin create grp request: name={}, suppliedId={}',
                request?.name, request?.id)
        return groupAdminService.create(request)
                .map { GroupRecord record -> ResponseEntity.status(HttpStatus.CREATED).body(record) }
    }

    @GetMapping
    Mono<ResponseEntity<Map<String, Object>>> listGroups(@AuthenticationPrincipal Jwt jwt) {
        log.debug('Admin list grp request')
        return groupAdminService.list()
                .collectList()
                .map { List<GroupRecord> items ->
                    Map<String, Object> body = new LinkedHashMap<>()
                    body.put('items', items)
                    return ResponseEntity.ok(body)
                } as Mono<ResponseEntity<Map<String, Object>>>
    }

    @GetMapping('/{id}')
    Mono<ResponseEntity<GroupRecord>> getGroup(
            @PathVariable('id') String id,
            @AuthenticationPrincipal Jwt jwt) {
        log.debug('Admin read grp request: id={}', id)
        return groupAdminService.findById(id)
                .map { GroupRecord record -> ResponseEntity.ok(record) }
                .defaultIfEmpty(ResponseEntity.notFound().build())
    }

    @PutMapping(value = '/{id}', consumes = MediaType.APPLICATION_JSON_VALUE)
    Mono<ResponseEntity<GroupRecord>> updateGroup(
            @PathVariable('id') String id,
            @RequestBody GroupUpdateRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        log.debug('Admin update grp request: id={}', id)
        return groupAdminService.update(id, request)
                .map { GroupRecord record -> ResponseEntity.ok(record) }
                .defaultIfEmpty(ResponseEntity.notFound().build())
    }
}
