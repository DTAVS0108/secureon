"use client";

import { useRef, useState, useTransition } from "react";
import { Badge, type BadgeTone } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { CopyValue } from "@/components/ui/CopyValue";
import { Select, Textarea } from "@/components/ui/Field";
import {
  exportConfigAction,
  importConfigAction,
  type ConfigChange,
  type ConfigResult,
} from "@/lib/actions/config";
import { PLATFORM_EXPORT } from "@/lib/config-export";
import type { Tenant } from "@/lib/types";

const ACTION_TONE: Record<ConfigChange["action"], BadgeTone> = { create: "success", update: "info", delete: "danger" };

const EXAMPLE = `apiVersion: secureone/v1
kind: TenantConfiguration
tenant:
  slug: acme
  name: Acme Corp
applications:
  - slug: shop
    name: Shop
    clients:
      - clientId: shop-web
        type: spa
        redirectUris: [http://localhost:5173/]
    permissions:
      - key: orders:read
    roles:
      - name: viewer
        permissions: [orders:read]
`;

export function ConfigWorkspace({ tenants }: { tenants: Tenant[] }) {
  const [tenantSlug, setTenantSlug] = useState(tenants.find((t) => !t.system)?.slug ?? tenants[0]?.slug ?? "");
  const [text, setText] = useState("");
  const [prune, setPrune] = useState(false);
  const [result, setResult] = useState<ConfigResult | null>(null);
  // Apply only what was previewed: the exact text and options of the last clean dry run.
  const [previewed, setPreviewed] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [pending, startTransition] = useTransition();
  const fileInput = useRef<HTMLInputElement>(null);

  const previewKey = `${prune}\n${text}`;
  const canApply = previewed === previewKey && !!result && result.dryRun && result.errors.length === 0 && result.changes.length > 0;

  function edit(next: string) {
    setText(next);
    setResult(null);
    setError(null);
  }

  function download() {
    setError(null);
    startTransition(async () => {
      const res = await exportConfigAction(tenantSlug);
      if (!res.ok) return setError(res.error);
      const url = URL.createObjectURL(new Blob([res.yaml], { type: "application/yaml" }));
      const a = document.createElement("a");
      a.href = url;
      a.download = `secureone-${tenantSlug === PLATFORM_EXPORT ? "platform" : tenantSlug}.yaml`;
      a.click();
      URL.revokeObjectURL(url);
    });
  }

  function loadIntoEditor() {
    setError(null);
    startTransition(async () => {
      const res = await exportConfigAction(tenantSlug);
      if (!res.ok) return setError(res.error);
      edit(res.yaml);
    });
  }

  async function readFile(file: File) {
    edit(await file.text());
  }

  function run(apply: boolean) {
    setError(null);
    startTransition(async () => {
      const res = await importConfigAction(text, { apply, prune });
      if (!res.ok) {
        setResult(null);
        setPreviewed(null);
        return setError(res.error);
      }
      setResult(res.result);
      setPreviewed(apply ? null : previewKey);
    });
  }

  return (
    <div className="grid gap-6">
      <Card padded={false}>
        <CardHeader
          title="Export"
          description="A tenant: applications, OAuth clients, permissions, roles, groups, settings, sign-in providers, webhooks, service accounts and SCIM. The platform: default SMTP, SMS and SCIM. Users and credentials are never included; files name environment variables for credentials."
        />
        <div className="flex flex-wrap items-end gap-3 p-5">
          <div className="min-w-56">
            <label htmlFor="config-tenant" className="mb-1.5 block text-sm font-medium">Tenant or platform</label>
            <Select id="config-tenant" value={tenantSlug} onChange={(e) => setTenantSlug(e.target.value)}>
              {tenants.map((t) => (
                <option key={t.id} value={t.slug}>
                  {t.name} ({t.slug})
                </option>
              ))}
              <option value={PLATFORM_EXPORT}>Platform: SMTP, SMS, SCIM</option>
            </Select>
          </div>
          <Button type="button" onClick={download} disabled={pending || !tenantSlug} data-testid="config-download">
            Download YAML
          </Button>
          <Button type="button" variant="secondary" onClick={loadIntoEditor} disabled={pending || !tenantSlug} data-testid="config-load">
            Open in the editor below
          </Button>
        </div>
      </Card>

      <Card padded={false}>
        <CardHeader
          title="Preview and apply a file"
          description="Paste or upload a configuration file, preview what would change, then apply it. Applying again changes nothing: what is already in place shows as unchanged."
        />
        <div className="grid gap-4 p-5">
          <div className="flex flex-wrap items-center gap-3">
            <input
              ref={fileInput}
              type="file"
              accept=".yaml,.yml,.json,application/yaml,application/json"
              className="hidden"
              onChange={(e) => {
                const file = e.target.files?.[0];
                if (file) void readFile(file);
                e.target.value = "";
              }}
            />
            <Button type="button" variant="secondary" size="sm" onClick={() => fileInput.current?.click()}>
              Upload file
            </Button>
            {!text && (
              <Button type="button" variant="ghost" size="sm" onClick={() => edit(EXAMPLE)}>
                Start from an example
              </Button>
            )}
          </div>
          <Textarea
            aria-label="Configuration file"
            data-testid="config-text"
            value={text}
            onChange={(e) => edit(e.target.value)}
            spellCheck={false}
            placeholder="apiVersion: secureone/v1&#10;kind: TenantConfiguration&#10;tenant:&#10;  slug: …"
            className="min-h-80 font-mono text-xs leading-5"
          />
          <label className="flex items-start gap-2 text-sm">
            <input
              type="checkbox"
              className="mt-0.5"
              checked={prune}
              data-testid="config-prune"
              onChange={(e) => {
                setPrune(e.target.checked);
                setResult(null);
              }}
            />
            <span>
              Delete what the file does not list
              <span className="block text-xs text-muted">
                Only inside the lists the file contains (clients, permissions, roles, groups, settings). Applications, users and
                built-in roles are never deleted.
              </span>
            </span>
          </label>
          <div className="flex flex-wrap gap-3">
            <Button type="button" onClick={() => run(false)} disabled={pending || !text.trim()} data-testid="config-preview">
              {pending ? "Working…" : "Preview changes"}
            </Button>
            <Button
              type="button"
              variant={prune && result?.changes.some((c) => c.action === "delete") ? "danger" : "primary"}
              onClick={() => run(true)}
              disabled={pending || !canApply}
              data-testid="config-apply"
              title={canApply ? undefined : "Preview the file first; apply is available when the preview shows changes and no problems."}
            >
              Apply {result?.dryRun && result.changes.length > 0 ? `${result.changes.length} change(s)` : ""}
            </Button>
          </div>
          {error && (
            <p role="alert" className="rounded-[var(--ui-radius)] bg-red-500/10 px-3 py-2 text-sm text-red-700 dark:text-red-300" data-testid="config-error">
              {error}
            </p>
          )}
          {result && <ResultView result={result} />}
        </div>
      </Card>

      <p className="text-xs text-muted">
        To apply files automatically, mount them in the server and set <code>SECUREONE_CONFIG_IMPORT</code> to the file or
        folder; every start-up applies them (see “Configuration as code” in the documentation).
      </p>
    </div>
  );
}

function ResultView({ result }: { result: ConfigResult }) {
  const headline = result.errors.length > 0
    ? result.dryRun
      ? "The file has problems; nothing was changed."
      : "Applying stopped."
    : result.dryRun
      ? result.changes.length === 0
        ? "Already up to date: applying this file would change nothing."
        : `Applying would make ${result.changes.length} change(s):`
      : result.changes.length === 0
        ? "Nothing to change."
        : `Applied ${result.changes.length} change(s).`;
  return (
    <div className="grid gap-3" data-testid="config-result">
      <p className="text-sm font-medium" data-testid="config-headline">{headline}</p>
      {result.errors.length > 0 && (
        <ul className="list-disc space-y-1 rounded-[var(--ui-radius)] bg-red-500/10 py-2 pl-8 pr-3 text-sm text-red-700 dark:text-red-300" data-testid="config-errors">
          {result.errors.map((e) => <li key={e}>{e}</li>)}
        </ul>
      )}
      {result.secrets.length > 0 && (
        <div className="rounded-[var(--ui-radius)] border border-amber-500/40 bg-amber-500/10 p-3 text-sm" data-testid="config-secrets">
          <p className="font-medium">
            Generated credentials, shown only now. Store them in a secret store; to keep them in your configuration,
            put each in an environment variable and name it in the file.
          </p>
          <ul className="mt-2 grid gap-1">
            {result.secrets.map((s) => (
              <li key={`${s.kind}:${s.name}`} className="flex flex-wrap items-center gap-2">
                <Badge>{s.kind}</Badge>
                <span className="font-mono text-xs">{s.name}</span>
                <code className="break-all rounded bg-ui-elevated px-1.5 py-0.5 text-xs">{s.secret}</code>
                <CopyValue value={s.secret} />
              </li>
            ))}
          </ul>
        </div>
      )}
      {result.changes.length > 0 && (
        <ul className="divide-y divide-[var(--ui-border)] rounded-[var(--ui-radius)] border border-ui text-sm" data-testid="config-changes">
          {result.changes.map((c, i) => (
            <li key={`${c.kind}:${c.target}:${i}`} className="flex flex-wrap items-start gap-2 px-3 py-2">
              <Badge tone={ACTION_TONE[c.action] ?? "neutral"}>{c.action}</Badge>
              <span className="text-muted">{c.kind}</span>
              <span className="font-mono text-xs">{c.target}</span>
              {c.details.length > 0 && (
                <ul className="basis-full pl-2 text-xs text-muted">
                  {c.details.map((d) => <li key={d}>{d}</li>)}
                </ul>
              )}
            </li>
          ))}
        </ul>
      )}
      {result.notes.length > 0 && (
        <ul className="list-disc space-y-1 pl-6 text-xs text-muted">
          {result.notes.map((n) => <li key={n}>{n}</li>)}
        </ul>
      )}
    </div>
  );
}
