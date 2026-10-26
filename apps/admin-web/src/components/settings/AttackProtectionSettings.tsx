"use client";

import { useCallback, useState } from "react";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { FieldRow, Input, Select } from "@/components/ui/Field";
import { Toggle } from "@/components/ui/Toggle";
import { useToast } from "@/components/ui/Toast";
import {
  loadAttackProtection,
  saveAttackProtection,
  type AttackProtection,
  type CaptchaProvider,
  type SignInChallenge,
} from "@/lib/api/attack-protection";

const PROVIDERS: { value: CaptchaProvider; label: string; keys: string }[] = [
  { value: "none", label: "Off", keys: "" },
  { value: "turnstile", label: "Cloudflare Turnstile", keys: "Cloudflare dashboard → Turnstile → your site" },
  { value: "hcaptcha", label: "hCaptcha", keys: "hCaptcha dashboard → Sites" },
  { value: "recaptcha", label: "Google reCAPTCHA v2 (checkbox)", keys: "Google reCAPTCHA admin console → v2 Checkbox" },
];

const ACTIONS: { key: "signUp" | "passwordReset" | "magicLink" | "oneTimeCodes"; label: string; hint: string }[] = [
  { key: "signUp", label: "Sign-up", hint: "Creating an account" },
  { key: "passwordReset", label: "Password reset", hint: "Asking for a reset link or code" },
  { key: "magicLink", label: "Magic link", hint: "Asking for a sign-in link" },
  { key: "oneTimeCodes", label: "One-time codes", hint: "Asking for a sign-in code by email or text" },
];

const lines = (list: string[]) => list.join("\n");
const parseLines = (text: string) =>
  text
    .split(/[\n,]/)
    .map((s) => s.trim())
    .filter(Boolean);

/** Platform settings › Attack protection: bot protection (CAPTCHA) and IP rules. */
export function AttackProtectionSettings() {
  const { toast } = useToast();
  const [config, setConfig] = useState<AttackProtection | null>(null);
  const [secret, setSecret] = useState("");
  const [hostnames, setHostnames] = useState("");
  const [blocked, setBlocked] = useState("");
  const [consoleAllowed, setConsoleAllowed] = useState("");
  const [saving, setSaving] = useState(false);

  const load = useCallback(async () => {
    try {
      const data = await loadAttackProtection();
      setConfig(data);
      setHostnames(lines(data.botProtection.allowedHostnames ?? []));
      setBlocked(lines(data.ipRules.blocked));
      setConsoleAllowed(lines(data.ipRules.consoleAllowed));
    } catch (e) {
      toast(e instanceof Error ? e.message : "Could not load attack protection", "error");
    }
  }, [toast]);

  useDeferredEffect(() => {
    load();
  }, [load]);

  if (!config) return <p className="text-sm text-muted">Loading…</p>;
  const bot = config.botProtection;
  const provider = PROVIDERS.find((p) => p.value === bot.provider) ?? PROVIDERS[0];
  const patchBot = (patch: Partial<AttackProtection["botProtection"]>) =>
    setConfig({ ...config, botProtection: { ...bot, ...patch } });

  async function save() {
    if (!config) return;
    const allowed = parseLines(consoleAllowed);
    if (
      allowed.length > 0 &&
      !window.confirm(
        "Only the listed addresses will reach the admin console. Make sure your own address is on the list, or you will lock yourself out. Save?",
      )
    ) {
      return;
    }
    setSaving(true);
    try {
      const saved = await saveAttackProtection({
        botProtection: {
          ...config.botProtection,
          secret: secret.trim() || undefined,
          allowedHostnames: parseLines(hostnames),
        },
        ipRules: { blocked: parseLines(blocked), consoleAllowed: allowed },
        newDeviceAlerts: config.newDeviceAlerts !== false,
      });
      setConfig(saved);
      setSecret("");
      setHostnames(lines(saved.botProtection.allowedHostnames ?? []));
      setBlocked(lines(saved.ipRules.blocked));
      setConsoleAllowed(lines(saved.ipRules.consoleAllowed));
      toast("Attack protection saved", "success");
    } catch (e) {
      toast(e instanceof Error ? e.message : "Save failed", "error");
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="space-y-6">
      <Card padded={false}>
        <CardHeader
          title="Bot protection"
          description="A CAPTCHA on the hosted pages bots abuse: sign-up, password reset, sign-in links and codes, and password sign-in."
        />
        <div className="grid gap-5 p-5 md:grid-cols-2">
          <FieldRow label="Provider">
            <Select value={bot.provider} onChange={(e) => patchBot({ provider: e.target.value as CaptchaProvider })}>
              {PROVIDERS.map((p) => (
                <option key={p.value} value={p.value}>
                  {p.label}
                </option>
              ))}
            </Select>
          </FieldRow>
          <div />
          {bot.provider !== "none" && (
            <>
              <FieldRow label="Site key" hint={provider.keys}>
                <Input value={bot.siteKey} onChange={(e) => patchBot({ siteKey: e.target.value })} />
              </FieldRow>
              <FieldRow label="Secret key" hint={bot.secretSet ? "Saved. Leave blank to keep it." : "Stored encrypted"}>
                <Input
                  type="password"
                  value={secret}
                  autoComplete="off"
                  placeholder={bot.secretSet ? "••••••••" : ""}
                  onChange={(e) => setSecret(e.target.value)}
                />
              </FieldRow>
            </>
          )}
        </div>
        {bot.provider !== "none" && (
          <>
            <ul className="divide-y divide-ui border-t border-ui">
              {ACTIONS.map((a) => (
                <li key={a.key} className="flex items-center justify-between gap-4 px-5 py-3">
                  <div>
                    <p className="text-sm font-medium text-ui">{a.label}</p>
                    <p className="text-xs text-muted">{a.hint}</p>
                  </div>
                  <Toggle checked={bot[a.key]} onChange={(v) => patchBot({ [a.key]: v })} aria-label={a.label} />
                </li>
              ))}
            </ul>
            <div className="grid gap-5 border-t border-ui p-5 md:grid-cols-2">
              <FieldRow label="Password sign-in" hint="After failures: only when one address keeps failing">
                <Select value={bot.signIn} onChange={(e) => patchBot({ signIn: e.target.value as SignInChallenge })}>
                  <option value="never">Never</option>
                  <option value="after_failures">After failed attempts</option>
                  <option value="always">Always</option>
                </Select>
              </FieldRow>
              {bot.signIn === "after_failures" && (
                <FieldRow label="Failed attempts first" hint="From one address within 15 minutes (1–20)">
                  <Input
                    type="number"
                    min={1}
                    max={20}
                    value={bot.signInFailures}
                    onChange={(e) => patchBot({ signInFailures: Number(e.target.value) })}
                  />
                </FieldRow>
              )}
              <FieldRow
                label="Allowed host names"
                hint="One per line, e.g. login.example.com. A challenge solved on any other site is refused. Empty: any."
              >
                <textarea
                  className="h-20 w-full rounded-lg border border-ui bg-ui-surface p-2 font-mono text-xs text-ui"
                  value={hostnames}
                  onChange={(e) => setHostnames(e.target.value)}
                  aria-label="Allowed CAPTCHA host names"
                />
              </FieldRow>
              <div className="flex items-start justify-between gap-4">
                <div>
                  <p className="text-sm font-medium text-ui">If the CAPTCHA provider is down</p>
                  <p className="text-xs text-muted">
                    Off (recommended): requests that need a CAPTCHA are refused until it is back. On: they are let
                    through without a check, so bots are not stopped during the outage.
                  </p>
                </div>
                <Toggle
                  checked={bot.failOpen === true}
                  onChange={(v) => patchBot({ failOpen: v })}
                  aria-label="Let requests through when the CAPTCHA provider is down"
                />
              </div>
            </div>
          </>
        )}
      </Card>

      <Card padded={false}>
        <CardHeader title="Sign-in alerts" />
        <div className="flex items-center justify-between gap-4 px-5 pb-5">
          <div>
            <p className="text-sm font-medium text-ui">Email users about sign-ins from a new device</p>
            <p className="text-xs text-muted">
              When an account signs in from a browser and system, or a network, it has not used in 180 days, its owner
              gets an email saying where from, so a stolen password is noticed.
            </p>
          </div>
          <Toggle
            checked={config.newDeviceAlerts !== false}
            onChange={(v) => setConfig({ ...config, newDeviceAlerts: v })}
            aria-label="New device alerts"
          />
        </div>
      </Card>

      <Card padded={false}>
        <CardHeader title="IP rules" description="Addresses or ranges, one per line: 203.0.113.7, 10.0.0.0/8, 2001:db8::/32." />
        <div className="grid gap-5 p-5 md:grid-cols-2">
          <FieldRow label="Blocked everywhere" hint="Refused on every page and API">
            <textarea
              className="h-32 w-full rounded-lg border border-ui bg-ui-surface p-2 font-mono text-xs text-ui"
              value={blocked}
              onChange={(e) => setBlocked(e.target.value)}
              aria-label="Blocked addresses"
            />
          </FieldRow>
          <FieldRow label="Admin console only from" hint="Empty: from anywhere. Sign-in pages are not affected.">
            <textarea
              className="h-32 w-full rounded-lg border border-ui bg-ui-surface p-2 font-mono text-xs text-ui"
              value={consoleAllowed}
              onChange={(e) => setConsoleAllowed(e.target.value)}
              aria-label="Console allowed addresses"
            />
          </FieldRow>
        </div>
      </Card>

      <div className="flex justify-end">
        <Button onClick={save} disabled={saving}>
          {saving ? "Saving…" : "Save attack protection"}
        </Button>
      </div>
    </div>
  );
}
