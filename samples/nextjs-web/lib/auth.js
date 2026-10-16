/**
 * SecureOne as an OpenID Connect provider for NextAuth.js (v4).
 * The client is confidential: its secret stays on this server (SECUREONE_CLIENT_SECRET).
 * @type {import("next-auth").AuthOptions}
 */
export const authOptions = {
  providers: [
    {
      id: "secureone",
      name: "SecureOne",
      type: "oauth",
      wellKnown: `${process.env.SECUREONE_ISSUER}/.well-known/openid-configuration`,
      clientId: process.env.SECUREONE_CLIENT_ID,
      clientSecret: process.env.SECUREONE_CLIENT_SECRET,
      authorization: { params: { scope: "openid profile email" } },
      idToken: true,
      checks: ["pkce", "state"],
      profile(profile) {
        return {
          id: profile.sub,
          name: profile.name ?? profile.preferred_username ?? profile.email,
          email: profile.email,
        };
      },
    },
  ],
};
