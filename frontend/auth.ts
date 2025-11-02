import NextAuth from "next-auth"
import Keycloak from "next-auth/providers/keycloak"

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
      // Persist the OAuth access_token and id_token to the token
      if (account) {
        token.accessToken = account.access_token
        token.idToken = account.id_token
      }
      return token
    },
    async session({ session, token }) {
      // Send access token to the client
      session.accessToken = token.accessToken as string
      session.idToken = token.idToken as string
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
