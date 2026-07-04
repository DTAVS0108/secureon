/**
 * Runtime configuration for the admin web app.
 *
 * The authorization server base URL is read from the environment so the same
 * build can target local dev, staging, and production. Defaults to the local
 * auth-server on port 9000 (see apps/auth-server).
 */
export const AUTH_SERVER_URL =
  process.env.NEXT_PUBLIC_AUTH_SERVER_URL?.replace(/\/$/, "") ??
  "http://localhost:9000";

export const ENVIRONMENT =
  process.env.NEXT_PUBLIC_SECUREONE_ENV?.trim().toLowerCase() ?? "development";

export const IS_DEV = ENVIRONMENT === "development";
export const IS_STAGING = ENVIRONMENT === "staging";
export const IS_PROD = ENVIRONMENT === "production";

export const APP_NAME = "SecureOne";
export const APP_TAGLINE = "Identity & Access Management";
