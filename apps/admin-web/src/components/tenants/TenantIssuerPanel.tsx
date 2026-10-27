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
  addTenantDomain,
  deleteTenantDomain,
  fetchTenantIssuer,
  setTenantIssuerMode,
  verifyTenantDomain,
  type TenantIssuer,
} from "@/lib/api/tenant-issuer";

/** Tenant › Issuer and domains: the tenant's own issuer, whether the shared one is still allowed, custom domains. */
export function TenantIssuerPanel({ tenantId }: { tenantId: string }) {
  const { toast } = useToast();
  const [data, setData] = useState<TenantIssuer | null>(null);
  const [domain, setDomain] = useState("");
  const [busy, setBusy] = useState(false);
  const [confirmRemove, setConfirmRemove] = useState<string | null>(null);

  const reload = useCallback(async () => {
    try {
      setData(await fetchTenantIssuer(tenantId));
    } catch (e) {
      toast(e instanceof Error ? e.message : "Failed to load the issuer", "error");
    }
  }, [tenantId, toast]);

  useDeferredEffect(() => {
    void reload();
  }, [reload]);

  async function run(work: () => Promise<unknown>, done: string) {
    setBusy(true);
    try {
      await work();
      toast(done, "success");
      await reload();
    } catch (e) {
      toast(e instanceof Error ? e.message : "Request failed", "error");
    } finally {
      setBusy(false);
    }
  }

  const ownIssuer = data?.issuers.find((i) => i.includes("/t/"));

  return (
    <Card padded={false} className={!data ? "pointer-events-none opacity-60" : ""}>
      <CardHeader
        title="Issuer and domains"
        description="This organization's own issuer, with its own discovery document. Tokens issued there carry it in iss, and only this organization's apps are served there."
      />
      <div className="space-y-5 px-5 pb-5">
        <div className="space-y-1">
          <p className="text-sm font-medium text-ui">Issuers apps can use</p>
          <ul className="space-y-1">
            {(data?.issuers ?? []).map((issuer) => (
              <li key={issuer} className="flex items-center gap-2 text-sm">
                <code className="break-all font-mono text-xs">{issuer}</code>
                <CopyValue value={issuer} />
                {issuer === data?.sharedIssuer && <Badge tone="neutral">shared</Badge>}
              </li>
            ))}
          </ul>
          {ownIssuer && (
            <p className="text-xs text-muted">
              Discovery: <code>{ownIssuer}/.well-known/openid-configuration</code>
            </p>
          )}
        </div>

        <label className="flex items-start gap-2 text-sm">
          <input
            type="checkbox"
            className="mt-0.5 rounded border-ui"
            checked={data?.issuerMode === "tenant"}
            disabled={busy}
            onChange={(e) =>
              void run(
                () => setTenantIssuerMode(tenantId, e.target.checked ? "tenant" : "shared"),
                e.target.checked ? "Apps must now use this organization's issuer" : "The shared issuer is allowed again",
              )
            }
          />
          <span>
            Use this organization&apos;s own issuer only
            <span className="block text-xs text-muted">
              Sign-ins and tokens on the shared issuer are refused for this organization&apos;s apps. Update every
              app&apos;s issuer setting before turning this on.
            </span>
          </span>
        </label>

        <div className="space-y-2">
          <p className="text-sm font-medium text-ui">Custom domains</p>
          <p className="text-xs text-muted">
            A domain of your own (login.example.com) for sign-in pages and as issuer. Prove you control it with the
            DNS TXT record, or point it at SecureOne and check. HTTPS for the domain is set up on your reverse
            proxy (it can ask <code>/api/v1/domains/check?domain=…</code> before issuing a certificate).
          </p>
          <div className="flex gap-2">
            <div className="flex-1">
              <FieldRow label="Domain">
                <Input value={domain} onChange={(e) => setDomain(e.target.value)} placeholder="login.example.com" />
              </FieldRow>
            </div>
            <Button
              className="self-end"
              disabled={busy || !domain.trim()}
              onClick={() =>
                void run(async () => {
                  await addTenantDomain(tenantId, domain.trim());
                  setDomain("");
                }, "Domain added: verify it next")
              }
            >
              Add domain
            </Button>
          </div>
          {(data?.domains ?? []).length === 0 ? (
            <p className="text-sm text-muted">No custom domains.</p>
          ) : (
            <ul className="divide-y divide-ui rounded-xl border border-ui">
              {data!.domains.map((d) => (
                <li key={d.id} className="space-y-2 px-4 py-3">
                  <div className="flex items-center justify-between gap-3">
                    <div className="flex items-center gap-2">
                      <span className="text-sm font-medium text-ui">{d.domain}</span>
                      <Badge tone={d.status === "verified" ? "success" : "neutral"}>{d.status}</Badge>
                    </div>
                    <div className="flex gap-2">
                      {d.status !== "verified" && (
                        <Button
                          variant="secondary"
                          size="sm"
                          disabled={busy}
                          onClick={() =>
                            void run(async () => {
                              const r = await verifyTenantDomain(tenantId, d.id);
                              if (r.status !== "verified") throw new Error(r.lastError ?? "Not verified yet.");
                            }, "Domain verified")
                          }
                        >
                          Verify
                        </Button>
                      )}
                      {confirmRemove === d.id ? (
                        <>
                          <Button
                            variant="danger"
                            size="sm"
                            disabled={busy}
                            onClick={() => void run(() => deleteTenantDomain(tenantId, d.id), "Domain removed")}
                          >
                            Remove now
                          </Button>
                          <Button variant="ghost" size="sm" onClick={() => setConfirmRemove(null)}>
                            Keep
                          </Button>
                        </>
                      ) : (
                        <Button variant="ghost" size="sm" onClick={() => setConfirmRemove(d.id)}>
                          Remove
                        </Button>
                      )}
                    </div>
                  </div>
                  {d.status !== "verified" ? (
                    <div className="space-y-1 text-xs text-muted">
                      <p>
                        TXT record <code>{d.txtRecordName}</code> with value <code className="break-all">{d.txtRecordValue}</code>{" "}
                        <CopyValue value={d.txtRecordValue} />
                      </p>
                      <p>
                        or point the domain at SecureOne: <code className="break-all">{d.httpCheckUrl}</code> must answer.
                      </p>
                      {d.lastError && <p className="text-amber-700 dark:text-amber-300">Last check: {d.lastError}</p>}
                    </div>
                  ) : (
                    <p className="text-xs text-muted">
                      Issuer <code>{d.issuer}</code>
                      {confirmRemove === d.id && " — apps using it stop working when it is removed."}
                    </p>
                  )}
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </Card>
  );
}
