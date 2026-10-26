import { normalizeHex } from "@/lib/theme/colors";
import { normalizeUiPreferences } from "@/lib/theme/prefs";
import {
  DEFAULT_CLIENT_APPEARANCE,
  DEFAULT_CLIENT_BRANDING,
  DEFAULT_CLIENT_WIDGET_COLORS,
  type ClientAppearance,
  type ClientBranding,
  type ClientWidgetColors,
} from "@/lib/theme/types";

const META_KEYS = new Set(["scope", "inheritsPlatformDefaults"]);

function coerceString(value: unknown, fallback: string): string {
  return typeof value === "string" ? value : fallback;
}

function normalizeWidgetColors(input: Partial<ClientWidgetColors> = {}): ClientWidgetColors {
  const d = DEFAULT_CLIENT_WIDGET_COLORS;
  const out: ClientWidgetColors = { ...d };
  (Object.keys(d) as (keyof ClientWidgetColors)[]).forEach((key) => {
    const raw = input[key];
    if (typeof raw === "string" && raw.trim()) {
      out[key] = key.includes("Overlay") ? raw.trim() : normalizeHex(raw, d[key]);
    }
  });
  return out;
}

function normalizeBranding(input: Partial<ClientBranding> = {}): ClientBranding {
  return {
    appName: coerceString(input.appName, DEFAULT_CLIENT_BRANDING.appName),
    logoUrl: coerceString(input.logoUrl, DEFAULT_CLIENT_BRANDING.logoUrl),
    language: coerceString(input.language, DEFAULT_CLIENT_BRANDING.language),
  };
}

export function normalizeClientAppearance(input: Partial<ClientAppearance> = {}): ClientAppearance {
  return {
    ...normalizeUiPreferences(input),
    ...normalizeWidgetColors(input),
    ...normalizeBranding(input),
  };
}

export function mergeClientAppearance(
  api: Partial<ClientAppearance>,
  fallback: Partial<ClientAppearance> = {},
): ClientAppearance {
  return normalizeClientAppearance({ ...fallback, ...api });
}

/** Payload persisted for application appearance (no admin metadata). */
export function toClientAppearancePayload(appearance: ClientAppearance): Record<string, unknown> {
  const normalized = normalizeClientAppearance(appearance);
  const out: Record<string, unknown> = { ...normalized };
  META_KEYS.forEach((k) => delete out[k]);
  return out;
}

export function stripAppearanceMeta<T extends Record<string, unknown>>(raw: T): Partial<ClientAppearance> {
  const out = { ...raw };
  META_KEYS.forEach((k) => delete out[k as keyof T]);
  return out as Partial<ClientAppearance>;
}
