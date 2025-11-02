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
package org.jadetipi.jadetipi.filter

import com.fasterxml.jackson.databind.ObjectMapper
import groovy.util.logging.Slf4j
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferFactory
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.HttpHeaders
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.http.server.reactive.ServerHttpRequestDecorator
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

import java.nio.charset.StandardCharsets
import java.util.Arrays

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
class RequestBodyLoggingFilter implements WebFilter {

    private final ObjectMapper objectMapper

    RequestBodyLoggingFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper
    }

    @jakarta.annotation.PostConstruct
    void init() {
        log.info("RequestBodyLoggingFilter initialized")
    }

    @Override
    Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.request

        if (!shouldLog(request)) {
            return chain.filter(exchange)
        }

        log.info("RequestBodyLoggingFilter intercepting {} {}", request.method, request.path.value())

        return DataBufferUtils.join(request.body)
                .defaultIfEmpty(exchange.response.bufferFactory().wrap(new byte[0]))
                .flatMap { DataBuffer dataBuffer ->
                    try {
                        byte[] bodyBytes = new byte[dataBuffer.readableByteCount()]
                        dataBuffer.read(bodyBytes)
                        DataBufferUtils.release(dataBuffer)

                        if (bodyBytes.length == 0) {
                            ServerWebExchange mutatedExchange = mutateExchange(exchange, bodyBytes)
                            return chain.filter(mutatedExchange)
                        }

                        String bodyText = new String(bodyBytes, StandardCharsets.UTF_8).trim()
                        if (bodyText) {
                            def prettyJson = tryPrettyPrint(bodyText)
                            if (prettyJson != null) {
                                log.info("HTTP {} {} request body:\n{}", request.method, request.path.value(), prettyJson)
                            }
                        }

                        ServerWebExchange mutatedExchange = mutateExchange(exchange, bodyBytes)
                        return chain.filter(mutatedExchange)
                    } catch (Exception ex) {
                        log.warn("Failed to log request body", ex)
                        DataBufferUtils.release(dataBuffer)
                        return chain.filter(exchange)
                    }
                }
    }

    private ServerWebExchange mutateExchange(ServerWebExchange exchange, byte[] cachedBody) {
        DataBufferFactory bufferFactory = exchange.response.bufferFactory()

        Flux<DataBuffer> cachedFlux = Flux.defer {
            byte[] copy = Arrays.copyOf(cachedBody, cachedBody.length)
            DataBuffer buffer = bufferFactory.wrap(copy)
            Mono.just(buffer)
        }

        ServerHttpRequest decoratedRequest = new ServerHttpRequestDecorator(exchange.request) {
            @Override
            Flux<DataBuffer> getBody() {
                return cachedFlux
            }
        }

        return exchange.mutate()
                .request(decoratedRequest)
                .build()
    }

    private boolean shouldLog(ServerHttpRequest request) {
        boolean methodAllowsBody = request.method != HttpMethod.GET && request.method != HttpMethod.HEAD
        String rawContentType = request.headers.getFirst(HttpHeaders.CONTENT_TYPE)
        MediaType contentType = null
        if (rawContentType) {
            try {
                contentType = MediaType.parseMediaType(rawContentType)
            } catch (Exception ex) {
                log.info("RequestBodyLoggingFilter encountered unparsable content type '{}'", rawContentType, ex)
            }
        }

        boolean isJson = false
        if (contentType) {
            isJson = contentType.isCompatibleWith(MediaType.APPLICATION_JSON) ||
                    (contentType.subtype?.toLowerCase()?.endsWith("+json") ?: false)
        }

        if (!methodAllowsBody || !isJson) {
            log.info("RequestBodyLoggingFilter skipping request: method={}, contentTypeRaw={}, contentTypeParsed={}", request.method, rawContentType, contentType)
        }

        return methodAllowsBody && isJson
    }

    private String tryPrettyPrint(String body) {
        try {
            def jsonNode = objectMapper.readTree(body)
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode)
        } catch (Exception ignored) {
            return null
        }
    }
}
