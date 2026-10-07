"use client";

import { useCallback, useState } from "react";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { CopyValue } from "@/components/ui/CopyValue";
import { FieldRow, Input } from "@/components/ui/Field";
import { useToast } from "@/components/ui/Toast";
import {
  createServiceAccount,
  createServiceAccountKey,
  deleteServiceAccount,
  listServiceAccountKeys,
  listServiceAccounts,
  revokeServiceAccountKey,
  type ApiKeySummary,
  type CreatedApiKey,
  type ServiceAccount,
} from "@/lib/api/service-accounts";

export function ServiceAccountsPanel({ tenantId }: { tenantId: string }) {
  const { toast } = useToast();
  const [accounts, setAccounts] = useState<ServiceAccount[]>([]);
  const [loaded, setLoaded] = useState(false);
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [creating, setCreating] = useState(false);
  const [expandedId, setExpandedId] = useState<string | null>(null);
  const [keys, setKeys] = useState<Record<string, ApiKeySummary[]>>({});
  const [newKey, setNewKey] = useState<CreatedApiKey | null>(null);

  const reload = useCallback(async () => {
    setLoaded(false);
    try {
      setAccounts(await listServiceAccounts(tenantId));
    } catch (e) {
      toast(e instanceof Error ? e.message : "Failed to load service accounts", "error");
    } finally {
      setLoaded(true);
    }
  }, [tenantId, toast]);

  useDeferredEffect(() => {
    reload();
  }, [reload]);

  async function loadKeys(accountId: string) {
    try {
      const rows = await listServiceAccountKeys(tenantId, accountId);
      setKeys((prev) => ({ ...prev, [accountId]: rows }));
    } catch (e) {
      toast(e instanceof Error ? e.message : "Failed to load API keys", "error");
    }
  }

  async function handleCreate() {
    if (!name.trim()) return;
    setCreating(true);
    try {
      await createServiceAccount(tenantId, { name: name.trim(), description: description.trim() || undefined });
      setName("");
      setDescription("");
      toast("Service account created", "success");
      await reload();
    } catch (e) {
      toast(e instanceof Error ? e.message : "Create failed", "error");
    } finally {
      setCreating(false);
    }
  }

  async function handleDelete(account: ServiceAccount) {
    try {
      await deleteServiceAccount(tenantId, account.id);
      toast("Service account deleted", "success");
      await reload();
    } catch (e) {
      toast(e instanceof Error ? e.message : "Delete failed", "error");
    }
  }

  async function handleCreateKey(accountId: string) {
    try {
      const created = await createServiceAccountKey(tenantId, accountId, "Default key");
      setNewKey(created);
      await loadKeys(accountId);
      toast("API key created — copy it now", "success");
    } catch (e) {
      toast(e instanceof Error ? e.message : "Could not create key", "error");
    }
  }

  return (
    <Card padded={false} className={!loaded ? "pointer-events-none opacity-60" : ""}>
      <CardHeader
        title="Service accounts"
        description="Machine identities for M2M integration. API keys are shown once at creation."
      />
      <div className="space-y-4 px-5 pb-5">
        <div className="grid gap-3 sm:grid-cols-2">
          <FieldRow label="Name">
            <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="billing-sync" />
          </FieldRow>
          <FieldRow label="Description">
            <Input value={description} onChange={(e) => setDescription(e.target.value)} placeholder="Optional" />
          </FieldRow>
        </div>
        <Button onClick={() => void handleCreate()} disabled={creating || !name.trim()}>
          {creating ? "Creating…" : "Create service account"}
        </Button>

        {newKey?.apiKey && (
          <div className="rounded-xl border border-amber-500/30 bg-amber-500/5 p-4 space-y-2">
            <p className="text-sm font-medium text-ui">New API key (copy now)</p>
            <CopyValue value={newKey.apiKey} />
            {newKey.scopes && newKey.scopes.length > 0 && (
              <p className="text-xs text-muted">
                Scopes: {newKey.scopes.join(", ")} — routes under <code>/api/v1/m2m/**</code> (120 req/min per key).
              </p>
            )}
            <Button variant="secondary" size="sm" onClick={() => setNewKey(null)}>
              Done
            </Button>
          </div>
        )}

        {accounts.length === 0 ? (
          <p className="text-sm text-muted">No service accounts yet.</p>
        ) : (
          <ul className="divide-y divide-ui rounded-xl border border-ui">
            {accounts.map((account) => (
              <li key={account.id} className="px-4 py-3 space-y-2">
                <div className="flex items-center justify-between gap-3">
                  <div>
                    <p className="text-sm font-medium text-ui">{account.name}</p>
                    {account.description && <p className="text-xs text-muted">{account.description}</p>}
                    <Badge tone={account.status === "ACTIVE" ? "success" : "neutral"} className="mt-1">
                      {account.status}
                    </Badge>
                  </div>
                  <div className="flex gap-2">
                    <Button
                      variant="secondary"
                      size="sm"
                      onClick={() => {
                        const next = expandedId === account.id ? null : account.id;
                        setExpandedId(next);
                        if (next) void loadKeys(account.id);
                      }}
                    >
                      Keys
                    </Button>
                    <Button variant="ghost" size="sm" onClick={() => void handleDelete(account)}>
                      Delete
                    </Button>
                  </div>
                </div>
                {expandedId === account.id && (
                  <div className="space-y-2 pt-2">
                    <Button variant="secondary" size="sm" onClick={() => void handleCreateKey(account.id)}>
                      Generate API key
                    </Button>
                    <ul className="space-y-1 text-xs text-muted">
                      {(keys[account.id] ?? []).map((key) => (
                        <li key={key.id} className="flex items-center justify-between gap-2">
                          <span>
                            {key.keyPrefix}… {key.label ? `(${key.label})` : ""}
                            {key.revokedAt ? " — revoked" : ""}
                          </span>
                          {!key.revokedAt && (
                            <Button
                              variant="ghost"
                              size="sm"
                              onClick={() =>
                                void revokeServiceAccountKey(tenantId, account.id, key.id).then(() =>
                                  loadKeys(account.id),
                                )
                              }
                            >
                              Revoke
                            </Button>
                          )}
                        </li>
                      ))}
                    </ul>
                  </div>
                )}
              </li>
            ))}
          </ul>
        )}
      </div>
    </Card>
  );
}
