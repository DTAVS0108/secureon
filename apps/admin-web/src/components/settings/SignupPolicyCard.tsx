"use client";

import { useCallback, useState } from "react";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { FieldRow, Input, Select, Textarea } from "@/components/ui/Field";
import { useToast } from "@/components/ui/Toast";
import { fetchSignupPolicy, saveSignupPolicy, type SignupField } from "@/lib/api/user-profile";

type Draft = { allowed: string; blocked: string; fields: (SignupField & { optionsText: string })[] };

const lines = (text: string) =>
  text
    .split(/[\s,;]+/)
    .map((d) => d.trim())
    .filter(Boolean);

/**
 * Authentication › Sign-up: which email domains may get an account through this application (hosted sign-up and
 * social or SAML just-in-time accounts), and the extra fields the hosted sign-up page asks for.
 */
export function SignupPolicyCard({ applicationId }: { applicationId: string }) {
  const { toast } = useToast();
  const [draft, setDraft] = useState<Draft | null>(null);
  const [saving, setSaving] = useState(false);

  const load = useCallback(async () => {
    try {
      const p = await fetchSignupPolicy(applicationId);
      setDraft({
        allowed: p.allowedDomains.join("\n"),
        blocked: p.blockedDomains.join("\n"),
        fields: p.fields.map((f) => ({ ...f, optionsText: f.options.join(", ") })),
      });
    } catch (e) {
      toast(e instanceof Error ? e.message : "Could not load the sign-up settings", "error");
    }
  }, [applicationId, toast]);

  useDeferredEffect(() => {
    void load();
  }, [load]);

  if (!draft) return null;
  const patch = (i: number, p: Partial<Draft["fields"][number]>) =>
    setDraft({ ...draft, fields: draft.fields.map((f, j) => (j === i ? { ...f, ...p } : f)) });

  async function save() {
    if (!draft) return;
    setSaving(true);
    try {
      const saved = await saveSignupPolicy(applicationId, {
        allowedDomains: lines(draft.allowed),
        blockedDomains: lines(draft.blocked),
        fields: draft.fields.map(({ optionsText, ...f }) => ({
          ...f,
          options: f.type === "select" ? optionsText.split(",").map((o) => o.trim()).filter(Boolean) : [],
        })),
      });
      setDraft({
        allowed: saved.allowedDomains.join("\n"),
        blocked: saved.blockedDomains.join("\n"),
        fields: saved.fields.map((f) => ({ ...f, optionsText: f.options.join(", ") })),
      });
      toast("Sign-up settings saved", "success");
    } catch (e) {
      toast(e instanceof Error ? e.message : "Save failed", "error");
    } finally {
      setSaving(false);
    }
  }

  return (
    <Card padded={false} className="mt-6">
      <CardHeader
        title="Sign-up"
        description="Who can create an account through this application, and what the hosted sign-up page asks. Domain rules also apply to accounts made by signing in with Google or SAML. Existing accounts are not affected."
      />
      <div className="space-y-4 px-5 pb-5">
        <div className="grid gap-4 md:grid-cols-2">
          <FieldRow label="Allowed email domains" hint="One per line. Empty: any domain. *.example.com for its subdomains.">
            <Textarea
              rows={4}
              value={draft.allowed}
              onChange={(e) => setDraft({ ...draft, allowed: e.target.value })}
              placeholder="Domains, one per line"
            />
          </FieldRow>
          <FieldRow label="Blocked email domains" hint="Refused even when allowed above (e.g. disposable mail).">
            <Textarea
              rows={4}
              value={draft.blocked}
              onChange={(e) => setDraft({ ...draft, blocked: e.target.value })}
              placeholder="Domains, one per line"
            />
          </FieldRow>
        </div>

        <div>
          <p className="text-sm font-medium text-ui">Extra sign-up fields</p>
          <p className="text-xs text-muted">Saved on the user as attributes (usable in token claims). Administrators can change them on the user.</p>
          <div className="mt-2 space-y-2">
            {draft.fields.length === 0 && <p className="text-sm text-muted">None: email, name and password only.</p>}
            {draft.fields.map((f, i) => (
              <div key={i} className="grid items-center gap-2 rounded-xl border border-ui p-3 md:grid-cols-[1fr_1.4fr_0.8fr_1.4fr_auto]">
                <Input value={f.key} onChange={(e) => patch(i, { key: e.target.value })} placeholder="Key" aria-label="Key" />
                <Input value={f.label} onChange={(e) => patch(i, { label: e.target.value })} placeholder="Label shown" aria-label="Label" />
                <Select value={f.type} onChange={(e) => patch(i, { type: e.target.value as SignupField["type"] })} aria-label="Type">
                  <option value="text">Text</option>
                  <option value="select">List</option>
                  <option value="checkbox">Checkbox</option>
                </Select>
                {f.type === "select" ? (
                  <Input value={f.optionsText} onChange={(e) => patch(i, { optionsText: e.target.value })} placeholder="Options, comma-separated" aria-label="Options" />
                ) : (
                  <span />
                )}
                <div className="flex items-center gap-3 text-xs">
                  <label className="flex items-center gap-1">
                    <input type="checkbox" checked={f.required} onChange={(e) => patch(i, { required: e.target.checked })} /> Required
                  </label>
                  <Button variant="ghost" size="sm" onClick={() => setDraft({ ...draft, fields: draft.fields.filter((_, j) => j !== i) })}>
                    Remove
                  </Button>
                </div>
              </div>
            ))}
          </div>
        </div>

        <div className="flex flex-wrap justify-between gap-2">
          {draft.fields.length < 20 ? (
            <Button
              variant="secondary"
              onClick={() =>
                setDraft({
                  ...draft,
                  fields: [...draft.fields, { key: "", label: "", type: "text", required: false, options: [], maxLength: 200, optionsText: "" }],
                })
              }
            >
              Add field
            </Button>
          ) : (
            <span />
          )}
          <Button onClick={() => void save()} disabled={saving}>
            {saving ? "Saving…" : "Save sign-up settings"}
          </Button>
        </div>
      </div>
    </Card>
  );
}
