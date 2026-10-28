"use client";

import { useCallback, useState } from "react";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { CopyValue } from "@/components/ui/CopyValue";
import { useDialog } from "@/components/ui/Dialogs";
import { FieldRow, Input, Select, Textarea } from "@/components/ui/Field";
import { useToast } from "@/components/ui/Toast";
import {
  createInitialAccessToken,
  fetchClientRegistration,
  fetchInitialAccessTokens,
  revokeInitialAccessToken,
  saveClientRegistration,
  type ClientRegistration,
  type InitialAccessToken,
  type RegistrationMode,
} from "@/lib/api/client-registration";

const GRANTS = [
  { value: "authorization_code", label: "Authorization code (users sign in)" },
  { value: "refresh_token", label: "Refresh tokens" },
  { value: "client_credentials", label: "Client credentials (machine tokens; not with open)" },
];

const lines = (text: string) =>
  text
    .split(/[\n,\s]+/)
    .map((s) => s.trim())
    .filter(Boolean);

/**
 * Integration › Client registration: apps (partner tools, AI assistants) register themselves as OAuth clients
 * (RFC 7591). Off by default; with an initial access token handed out here, or open.
 */
export function ClientRegistrationCard({ applicationId }: { applicationId: string }) {
  const { toast } = useToast();
  const dialog = useDialog();
  const [data, setData] = useState<ClientRegistration | null>(null);
  const [tokens, setTokens] = useState<InitialAccessToken[]>([]);
  const [mode, setMode] = useState<RegistrationMode>("off");
  const [scopes, setScopes] = useState("");
  const [grants, setGrants] = useState<string[]>([]);
  const [hosts, setHosts] = useState("");
  const [maxClients, setMaxClients] = useState("100");
  const [unusedDays, setUnusedDays] = useState("30");
  const [busy, setBusy] = useState(false);
  const [newToken, setNewToken] = useState<string | null>(null);
  const [tokenLabel, setTokenLabel] = useState("");
  const [tokenUses, setTokenUses] = useState("1");
  const [tokenHours, setTokenHours] = useState("24");

  const load = useCallback(async () => {
    try {
      const [settings, list] = await Promise.all([
        fetchClientRegistration(applicationId),
        fetchInitialAccessTokens(applicationId),
      ]);
      setData(settings);
      setTokens(list);
      setMode(settings.mode);
      setScopes(settings.allowedScopes.join(" "));
      setGrants(settings.allowedGrantTypes);
      setHosts(settings.trustedHosts.join("\n"));
      setMaxClients(String(settings.maxClients));
      setUnusedDays(String(settings.unusedDays));
    } catch (e) {
      toast(e instanceof Error ? e.message : "Could not load client registration", "error");
    }
  }, [applicationId, toast]);

  useDeferredEffect(() => {
    void load();
  }, [load]);

  async function save() {
    if (
      mode === "open" &&
      data?.mode !== "open" &&
      !(await dialog.confirm({
        title: "Allow anyone to register a client?",
        message:
          "Any app can then register itself without a token (rate limited, within these policies). Users still see the consent screen before an app gets access.",
        confirmLabel: "Allow open registration",
      }))
    ) {
      return;
    }
    setBusy(true);
    try {
      await saveClientRegistration(applicationId, {
        mode,
        allowedScopes: lines(scopes),
        allowedGrantTypes: grants,
        trustedHosts: lines(hosts),
        maxClients: Number(maxClients),
        unusedDays: Number(unusedDays),
      });
      toast("Client registration saved", "success");
      await load();
    } catch (e) {
      toast(e instanceof Error ? e.message : "Save failed", "error");
    } finally {
      setBusy(false);
    }
  }

  async function createToken() {
    setBusy(true);
    try {
      const created = await createInitialAccessToken(applicationId, {
        label: tokenLabel.trim() || undefined,
        maxUses: Number(tokenUses),
        expiresInHours: Number(tokenHours),
      });
      setNewToken(created.value);
      setTokenLabel("");
      await load();
    } catch (e) {
      toast(e instanceof Error ? e.message : "Could not create the token", "error");
    } finally {
      setBusy(false);
    }
  }

  async function revoke(token: InitialAccessToken) {
    if (
      !(await dialog.confirm({
        title: "Revoke this token?",
        message: "It can no longer register clients. Clients it already registered keep working.",
        confirmLabel: "Revoke token",
      }))
    )
      return;
    try {
      await revokeInitialAccessToken(applicationId, token.id);
      toast("Token revoked", "success");
      await load();
    } catch (e) {
      toast(e instanceof Error ? e.message : "Revoke failed", "error");
    }
  }

  if (!data) return null;

  return (
    <Card padded={false} className="mt-6">
      <CardHeader
        title="Client registration"
        description="Apps register themselves as OAuth clients of this application (RFC 7591), such as partner tools or AI assistants. Registered clients always show the consent screen and can manage their own registration (RFC 7592)."
      />
      <div className="space-y-4 px-5 pb-5">
        <div className="grid gap-3 md:grid-cols-2">
          <FieldRow label="Who may register">
            <Select value={mode} onChange={(e) => setMode(e.target.value as RegistrationMode)}>
              <option value="off">Nobody (off)</option>
              <option value="token">Apps with an initial access token</option>
              <option value="open">Anyone (open, rate limited)</option>
            </Select>
          </FieldRow>
          <div className="space-y-1 text-xs text-muted">
            <p>
              Registered clients: <strong className="text-ui">{data.registeredClients}</strong> of {data.maxClients}
            </p>
            <p className="flex flex-wrap items-center gap-1">
              Endpoint: <code className="break-all">{mode === "open" ? data.openRegistrationEndpoint : data.registrationEndpoint}</code>
              <CopyValue value={mode === "open" ? data.openRegistrationEndpoint : data.registrationEndpoint} />
            </p>
          </div>
        </div>
        {mode !== "off" && (
          <>
            <div className="grid gap-3 md:grid-cols-2">
              <FieldRow label="Allowed scopes" hint="A registered client may ask only for these (space separated).">
                <Input value={scopes} onChange={(e) => setScopes(e.target.value)} placeholder="Scopes, space-separated" />
              </FieldRow>
              <FieldRow label="Trusted hosts" hint="Redirect URIs only on these hosts (one per line, *.example.com allowed). Empty: any https host.">
                <Textarea rows={3} value={hosts} onChange={(e) => setHosts(e.target.value)} placeholder="Hosts, one per line" />
              </FieldRow>
            </div>
            <div className="flex flex-wrap gap-4 text-sm">
              {GRANTS.map((g) => (
                <label key={g.value} className="flex items-center gap-2">
                  <input
                    type="checkbox"
                    className="rounded border-ui"
                    checked={grants.includes(g.value)}
                    disabled={g.value === "client_credentials" && mode === "open"}
                    onChange={(e) => setGrants(e.target.checked ? [...grants, g.value] : grants.filter((x) => x !== g.value))}
                  />
                  {g.label}
                </label>
              ))}
            </div>
            <div className="grid gap-3 md:grid-cols-2">
              <FieldRow label="Most registered clients">
                <Input type="number" min={1} max={10000} value={maxClients} onChange={(e) => setMaxClients(e.target.value)} />
              </FieldRow>
              <FieldRow label="Remove never-used clients after (days)" hint="0: keep them.">
                <Input type="number" min={0} max={3650} value={unusedDays} onChange={(e) => setUnusedDays(e.target.value)} />
              </FieldRow>
            </div>
          </>
        )}
        <div className="flex justify-end">
          <Button onClick={() => void save()} disabled={busy}>
            {busy ? "Saving…" : "Save client registration"}
          </Button>
        </div>

        {data.mode !== "off" && (
          <div className="space-y-3 rounded-xl border border-ui p-3">
            <p className="text-sm font-medium text-ui">Initial access tokens</p>
            <p className="text-xs text-muted">
              Give one to a partner: their app sends it as <code>Authorization: Bearer …</code> when it registers.
            </p>
            {newToken && (
              <div className="space-y-2 rounded-lg border border-amber-500/40 bg-amber-500/10 p-3">
                <p className="text-sm text-ui">New token. Copy it now: it is not shown again.</p>
                <code className="block break-all font-mono text-xs">{newToken}</code>
                <div className="flex gap-2">
                  <CopyValue value={newToken} />
                  <Button variant="secondary" size="sm" onClick={() => setNewToken(null)}>
                    Done
                  </Button>
                </div>
              </div>
            )}
            <div className="grid gap-3 md:grid-cols-4">
              <FieldRow label="Label">
                <Input value={tokenLabel} onChange={(e) => setTokenLabel(e.target.value)} placeholder="Label" />
              </FieldRow>
              <FieldRow label="Registrations">
                <Input type="number" min={1} max={1000} value={tokenUses} onChange={(e) => setTokenUses(e.target.value)} />
              </FieldRow>
              <FieldRow label="Valid for (hours)">
                <Input type="number" min={1} max={8760} value={tokenHours} onChange={(e) => setTokenHours(e.target.value)} />
              </FieldRow>
              <Button className="self-end" variant="secondary" disabled={busy} onClick={() => void createToken()}>
                Create token
              </Button>
            </div>
            {tokens.length > 0 && (
              <ul className="divide-y divide-ui rounded-lg border border-ui text-sm">
                {tokens.map((t) => {
                  const usable = !t.revokedAt && t.uses < t.maxUses && new Date(t.expiresAt) > new Date();
                  return (
                    <li key={t.id} className="flex flex-wrap items-center justify-between gap-2 px-3 py-2">
                      <span>
                        {t.label ?? "Token"} · {t.uses}/{t.maxUses} used · until {new Date(t.expiresAt).toLocaleString()}
                      </span>
                      <span className="flex items-center gap-2">
                        <Badge tone={usable ? "success" : "neutral"}>{t.revokedAt ? "revoked" : usable ? "usable" : "used up or expired"}</Badge>
                        {!t.revokedAt && (
                          <Button variant="ghost" size="sm" onClick={() => void revoke(t)}>
                            Revoke
                          </Button>
                        )}
                      </span>
                    </li>
                  );
                })}
              </ul>
            )}
          </div>
        )}
      </div>
    </Card>
  );
}
