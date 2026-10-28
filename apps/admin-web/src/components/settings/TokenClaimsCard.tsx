"use client";

import { useCallback, useState } from "react";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";
import { appScopeHeaders } from "@/lib/api/http";
import { browserApiFetch as apiFetch } from "@/lib/api/browser-client";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { Input, Select } from "@/components/ui/Field";
import { useToast } from "@/components/ui/Toast";

type Claim = { name: string; source: string; value: string; accessToken: boolean; idToken: boolean };

const SOURCES: { value: string; label: string; needsValue?: string }[] = [
  { value: "attribute", label: "User attribute", needsValue: "Attribute name" },
  { value: "static", label: "Fixed value", needsValue: "Value" },
  { value: "email", label: "Email" },
  { value: "email_verified", label: "Email verified" },
  { value: "phone", label: "Phone number" },
  { value: "phone_verified", label: "Phone verified" },
  { value: "display_name", label: "Display name" },
  { value: "given_name", label: "First name" },
  { value: "family_name", label: "Last name" },
  { value: "username", label: "Username" },
  { value: "tenant_slug", label: "Tenant slug" },
];

/** Integration › Token claims: this application's own claims in its access and ID tokens. */
export function TokenClaimsCard({ applicationId }: { applicationId: string }) {
  const { toast } = useToast();
  const [claims, setClaims] = useState<Claim[] | null>(null);
  const [saving, setSaving] = useState(false);
  const path = `/api/admin/v1/applications/${applicationId}/settings/token-claims`;
  const init = { headers: appScopeHeaders(applicationId) };

  const load = useCallback(async () => {
    try {
      const data = await apiFetch<{ claims: Claim[] }>(path, init);
      setClaims(data.claims ?? []);
    } catch (e) {
      toast(e instanceof Error ? e.message : "Could not load token claims", "error");
      setClaims([]);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [path, toast]);

  useDeferredEffect(() => {
    load();
  }, [load]);

  if (!claims) return null;
  const patch = (i: number, p: Partial<Claim>) => setClaims(claims.map((c, j) => (j === i ? { ...c, ...p } : c)));

  async function save() {
    setSaving(true);
    try {
      const data = await apiFetch<{ claims: Claim[] }>(path, {
        ...init,
        method: "PUT",
        body: JSON.stringify({ claims }),
      });
      setClaims(data.claims ?? []);
      toast("Token claims saved", "success");
    } catch (e) {
      toast(e instanceof Error ? e.message : "Save failed", "error");
    } finally {
      setSaving(false);
    }
  }

  return (
    <Card padded={false} className="mt-6">
      <CardHeader
        title="Token claims"
        description="Extra claims in this application's tokens, from a user attribute, a fixed value or a profile field. Names SecureOne already sets (sub, roles, email…) cannot be used; prefix with your domain to avoid clashes."
      />
      <div className="space-y-3 px-5 pb-5">
        {claims.length === 0 && <p className="text-sm text-muted">No custom claims.</p>}
        {claims.map((c, i) => {
          const source = SOURCES.find((s) => s.value === c.source);
          return (
            <div key={i} className="grid items-center gap-2 rounded-xl border border-ui p-3 md:grid-cols-[1.2fr_1fr_1fr_auto]">
              <Input value={c.name} onChange={(e) => patch(i, { name: e.target.value })} placeholder="Claim name" aria-label="Claim name" />
              <Select value={c.source} onChange={(e) => patch(i, { source: e.target.value })} aria-label="Source">
                {SOURCES.map((s) => (
                  <option key={s.value} value={s.value}>
                    {s.label}
                  </option>
                ))}
              </Select>
              {source?.needsValue ? (
                <Input value={c.value} onChange={(e) => patch(i, { value: e.target.value })} placeholder={source.needsValue} aria-label={source.needsValue} />
              ) : (
                <span />
              )}
              <div className="flex items-center gap-3 text-xs">
                <label className="flex items-center gap-1">
                  <input type="checkbox" checked={c.accessToken} onChange={(e) => patch(i, { accessToken: e.target.checked })} /> Access
                </label>
                <label className="flex items-center gap-1">
                  <input type="checkbox" checked={c.idToken} onChange={(e) => patch(i, { idToken: e.target.checked })} /> ID
                </label>
                <Button variant="ghost" size="sm" onClick={() => setClaims(claims.filter((_, j) => j !== i))}>
                  Remove
                </Button>
              </div>
            </div>
          );
        })}
        <div className="flex flex-wrap justify-between gap-2">
          {claims.length < 20 ? (
            <Button
              variant="secondary"
              onClick={() => setClaims([...claims, { name: "", source: "attribute", value: "", accessToken: true, idToken: false }])}
            >
              Add claim
            </Button>
          ) : (
            <span />
          )}
          <Button onClick={save} disabled={saving}>
            {saving ? "Saving…" : "Save token claims"}
          </Button>
        </div>
      </div>
    </Card>
  );
}
