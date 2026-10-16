/**
 * "Connect an app": the kinds of software that sign in with SecureOne, the frameworks we give
 * ready-to-paste setup for, and the OAuth client settings each kind should get (secure defaults).
 */

export type ConnectKind = "web" | "spa" | "native" | "service" | "device" | "api";

export type ClientTemplate = {
  /** OAuth client type stored by the admin API. */
  type: "web" | "spa" | "native" | "m2m";
  grantTypes: string[];
  scopes: string[];
  tokenEndpointAuthMethod: "client_secret_basic" | "none";
  pkceRequired: boolean;
  /** Whether the client has a secret (kept by the app's server, shown once). */
  confidential: boolean;
};

export type KindInfo = {
  id: ConnectKind;
  title: string;
  description: string;
  examples: string;
  /** No client for "api": an API only checks tokens other apps send it. */
  client: ClientTemplate | null;
  /** Users sign in through a browser redirect (needs redirect URIs). */
  redirects: boolean;
};

export const KINDS: KindInfo[] = [
  {
    id: "web",
    title: "Web app with a server",
    description: "Pages rendered or backed by your server, which keeps a client secret.",
    examples: "Next.js, Spring Boot, Express, ASP.NET Core",
    redirects: true,
    client: {
      type: "web",
      grantTypes: ["authorization_code", "refresh_token"],
      scopes: ["openid", "profile", "email"],
      tokenEndpointAuthMethod: "client_secret_basic",
      pkceRequired: true,
      confidential: true,
    },
  },
  {
    id: "spa",
    title: "Single-page app",
    description: "Runs entirely in the browser; no secret, sign-in uses PKCE.",
    examples: "React, Angular, Vue, plain JavaScript",
    redirects: true,
    client: {
      type: "spa",
      grantTypes: ["authorization_code", "refresh_token"],
      scopes: ["openid", "profile", "email"],
      tokenEndpointAuthMethod: "none",
      pkceRequired: true,
      confidential: false,
    },
  },
  {
    id: "native",
    title: "Mobile app",
    description: "iOS or Android app; sign-in opens the system browser and returns to the app.",
    examples: "React Native, Android, iOS, Flutter",
    redirects: true,
    client: {
      type: "native",
      grantTypes: ["authorization_code", "refresh_token"],
      scopes: ["openid", "profile", "email"],
      tokenEndpointAuthMethod: "none",
      pkceRequired: true,
      confidential: false,
    },
  },
  {
    id: "service",
    title: "Backend service",
    description: "A server calling your APIs as itself, with no user (machine-to-machine).",
    examples: "Batch jobs, integrations, internal services",
    redirects: false,
    client: {
      type: "m2m",
      grantTypes: ["client_credentials"],
      scopes: [],
      tokenEndpointAuthMethod: "client_secret_basic",
      pkceRequired: false,
      confidential: true,
    },
  },
  {
    id: "device",
    title: "TV or command-line app",
    description: "No keyboard or browser: the user approves on their phone or computer with a short code.",
    examples: "Smart TVs, consoles, CLIs",
    redirects: false,
    client: {
      type: "native",
      grantTypes: ["device_code", "refresh_token"],
      scopes: ["profile", "email"],
      tokenEndpointAuthMethod: "none",
      pkceRequired: false,
      confidential: false,
    },
  },
  {
    id: "api",
    title: "API that accepts SecureOne tokens",
    description: "Your API checks the access tokens your apps send. Nothing to register.",
    examples: "Spring Boot, Node.js, ASP.NET Core, Python",
    redirects: false,
    client: null,
  },
];

export type FrameworkInfo = {
  id: string;
  kind: ConnectKind;
  label: string;
  /** Default address the app runs at during development. */
  appUrl?: string;
  /** Path SecureOne redirects back to after sign-in (appended to appUrl). */
  callbackPath?: string;
  /** Path after sign-out. */
  logoutPath?: string;
};

export const FRAMEWORKS: FrameworkInfo[] = [
  { id: "nextjs", kind: "web", label: "Next.js (NextAuth.js)", appUrl: "http://localhost:3000", callbackPath: "/api/auth/callback/secureone", logoutPath: "/" },
  { id: "spring", kind: "web", label: "Spring Boot", appUrl: "http://localhost:8080", callbackPath: "/login/oauth2/code/secureone", logoutPath: "/" },
  { id: "express", kind: "web", label: "Node.js / Express", appUrl: "http://localhost:3000", callbackPath: "/callback", logoutPath: "/" },
  { id: "aspnet", kind: "web", label: "ASP.NET Core", appUrl: "https://localhost:5001", callbackPath: "/signin-oidc", logoutPath: "/signout-callback-oidc" },

  { id: "react", kind: "spa", label: "React", appUrl: "http://localhost:5173", callbackPath: "/", logoutPath: "/" },
  { id: "angular", kind: "spa", label: "Angular", appUrl: "http://localhost:4200", callbackPath: "/", logoutPath: "/" },
  { id: "javascript", kind: "spa", label: "Plain JavaScript", appUrl: "http://localhost:5173", callbackPath: "/callback.html", logoutPath: "/" },

  { id: "react-native", kind: "native", label: "React Native" },
  { id: "android", kind: "native", label: "Android (Kotlin)" },
  { id: "ios", kind: "native", label: "iOS (Swift)" },
  { id: "flutter", kind: "native", label: "Flutter" },

  { id: "curl", kind: "service", label: "curl" },
  { id: "node", kind: "service", label: "Node.js" },
  { id: "java", kind: "service", label: "Java (Spring)" },
  { id: "python", kind: "service", label: "Python" },

  { id: "shell", kind: "device", label: "curl / shell" },
  { id: "node-cli", kind: "device", label: "Node.js CLI" },

  { id: "spring-api", kind: "api", label: "Spring Boot" },
  { id: "node-api", kind: "api", label: "Node.js (Express)" },
  { id: "aspnet-api", kind: "api", label: "ASP.NET Core" },
  { id: "python-api", kind: "api", label: "Python" },
];

export function kindInfo(kind: ConnectKind): KindInfo {
  return KINDS.find((k) => k.id === kind)!;
}

export function frameworksFor(kind: ConnectKind): FrameworkInfo[] {
  return FRAMEWORKS.filter((f) => f.kind === kind);
}

/** Lowercase, dash-separated id usable as an OAuth client id or slug. */
export function slugify(value: string): string {
  return value
    .toLowerCase()
    .normalize("NFKD")
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 48);
}

/** Default redirect URI for a framework, joining the app URL and callback path. */
export function joinUrl(base: string, path: string | undefined): string {
  if (!path) return base;
  return `${base.replace(/\/+$/, "")}${path.startsWith("/") ? path : `/${path}`}`;
}

/** Reverse-domain app scheme for native redirects, e.g. com.example.shop. */
export function defaultAppScheme(appName: string): string {
  const part = slugify(appName).replace(/-/g, "") || "app";
  return `com.example.${part}`;
}

/** The sign-in test page on the auth-server (its URL is registered as an extra redirect URI). */
export function testRedirectUri(issuer: string): string {
  return `${issuer.replace(/\/+$/, "")}/account/connect-test.html`;
}
