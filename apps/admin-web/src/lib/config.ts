/**
 * Runtime configuration for the admin console.
 *
 * Everything here is read when the server starts or renders, never at build time, so one built
 * image serves every installation. The variable names are the auth-server's own, so a single
 * container can pass the same environment to both processes:
 *
 * - SECUREONE_PUBLIC_BASE_URL (or SECUREONE_ISSUER_URL): the URL users and apps see, e.g.
 *   https://id.example.com. Shown in the console and used for links to hosted pages.
 * - SECUREONE_AUTH_SERVER_INTERNAL_URL: where this console's server reaches the auth-server API.
 *   Defaults to the public URL; inside the single image it is http://127.0.0.1:9000.
 * - SECUREONE_ENV: development | staging | production.
 *
 * Browser code receives the public values from the root layout (window.__SECUREONE_CONFIG__).
 */

/** The console is served under this path (next.config.ts basePath; fixed at build, same everywhere). */
export const BASE_PATH = "/admin";

export type PublicConfig = {
  publicUrl: string;
  environment: string;
};

declare global {
  interface Window {
    __SECUREONE_CONFIG__?: PublicConfig;
  }
}

const DEFAULT_URL = "http://localhost:9000";

function trimSlash(url: string): string {
  return url.trim().replace(/\/+$/, "");
}

/** Public settings, read from the environment on the server. Rendered into the page by the root layout. */
export function serverPublicConfig(): PublicConfig {
  return {
    publicUrl: trimSlash(process.env.SECUREONE_PUBLIC_BASE_URL || process.env.SECUREONE_ISSUER_URL || DEFAULT_URL),
    environment: (process.env.SECUREONE_ENV || "development").trim().toLowerCase(),
  };
}

/** Browser side: records the settings the server rendered (RuntimeConfigProvider). Same value every time. */
export function installPublicConfig(config: PublicConfig): void {
  if (typeof window !== "undefined") {
    window.__SECUREONE_CONFIG__ = config;
  }
}

export function publicConfig(): PublicConfig {
  if (typeof window !== "undefined" && window.__SECUREONE_CONFIG__) {
    return window.__SECUREONE_CONFIG__;
  }
  return serverPublicConfig();
}

/** The auth-server URL users and integrating apps see (issuer, discovery, hosted pages). */
export function publicAuthServerUrl(): string {
  return publicConfig().publicUrl;
}

/** Server-side only: base URL for this console's calls to the auth-server API. */
export function authServerInternalUrl(): string {
  const internal = process.env.SECUREONE_AUTH_SERVER_INTERNAL_URL;
  return internal ? trimSlash(internal) : serverPublicConfig().publicUrl;
}

export function environment(): string {
  return publicConfig().environment;
}

export function isProd(): boolean {
  return environment() === "production";
}

/** Prefixes a console path with the base path, for code that bypasses next/link and next/navigation. */
export function withBasePath(path: string): string {
  return `${BASE_PATH}${path.startsWith("/") ? path : `/${path}`}`;
}

/** Strips the base path from a browser pathname so it can be handed to the Next.js router again. */
export function stripBasePath(pathname: string): string {
  if (pathname === BASE_PATH) return "/";
  return pathname.startsWith(`${BASE_PATH}/`) ? pathname.slice(BASE_PATH.length) : pathname;
}

export const APP_NAME = "SecureOne";
export const APP_TAGLINE = "Identity & Access Management";
