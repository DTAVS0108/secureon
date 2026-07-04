# @secureone/client

TypeScript SDK for SecureOne public APIs.

```typescript
import { SecureOneClient, generatePkcePairAsync } from "@secureone/client";

const client = new SecureOneClient({
  baseUrl: "http://localhost:9000",
  applicationId: "your-app-id",
});

const manifest = await client.getManifest();
const { verifier, challenge } = await generatePkcePairAsync();
const authorizeUrl = client.buildAuthorizeUrl({
  clientId: "your-client-id",
  redirectUri: "http://localhost:5173/callback",
  state: crypto.randomUUID(),
  codeChallenge: challenge,
});
```

Build: `npm run build` from this directory.
