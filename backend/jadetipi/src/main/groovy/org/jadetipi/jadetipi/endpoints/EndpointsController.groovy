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

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@RestController
class EndpointsController {

    @Value('${spring.application.name}')
    private String applicationMame

    @Value('${base-package}')
    private String basePackage

    @Value('${docs.url}')
    private String docsUrl

    @Autowired
    private ServerPort serverPort

    @RequestMapping(value = '/', method = RequestMethod.GET, produces = MediaType.TEXT_PLAIN_VALUE)
    def mappings() {
        String actuatorMappingsUrl = "http://localhost:${serverPort.port}/actuator/mappings"
        ApplicationEndpoints.mappings(actuatorMappingsUrl, basePackage)
    }

    @GetMapping('/hello')
    String hello() {
        return "Greetings from Spring Boot app ${applicationMame}!"
    }

    @RequestMapping(value = '/version', method = RequestMethod.GET, produces = MediaType.TEXT_PLAIN_VALUE)
    Mono<String> version() {
        return Mono.fromCallable {
            String propertiesPath = 'service.properties'
            URL propertiesUrl = Thread.currentThread().contextClassLoader.getResource(propertiesPath)
            if (propertiesUrl == null) {
                return "service.properties not found"
            }
            Properties props = new Properties()
            propertiesUrl.openStream().withCloseable { stream ->
                props.load(stream)
            }
            props.propertyNames().toList().collect { key ->
                "${key}=${props.getProperty(key)}"
            }.join('\n')
        }
    }

    @GetMapping('/docs')
    Mono<ResponseEntity<Void>> docs() {
        return Mono.just(
            ResponseEntity.status(HttpStatus.FOUND)
                .header('Location', docsUrl)
                .build()
        )
    }

}
