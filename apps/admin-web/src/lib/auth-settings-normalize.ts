import type { FeatureFlag, PasswordPolicy } from "@/lib/types";

const DEFAULT_PASSWORD_POLICY: PasswordPolicy = {
  minLength: 12,
  requireUppercase: true,
  requireNumber: true,
  requireSymbol: true,
  expiryDays: 0,
  historyCount: 5,
  hashAlgorithm: "bcrypt",
  breachCheck: false,
  maxFailedAttempts: 5,
  lockoutDurationMinutes: 15,
};

function coerceBool(value: unknown, fallback: boolean): boolean {
  if (typeof value === "boolean") return value;
  return fallback;
}

function coerceInt(value: unknown, fallback: number): number {
  const n = typeof value === "number" ? value : typeof value === "string" ? Number(value) : NaN;
  return Number.isFinite(n) ? Math.round(n) : fallback;
}

export function normalizePasswordPolicy(raw: unknown): PasswordPolicy {
  const r = (raw && typeof raw === "object" ? raw : {}) as Record<string, unknown>;
  return {
    minLength: coerceInt(r.minLength, DEFAULT_PASSWORD_POLICY.minLength),
    requireUppercase: coerceBool(r.requireUppercase, DEFAULT_PASSWORD_POLICY.requireUppercase),
    requireNumber: coerceBool(r.requireNumber, DEFAULT_PASSWORD_POLICY.requireNumber),
    requireSymbol: coerceBool(r.requireSymbol, DEFAULT_PASSWORD_POLICY.requireSymbol),
    expiryDays: coerceInt(r.expiryDays, DEFAULT_PASSWORD_POLICY.expiryDays),
    historyCount: coerceInt(r.historyCount, DEFAULT_PASSWORD_POLICY.historyCount),
    hashAlgorithm:
      typeof r.hashAlgorithm === "string" && r.hashAlgorithm.trim()
        ? r.hashAlgorithm
        : DEFAULT_PASSWORD_POLICY.hashAlgorithm,
    // Every field the server stores must survive load and save, or saving one setting resets these.
    breachCheck: coerceBool(r.breachCheck, DEFAULT_PASSWORD_POLICY.breachCheck ?? false),
    maxFailedAttempts: coerceInt(r.maxFailedAttempts, DEFAULT_PASSWORD_POLICY.maxFailedAttempts ?? 5),
    lockoutDurationMinutes: coerceInt(
      r.lockoutDurationMinutes,
      DEFAULT_PASSWORD_POLICY.lockoutDurationMinutes ?? 15,
    ),
    inheritsPlatformDefaults:
      typeof r.inheritsPlatformDefaults === "boolean" ? r.inheritsPlatformDefaults : undefined,
  };
}

export function passwordPolicyPayload(policy: PasswordPolicy): PasswordPolicy {
  const { inheritsPlatformDefaults: _i, requirements: _r, ...payload } = policy;
  return normalizePasswordPolicy(payload);
}

export function normalizeFeatureFlags(raw: unknown): FeatureFlag[] {
  if (!Array.isArray(raw)) return [];
  const out: FeatureFlag[] = [];
  for (const item of raw) {
    if (!item || typeof item !== "object") continue;
    const row = item as Record<string, unknown>;
    const key = typeof row.key === "string" ? row.key.trim() : "";
    if (!key) continue;
    out.push({
      key,
      name: typeof row.name === "string" ? row.name : key,
      description: typeof row.description === "string" ? row.description : "",
      enabled: coerceBool(row.enabled, false),
      category:
        row.category === "oauth" ||
        row.category === "identity" ||
        row.category === "notifications" ||
        row.category === "provisioning"
          ? row.category
          : typeof row.category === "string"
            ? row.category
            : undefined,
      platformEnabled:
        row.platformEnabled === undefined ? undefined : coerceBool(row.platformEnabled, false),
      scope: row.scope === "application" || row.scope === "platform" ? row.scope : undefined,
    });
  }
  return out;
}

export function featureFlagsPayload(flags: FeatureFlag[]): FeatureFlag[] {
  return normalizeFeatureFlags(flags).map(({ platformEnabled: _ignored, ...flag }) => flag);
}
