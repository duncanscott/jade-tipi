import NextAuth from "next-auth"
import Keycloak from "next-auth/providers/keycloak"
import type { JWT } from "next-auth/jwt"

const ADMIN_ROLE = 'jade-tipi-admin'
const ACCESS_TOKEN_REFRESH_BUFFER_SECONDS = 30

/**
 * Decode the JWT payload section without verifying the signature. Verification
 * remains the Spring backend's job — the frontend only uses this to expose a
 * boolean admin signal so the Header can render the Groups link and the
 * /admin/groups page can short-circuit on missing-role. Returns null on any
 * shape error.
 */
function decodeJwtPayload(token: string): Record<string, unknown> | null {
  if (!token) {
    return null
  }
  const parts = token.split('.')
  if (parts.length < 2) {
    return null
  }
  try {
    const padded = parts[1] + '='.repeat((4 - (parts[1].length % 4)) % 4)
    const base64 = padded.replace(/-/g, '+').replace(/_/g, '/')
    const decoded = Buffer.from(base64, 'base64').toString('utf8')
    return JSON.parse(decoded) as Record<string, unknown>
  } catch {
    return null
  }
}

function isAdminFromAccessToken(accessToken: string | undefined | null): boolean {
  if (!accessToken) {
    return false
  }
  const payload = decodeJwtPayload(accessToken)
  if (!payload) {
    return false
  }
  const realmAccess = payload['realm_access']
  if (!realmAccess || typeof realmAccess !== 'object') {
    return false
  }
  const roles = (realmAccess as Record<string, unknown>)['roles']
  if (!Array.isArray(roles)) {
    return false
  }
  return roles.some(role => typeof role === 'string' && role === ADMIN_ROLE)
}

async function refreshAccessToken(token: JWT): Promise<JWT> {
  if (!token.refreshToken || typeof token.refreshToken !== 'string') {
    return { ...token, accessToken: undefined, isAdmin: false, authError: 'RefreshAccessTokenError' }
  }

  try {
    const issuerUrl = process.env.KEYCLOAK_ISSUER!
    const params = new URLSearchParams({
      client_id: process.env.KEYCLOAK_CLIENT_ID!,
      grant_type: 'refresh_token',
      refresh_token: token.refreshToken,
    })
    if (process.env.KEYCLOAK_CLIENT_SECRET) {
      params.set('client_secret', process.env.KEYCLOAK_CLIENT_SECRET)
    }

    const response = await fetch(`${issuerUrl}/protocol/openid-connect/token`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: params,
    })
    const refreshed = await response.json()

    if (!response.ok) {
      throw new Error(refreshed?.error_description || refreshed?.error || 'Failed to refresh access token')
    }

    const accessToken = refreshed.access_token as string
    const expiresIn = typeof refreshed.expires_in === 'number' ? refreshed.expires_in : 300
    return {
      ...token,
      accessToken,
      idToken: (refreshed.id_token as string | undefined) ?? token.idToken,
      refreshToken: (refreshed.refresh_token as string | undefined) ?? token.refreshToken,
      accessTokenExpiresAt: Math.floor(Date.now() / 1000) + expiresIn,
      isAdmin: isAdminFromAccessToken(accessToken),
      authError: undefined,
    }
  } catch (error) {
    console.error('Error refreshing Keycloak access token:', error)
    return { ...token, accessToken: undefined, isAdmin: false, authError: 'RefreshAccessTokenError' }
  }
}

export const { handlers, signIn, signOut, auth } = NextAuth({
  providers: [
    Keycloak({
      clientId: process.env.KEYCLOAK_CLIENT_ID!,
      clientSecret: process.env.KEYCLOAK_CLIENT_SECRET,
      issuer: process.env.KEYCLOAK_ISSUER,
    })
  ],
  callbacks: {
    async jwt({ token, account }) {
      // Persist the OAuth access_token and id_token to the token, and derive
      // the admin-role boolean from realm_access.roles so the session does
      // not need to expose the raw realm claims to the client.
      if (account) {
        token.accessToken = account.access_token
        token.idToken = account.id_token
        token.refreshToken = account.refresh_token
        token.accessTokenExpiresAt = account.expires_at
        token.isAdmin = isAdminFromAccessToken(account.access_token)
        token.authError = undefined
        return token
      }

      if (
        typeof token.accessTokenExpiresAt === 'number' &&
        Date.now() < (token.accessTokenExpiresAt - ACCESS_TOKEN_REFRESH_BUFFER_SECONDS) * 1000
      ) {
        return token
      }

      return refreshAccessToken(token)
    },
    async session({ session, token }) {
      // Send access token and admin flag to the client. Raw realm_access
      // claims are intentionally not forwarded.
      session.accessToken = token.accessToken as string
      session.idToken = token.idToken as string
      session.isAdmin = token.isAdmin === true
      session.authError = token.authError as string | undefined
      return session
    }
  },
  events: {
    async signOut(message) {
      // The signOut event message is a discriminated union: { session } for
      // database-session strategies and { token } for JWT. This app uses the
      // JWT strategy via Keycloak; narrow with 'in' so the type checker
      // stays honest if a future adapter is added.
      if (!('token' in message) || !message.token?.idToken) {
        return
      }
      const token = message.token
      const issuerUrl = process.env.KEYCLOAK_ISSUER!
      const logoutUrl = `${issuerUrl}/protocol/openid-connect/logout`
      const params = new URLSearchParams({
        id_token_hint: token.idToken as string,
        post_logout_redirect_uri: process.env.NEXTAUTH_URL || 'http://localhost:3000'
      })

      try {
        // Call Keycloak's logout endpoint to end the session
        await fetch(`${logoutUrl}?${params.toString()}`, { method: 'GET' })
      } catch (error) {
        console.error('Error during Keycloak logout:', error)
      }
    }
  }
})
