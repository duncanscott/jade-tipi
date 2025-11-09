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
package org.jadetipi.jadetipi.endpoints

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.web.client.RestTemplate

class ApplicationEndpoints {

    static String mappings(String actuatorMappingsUrl, Collection<String> basePackages) {
        StringBuilder sb = new StringBuilder()
        Map<String, Set<String>> patterns = [:].withDefault { new HashSet<String>() }
        patterns['/actuator'].add('GET')
        endpointHandlersFromActuator(actuatorMappingsUrl, basePackages).each { JsonNode handler ->
            JsonNode requestMappingConditions = handler.path('details').path('requestMappingConditions')
            JsonNode endpointMethods = requestMappingConditions.path('methods')
            JsonNode endpointPatterns = requestMappingConditions.path('patterns')
            if (endpointMethods.isArray() && endpointPatterns.isArray()) {
                endpointPatterns.forEach { patternNode ->
                    String pattern = patternNode.asText()
                    endpointMethods.forEach { methodNode ->
                        patterns[pattern].add(methodNode.asText())
                    }
                }
            }
        }

        patterns.remove('/swagger-ui.html')

        int maxPatternLength = patterns.keySet()*.length().max()
        int bufferSize = 2 + maxPatternLength
        patterns.sort { it.key }.each { String pattern, Set<String> methods ->
            sb.append(pattern)
            sb.append(' ' * (bufferSize - pattern.length()))
            sb.append(': ')
            sb.append(methods.sort().join(','))
            sb.append('\n')
        }
        sb.toString()
    }

    private static List<JsonNode> endpointHandlersFromActuator(String actuatorMappingsUrl, Collection<String> basePackages) {
        Collection<String> prefixes = (basePackages ?: []).collect { it?.trim() }.findAll { it }
        RestTemplate restTemplate = new RestTemplate()
        ObjectMapper objectMapper = new ObjectMapper()
        String jsonString = restTemplate.getForObject(actuatorMappingsUrl, String)
        JsonNode rootNode = objectMapper.readTree(jsonString)

        List<JsonNode> handlers = []
        extractHandlers(rootNode, handlers)

        if (prefixes.isEmpty()) {
            return handlers
        }

        handlers.findAll { JsonNode handler ->
            String className = handler.path('details').path('handlerMethod').path('className').asText()
            prefixes.any { prefix -> className.startsWith(prefix) }
        }
    }

    private static void extractHandlers(JsonNode json, List<JsonNode> handlers) {
        if (json.isObject()) {
            if (json.has('handler')) {
                handlers.add(json)
            } else {
                json.fields().each { entry ->
                    extractHandlers(entry.value, handlers)
                }
            }
        } else if (json.isArray()) {
            json.forEach { node ->
                extractHandlers(node, handlers)
            }
        }
    }
}
