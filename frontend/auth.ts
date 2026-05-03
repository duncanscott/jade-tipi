import NextAuth from "next-auth"
import Keycloak from "next-auth/providers/keycloak"

const ADMIN_ROLE = 'jade-tipi-admin'

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
        token.isAdmin = isAdminFromAccessToken(account.access_token)
      }
      return token
    },
    async session({ session, token }) {
      // Send access token and admin flag to the client. Raw realm_access
      // claims are intentionally not forwarded.
      session.accessToken = token.accessToken as string
      session.idToken = token.idToken as string
      session.isAdmin = token.isAdmin === true
      return session
    }
  },
  events: {
    async signOut({ token }) {
      // Construct Keycloak logout URL to terminate the Keycloak session
      if (token?.idToken) {
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
  }
})
