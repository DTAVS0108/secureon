"use client";

import { useCallback, useState } from "react";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { CopyValue } from "@/components/ui/CopyValue";
import { FieldRow, Input, Select, Textarea } from "@/components/ui/Field";
import { useToast } from "@/components/ui/Toast";
import {
  createSamlApp,
  deleteSamlApp,
  fetchSamlApps,
  updateSamlApp,
  type SamlApp,
  type SamlAppInput,
  type SamlApps,
  type SamlAttribute,
} from "@/lib/api/saml-apps";

type Draft = {
  id: string | null;
  name: string;
  entityId: string;
  acsUrl: string;
  sloUrl: string;
  nameIdFormat: SamlApp["nameIdFormat"];
  signResponse: boolean;
  signAssertion: boolean;
  spCertificate: string;
  requireSignedRequests: boolean;
  attributesText: string;
  status: SamlApp["status"];
};

const EMPTY: Draft = {
  id: null,
  name: "",
  entityId: "",
  acsUrl: "",
  sloUrl: "",
  nameIdFormat: "email",
  signResponse: true,
  signAssertion: true,
  spCertificate: "",
  requireSignedRequests: false,
  attributesText: "",
  status: "active",
};

/** "name source [value]" per line (value for the attribute and static sources). */
function parseAttributes(text: string): SamlAttribute[] {
  return text
    .split("\n")
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => {
      const [name, source, ...rest] = line.split(/\s+/);
      return { name, source: source ?? "", value: rest.length ? rest.join(" ") : undefined };
    });
}

const attributesText = (attributes: SamlAttribute[]) =>
  attributes.map((a) => [a.name, a.source, a.value].filter(Boolean).join(" ")).join("\n");

function toDraft(app: SamlApp): Draft {
  return {
    id: app.id,
    name: app.name,
    entityId: app.entityId,
    acsUrl: app.acsUrl,
    sloUrl: app.sloUrl ?? "",
    nameIdFormat: app.nameIdFormat,
    signResponse: app.signResponse,
    signAssertion: app.signAssertion,
    spCertificate: app.spCertificate ?? "",
    requireSignedRequests: app.requireSignedRequests,
    attributesText: attributesText(app.attributes),
    status: app.status,
  };
}

/**
 * Integration › SAML apps: applications that sign users in with SAML 2.0 through this organization's identity
 * provider. This application's access rules decide who may sign in to them.
 */
export function SamlAppsCard({ applicationId }: { applicationId: string }) {
  const { toast } = useToast();
  const [data, setData] = useState<SamlApps | null>(null);
  const [draft, setDraft] = useState<Draft | null>(null);
  const [confirmDelete, setConfirmDelete] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      setData(await fetchSamlApps(applicationId));
    } catch (e) {
      toast(e instanceof Error ? e.message : "Could not load the SAML apps", "error");
    }
  }, [applicationId, toast]);

  useDeferredEffect(() => {
    void load();
  }, [load]);

  async function save() {
    if (!draft) return;
    setBusy(true);
    try {
      const input: SamlAppInput = {
        name: draft.name.trim(),
        entityId: draft.entityId.trim(),
        acsUrl: draft.acsUrl.trim(),
        sloUrl: draft.sloUrl.trim(),
        nameIdFormat: draft.nameIdFormat,
        signResponse: draft.signResponse,
        signAssertion: draft.signAssertion,
        spCertificate: draft.spCertificate.trim(),
        requireSignedRequests: draft.requireSignedRequests,
        attributes: parseAttributes(draft.attributesText),
        status: draft.status,
      };
      if (draft.id) {
        await updateSamlApp(applicationId, draft.id, input);
        toast("SAML app saved", "success");
      } else {
        await createSamlApp(applicationId, input);
        toast("SAML app added", "success");
      }
      setDraft(null);
      await load();
    } catch (e) {
      toast(e instanceof Error ? e.message : "Save failed", "error");
    } finally {
      setBusy(false);
    }
  }

  async function remove(app: SamlApp) {
    setBusy(true);
    try {
      await deleteSamlApp(applicationId, app.id);
      toast(`${app.name} removed`, "success");
      setConfirmDelete(null);
      await load();
    } catch (e) {
      toast(e instanceof Error ? e.message : "Remove failed", "error");
    } finally {
      setBusy(false);
    }
  }

  if (!data) return null;
  const idp = data.identityProvider;

  return (
    <Card padded={false} className="mt-6">
      <CardHeader
        title="SAML apps"
        description="Applications that only speak SAML 2.0 sign users in through SecureOne as their identity provider. Give the app the metadata URL (or the entity ID, SSO URL and certificate); users sign in with this application's methods, two-step rules and access."
      />
      <div className="space-y-4 px-5 pb-5">
        <div className="space-y-1.5 rounded-xl border border-ui p-3 text-sm">
          <p className="font-medium text-ui">Identity provider for these apps</p>
          {[
            ["Metadata URL", idp.metadataUrl],
            ["Entity ID", idp.entityId],
            ["SSO URL (Redirect and POST)", idp.ssoUrl],
            ["Single logout URL", idp.sloUrl],
          ].map(([label, value]) => (
            <div key={label} className="flex flex-wrap items-center gap-2">
              <span className="w-48 text-xs text-muted">{label}</span>
              <code className="break-all font-mono text-xs">{value}</code>
              <CopyValue value={value} />
            </div>
          ))}
          <div className="flex flex-wrap items-center gap-2">
            <span className="w-48 text-xs text-muted">Signing certificate</span>
            <CopyValue value={idp.certificatePem} />
          </div>
        </div>

        {data.serviceProviders.length === 0 && !draft && <p className="text-sm text-muted">No SAML apps yet.</p>}
        {data.serviceProviders.map((app) => (
          <div key={app.id} className="rounded-xl border border-ui p-3">
            <div className="flex flex-wrap items-start justify-between gap-2">
              <div className="min-w-0">
                <p className="flex items-center gap-2 text-sm font-medium text-ui">
                  {app.name}
                  <Badge tone={app.status === "active" ? "success" : "neutral"}>{app.status}</Badge>
                </p>
                <p className="break-all font-mono text-xs text-muted">{app.entityId}</p>
                <p className="break-all text-xs text-muted">ACS {app.acsUrl}</p>
              </div>
              <div className="flex gap-2">
                <Button variant="ghost" size="sm" onClick={() => setDraft(toDraft(app))}>
                  Edit
                </Button>
                {confirmDelete === app.id ? (
                  <>
                    <Button variant="danger" size="sm" disabled={busy} onClick={() => void remove(app)}>
                      Remove {app.name}
                    </Button>
                    <Button variant="ghost" size="sm" onClick={() => setConfirmDelete(null)}>
                      Keep
                    </Button>
                  </>
                ) : (
                  <Button variant="ghost" size="sm" onClick={() => setConfirmDelete(app.id)}>
                    Remove
                  </Button>
                )}
              </div>
            </div>
            <p className="mt-2 text-xs text-muted">
              Name ID: {app.nameIdFormat} · attributes: {app.effectiveAttributes.map((a) => a.name).join(", ")}
            </p>
            <div className="mt-1 flex flex-wrap items-center gap-2 text-xs text-muted">
              Sign-in link (IdP-initiated): <code className="break-all">{idp.initUrl + encodeURIComponent(app.entityId)}</code>
              <CopyValue value={idp.initUrl + encodeURIComponent(app.entityId)} />
            </div>
            {confirmDelete === app.id && <p className="mt-2 text-xs text-muted">The app can no longer sign users in.</p>}
          </div>
        ))}

        {draft ? (
          <div className="space-y-3 rounded-xl border border-ui p-3">
            <div className="grid gap-3 md:grid-cols-2">
              <FieldRow label="Name">
                <Input value={draft.name} onChange={(e) => setDraft({ ...draft, name: e.target.value })} placeholder="Application name" />
              </FieldRow>
              <FieldRow label="SP entity ID" hint="The app's entity ID (entityID in its SAML metadata).">
                <Input value={draft.entityId} onChange={(e) => setDraft({ ...draft, entityId: e.target.value })} placeholder="Entity ID" />
              </FieldRow>
              <FieldRow label="ACS URL" hint="Where the app receives assertions (HTTP-POST). Only this URL is ever sent one.">
                <Input value={draft.acsUrl} onChange={(e) => setDraft({ ...draft, acsUrl: e.target.value })} placeholder="ACS URL" />
              </FieldRow>
              <FieldRow label="Single logout URL (optional)">
                <Input value={draft.sloUrl} onChange={(e) => setDraft({ ...draft, sloUrl: e.target.value })} placeholder="SLO URL" />
              </FieldRow>
              <FieldRow label="Name ID" hint="email: the user's email; persistent: the user's ID, which never changes.">
                <Select value={draft.nameIdFormat} onChange={(e) => setDraft({ ...draft, nameIdFormat: e.target.value as Draft["nameIdFormat"] })}>
                  <option value="email">Email address</option>
                  <option value="persistent">Persistent (user ID)</option>
                  <option value="unspecified">Unspecified (email)</option>
                </Select>
              </FieldRow>
              <FieldRow label="Status">
                <Select value={draft.status} onChange={(e) => setDraft({ ...draft, status: e.target.value as Draft["status"] })}>
                  <option value="active">Active</option>
                  <option value="disabled">Disabled</option>
                </Select>
              </FieldRow>
            </div>
            <FieldRow
              label="Attributes"
              hint={`One per line: attribute name, then source (${data.attributeSources.join(", ")}), then a value for attribute/static. Empty: ${data.defaultAttributes.map((a) => a.name).join(", ")}.`}
            >
              <Textarea
                rows={4}
                className="font-mono text-xs"
                value={draft.attributesText}
                onChange={(e) => setDraft({ ...draft, attributesText: e.target.value })}
                placeholder={"email email\nfirstName given_name\nlastName family_name\ngroups roles"}
              />
            </FieldRow>
            <FieldRow label="SP signing certificate (optional)" hint="To check signed requests from the app (PEM).">
              <Textarea
                rows={3}
                className="font-mono text-xs"
                value={draft.spCertificate}
                onChange={(e) => setDraft({ ...draft, spCertificate: e.target.value })}
                placeholder="-----BEGIN CERTIFICATE-----"
              />
            </FieldRow>
            <div className="flex flex-wrap gap-6 text-sm">
              {(
                [
                  ["signResponse", "Sign the response"],
                  ["signAssertion", "Sign the assertion"],
                  ["requireSignedRequests", "Require signed requests"],
                ] as const
              ).map(([key, label]) => (
                <label key={key} className="flex items-center gap-2">
                  <input
                    type="checkbox"
                    className="rounded border-ui"
                    checked={draft[key]}
                    onChange={(e) => setDraft({ ...draft, [key]: e.target.checked })}
                  />
                  {label}
                </label>
              ))}
            </div>
            <div className="flex justify-end gap-2">
              <Button variant="ghost" onClick={() => setDraft(null)}>
                Cancel
              </Button>
              <Button
                onClick={() => void save()}
                disabled={busy || !draft.name.trim() || !draft.entityId.trim() || !draft.acsUrl.trim()}
              >
                {busy ? "Saving…" : draft.id ? "Save SAML app" : "Add SAML app"}
              </Button>
            </div>
          </div>
        ) : (
          <Button variant="secondary" onClick={() => setDraft(EMPTY)}>
            Add SAML app
          </Button>
        )}
      </div>
    </Card>
  );
}
