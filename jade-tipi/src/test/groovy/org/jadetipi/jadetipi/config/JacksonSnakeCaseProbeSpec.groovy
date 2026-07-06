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
package org.jadetipi.jadetipi.config

import com.fasterxml.jackson.databind.ObjectMapper
import org.jadetipi.jadetipi.service.LocationSummaryRecord
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import spock.lang.Specification

/**
 * TASK-055 probe: the auto-configured ObjectMapper serializes read records
 * with snake_case field names.
 */
@SpringBootTest
@ActiveProfiles('test')
class JacksonSnakeCaseProbeSpec extends Specification {

    @Autowired
    ObjectMapper objectMapper

    def 'the context ObjectMapper serializes record properties as snake_case'() {
        given:
        LocationSummaryRecord record = new LocationSummaryRecord(
                locationId: 'x~y~z~loc~a', typeId: 't', name: 'n', description: 'd')

        when:
        String json = objectMapper.writeValueAsString(record)

        then:
        println "PROBE naming strategy: ${objectMapper.serializationConfig.propertyNamingStrategy}"
        println "PROBE json: ${json}"
        json.contains('"location_id"')
        json.contains('"type_id"')
    }
}
