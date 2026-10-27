"use client";

import { useCallback, useState } from "react";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { FieldRow, Input, Textarea } from "@/components/ui/Field";
import { useToast } from "@/components/ui/Toast";
import {
  createApplicationApi,
  deleteApplicationApi,
  fetchApplicationApis,
  updateApplicationApi,
  type ApiScope,
  type ApplicationApi,
} from "@/lib/api/application-apis";

type Draft = { id: string | null; name: string; identifier: string; scopesText: string };

const EMPTY: Draft = { id: null, name: "", identifier: "", scopesText: "" };

/** "orders:read Read orders" per line → scopes (the first word is the scope, the rest its description). */
function parseScopes(text: string): ApiScope[] {
  return text
    .split("\n")
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => {
      const [value, ...rest] = line.split(/\s+/);
      return { value, description: rest.join(" ") };
    });
}

const scopesText = (scopes: ApiScope[]) =>
  scopes.map((s) => (s.description ? `${s.value} ${s.description}` : s.value)).join("\n");

/**
 * Integration › APIs: the application's APIs (resource servers). An access token granted one of an API's scopes
 * names the API in "aud", so the API can refuse tokens that were issued for something else.
 */
export function ApplicationApisCard({ applicationId }: { applicationId: string }) {
  const { toast } = useToast();
  const [apis, setApis] = useState<ApplicationApi[] | null>(null);
  const [draft, setDraft] = useState<Draft | null>(null);
  const [confirmDelete, setConfirmDelete] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      setApis(await fetchApplicationApis(applicationId));
    } catch (e) {
      toast(e instanceof Error ? e.message : "Could not load the APIs", "error");
    }
  }, [applicationId, toast]);

  useDeferredEffect(() => {
    void load();
  }, [load]);

  async function save() {
    if (!draft) return;
    setBusy(true);
    try {
      const input = { name: draft.name.trim(), identifier: draft.identifier.trim(), scopes: parseScopes(draft.scopesText) };
      if (draft.id) {
        await updateApplicationApi(applicationId, draft.id, input);
        toast("API saved", "success");
      } else {
        await createApplicationApi(applicationId, input);
        toast("API added", "success");
      }
      setDraft(null);
      await load();
    } catch (e) {
      toast(e instanceof Error ? e.message : "Save failed", "error");
    } finally {
      setBusy(false);
    }
  }

  async function remove(api: ApplicationApi) {
    setBusy(true);
    try {
      await deleteApplicationApi(applicationId, api.id);
      toast(`${api.name} removed`, "success");
      setConfirmDelete(null);
      await load();
    } catch (e) {
      toast(e instanceof Error ? e.message : "Remove failed", "error");
    } finally {
      setBusy(false);
    }
  }

  if (!apis) return null;

  return (
    <Card padded={false} className="mt-6">
      <CardHeader
        title="APIs"
        description="Backends this application's access tokens are for. A token granted one of an API's scopes carries the API's identifier in aud; the API checks aud before it accepts the token. Give a client the scopes it needs under its OAuth client settings."
      />
      <div className="space-y-3 px-5 pb-5">
        {apis.length === 0 && !draft && (
          <p className="text-sm text-muted">No APIs yet. Access tokens name only the client in aud.</p>
        )}
        {apis.map((api) => (
          <div key={api.id} className="rounded-xl border border-ui p-3">
            <div className="flex flex-wrap items-start justify-between gap-2">
              <div className="min-w-0">
                <p className="text-sm font-medium text-ui">{api.name}</p>
                <p className="break-all font-mono text-xs text-muted">{api.identifier}</p>
              </div>
              <div className="flex gap-2">
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => setDraft({ id: api.id, name: api.name, identifier: api.identifier, scopesText: scopesText(api.scopes) })}
                >
                  Edit
                </Button>
                {confirmDelete === api.id ? (
                  <>
                    <Button variant="danger" size="sm" disabled={busy} onClick={() => void remove(api)}>
                      Remove {api.name}
                    </Button>
                    <Button variant="ghost" size="sm" onClick={() => setConfirmDelete(null)}>
                      Keep
                    </Button>
                  </>
                ) : (
                  <Button variant="ghost" size="sm" onClick={() => setConfirmDelete(api.id)}>
                    Remove
                  </Button>
                )}
              </div>
            </div>
            {api.scopes.length > 0 && (
              <ul className="mt-2 flex flex-wrap gap-1.5">
                {api.scopes.map((s) => (
                  <li key={s.value} title={s.description} className="rounded-md border border-ui px-1.5 py-0.5 font-mono text-xs">
                    {s.value}
                  </li>
                ))}
              </ul>
            )}
            {confirmDelete === api.id && (
              <p className="mt-2 text-xs text-muted">New tokens stop naming this API. Tokens already issued keep it until they expire.</p>
            )}
          </div>
        ))}

        {draft ? (
          <div className="space-y-3 rounded-xl border border-ui p-3">
            <div className="grid gap-3 md:grid-cols-2">
              <FieldRow label="Name">
                <Input id="api-name" value={draft.name} onChange={(e) => setDraft({ ...draft, name: e.target.value })} placeholder="Orders API" />
              </FieldRow>
              <FieldRow label="Identifier (audience)" hint="An https:// URL or urn: name. Unique; it is a name, nothing calls it.">
                <Input
                  id="api-identifier"
                  value={draft.identifier}
                  onChange={(e) => setDraft({ ...draft, identifier: e.target.value })}
                  placeholder="https://api.example.com/orders"
                />
              </FieldRow>
            </div>
            <FieldRow label="Scopes" hint="One per line: the scope, then what it allows (e.g. orders:read Read orders).">
              <Textarea
                id="api-scopes"
                rows={4}
                value={draft.scopesText}
                onChange={(e) => setDraft({ ...draft, scopesText: e.target.value })}
                placeholder={"orders:read Read orders\norders:write Create and change orders"}
              />
            </FieldRow>
            <div className="flex justify-end gap-2">
              <Button variant="ghost" onClick={() => setDraft(null)}>
                Cancel
              </Button>
              <Button onClick={() => void save()} disabled={busy || !draft.name.trim() || !draft.identifier.trim()}>
                {busy ? "Saving…" : draft.id ? "Save API" : "Add API"}
              </Button>
            </div>
          </div>
        ) : (
          <Button variant="secondary" onClick={() => setDraft(EMPTY)}>
            Add API
          </Button>
        )}
      </div>
    </Card>
  );
}
