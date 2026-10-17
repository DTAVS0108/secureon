/**
 * Copy-paste setup for each framework in the "Connect an app" wizard, filled in with the real
 * issuer, client id and URLs. Secrets are never written into code: snippets read
 * SECUREONE_CLIENT_SECRET from the environment (the console shows the value once, separately).
 * Standard libraries only; SecureOne is a plain OpenID Connect / OAuth 2 provider.
 */

export type SnippetValues = {
  issuer: string;
  clientId: string;
  appUrl: string;
  redirectUri: string;
  postLogoutUri: string;
  /** Reverse-domain scheme for mobile redirects, e.g. com.example.shop. */
  appScheme: string;
  scopes: string[];
  /** Client ids allowed to call an API (token audience), for the "api" kind. */
  audiences: string[];
};

export type Snippet = { title: string; language: string; code: string };

export type FrameworkSetup = {
  install?: string;
  snippets: Snippet[];
  notes: string[];
};

const q = (s: string) => JSON.stringify(s);

export function setupFor(framework: string, v: SnippetValues): FrameworkSetup {
  const scope = v.scopes.join(" ");
  const httpIssuer = v.issuer.startsWith("http://");
  switch (framework) {
    // ---------------------------------------------------------------- web apps with a server
    case "nextjs":
      return {
        install: "npm install next-auth@4   (NextAuth.js v4, the current stable release)",
        snippets: [
          {
            title: ".env.local",
            language: "bash",
            code: `NEXTAUTH_URL=${v.appUrl}
NEXTAUTH_SECRET=   # any long random string: openssl rand -base64 32
SECUREONE_ISSUER=${v.issuer}
SECUREONE_CLIENT_ID=${v.clientId}
SECUREONE_CLIENT_SECRET=   # shown once in the console`,
          },
          {
            title: "lib/auth.ts",
            language: "ts",
            code: `import type { AuthOptions } from "next-auth";

export const authOptions: AuthOptions = {
  providers: [
    {
      id: "secureone",
      name: "SecureOne",
      type: "oauth",
      wellKnown: \`\${process.env.SECUREONE_ISSUER}/.well-known/openid-configuration\`,
      clientId: process.env.SECUREONE_CLIENT_ID,
      clientSecret: process.env.SECUREONE_CLIENT_SECRET,
      authorization: { params: { scope: ${q(scope)} } },
      idToken: true,
      checks: ["pkce", "state"],
      profile(profile) {
        return { id: profile.sub, name: profile.name ?? profile.preferred_username, email: profile.email };
      },
    },
  ],
};`,
          },
          {
            title: "app/api/auth/[...nextauth]/route.ts",
            language: "ts",
            code: `import NextAuth from "next-auth";
import { authOptions } from "@/lib/auth";

// Route files may only export handlers, so the options live in lib/auth.ts.
const handler = NextAuth(authOptions);
export { handler as GET, handler as POST };`,
          },
          {
            title: "Use it (server component)",
            language: "tsx",
            code: `import { getServerSession } from "next-auth";
import { authOptions } from "@/lib/auth";

export default async function Page() {
  const session = await getServerSession(authOptions);
  return session
    ? <p>Signed in as {session.user?.email} · <a href="/api/auth/signout">Sign out</a></p>
    : <a href="/api/auth/signin/secureone">Sign in</a>;
}`,
          },
        ],
        notes: [`Callback URL registered for this client: ${v.redirectUri}`],
      };

    case "spring":
      return {
        install: "Add the dependency org.springframework.boot:spring-boot-starter-oauth2-client",
        snippets: [
          {
            title: "src/main/resources/application.yml",
            language: "yaml",
            code: `spring:
  security:
    oauth2:
      client:
        registration:
          secureone:
            client-id: ${v.clientId}
            client-secret: \${SECUREONE_CLIENT_SECRET}
            scope: ${v.scopes.join(",")}
            authorization-grant-type: authorization_code
            redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
        provider:
          secureone:
            issuer-uri: ${v.issuer}`,
          },
          {
            title: "SecurityConfig.java",
            language: "java",
            code: `@Configuration
class SecurityConfig {

    @Bean
    SecurityFilterChain security(HttpSecurity http, ClientRegistrationRepository registrations) throws Exception {
        var logout = new OidcClientInitiatedLogoutSuccessHandler(registrations);
        logout.setPostLogoutRedirectUri("{baseUrl}/");
        http
            .authorizeHttpRequests(auth -> auth.requestMatchers("/").permitAll().anyRequest().authenticated())
            .oauth2Login(Customizer.withDefaults())
            .logout(l -> l.logoutSuccessHandler(logout));
        return http.build();
    }
}`,
          },
          {
            title: "Read the user",
            language: "java",
            code: `@GetMapping("/me")
Map<String, Object> me(@AuthenticationPrincipal OidcUser user) {
    return Map.of("email", user.getEmail(), "name", user.getFullName());
}`,
          },
        ],
        notes: [`Callback URL registered for this client: ${v.redirectUri}`],
      };

    case "express":
      return {
        install: "npm install express express-session openid-client",
        snippets: [
          {
            title: "server.mjs",
            language: "js",
            code: `import express from "express";
import session from "express-session";
import * as client from "openid-client";

const ISSUER = new URL(process.env.SECUREONE_ISSUER ?? ${q(v.issuer)});
const APP_URL = ${q(v.appUrl)};
const REDIRECT_URI = ${q(v.redirectUri)};

const config = await client.discovery(
  ISSUER,
  process.env.SECUREONE_CLIENT_ID ?? ${q(v.clientId)},
  process.env.SECUREONE_CLIENT_SECRET,
  undefined,${httpIssuer ? "\n  // http:// is only allowed for local development\n  { execute: [client.allowInsecureRequests] }," : "\n  undefined,"}
);

const app = express();
app.use(session({ secret: process.env.SESSION_SECRET, resave: false, saveUninitialized: false }));

app.get("/login", async (req, res) => {
  const codeVerifier = client.randomPKCECodeVerifier();
  const state = client.randomState();
  req.session.oidc = { codeVerifier, state };
  const url = client.buildAuthorizationUrl(config, {
    redirect_uri: REDIRECT_URI,
    scope: ${q(scope)},
    code_challenge: await client.calculatePKCECodeChallenge(codeVerifier),
    code_challenge_method: "S256",
    state,
  });
  res.redirect(url.href);
});

app.get(${q(new URL(v.redirectUri).pathname)}, async (req, res) => {
  const { codeVerifier, state } = req.session.oidc ?? {};
  const tokens = await client.authorizationCodeGrant(config, new URL(req.originalUrl, APP_URL), {
    pkceCodeVerifier: codeVerifier,
    expectedState: state,
  });
  req.session.user = tokens.claims();
  req.session.idToken = tokens.id_token;
  res.redirect("/");
});

app.get("/logout", (req, res) => {
  const url = client.buildEndSessionUrl(config, {
    id_token_hint: req.session.idToken,
    post_logout_redirect_uri: ${q(v.postLogoutUri)},
  });
  req.session.destroy(() => res.redirect(url.href));
});

app.get("/", (req, res) =>
  res.send(req.session.user ? \`Signed in as \${req.session.user.email} · <a href="/logout">Sign out</a>\` : '<a href="/login">Sign in</a>'),
);

app.listen(new URL(APP_URL).port || 3000);`,
          },
        ],
        notes: ["Set SESSION_SECRET and SECUREONE_CLIENT_SECRET in the environment before starting."],
      };

    case "aspnet":
      return {
        install: "dotnet add package Microsoft.AspNetCore.Authentication.OpenIdConnect",
        snippets: [
          {
            title: "Program.cs",
            language: "csharp",
            code: `using Microsoft.AspNetCore.Authentication.Cookies;
using Microsoft.AspNetCore.Authentication.OpenIdConnect;

var builder = WebApplication.CreateBuilder(args);

builder.Services
    .AddAuthentication(options =>
    {
        options.DefaultScheme = CookieAuthenticationDefaults.AuthenticationScheme;
        options.DefaultChallengeScheme = OpenIdConnectDefaults.AuthenticationScheme;
    })
    .AddCookie()
    .AddOpenIdConnect(options =>
    {
        options.Authority = ${q(v.issuer)};
        options.ClientId = ${q(v.clientId)};
        options.ClientSecret = builder.Configuration["SECUREONE_CLIENT_SECRET"];
        options.ResponseType = "code";
        options.UsePkce = true;
        options.Scope.Clear();
${v.scopes.map((s) => `        options.Scope.Add(${q(s)});`).join("\n")}
        options.SaveTokens = true;
        options.MapInboundClaims = false;
        options.CallbackPath = ${q(new URL(v.redirectUri).pathname)};
        options.SignedOutCallbackPath = ${q(new URL(v.postLogoutUri).pathname)};${httpIssuer ? "\n        options.RequireHttpsMetadata = false; // local development only" : ""}
    });
builder.Services.AddAuthorization();

var app = builder.Build();
app.UseAuthentication();
app.UseAuthorization();

app.MapGet("/", (HttpContext ctx) => ctx.User.Identity?.IsAuthenticated == true
    ? $"Signed in as {ctx.User.FindFirst("email")?.Value}"
    : "Not signed in").AllowAnonymous();
app.MapGet("/login", () => Results.Challenge(new() { RedirectUri = "/" }));
app.Run();`,
          },
        ],
        notes: [`Callback URL registered for this client: ${v.redirectUri}`],
      };

    // ---------------------------------------------------------------- single-page apps
    case "react":
      return {
        install: "npm install react-oidc-context oidc-client-ts",
        snippets: [
          {
            title: "src/main.tsx",
            language: "tsx",
            code: `import { createRoot } from "react-dom/client";
import { AuthProvider } from "react-oidc-context";
import App from "./App";

const oidcConfig = {
  authority: ${q(v.issuer)},
  client_id: ${q(v.clientId)},
  redirect_uri: ${q(v.redirectUri)},
  post_logout_redirect_uri: ${q(v.postLogoutUri)},
  scope: ${q(scope)},
  // Remove ?code=&state= from the address bar after sign-in.
  onSigninCallback: () => window.history.replaceState({}, document.title, window.location.pathname),
};

createRoot(document.getElementById("root")!).render(
  <AuthProvider {...oidcConfig}>
    <App />
  </AuthProvider>,
);`,
          },
          {
            title: "src/App.tsx",
            language: "tsx",
            code: `import { useAuth } from "react-oidc-context";

export default function App() {
  const auth = useAuth();
  if (auth.isLoading) return <p>Loading…</p>;
  if (auth.error) return <p>Sign-in failed: {auth.error.message}</p>;
  if (!auth.isAuthenticated) return <button onClick={() => auth.signinRedirect()}>Sign in</button>;

  // Call your API with: fetch(url, { headers: { Authorization: \`Bearer \${auth.user?.access_token}\` } })
  return (
    <p>
      Signed in as {auth.user?.profile.email}{" "}
      <button onClick={() => auth.signoutRedirect()}>Sign out</button>
    </p>
  );
}`,
          },
        ],
        notes: [
          "No secret: the app proves itself with PKCE. Tokens stay in the browser (session storage by default).",
          `The browser origin ${new URL(v.redirectUri).origin} is allowed automatically because it is registered on this client.`,
        ],
      };

    case "angular":
      return {
        install: "npm install angular-auth-oidc-client",
        snippets: [
          {
            title: "src/app/app.config.ts",
            language: "ts",
            code: `import { ApplicationConfig } from "@angular/core";
import { provideAuth } from "angular-auth-oidc-client";

export const appConfig: ApplicationConfig = {
  providers: [
    provideAuth({
      config: {
        authority: ${q(v.issuer)},
        clientId: ${q(v.clientId)},
        redirectUrl: ${q(v.redirectUri)},
        postLogoutRedirectUri: ${q(v.postLogoutUri)},
        scope: ${q(scope)},
        responseType: "code",
        silentRenew: true,
        useRefreshToken: true,
      },
    }),
  ],
};`,
          },
          {
            title: "src/app/app.component.ts",
            language: "ts",
            code: `import { Component, OnInit, inject } from "@angular/core";
import { AsyncPipe } from "@angular/common";
import { OidcSecurityService } from "angular-auth-oidc-client";

@Component({
  selector: "app-root",
  imports: [AsyncPipe],
  template: \`
    @if ((auth.isAuthenticated$ | async)?.isAuthenticated) {
      <p>Signed in as {{ (auth.userData$ | async)?.userData?.email }} <button (click)="auth.logoff().subscribe()">Sign out</button></p>
    } @else {
      <button (click)="auth.authorize()">Sign in</button>
    }
  \`,
})
export class AppComponent implements OnInit {
  readonly auth = inject(OidcSecurityService);
  ngOnInit() {
    this.auth.checkAuth().subscribe();
  }
}`,
          },
        ],
        notes: ["No secret: the app proves itself with PKCE."],
      };

    case "javascript":
      return {
        install: "npm install oidc-client-ts   (or load it from a CDN as an ES module)",
        snippets: [
          {
            title: "auth.js",
            language: "js",
            code: `import { UserManager } from "oidc-client-ts";

export const userManager = new UserManager({
  authority: ${q(v.issuer)},
  client_id: ${q(v.clientId)},
  redirect_uri: ${q(v.redirectUri)},
  post_logout_redirect_uri: ${q(v.postLogoutUri)},
  response_type: "code",
  scope: ${q(scope)},
});

export const signIn = () => userManager.signinRedirect();
export const signOut = () => userManager.signoutRedirect();
export const currentUser = () => userManager.getUser(); // .access_token, .profile.email`,
          },
          {
            title: `${new URL(v.redirectUri).pathname} (callback page script)`,
            language: "js",
            code: `import { userManager } from "./auth.js";

await userManager.signinRedirectCallback();
window.location.replace("/");`,
          },
        ],
        notes: ["No secret: the app proves itself with PKCE."],
      };

    // ---------------------------------------------------------------- mobile apps
    case "react-native":
      return {
        install: "npm install react-native-app-auth",
        snippets: [
          {
            title: "auth.ts",
            language: "ts",
            code: `import { authorize, logout, refresh } from "react-native-app-auth";

const config = {
  issuer: ${q(v.issuer)},
  clientId: ${q(v.clientId)},
  redirectUrl: ${q(v.redirectUri)},
  scopes: ${JSON.stringify(v.scopes)},${httpIssuer ? "\n  dangerouslyAllowInsecureHttpRequests: true, // local development only" : ""}
};

export async function signIn() {
  const result = await authorize(config);
  return result; // accessToken, idToken, refreshToken, accessTokenExpirationDate
}

export const renew = (refreshToken: string) => refresh(config, { refreshToken });
export const signOut = (idToken: string) =>
  logout(config, { idToken, postLogoutRedirectUrl: ${q(v.postLogoutUri)} });`,
          },
          {
            title: "android/app/build.gradle",
            language: "groovy",
            code: `android {
  defaultConfig {
    manifestPlaceholders = [appAuthRedirectScheme: ${q(v.appScheme)}]
  }
}`,
          },
          {
            title: "ios/<App>/Info.plist",
            language: "xml",
            code: `<key>CFBundleURLTypes</key>
<array>
  <dict>
    <key>CFBundleURLSchemes</key>
    <array><string>${v.appScheme}</string></array>
  </dict>
</array>`,
          },
        ],
        notes: mobileNotes(v, httpIssuer),
      };

    case "android":
      return {
        install: 'implementation("net.openid:appauth:0.11.1")  // and: manifestPlaceholders["appAuthRedirectScheme"] = "' + v.appScheme + '"',
        snippets: [
          {
            title: "SignIn.kt",
            language: "kotlin",
            code: `private val authService by lazy { AuthorizationService(this) }

private val signIn = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
    val response = AuthorizationResponse.fromIntent(result.data ?: return@registerForActivityResult)
        ?: return@registerForActivityResult
    authService.performTokenRequest(response.createTokenExchangeRequest()) { tokens, error ->
        // tokens?.accessToken, tokens?.idToken, tokens?.refreshToken
    }
}

fun startSignIn() {
    AuthorizationServiceConfiguration.fetchFromIssuer(Uri.parse(${q(v.issuer)})) { config, error ->
        if (config == null) return@fetchFromIssuer
        val request = AuthorizationRequest.Builder(
            config,
            ${q(v.clientId)},
            ResponseTypeValues.CODE,
            Uri.parse(${q(v.redirectUri)}),
        ).setScope(${q(scope)}).build()
        signIn.launch(authService.getAuthorizationRequestIntent(request))
    }
}`,
          },
        ],
        notes: mobileNotes(v, httpIssuer),
      };

    case "ios":
      return {
        install: "Swift Package: https://github.com/openid/AppAuth-iOS  (add the URL scheme " + v.appScheme + " to Info.plist)",
        snippets: [
          {
            title: "SignIn.swift",
            language: "swift",
            code: `import AppAuth

var currentFlow: OIDExternalUserAgentSession?

func signIn(from viewController: UIViewController) {
    OIDAuthorizationService.discoverConfiguration(forIssuer: URL(string: ${q(v.issuer)})!) { configuration, error in
        guard let configuration else { return }
        let request = OIDAuthorizationRequest(
            configuration: configuration,
            clientId: ${q(v.clientId)},
            scopes: ${JSON.stringify(v.scopes)},
            redirectURL: URL(string: ${q(v.redirectUri)})!,
            responseType: OIDResponseTypeCode,
            additionalParameters: nil)
        currentFlow = OIDAuthState.authState(byPresenting: request, presenting: viewController) { authState, error in
            // authState?.lastTokenResponse?.accessToken, .idToken, .refreshToken
        }
    }
}`,
          },
        ],
        notes: mobileNotes(v, httpIssuer),
      };

    case "flutter":
      return {
        install: "flutter pub add flutter_appauth   (Android: manifestPlaceholders appAuthRedirectScheme = " + v.appScheme + ")",
        snippets: [
          {
            title: "lib/auth.dart",
            language: "dart",
            code: `import 'package:flutter_appauth/flutter_appauth.dart';

const appAuth = FlutterAppAuth();

Future<AuthorizationTokenResponse> signIn() {
  return appAuth.authorizeAndExchangeCode(
    AuthorizationTokenRequest(
      ${q(v.clientId)},
      ${q(v.redirectUri)},
      issuer: ${q(v.issuer)},
      scopes: ${JSON.stringify(v.scopes).replace(/"/g, "'")},${httpIssuer ? "\n      allowInsecureConnections: true, // local development only" : ""}
    ),
  );
}`,
          },
        ],
        notes: mobileNotes(v, httpIssuer),
      };

    // ---------------------------------------------------------------- backend services
    case "curl":
      return {
        snippets: [
          {
            title: "Get an access token",
            language: "bash",
            code: `curl -s -u "${v.clientId}:$SECUREONE_CLIENT_SECRET" \\
  -d grant_type=client_credentials \\
  ${v.issuer}/oauth2/token
# → {"access_token":"…","token_type":"Bearer","expires_in":…}
# Send it to your API:  curl -H "Authorization: Bearer $TOKEN" https://api.example.com/…`,
          },
        ],
        notes: serviceNotes(),
      };

    case "node":
      return {
        snippets: [
          {
            title: "token.mjs",
            language: "js",
            code: `const ISSUER = ${q(v.issuer)};
const CLIENT_ID = ${q(v.clientId)};
let cached = { token: null, expiresAt: 0 };

export async function accessToken() {
  if (cached.token && Date.now() < cached.expiresAt - 30_000) return cached.token;
  const res = await fetch(\`\${ISSUER}/oauth2/token\`, {
    method: "POST",
    headers: {
      "Content-Type": "application/x-www-form-urlencoded",
      Authorization: "Basic " + Buffer.from(\`\${CLIENT_ID}:\${process.env.SECUREONE_CLIENT_SECRET}\`).toString("base64"),
    },
    body: new URLSearchParams({ grant_type: "client_credentials" }),
  });
  if (!res.ok) throw new Error(\`token request failed: \${res.status} \${await res.text()}\`);
  const { access_token, expires_in } = await res.json();
  cached = { token: access_token, expiresAt: Date.now() + expires_in * 1000 };
  return access_token;
}

// await fetch("https://api.example.com/orders", { headers: { Authorization: \`Bearer \${await accessToken()}\` } });`,
          },
        ],
        notes: serviceNotes(),
      };

    case "java":
      return {
        install: "Add the dependency org.springframework.boot:spring-boot-starter-oauth2-client",
        snippets: [
          {
            title: "application.yml",
            language: "yaml",
            code: `spring:
  security:
    oauth2:
      client:
        registration:
          secureone:
            client-id: ${v.clientId}
            client-secret: \${SECUREONE_CLIENT_SECRET}
            authorization-grant-type: client_credentials
        provider:
          secureone:
            issuer-uri: ${v.issuer}`,
          },
          {
            title: "ApiClientConfig.java",
            language: "java",
            code: `@Configuration
class ApiClientConfig {

    // Every request gets a SecureOne access token (fetched and renewed automatically).
    @Bean
    RestClient ordersApi(RestClient.Builder builder, OAuth2AuthorizedClientManager clients) {
        var tokens = new OAuth2ClientHttpRequestInterceptor(clients);
        tokens.setClientRegistrationIdResolver(request -> "secureone");
        return builder.baseUrl("https://api.example.com").requestInterceptor(tokens).build();
    }
}`,
          },
        ],
        notes: serviceNotes(),
      };

    case "python":
      return {
        install: "pip install requests",
        snippets: [
          {
            title: "token.py",
            language: "python",
            code: `import os
import time
import requests

ISSUER = ${q(v.issuer)}
CLIENT_ID = ${q(v.clientId)}
_cache = {"token": None, "expires_at": 0}

def access_token() -> str:
    if _cache["token"] and time.time() < _cache["expires_at"] - 30:
        return _cache["token"]
    res = requests.post(
        f"{ISSUER}/oauth2/token",
        data={"grant_type": "client_credentials"},
        auth=(CLIENT_ID, os.environ["SECUREONE_CLIENT_SECRET"]),
        timeout=10,
    )
    res.raise_for_status()
    body = res.json()
    _cache.update(token=body["access_token"], expires_at=time.time() + body["expires_in"])
    return _cache["token"]

# requests.get("https://api.example.com/orders", headers={"Authorization": f"Bearer {access_token()}"})`,
          },
        ],
        notes: serviceNotes(),
      };

    // ---------------------------------------------------------------- TV / CLI (device grant)
    case "shell":
      return {
        snippets: [
          {
            title: "1. Ask for a code",
            language: "bash",
            code: `curl -s -d client_id=${v.clientId} -d scope="${scope}" ${v.issuer}/oauth2/device_authorization
# → {"device_code":"…","user_code":"WDJB-MJHT","verification_uri_complete":"…","interval":5,"expires_in":…}
# Show the user_code and verification_uri (or a QR code of verification_uri_complete).`,
          },
          {
            title: "2. Poll until the user approves",
            language: "bash",
            code: `curl -s -d grant_type=urn:ietf:params:oauth:grant-type:device_code \\
  -d device_code=$DEVICE_CODE -d client_id=${v.clientId} \\
  ${v.issuer}/oauth2/token
# Repeat every "interval" seconds: authorization_pending → keep waiting; slow_down → add 5 s;
# access_denied / expired_token → stop. Success returns access_token and refresh_token.`,
          },
        ],
        notes: deviceNotes(),
      };

    case "node-cli":
      return {
        snippets: [
          {
            title: "login.mjs",
            language: "js",
            code: `const ISSUER = ${q(v.issuer)};
const CLIENT_ID = ${q(v.clientId)};

const post = (path, body) =>
  fetch(ISSUER + path, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams(body),
  }).then(async (r) => ({ ok: r.ok, body: await r.json() }));

const start = await post("/oauth2/device_authorization", { client_id: CLIENT_ID, scope: ${q(scope)} });
const { device_code, user_code, verification_uri, verification_uri_complete } = start.body;
let interval = start.body.interval ?? 5;
console.log(\`Open \${verification_uri} and enter \${user_code}\\n(or: \${verification_uri_complete})\`);

for (;;) {
  await new Promise((r) => setTimeout(r, interval * 1000));
  const res = await post("/oauth2/token", {
    grant_type: "urn:ietf:params:oauth:grant-type:device_code",
    device_code,
    client_id: CLIENT_ID,
  });
  if (res.ok) {
    console.log("Signed in. Access token expires in", res.body.expires_in, "s");
    break; // store res.body.refresh_token securely to stay signed in
  }
  if (res.body.error === "authorization_pending") continue;
  if (res.body.error === "slow_down") { interval += 5; continue; }
  throw new Error(\`Sign-in failed: \${res.body.error}\`);
}`,
          },
        ],
        notes: deviceNotes(),
      };

    // ---------------------------------------------------------------- APIs that accept tokens
    case "spring-api":
      return {
        install: "Add the dependency org.springframework.boot:spring-boot-starter-oauth2-resource-server",
        snippets: [
          {
            title: "application.yml",
            language: "yaml",
            code: `spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${v.issuer}
          audiences: ${v.audiences.join(",") || "<client ids allowed to call this API>"}`,
          },
          {
            title: "SecurityConfig.java",
            language: "java",
            code: `@Configuration
class SecurityConfig {

    @Bean
    SecurityFilterChain api(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/orders/**").access(hasPermission("orders:read"))
                .anyRequest().authenticated())
            .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()));
        return http.build();
    }

    // SecureOne puts the user's application permissions in the "permissions" claim.
    static AuthorizationManager<RequestAuthorizationContext> hasPermission(String permission) {
        return (authentication, context) -> new AuthorizationDecision(
            authentication.get() instanceof JwtAuthenticationToken jwt
                && jwt.getToken().getClaimAsStringList("permissions") != null
                && jwt.getToken().getClaimAsStringList("permissions").contains(permission));
    }
}`,
          },
        ],
        notes: apiNotes(v),
      };

    case "node-api":
      return {
        install: "npm install express jose",
        snippets: [
          {
            title: "auth.mjs",
            language: "js",
            code: `import { createRemoteJWKSet, jwtVerify } from "jose";

const ISSUER = ${q(v.issuer)};
const AUDIENCES = ${JSON.stringify(v.audiences.length ? v.audiences : ["<client id>"])};
const JWKS = createRemoteJWKSet(new URL(\`\${ISSUER}/oauth2/jwks\`));

/** Express middleware: 401 without a valid SecureOne token, 403 without the permission. */
export const requirePermission = (permission) => async (req, res, next) => {
  const token = req.headers.authorization?.match(/^Bearer (.+)$/)?.[1];
  try {
    const { payload } = await jwtVerify(token ?? "", JWKS, { issuer: ISSUER, audience: AUDIENCES });
    if (permission && !(payload.permissions ?? []).includes(permission)) {
      return res.status(403).json({ error: "insufficient_scope" });
    }
    req.user = payload; // sub, email, roles, permissions
    next();
  } catch {
    res.status(401).set("WWW-Authenticate", "Bearer").json({ error: "invalid_token" });
  }
};

// app.get("/orders", requirePermission("orders:read"), (req, res) => res.json([]));`,
          },
        ],
        notes: apiNotes(v),
      };

    case "aspnet-api":
      return {
        install: "dotnet add package Microsoft.AspNetCore.Authentication.JwtBearer",
        snippets: [
          {
            title: "Program.cs",
            language: "csharp",
            code: `var builder = WebApplication.CreateBuilder(args);

builder.Services
    .AddAuthentication()
    .AddJwtBearer(options =>
    {
        options.Authority = ${q(v.issuer)};
        options.MapInboundClaims = false;
        options.TokenValidationParameters.ValidAudiences = new[] { ${(v.audiences.length ? v.audiences : ["<client id>"]).map(q).join(", ")} };${httpIssuer ? "\n        options.RequireHttpsMetadata = false; // local development only" : ""}
    });
builder.Services.AddAuthorization(options =>
    options.AddPolicy("orders:read", p => p.RequireClaim("permissions", "orders:read")));

var app = builder.Build();
app.UseAuthentication();
app.UseAuthorization();
app.MapGet("/orders", () => Array.Empty<object>()).RequireAuthorization("orders:read");
app.Run();`,
          },
        ],
        notes: apiNotes(v),
      };

    case "python-api":
      return {
        install: "pip install pyjwt[crypto]",
        snippets: [
          {
            title: "auth.py",
            language: "python",
            code: `import jwt

ISSUER = ${q(v.issuer)}
AUDIENCES = ${JSON.stringify(v.audiences.length ? v.audiences : ["<client id>"])}
_jwks = jwt.PyJWKClient(f"{ISSUER}/oauth2/jwks")

def verify(authorization_header: str, permission: str | None = None) -> dict:
    """Claims of a valid SecureOne access token; raises jwt.PyJWTError otherwise."""
    token = authorization_header.removeprefix("Bearer ").strip()
    key = _jwks.get_signing_key_from_jwt(token)
    claims = jwt.decode(token, key.key, algorithms=["RS256"], issuer=ISSUER, audience=AUDIENCES)
    if permission and permission not in claims.get("permissions", []):
        raise PermissionError(permission)
    return claims  # sub, email, roles, permissions`,
          },
        ],
        notes: apiNotes(v),
      };

    case "flask":
      return {
        install: "pip install flask authlib requests",
        snippets: [
          {
            title: "app.py",
            language: "python",
            code: `import os
from urllib.parse import urlencode
from flask import Flask, redirect, session, url_for
from authlib.integrations.flask_client import OAuth

ISSUER = os.environ.get("SECUREONE_ISSUER", ${q(v.issuer)})
REDIRECT_URI = ${q(v.redirectUri)}
POST_LOGOUT_URI = ${q(v.postLogoutUri)}

app = Flask(__name__)
app.secret_key = os.environ["SESSION_SECRET"]  # signs the session cookie

oauth = OAuth(app)
oauth.register(
    "secureone",
    client_id=os.environ.get("SECUREONE_CLIENT_ID", ${q(v.clientId)}),
    client_secret=os.environ["SECUREONE_CLIENT_SECRET"],
    server_metadata_url=f"{ISSUER}/.well-known/openid-configuration",
    client_kwargs={"scope": ${q(scope)}, "code_challenge_method": "S256"},  # PKCE
)

@app.route("/login")
def login():
    return oauth.secureone.authorize_redirect(REDIRECT_URI)

@app.route(${q(new URL(v.redirectUri).pathname)})
def callback():
    token = oauth.secureone.authorize_access_token()  # checks state, PKCE and the ID token
    session["user"] = token["userinfo"]  # sub, email, name, roles, permissions
    session["id_token"] = token["id_token"]
    return redirect("/")

@app.route("/logout")
def logout():
    end_session = oauth.secureone.load_server_metadata()["end_session_endpoint"]
    query = urlencode({"id_token_hint": session.pop("id_token", ""), "post_logout_redirect_uri": POST_LOGOUT_URI})
    session.clear()
    return redirect(f"{end_session}?{query}")

@app.route("/")
def home():
    user = session.get("user")
    if not user:
        return '<a href="/login">Sign in</a>'
    return f'Signed in as {user["email"]} · <a href="/logout">Sign out</a>'

if __name__ == "__main__":
    app.run(port=${new URL(v.appUrl).port || 5000})`,
          },
        ],
        notes: [
          "Set SESSION_SECRET and SECUREONE_CLIENT_SECRET in the environment before starting.",
          "Protect a route by checking session.get(\"user\"); the user's permissions are in session[\"user\"][\"permissions\"].",
        ],
      };

    case "django":
      return {
        install: "pip install mozilla-django-oidc",
        snippets: [
          {
            title: "settings.py",
            language: "python",
            code: `import os

ISSUER = os.environ.get("SECUREONE_ISSUER", ${q(v.issuer)})

INSTALLED_APPS += ["mozilla_django_oidc"]  # after django.contrib.auth
AUTHENTICATION_BACKENDS = ["mozilla_django_oidc.auth.OIDCAuthenticationBackend"]

OIDC_RP_CLIENT_ID = os.environ.get("SECUREONE_CLIENT_ID", ${q(v.clientId)})
OIDC_RP_CLIENT_SECRET = os.environ["SECUREONE_CLIENT_SECRET"]
OIDC_RP_SIGN_ALGO = "RS256"
OIDC_RP_SCOPES = ${q(scope)}
OIDC_USE_PKCE = True
OIDC_OP_AUTHORIZATION_ENDPOINT = f"{ISSUER}/oauth2/authorize"
OIDC_OP_TOKEN_ENDPOINT = f"{ISSUER}/oauth2/token"
OIDC_OP_USER_ENDPOINT = f"{ISSUER}/userinfo"
OIDC_OP_JWKS_ENDPOINT = f"{ISSUER}/oauth2/jwks"

LOGIN_URL = "/oidc/authenticate/"
LOGIN_REDIRECT_URL = "/"
LOGOUT_REDIRECT_URL = ${q(v.postLogoutUri)}`,
          },
          {
            title: "urls.py",
            language: "python",
            code: `from django.urls import include, path

urlpatterns = [
    # /oidc/authenticate/ (sign in), /oidc/callback/ (redirect URL), /oidc/logout/ (POST to sign out)
    path("oidc/", include("mozilla_django_oidc.urls")),
    # ...your routes; protect views with @login_required
]`,
          },
        ],
        notes: [
          `Redirect URL registered for this client: ${v.redirectUri} (mozilla-django-oidc's /oidc/callback/).`,
          "Django creates a local user for each SecureOne account on first sign-in (matched by email).",
          "Set SECUREONE_CLIENT_SECRET in the environment.",
        ],
      };

    case "python-bff": {
      const apiPort = 8000;
      return {
        install: "pip install fastapi uvicorn authlib httpx itsdangerous",
        snippets: [
          {
            title: "backend/main.py (Python)",
            language: "python",
            code: `import os
from urllib.parse import urlencode
from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import RedirectResponse
from starlette.middleware.sessions import SessionMiddleware
from authlib.integrations.starlette_client import OAuth

ISSUER = os.environ.get("SECUREONE_ISSUER", ${q(v.issuer)})
FRONTEND_URL = ${q(v.appUrl)}            # where the browser is; it proxies /auth and /api to this server
REDIRECT_URI = ${q(v.redirectUri)}
POST_LOGOUT_URI = ${q(v.postLogoutUri)}

app = FastAPI()
# HttpOnly session cookie for the browser; tokens never reach the frontend's JavaScript.
app.add_middleware(
    SessionMiddleware,
    secret_key=os.environ["SESSION_SECRET"],
    same_site="lax",
    https_only=FRONTEND_URL.startswith("https://"),
)

oauth = OAuth()
oauth.register(
    "secureone",
    client_id=os.environ.get("SECUREONE_CLIENT_ID", ${q(v.clientId)}),
    client_secret=os.environ["SECUREONE_CLIENT_SECRET"],
    server_metadata_url=f"{ISSUER}/.well-known/openid-configuration",
    client_kwargs={"scope": ${q(scope)}, "code_challenge_method": "S256"},
)

@app.get("/auth/login")
async def login(request: Request):
    return await oauth.secureone.authorize_redirect(request, REDIRECT_URI)

@app.get(${q(new URL(v.redirectUri).pathname)})
async def callback(request: Request):
    token = await oauth.secureone.authorize_access_token(request)
    request.session["user"] = dict(token["userinfo"])  # sub, email, roles, permissions
    request.session["id_token"] = token["id_token"]
    return RedirectResponse(FRONTEND_URL)

@app.get("/auth/logout")
async def logout(request: Request):
    metadata = await oauth.secureone.load_server_metadata()
    query = urlencode({"id_token_hint": request.session.get("id_token", ""), "post_logout_redirect_uri": POST_LOGOUT_URI})
    request.session.clear()
    return RedirectResponse(f"{metadata['end_session_endpoint']}?{query}")

def current_user(request: Request) -> dict:
    user = request.session.get("user")
    if not user:
        raise HTTPException(status_code=401, detail="Not signed in")
    return user

@app.get("/api/me")
async def me(request: Request):
    return current_user(request)

@app.get("/api/orders")
async def orders(request: Request):
    user = current_user(request)
    if "orders:read" not in user.get("permissions", []):
        raise HTTPException(status_code=403)
    return []

# Run: uvicorn main:app --port ${apiPort}`,
          },
          {
            title: "frontend/vite.config.ts (dev proxy)",
            language: "ts",
            code: `import { defineConfig } from "vite";

// The browser talks to ONE origin (${v.appUrl}); the dev server forwards sign-in and API calls to Python,
// so the session cookie is first-party. In production, serve both behind the same domain the same way.
export default defineConfig({
  server: {
    port: ${new URL(v.appUrl).port || 5173},
    proxy: {
      "/auth": "http://localhost:${apiPort}",
      "/api": "http://localhost:${apiPort}",
    },
  },
});`,
          },
          {
            title: "frontend/src/auth.ts (React, Vue, Angular…)",
            language: "ts",
            code: `/** The signed-in user, or null. The session cookie is sent automatically (same origin). */
export async function currentUser(): Promise<{ email: string; permissions?: string[] } | null> {
  const res = await fetch("/api/me", { credentials: "include" });
  return res.ok ? res.json() : null;
}

export const signIn = () => window.location.assign("/auth/login");
export const signOut = () => window.location.assign("/auth/logout");

/** Call your Python API; no tokens in the browser. */
export async function api<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(path, { ...init, credentials: "include" });
  if (res.status === 401) signIn();
  if (!res.ok) throw new Error(\`\${res.status} \${res.statusText}\`);
  return res.json();
}`,
          },
        ],
        notes: [
          "Backend-for-frontend: Python is the OAuth client (it keeps the secret and tokens); the frontend only sees an HttpOnly session cookie, which is the safest way to sign users in to a browser app.",
          `Redirect URL registered for this client: ${v.redirectUri} — the browser reaches it through the frontend's origin, and the dev proxy passes it to Python.`,
          "Set SESSION_SECRET and SECUREONE_CLIENT_SECRET for the Python process. Need to call another API as the user? Keep token[\"access_token\"] in a server-side store, not in the cookie.",
          "Prefer the frontend to hold tokens itself? Register it as a Single-page app instead, and protect the Python API with the API setup (Python (FastAPI)).",
        ],
      };
    }

    case "vue":
      return {
        install: "npm install oidc-client-ts",
        snippets: [
          {
            title: "src/auth.ts",
            language: "ts",
            code: `import { UserManager, WebStorageStateStore, type User } from "oidc-client-ts";
import { ref } from "vue";

export const userManager = new UserManager({
  authority: ${q(v.issuer)},
  client_id: ${q(v.clientId)},
  redirect_uri: ${q(v.redirectUri)},
  post_logout_redirect_uri: ${q(v.postLogoutUri)},
  scope: ${q(scope)},
  userStore: new WebStorageStateStore({ store: window.sessionStorage }),
});

export const user = ref<User | null>(null);
void userManager.getUser().then((u) => (user.value = u));
userManager.events.addUserLoaded((u) => (user.value = u));
userManager.events.addUserUnloaded(() => (user.value = null));

export const signIn = () => userManager.signinRedirect();
export const signOut = () => userManager.signoutRedirect();`,
          },
          {
            title: "src/router.ts",
            language: "ts",
            code: `import { createRouter, createWebHistory } from "vue-router";
import { userManager } from "./auth";
import Home from "./Home.vue";

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: "/", component: Home },
    {
      path: ${q(new URL(v.redirectUri).pathname)},
      component: Home,
      beforeEnter: async () => {
        await userManager.signinRedirectCallback(); // checks state and PKCE, stores the tokens
        return "/";
      },
    },
  ],
});`,
          },
          {
            title: "src/Home.vue",
            language: "vue",
            code: `<script setup lang="ts">
import { user, signIn, signOut } from "./auth";
// Call your API with: fetch(url, { headers: { Authorization: \`Bearer \${user.value?.access_token}\` } })
</script>

<template>
  <p v-if="user">Signed in as {{ user.profile.email }} <button @click="signOut">Sign out</button></p>
  <button v-else @click="signIn">Sign in</button>
</template>`,
          },
        ],
        notes: [
          "No secret: the app proves itself with PKCE. Tokens stay in the browser (session storage).",
          `The browser origin ${new URL(v.redirectUri).origin} is allowed automatically because it is registered on this client.`,
        ],
      };

    case "electron":
      return {
        install: "npm install openid-client",
        snippets: [
          {
            title: "main/auth.mjs (main process)",
            language: "js",
            code: `import http from "node:http";
import { shell } from "electron";
import * as client from "openid-client";

const ISSUER = new URL(${q(v.issuer)});
const REDIRECT_URI = ${q(v.redirectUri)}; // loopback address registered on the client

const config = await client.discovery(ISSUER, ${q(v.clientId)}, undefined, client.None()${httpIssuer ? ", {\n  execute: [client.allowInsecureRequests], // http:// only for local development\n}" : ""});

/** Opens the system browser, waits for the redirect on 127.0.0.1 and returns the tokens. */
export async function signIn() {
  const codeVerifier = client.randomPKCECodeVerifier();
  const state = client.randomState();
  const url = client.buildAuthorizationUrl(config, {
    redirect_uri: REDIRECT_URI,
    scope: ${q(scope)},
    code_challenge: await client.calculatePKCECodeChallenge(codeVerifier),
    code_challenge_method: "S256",
    state,
  });

  const callbackUrl = await new Promise((resolve, reject) => {
    const { port, pathname } = new URL(REDIRECT_URI);
    const server = http.createServer((req, res) => {
      if (!req.url.startsWith(pathname)) return res.writeHead(404).end();
      res.end("Signed in. You can close this window and return to the app.");
      server.close();
      resolve(new URL(req.url, REDIRECT_URI));
    });
    server.on("error", reject);
    server.listen(Number(port), "127.0.0.1", () => shell.openExternal(url.href));
  });

  const tokens = await client.authorizationCodeGrant(config, callbackUrl, {
    pkceCodeVerifier: codeVerifier,
    expectedState: state,
  });
  return { user: tokens.claims(), accessToken: tokens.access_token, refreshToken: tokens.refresh_token };
}

/** New access token without a browser round trip. */
export async function refresh(refreshToken) {
  const tokens = await client.refreshTokenGrant(config, refreshToken);
  return { accessToken: tokens.access_token, refreshToken: tokens.refresh_token ?? refreshToken };
}`,
          },
        ],
        notes: desktopNotes(v),
      };

    case "dotnet-desktop":
      return {
        install: "dotnet add package Duende.IdentityModel.OidcClient",
        snippets: [
          {
            title: "SecureOneSignIn.cs",
            language: "csharp",
            code: `using System.Diagnostics;
using System.Net;
using Duende.IdentityModel.OidcClient;
using Duende.IdentityModel.OidcClient.Browser;

public static class SecureOneSignIn
{
    static readonly OidcClient Client = new(new OidcClientOptions
    {
        Authority = ${q(v.issuer)},
        ClientId = ${q(v.clientId)},
        Scope = ${q(scope)},
        RedirectUri = ${q(v.redirectUri)}, // loopback address registered on the client
        PostLogoutRedirectUri = ${q(v.postLogoutUri)},
        Browser = new LoopbackBrowser(),${httpIssuer ? "\n        Policy = { Discovery = { RequireHttps = false } }, // local development only" : ""}
    });

    /// <summary>Opens the system browser; PKCE and state are handled by OidcClient.</summary>
    public static async Task<LoginResult> SignInAsync() => await Client.LoginAsync();

    public static Task<RefreshTokenResult> RefreshAsync(string refreshToken) => Client.RefreshTokenAsync(refreshToken);
}

/// <summary>System browser + a one-shot HTTP listener on the loopback redirect URI (RFC 8252).</summary>
sealed class LoopbackBrowser : IBrowser
{
    public async Task<BrowserResult> InvokeAsync(BrowserOptions options, CancellationToken cancellationToken = default)
    {
        var prefix = new Uri(options.EndUrl).GetLeftPart(UriPartial.Path).TrimEnd('/') + "/";
        using var listener = new HttpListener();
        listener.Prefixes.Add(prefix);
        listener.Start();
        Process.Start(new ProcessStartInfo(options.StartUrl) { UseShellExecute = true });

        var context = await listener.GetContextAsync().WaitAsync(cancellationToken);
        var body = System.Text.Encoding.UTF8.GetBytes("Signed in. You can close this window and return to the app.");
        context.Response.ContentType = "text/plain; charset=utf-8";
        await context.Response.OutputStream.WriteAsync(body, cancellationToken);
        context.Response.Close();

        return new BrowserResult { ResultType = BrowserResultType.Success, Response = context.Request.Url!.ToString() };
    }
}`,
          },
        ],
        notes: desktopNotes(v),
      };

    case "python-desktop":
      return {
        install: "pip install requests",
        snippets: [
          {
            title: "auth.py",
            language: "python",
            code: `import base64, hashlib, http.server, secrets, urllib.parse, webbrowser
import requests

ISSUER = ${q(v.issuer)}
CLIENT_ID = ${q(v.clientId)}
REDIRECT_URI = ${q(v.redirectUri)}  # loopback address registered on the client
SCOPE = ${q(scope)}

def sign_in() -> dict:
    """Opens the system browser, waits for the redirect on 127.0.0.1 and returns the tokens."""
    verifier = secrets.token_urlsafe(64)
    challenge = base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).rstrip(b"=").decode()
    state = secrets.token_urlsafe(16)
    webbrowser.open(f"{ISSUER}/oauth2/authorize?" + urllib.parse.urlencode({
        "response_type": "code", "client_id": CLIENT_ID, "redirect_uri": REDIRECT_URI, "scope": SCOPE,
        "state": state, "code_challenge": challenge, "code_challenge_method": "S256",
    }))

    result: dict = {}
    class Handler(http.server.BaseHTTPRequestHandler):
        def do_GET(self):
            result.update(urllib.parse.parse_qs(urllib.parse.urlparse(self.path).query))
            self.send_response(200)
            self.end_headers()
            self.wfile.write(b"Signed in. You can close this window and return to the app.")
        def log_message(self, *args):
            pass

    target = urllib.parse.urlparse(REDIRECT_URI)
    http.server.HTTPServer((target.hostname, target.port), Handler).handle_request()
    if result.get("state", [None])[0] != state or "code" not in result:
        raise RuntimeError(f"Sign-in failed: {result.get('error', ['unknown'])[0]}")

    res = requests.post(f"{ISSUER}/oauth2/token", data={
        "grant_type": "authorization_code", "code": result["code"][0], "redirect_uri": REDIRECT_URI,
        "client_id": CLIENT_ID, "code_verifier": verifier,
    }, timeout=10)
    res.raise_for_status()
    return res.json()  # access_token, refresh_token, id_token, expires_in

def refresh(refresh_token: str) -> dict:
    res = requests.post(f"{ISSUER}/oauth2/token", data={
        "grant_type": "refresh_token", "refresh_token": refresh_token, "client_id": CLIENT_ID,
    }, timeout=10)
    res.raise_for_status()
    return res.json()`,
          },
        ],
        notes: desktopNotes(v),
      };

    case "python-cli":
      return {
        install: "pip install requests",
        snippets: [
          {
            title: "login.py",
            language: "python",
            code: `import time
import requests

ISSUER = ${q(v.issuer)}
CLIENT_ID = ${q(v.clientId)}

start = requests.post(f"{ISSUER}/oauth2/device_authorization",
                      data={"client_id": CLIENT_ID, "scope": ${q(scope)}}, timeout=10).json()
print(f"Open {start['verification_uri']} and enter {start['user_code']}")
print(f"(or: {start['verification_uri_complete']})")

interval = start.get("interval", 5)
while True:
    time.sleep(interval)
    res = requests.post(f"{ISSUER}/oauth2/token", data={
        "grant_type": "urn:ietf:params:oauth:grant-type:device_code",
        "device_code": start["device_code"],
        "client_id": CLIENT_ID,
    }, timeout=10)
    body = res.json()
    if res.ok:
        print("Signed in. Access token expires in", body["expires_in"], "s")
        break  # store body["refresh_token"] securely (e.g. the OS keyring) to stay signed in
    if body.get("error") == "authorization_pending":
        continue
    if body.get("error") == "slow_down":
        interval += 5
        continue
    raise SystemExit(f"Sign-in failed: {body.get('error')}")`,
          },
        ],
        notes: deviceNotes(),
      };

    case "fastapi-api":
      return {
        install: "pip install fastapi pyjwt[crypto]",
        snippets: [
          {
            title: "auth.py",
            language: "python",
            code: `import jwt
from fastapi import Depends, Header, HTTPException

ISSUER = ${q(v.issuer)}
AUDIENCES = ${JSON.stringify(v.audiences.length ? v.audiences : ["<client id>"])}
_jwks = jwt.PyJWKClient(f"{ISSUER}/oauth2/jwks")

def require(permission: str | None = None):
    """FastAPI dependency: 401 without a valid SecureOne token, 403 without the permission."""
    def dependency(authorization: str = Header(default="")) -> dict:
        token = authorization.removeprefix("Bearer ").strip()
        try:
            key = _jwks.get_signing_key_from_jwt(token)
            claims = jwt.decode(token, key.key, algorithms=["RS256"], issuer=ISSUER, audience=AUDIENCES)
        except jwt.PyJWTError:
            raise HTTPException(status_code=401, headers={"WWW-Authenticate": "Bearer"})
        if permission and permission not in claims.get("permissions", []):
            raise HTTPException(status_code=403, detail="insufficient_scope")
        return claims  # sub, email, roles, permissions
    return dependency`,
          },
          {
            title: "main.py",
            language: "python",
            code: `from fastapi import Depends, FastAPI
from auth import require

app = FastAPI()

@app.get("/orders")
def orders(user: dict = Depends(require("orders:read"))):
    return []`,
          },
        ],
        notes: apiNotes(v),
      };

    default:
      return { snippets: [], notes: [] };
  }
}

function mobileNotes(v: SnippetValues, httpIssuer: boolean): string[] {
  const notes = [
    `Redirect registered for this client: ${v.redirectUri} (the app's own URL scheme ${v.appScheme}).`,
    "No secret: the app proves itself with PKCE; sign-in opens the system browser, never an embedded web view.",
  ];
  if (httpIssuer) {
    notes.push(`A phone cannot reach ${v.issuer}: use a URL it can reach (your machine's network address or an https tunnel) and register that as the issuer.`);
  }
  return notes;
}

function desktopNotes(v: SnippetValues): string[] {
  return [
    `Redirect registered for this client: ${v.redirectUri}. The app listens there only while signing in (RFC 8252 loopback redirect); if that port is taken on a user's machine, register another port on the client.`,
    "No secret: a desktop app cannot keep one. It proves itself with PKCE; sign-in opens the system browser, never an embedded web view.",
    "Keep the refresh token in the operating system's secure store (Windows Credential Manager, macOS Keychain, libsecret), not in a plain file.",
  ];
}

function serviceNotes(): string[] {
  return [
    "Keep SECUREONE_CLIENT_SECRET on the server only (environment or secret store).",
    "Tokens describe the service, not a user: the 'sub' claim is the client id.",
  ];
}

function deviceNotes(): string[] {
  return [
    "No secret and no redirect: the user approves on another device at the verification URL.",
    "Device sign-in returns an access token and a refresh token, but no ID token.",
  ];
}

function apiNotes(v: SnippetValues): string[] {
  return [
    `Tokens are signed by ${v.issuer}; keys are published at ${v.issuer}/oauth2/jwks and rotate without changes on your side.`,
    "The audience (aud) of an access token is the client id of the app that requested it; list the apps allowed to call this API.",
    "Each token carries the user's roles and permissions for the calling application ('roles', 'permissions').",
  ];
}
