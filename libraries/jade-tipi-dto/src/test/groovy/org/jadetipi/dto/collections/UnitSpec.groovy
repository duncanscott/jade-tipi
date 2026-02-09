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
package org.jadetipi.dto.collections

import org.jadetipi.dto.util.JsonMapper
import org.jadetipi.dto.util.ValidationException
import spock.lang.Specification

class UnitSpec extends Specification {

    private static final String UNITS_JSONL = '/units/jade_tipi_si_units.jsonl'

    def "base unit validates successfully"() {
        given:
        def unit = new Unit('volt', null, 'V', 'SI')

        when:
        unit.validate()

        then:
        noExceptionThrown()
    }

    def "prefixed unit validates successfully"() {
        given:
        def unit = new Unit('volt', 'kilo', 'kV', 'SI')

        when:
        unit.validate()

        then:
        noExceptionThrown()
    }

    def "all SI units from JSONL validate successfully"() {
        given:
        def lines = readJsonlLines()

        expect:
        lines.size() == 701

        and:
        lines.each { line ->
            def unit = JsonMapper.fromJson(line, Unit)
            unit.validate()
        }
    }

    def "validation fails when required field #field is null"() {
        given:
        def unit = new Unit(unitVal, prefix, symbol, system)

        when:
        unit.validate()

        then:
        def ex = thrown(ValidationException)
        ex.message.contains(field)

        where:
        field    | unitVal | prefix | symbol | system
        'unit'   | null    | null   | 'V'    | 'SI'
        'symbol' | 'volt'  | null   | null   | 'SI'
        'system' | 'volt'  | null   | 'V'    | null
    }

    def "validation fails when required field #field is empty"() {
        given:
        def unit = new Unit(unitVal, prefix, symbol, system)

        when:
        unit.validate()

        then:
        def ex = thrown(ValidationException)
        ex.message.contains(field)

        where:
        field    | unitVal | prefix | symbol | system
        'unit'   | ''      | null   | 'V'    | 'SI'
        'symbol' | 'volt'  | null   | ''     | 'SI'
    }

    private List<String> readJsonlLines() {
        def is = getClass().getResourceAsStream(UNITS_JSONL)
        assert is != null: "JSONL resource not found: ${UNITS_JSONL}"
        is.readLines('UTF-8').findAll { !it.isBlank() }
    }
}
