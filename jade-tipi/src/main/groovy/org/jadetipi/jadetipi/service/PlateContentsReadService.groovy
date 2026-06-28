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

import groovy.util.logging.Slf4j
import org.springframework.stereotype.Service
import org.springframework.util.Assert
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * Builds the first plate-shaped read model over materialized contents links.
 *
 * <p>The service composes existing read services only: it reads
 * {@link ContentsLinkRecord} values through {@link ContentsLinkReadService}
 * and resolves each link's right endpoint through
 * {@link EntityPropertyValuesReadService}. It performs no Mongo writes, adds
 * no materializer projection, and does not validate domain semantics beyond
 * placing well-positioned links into a fixed 96-well grid.
 */
@Slf4j
@Service
class PlateContentsReadService {

    static final Integer ROW_COUNT = 8
    static final Integer COLUMN_COUNT = 12
    static final List<String> ROW_LABELS =
            ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H'].asImmutable()

    static final String POSITION = 'position'
    static final String POSITION_KIND = 'kind'
    static final String POSITION_KIND_PLATE_WELL = 'plate_well'
    static final String POSITION_ROW = 'row'
    static final String POSITION_COLUMN = 'column'

    private final ContentsLinkReadService contentsLinkReadService
    private final EntityPropertyValuesReadService entityPropertyValuesReadService

    PlateContentsReadService(
            ContentsLinkReadService contentsLinkReadService,
            EntityPropertyValuesReadService entityPropertyValuesReadService) {

        this.contentsLinkReadService = contentsLinkReadService
        this.entityPropertyValuesReadService = entityPropertyValuesReadService
    }

    /**
     * Returns a fixed 96-well grid for {@code containerId}. Empty contents
     * links produce the same 96 wells with empty {@code contents} lists.
     */
    Mono<PlateContentsRecord> findPlateContents(String containerId) {
        Assert.hasText(containerId, 'containerId must not be blank')
        return contentsLinkReadService.findContents(containerId)
                .collectList()
                .flatMap { List<ContentsLinkRecord> links ->
                    return Flux.fromIterable(links)
                            .concatMap { ContentsLinkRecord link -> resolveEntry(link) }
                            .collectList()
                            .map { List<PlateContentsEntryRecord> entries ->
                                buildPlate(containerId, entries)
                            }
                } as Mono<PlateContentsRecord>
    }

    private Mono<PlateContentsEntryRecord> resolveEntry(ContentsLinkRecord link) {
        PlateContentsEntryRecord unresolved = toEntry(link, null)
        if (!hasText(link.right)) {
            return Mono.just(unresolved)
        }
        return entityPropertyValuesReadService.findPropertyValues(link.right)
                .map { EntityPropertyValuesRecord entity -> toEntry(link, entity) }
                .defaultIfEmpty(unresolved)
    }

    private static PlateContentsEntryRecord toEntry(
            ContentsLinkRecord link,
            EntityPropertyValuesRecord entity) {

        return new PlateContentsEntryRecord(
                linkId: link.linkId,
                typeId: link.typeId,
                objectId: link.right,
                position: extractPositionMap(link),
                linkProvenance: link.provenance,
                entity: entity
        )
    }

    private static PlateContentsRecord buildPlate(
            String containerId,
            List<PlateContentsEntryRecord> entries) {

        Map<String, List<PlateContentsEntryRecord>> contentsByWell = new LinkedHashMap<>()
        ROW_LABELS.each { String row ->
            (1..COLUMN_COUNT).each { Integer column ->
                contentsByWell["${row}${column}".toString()] = []
            }
        }

        List<PlateContentsEntryRecord> unplaced = []
        entries.each { PlateContentsEntryRecord entry ->
            PlateWellPosition position = extractPlateWellPosition(entry.position)
            if (position == null || !contentsByWell.containsKey(position.label)) {
                unplaced << entry
                return
            }
            contentsByWell[position.label] << entry
        }

        List<PlateContentsWellRecord> wells = []
        ROW_LABELS.each { String row ->
            (1..COLUMN_COUNT).each { Integer column ->
                String label = "${row}${column}".toString()
                wells << new PlateContentsWellRecord(
                        label: label,
                        row: row,
                        column: column,
                        contents: contentsByWell[label]
                )
            }
        }

        return new PlateContentsRecord(
                containerId: containerId,
                rowCount: ROW_COUNT,
                columnCount: COLUMN_COUNT,
                rowLabels: ROW_LABELS,
                wells: wells,
                unplacedContents: unplaced
        )
    }

    private static Map<String, Object> extractPositionMap(ContentsLinkRecord link) {
        Object properties = link?.properties
        if (properties instanceof Map) {
            Object position = ((Map) properties).get(POSITION)
            if (position instanceof Map) {
                return (Map<String, Object>) position
            }
        }
        return new LinkedHashMap<String, Object>()
    }

    private static PlateWellPosition extractPlateWellPosition(Map<String, Object> position) {
        if (position == null || position.get(POSITION_KIND) != POSITION_KIND_PLATE_WELL) {
            return null
        }
        String row = normalizeRow(position.get(POSITION_ROW))
        Integer column = normalizeColumn(position.get(POSITION_COLUMN))
        if (row == null || column == null) {
            return null
        }
        return new PlateWellPosition(row: row, column: column, label: "${row}${column}".toString())
    }

    private static String normalizeRow(Object value) {
        if (value == null) {
            return null
        }
        String row = value.toString().trim().toUpperCase(Locale.ROOT)
        return ROW_LABELS.contains(row) ? row : null
    }

    private static Integer normalizeColumn(Object value) {
        if (value == null) {
            return null
        }
        Integer column
        if (value instanceof Number) {
            column = ((Number) value).intValue()
        } else {
            try {
                column = Integer.valueOf(value.toString().trim())
            } catch (NumberFormatException ignored) {
                return null
            }
        }
        return column >= 1 && column <= COLUMN_COUNT ? column : null
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty()
    }

    private static class PlateWellPosition {
        String label
        String row
        Integer column
    }
}
