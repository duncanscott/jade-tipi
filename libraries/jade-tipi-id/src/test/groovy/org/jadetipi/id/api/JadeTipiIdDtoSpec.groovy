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
package org.jadetipi.id.api

import jakarta.validation.Validation
import jakarta.validation.Validator
import spock.lang.Shared
import spock.lang.Specification

class JadeTipiIdDtoSpec extends Specification {

    @Shared
    Validator validator = Validation.buildDefaultValidatorFactory().validator

    private static String PREFIX = "jkipguyzobiinnau";
    private static String TIMESTAMP = "1760345162888"

    def "valid DTO passes"() {
        given:
        def dto = new JadeTipiIdDto(
                "jkipguyzobiinnau",            // prefix
                "1760287077097",     // timestamp
                "0ab",               // sequence (base-36)
                "jgi_lbl-gov",       // org (mix '-' and '_', no doubles)
                "ii-pps",            // group
                "123e4567-e89b-12d3-a456-426614174000", // uuid
                "ent",               // type
                "plate-384"          // subtype (hyphen slug)
        )

        when:
        def violations = validator.validate(dto)

        then:
        violations.empty
    }

    def "timestamp must be 13 digits"() {
        expect:
        !validator.validate(new JadeTipiIdDto(
                "a", bad, "a", "a", "a", "123e4567-e89b-12d3-a456-426614174000", "a", "a"
        )).empty

        where:
        bad << ["", "abc", "000000000000000", "12 34"]
    }

    def "sequence must be base36 lowercase 1-16"() {
        expect:
        !validator.validate(new JadeTipiIdDto(
                PREFIX, TIMESTAMP, bad, "a", "a", "123e4567-e89b-12d3-a456-426614174000", "a", "a"
        )).empty

        where:
        bad << ["", "AA", "a_", "a-", "thisiswaytoolongforsequence123"]
    }

    def "org and group rules: start letter, end letter/digit, no doubles, only - and _ as seps, max 48"() {
        expect:
        !validator.validate(new JadeTipiIdDto(
                PREFIX, TIMESTAMP, "a", bad, "good", "123e4567-e89b-12d3-a456-426614174000", "a", "a"
        )).empty

        where:
        bad << ["-foo", "foo-", "foo--bar", "foo__bar", "f--", "f__",
                "f@", "1foo", ""] // invalid starts/ends, doubles, illegal char
    }

    def "subtype uses hyphen slug up to 30 chars"() {
        given:
        def over30 = "a" * 31

        expect:
        validator.validate(new JadeTipiIdDto(
                PREFIX, TIMESTAMP, "0ab", "org", "grp", "123e4567-e89b-12d3-a456-426614174000", "type", "a-slug-1"
        )).empty

        !validator.validate(new JadeTipiIdDto(
                PREFIX, TIMESTAMP, "0ab", "org", "grp", "123e4567-e89b-12d3-a456-426614174000", "type", "bad__slug"
        )).empty

        !validator.validate(new JadeTipiIdDto(
                PREFIX, TIMESTAMP, "0ab", "org", "grp", "123e4567-e89b-12d3-a456-426614174000", "type", over30
        )).empty
    }

    def "uuid must be RFC form"() {
        expect:
        validator.validate(new JadeTipiIdDto(
                PREFIX, TIMESTAMP, "a1b", "org", "grp", "123e4567-e89b-12d3-a456-426614174000", "type", "sub"
        )).empty

        !validator.validate(new JadeTipiIdDto(
                PREFIX, TIMESTAMP, "a1b", "org", "grp", "123e4567e89b12d3a456426614174000", "type", "sub"
        )).empty

        !validator.validate(new JadeTipiIdDto(
                PREFIX, TIMESTAMP, "a1b", "org", "grp", "g23e4567-e89b-12d3-a456-426614174000", "type", "sub"
        )).empty
    }
}
