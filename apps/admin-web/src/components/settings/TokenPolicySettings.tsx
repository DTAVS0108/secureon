"use client";

import { useCallback, useId, useRef, useState } from "react";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";
import { Card, CardHeader } from "@/components/ui/Card";
import { FieldRow, Input } from "@/components/ui/Field";
import { Toggle } from "@/components/ui/Toggle";
import { useToast } from "@/components/ui/Toast";
import { ensureApplicationPolicyScope } from "@/lib/api/ensure-application-policy";
import {
  fetchApplicationTokenPolicy,
  saveApplicationTokenPolicy,
} from "@/lib/api/application-settings";
import {
  fetchPlatformTokenPolicy,
  savePlatformTokenPolicy,
  type TokenPolicy,
} from "@/lib/api/token-policy";
const DEFAULTS: TokenPolicy = {
  accessTokenTtlSeconds: 3600,
  refreshTokenTtlSeconds: 604_800,
  authorizationCodeTtlSeconds: 300,
  idTokenTtlSeconds: 3600,
  clientCredentialsTtlSeconds: 3600,
  deviceCodeTtlSeconds: 600,
  refreshTokensEnabled: true,
  reuseRefreshTokens: false,
  rotateRefreshTokens: true,
  refreshTokenReuseDetection: true,
};

function formatTtl(seconds: number): string {
  if (seconds >= 86_400 && seconds % 86_400 === 0) {
    const days = seconds / 86_400;
    return `${days} day${days === 1 ? "" : "s"}`;
  }
  if (seconds >= 3600 && seconds % 3600 === 0) {
    const hours = seconds / 3600;
    return `${hours} hour${hours === 1 ? "" : "s"}`;
  }
  if (seconds >= 60 && seconds % 60 === 0) {
    const mins = seconds / 60;
    return `${mins} min`;
  }
  return `${seconds}s`;
}

export function TokenPolicySettings({
  applicationId,
}: {
  /** Omit for platform-wide defaults (super admin only). */
  applicationId?: string;
}) {
  const { toast } = useToast();
  const [policy, setPolicy] = useState<TokenPolicy>(DEFAULTS);
  const [loaded, setLoaded] = useState(false);
  const saveTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const isAppScope = Boolean(applicationId);

  const reload = useCallback(async () => {
    setLoaded(false);
    try {
      if (applicationId) {
        await ensureApplicationPolicyScope(applicationId, "token-policy");
        const data = await fetchApplicationTokenPolicy(applicationId);
        setPolicy({ ...DEFAULTS, ...data });
      } else {
        const data = await fetchPlatformTokenPolicy();
        setPolicy({ ...DEFAULTS, ...data });
      }
    } catch (e) {
      toast(e instanceof Error ? e.message : "Failed to load token policy", "error");
    } finally {
      setLoaded(true);
    }
  }, [applicationId, toast]);

  useDeferredEffect(() => {
    reload();
  }, [reload]);

  const scheduleSave = (next: TokenPolicy) => {
    if (!loaded) return;
    if (saveTimer.current) clearTimeout(saveTimer.current);
    saveTimer.current = setTimeout(async () => {
      try {
        const { scope: _s, inheritsPlatformDefaults: _i, ...body } = next;
        const saved = applicationId
          ? await saveApplicationTokenPolicy(applicationId, body as TokenPolicy)
          : await savePlatformTokenPolicy(body as TokenPolicy);
        setPolicy({ ...DEFAULTS, ...saved });
        toast("Settings saved", "success");
      } catch (e) {
        toast(e instanceof Error ? e.message : "Save failed", "error");
      }
    }, 700);
  };

  const patch = (partial: Partial<TokenPolicy>) => {
    const next = { ...policy, ...partial };
    if (partial.reuseRefreshTokens !== undefined) {
      next.rotateRefreshTokens = !partial.reuseRefreshTokens;
    }
    if (partial.rotateRefreshTokens !== undefined) {
      next.reuseRefreshTokens = !partial.rotateRefreshTokens;
    }
    setPolicy(next);
    scheduleSave(next);
  };

  const patchSeconds = (key: keyof TokenPolicy, raw: string) => {
    const n = Math.max(0, Number(raw) || 0);
    patch({ [key]: n } as Partial<TokenPolicy>);
  };

  return (
    <div className={!loaded ? "pointer-events-none opacity-60" : ""}>
      {!isAppScope && (
        <p className="mb-4 text-sm text-muted">
          Platform defaults for OAuth2 access tokens, refresh tokens, and authorization codes. Enable{" "}
          <strong>For applications → OAuth tokens</strong> to let each app override these values.
        </p>
      )}

      <div className="space-y-6">
        <Card padded={false}>
          <CardHeader
            title="Access & ID tokens"
            description="Short-lived tokens returned to clients after sign-in."
          />
          <div className="grid grid-cols-1 gap-5 p-5 md:grid-cols-2">
            <TtlField
              label="Access token lifetime"
              hint={`Currently ${formatTtl(policy.accessTokenTtlSeconds)}`}
              value={policy.accessTokenTtlSeconds}
              onChange={(v) => patchSeconds("accessTokenTtlSeconds", v)}
            />
            <TtlField
              label="ID token lifetime"
              hint={`OIDC id_token · ${formatTtl(policy.idTokenTtlSeconds)}`}
              value={policy.idTokenTtlSeconds}
              onChange={(v) => patchSeconds("idTokenTtlSeconds", v)}
            />
            <TtlField
              label="Client credentials token"
              hint={`M2M grant · ${formatTtl(policy.clientCredentialsTtlSeconds)}`}
              value={policy.clientCredentialsTtlSeconds}
              onChange={(v) => patchSeconds("clientCredentialsTtlSeconds", v)}
            />
          </div>
        </Card>

        <Card padded={false}>
          <CardHeader
            title="Refresh tokens"
            description="Long-lived sessions; rotation reduces replay risk."
          />
          <ul className="divide-y divide-ui">
            <li className="flex items-center justify-between gap-4 px-5 py-4">
              <div>
                <p className="text-sm font-medium text-ui">Allow refresh tokens</p>
                <p className="text-xs text-muted">Requires refresh_token grant on the OAuth client</p>
              </div>
              <Toggle
                checked={policy.refreshTokensEnabled}
                onChange={(v) => patch({ refreshTokensEnabled: v })}
                aria-label="Allow refresh tokens"
              />
            </li>
            <li className="flex items-center justify-between gap-4 px-5 py-4">
              <div>
                <p className="text-sm font-medium text-ui">Rotate on each use</p>
                <p className="text-xs text-muted">Issue a new refresh token when the old one is exchanged</p>
              </div>
              <Toggle
                checked={policy.rotateRefreshTokens}
                disabled={!policy.refreshTokensEnabled}
                onChange={(v) => patch({ rotateRefreshTokens: v, reuseRefreshTokens: !v })}
                aria-label="Rotate refresh tokens"
              />
            </li>
            <li className="flex items-center justify-between gap-4 px-5 py-4">
              <div>
                <p className="text-sm font-medium text-ui">Reuse detection</p>
                <p className="text-xs text-muted">Revoke token family if a rotated refresh token is reused</p>
              </div>
              <Toggle
                checked={policy.refreshTokenReuseDetection}
                disabled={!policy.refreshTokensEnabled}
                onChange={(v) => patch({ refreshTokenReuseDetection: v })}
                aria-label="Refresh token reuse detection"
              />
            </li>
          </ul>
          <div className="grid grid-cols-1 gap-5 border-t border-ui p-5 md:grid-cols-2">
            <TtlField
              label="Refresh token lifetime"
              hint={
                policy.refreshTokensEnabled
                  ? formatTtl(policy.refreshTokenTtlSeconds)
                  : "Set to 0 to disable when refresh tokens are off"
              }
              value={policy.refreshTokenTtlSeconds}
              disabled={!policy.refreshTokensEnabled}
              onChange={(v) => patchSeconds("refreshTokenTtlSeconds", v)}
            />
          </div>
        </Card>

        <Card padded={false}>
          <CardHeader title="Authorization flow" description="Short-lived codes and device login." />
          <div className="grid grid-cols-1 gap-5 p-5 md:grid-cols-2">
            <TtlField
              label="Authorization code lifetime"
              hint={formatTtl(policy.authorizationCodeTtlSeconds)}
              value={policy.authorizationCodeTtlSeconds}
              onChange={(v) => patchSeconds("authorizationCodeTtlSeconds", v)}
            />
            <TtlField
              label="Device code lifetime"
              hint={formatTtl(policy.deviceCodeTtlSeconds)}
              value={policy.deviceCodeTtlSeconds}
              onChange={(v) => patchSeconds("deviceCodeTtlSeconds", v)}
            />
          </div>
        </Card>
      </div>
    </div>
  );
}

function TtlField({
  label,
  hint,
  value,
  disabled,
  onChange,
}: {
  label: string;
  hint: string;
  value: number;
  disabled?: boolean;
  onChange: (value: string) => void;
}) {
  const id = useId();
  return (
    <FieldRow label={label} hint={hint} htmlFor={id}>
      <div className="flex items-center gap-2">
        <Input
          id={id}
          type="number"
          min={0}
          value={String(value)}
          disabled={disabled}
          onChange={(e) => onChange(e.target.value)}
          className="tabular-nums"
        />
        <span className="shrink-0 text-xs text-muted">seconds</span>
      </div>
    </FieldRow>
  );
}
