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

import org.springframework.http.MediaType
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient

/**
 * Helper class to obtain JWT tokens from Keycloak for integration tests
 */
class KeycloakTestHelper {

    private static final String KEYCLOAK_URL = "http://localhost:8484"
    private static final String REALM = "jade-tipi"
    private static final String CLIENT_ID = "jade-tipi-backend"
    private static final String CLIENT_SECRET = "d84c91af-7f37-4b8d-9157-0f6c6a91fb45"

    private static String cachedToken = null

    /**
     * Get a JWT access token from Keycloak using client credentials grant
     */
    static String getAccessToken() {
        if (cachedToken != null) {
            return cachedToken
        }

        WebClient webClient = WebClient.builder()
                .baseUrl(KEYCLOAK_URL)
                .build()

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>()
        formData.add("grant_type", "client_credentials")
        formData.add("client_id", CLIENT_ID)
        formData.add("client_secret", CLIENT_SECRET)

        def response = webClient.post()
                .uri("/realms/${REALM}/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .bodyToMono(Map.class)
                .block()

        cachedToken = response.access_token as String
        return cachedToken
    }

    /**
     * Clear the cached token (useful for testing token expiration scenarios)
     */
    static void clearToken() {
        cachedToken = null
    }
}