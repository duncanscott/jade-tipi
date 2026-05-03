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

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import spock.lang.Specification

import java.time.Instant

/**
 * Narrow coverage for {@link RealmAccessRolesAuthoritiesConverter}.
 *
 * <p>The converter is the JWT-to-authority half of the WebFlux JWT
 * authentication pipeline; the {@code ReactiveJwtAuthenticationConverterAdapter}
 * adaption is wired up in {@code SecurityConfig} and verified by integration
 * coverage. Here we exercise the claim parsing only.
 */
class RealmAccessRolesAuthoritiesConverterSpec extends Specification {

    RealmAccessRolesAuthoritiesConverter converter = new RealmAccessRolesAuthoritiesConverter()

    private static Jwt jwt(Map<String, Object> claims) {
        return new Jwt(
                'token-value',
                Instant.parse('2026-01-01T00:00:00Z'),
                Instant.parse('2026-01-01T01:00:00Z'),
                ['alg': 'RS256'] as Map<String, Object>,
                claims
        )
    }

    def 'returns ROLE_ prefixed authorities for each realm role'() {
        given:
        Jwt token = jwt([
                realm_access: [roles: ['jade-tipi-admin', 'offline_access']]
        ] as Map<String, Object>)

        when:
        Collection<GrantedAuthority> authorities = converter.convert(token)

        then:
        authorities*.authority as Set == ['ROLE_jade-tipi-admin', 'ROLE_offline_access'] as Set
    }

    def 'returns empty list when realm_access claim is missing'() {
        given:
        Jwt token = jwt([sub: 'test-user'] as Map<String, Object>)

        when:
        Collection<GrantedAuthority> authorities = converter.convert(token)

        then:
        authorities.isEmpty()
    }

    def 'returns empty list when realm_access is not a Map'() {
        given:
        Jwt token = jwt([realm_access: 'not-a-map'] as Map<String, Object>)

        when:
        Collection<GrantedAuthority> authorities = converter.convert(token)

        then:
        authorities.isEmpty()
    }

    def 'returns empty list when realm_access has no roles'() {
        given:
        Jwt token = jwt([realm_access: [:]] as Map<String, Object>)

        when:
        Collection<GrantedAuthority> authorities = converter.convert(token)

        then:
        authorities.isEmpty()
    }

    def 'returns empty list when realm_access.roles is not a Collection'() {
        given:
        Jwt token = jwt([realm_access: [roles: 'jade-tipi-admin']] as Map<String, Object>)

        when:
        Collection<GrantedAuthority> authorities = converter.convert(token)

        then:
        authorities.isEmpty()
    }

    def 'skips blank and null role entries'() {
        given:
        Jwt token = jwt([
                realm_access: [roles: [null, '', '   ', 'jade-tipi-admin']]
        ] as Map<String, Object>)

        when:
        Collection<GrantedAuthority> authorities = converter.convert(token)

        then:
        authorities*.authority == ['ROLE_jade-tipi-admin']
    }

    def 'returns empty list for null Jwt'() {
        expect:
        converter.convert(null).isEmpty()
    }
}
