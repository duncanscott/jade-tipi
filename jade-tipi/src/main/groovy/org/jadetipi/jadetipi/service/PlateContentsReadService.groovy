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
import reactor.core.publisher.Mono

/**
 * Builds the first plate-shaped read model over materialized contents links.
 *
 * <p>The service composes existing read services only: it reads
 * {@link ContentsLinkRecord} values through {@link ContentsLinkReadService}
 * and resolves each link's right endpoint through
 * {@link ObjectPropertyValuesReadService}. It performs no Mongo writes, adds
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
    static final List<Integer> COLUMN_LABELS = (1..COLUMN_COUNT).toList().asImmutable()

    static final String POSITION = 'position'
    static final String POSITION_KIND = 'kind'
    static final String POSITION_KIND_PLATE_WELL = 'plate_well'
    static final String POSITION_ROW = 'row'
    static final String POSITION_COLUMN = 'column'

    private final ContentsLinkReadService contentsLinkReadService
    private final ObjectPropertyValuesReadService objectPropertyValuesReadService

    PlateContentsReadService(
            ContentsLinkReadService contentsLinkReadService,
            ObjectPropertyValuesReadService objectPropertyValuesReadService) {

        this.contentsLinkReadService = contentsLinkReadService
        this.objectPropertyValuesReadService = objectPropertyValuesReadService
    }

    /**
     * Returns a fixed 96-well grid for {@code containerId}. Empty contents
     * links produce the same 96 wells with empty {@code contents} lists.
     */
    Mono<PlateContentsRecord> findPlateContents(String containerId) {
        Assert.hasText(containerId, 'containerId must not be blank')
        return contentsLinkReadService.findContents(containerId)
                .concatMap { ContentsLinkRecord link -> resolveEntry(link) }
                .collectList()
                .map { List<PlateContentsEntryRecord> entries -> buildPlate(containerId, entries) } as Mono<PlateContentsRecord>
    }

    private Mono<PlateContentsEntryRecord> resolveEntry(ContentsLinkRecord link) {
        PlateContentsEntryRecord unresolved = toEntry(link, null)
        if (!hasText(link.right)) {
            return Mono.just(unresolved)
        }
        return objectPropertyValuesReadService.findPropertyValues('ent', link.right)
                .map { ObjectPropertyValuesRecord entity -> toEntry(link, entity) }
                .defaultIfEmpty(unresolved)
    }

    private static PlateContentsEntryRecord toEntry(
            ContentsLinkRecord link,
            ObjectPropertyValuesRecord entity) {

        return new PlateContentsEntryRecord(
                linkId: link.linkId,
                typeId: link.typeId,
                objectId: link.right,
                position: extractPositionMap(link),
                unplacedReason: null,
                linkProvenance: link.provenance,
                entity: entity
        )
    }

    private static PlateContentsRecord buildPlate(
            String containerId,
            List<PlateContentsEntryRecord> entries) {

        Map<String, List<PlateContentsEntryRecord>> contentsByWell = new LinkedHashMap<>()
        ROW_LABELS.each { String row ->
            COLUMN_LABELS.each { Integer column ->
                contentsByWell["${row}${column}".toString()] = []
            }
        }

        List<PlateContentsEntryRecord> unplaced = []
        entries.each { PlateContentsEntryRecord entry ->
            PlateWellPlacement placement = extractPlateWellPlacement(entry.position)
            if (placement.unplacedReason != null) {
                unplaced << entry.copyWith(unplacedReason: placement.unplacedReason)
                return
            }
            contentsByWell[placement.position.label] << entry
        }

        List<PlateContentsWellRecord> wells = []
        ROW_LABELS.each { String row ->
            COLUMN_LABELS.each { Integer column ->
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
                columnLabels: COLUMN_LABELS,
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
        return null
    }

    private static PlateWellPlacement extractPlateWellPlacement(Map<String, Object> position) {
        if (position == null) {
            return new PlateWellPlacement(unplacedReason: PlateContentsUnplacedReason.POSITION_MISSING)
        }
        if (position.get(POSITION_KIND) != POSITION_KIND_PLATE_WELL) {
            return new PlateWellPlacement(unplacedReason: PlateContentsUnplacedReason.POSITION_KIND_UNSUPPORTED)
        }

        Object rowValue = position.get(POSITION_ROW)
        if (rowValue == null) {
            return new PlateWellPlacement(unplacedReason: PlateContentsUnplacedReason.ROW_MISSING)
        }
        String row = normalizeRow(rowValue)
        if (row == null) {
            return new PlateWellPlacement(unplacedReason: PlateContentsUnplacedReason.ROW_INVALID)
        }

        ColumnNormalization column = normalizeColumn(position.get(POSITION_COLUMN))
        if (column.unplacedReason != null) {
            return new PlateWellPlacement(unplacedReason: column.unplacedReason)
        }

        PlateWellPosition wellPosition = new PlateWellPosition(
                row: row,
                column: column.column,
                label: "${row}${column.column}".toString()
        )
        return new PlateWellPlacement(position: wellPosition)
    }

    private static String normalizeRow(Object value) {
        if (value == null) {
            return null
        }
        String row = value.toString().trim().toUpperCase(Locale.ROOT)
        return ROW_LABELS.contains(row) ? row : null
    }

    private static ColumnNormalization normalizeColumn(Object value) {
        if (value == null) {
            return new ColumnNormalization(unplacedReason: PlateContentsUnplacedReason.COLUMN_MISSING)
        }
        Integer column
        if (value instanceof Number) {
            column = ((Number) value).intValue()
        } else {
            try {
                column = Integer.valueOf(value.toString().trim())
            } catch (NumberFormatException ignored) {
                return new ColumnNormalization(unplacedReason: PlateContentsUnplacedReason.COLUMN_MALFORMED)
            }
        }
        if (column < 1 || column > COLUMN_COUNT) {
            return new ColumnNormalization(unplacedReason: PlateContentsUnplacedReason.COLUMN_OUT_OF_RANGE)
        }
        return new ColumnNormalization(column: column)
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty()
    }

    private static class PlateWellPosition {
        String label
        String row
        Integer column
    }

    private static class PlateWellPlacement {
        PlateWellPosition position
        PlateContentsUnplacedReason unplacedReason
    }

    private static class ColumnNormalization {
        Integer column
        PlateContentsUnplacedReason unplacedReason
    }
}
