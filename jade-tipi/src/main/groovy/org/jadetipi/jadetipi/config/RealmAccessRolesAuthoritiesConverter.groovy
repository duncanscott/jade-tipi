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

import org.springframework.core.convert.converter.Converter
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt

/**
 * Maps Keycloak realm roles from a JWT into Spring authorities.
 *
 * <p>Realm roles ride on the access token under
 * {@code realm_access.roles: [...]}. Each role is emitted as a
 * {@code ROLE_<name>} authority so {@code hasRole('jade-tipi-admin')} matches
 * an admin user without any per-client wiring. Missing or wrong-typed claims
 * yield an empty authority list.
 */
class RealmAccessRolesAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final String CLAIM_REALM_ACCESS = 'realm_access'
    private static final String CLAIM_ROLES = 'roles'
    private static final String ROLE_PREFIX = 'ROLE_'

    @Override
    Collection<GrantedAuthority> convert(Jwt jwt) {
        if (jwt == null) {
            return Collections.emptyList()
        }
        Object realmAccess = jwt.getClaim(CLAIM_REALM_ACCESS)
        if (!(realmAccess instanceof Map)) {
            return Collections.emptyList()
        }
        Object rolesValue = ((Map) realmAccess).get(CLAIM_ROLES)
        if (!(rolesValue instanceof Collection)) {
            return Collections.emptyList()
        }
        List<GrantedAuthority> authorities = new ArrayList<>()
        for (Object role : (Collection) rolesValue) {
            if (role == null) {
                continue
            }
            String name = role.toString().trim()
            if (name.isEmpty()) {
                continue
            }
            authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + name))
        }
        return authorities
    }
}
