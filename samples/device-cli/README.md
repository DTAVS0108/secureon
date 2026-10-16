# Command-line app (device sign-in)

Signs in with a short code the user approves in a browser on any device (OAuth device authorization grant, RFC 8628), then calls the [Express API sample](../express-api). No dependencies.

## Register it

Console → **Connect an app** → **TV or command-line app**. There is no redirect URL and no secret.

## Run

```bash
cp .env.example .env    # set SECUREONE_CLIENT_ID (and SECUREONE_ISSUER if not localhost)
npm run login           # shows a code and a link; approve it in the browser
npm run whoami          # calls the API with the access token (refreshes it when needed)
npm run logout
```

For `whoami`, start the API sample and add this client ID to its `ALLOWED_AUDIENCES`.

## How it works

1. `POST /oauth2/device_authorization` returns a `user_code`, a `verification_uri` and a polling `interval`.
2. The user opens the link, signs in, checks the code and clicks **Allow**.
3. The CLI polls `POST /oauth2/token` every `interval` seconds: `authorization_pending` means keep waiting, `slow_down` means wait 5 seconds longer.
4. It stores the access and refresh tokens in `~/.secureone-demo-cli.json` (readable only by you) and refreshes the access token when it is about to expire.
