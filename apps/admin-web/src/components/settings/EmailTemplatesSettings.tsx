"use client";

import { useMemo, useState } from "react";
import { useDialog } from "@/components/ui/Dialogs";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { FieldRow, Input, Select, Textarea } from "@/components/ui/Field";
import { Toggle } from "@/components/ui/Toggle";
import { TemplateRecipientsPicker } from "@/components/settings/TemplateRecipientsPicker";
import {
  loadEmailTemplateDefaultsAction,
  resetEmailTemplateAction,
  saveEmailTemplatesAction,
  type AdminRecipientOption,
  type RecipientUserOption,
} from "@/lib/actions/settings";
import type { EmailTemplate, EmailTemplatesMap, RecipientGroups } from "@/lib/api/settings";

const TEMPLATE_ORDER = [
  "test",
  "verify_email",
  "password_reset",
  "magic_link",
  "set_password_invite",
  "account_suspended",
  "account_reactivated",
  "password_changed",
  "admin_notification",
  "admin_security_alert",
];

function parseVariables(text: string): string[] {
  return text
    .split(/[,;\s]+/)
    .map((v) => v.trim().replace(/^\{\{|\}\}$/g, ""))
    .filter(Boolean);
}

function variablesToText(variables?: string[]): string {
  return (variables ?? []).join(", ");
}

function templateLabel(key: string, template?: EmailTemplate): string {
  if (template?.name) {
    return `${template.name} (${key})`;
  }
  return key;
}

function isDraftDirty(current: EmailTemplate, saved: EmailTemplate): boolean {
  return JSON.stringify(current) !== JSON.stringify(saved);
}

export function EmailTemplatesSettings({
  applicationId,
  templates,
  recipientGroups,
  adminOptions,
  allUsers,
  onSaved,
}: {
  applicationId: string;
  templates: EmailTemplatesMap;
  recipientGroups: RecipientGroups;
  adminOptions: AdminRecipientOption[];
  allUsers: RecipientUserOption[];
  onSaved: (next: EmailTemplatesMap) => void;
}) {
  const keys = useMemo(() => {
    const ordered = TEMPLATE_ORDER.filter((k) => templates[k]);
    const rest = Object.keys(templates).filter((k) => !ordered.includes(k));
    return [...ordered, ...rest];
  }, [templates]);

  const [selectedKey, setSelectedKey] = useState(keys[0] ?? "test");
  const saved = templates[selectedKey] ?? {};
  const [draft, setDraft] = useState<EmailTemplate>(saved);
  const [platformDefaults, setPlatformDefaults] = useState<EmailTemplatesMap | null>(null);
  const [saving, setSaving] = useState(false);
  const [loadingDefaults, setLoadingDefaults] = useState(false);
  const [resetting, setResetting] = useState(false);
  const dialog = useDialog();
  const [message, setMessage] = useState<string | null>(null);
  const [newTemplateKey, setNewTemplateKey] = useState("");
  const [newTemplateName, setNewTemplateName] = useState("");

  const dirty = isDraftDirty(draft, saved);

  useDeferredEffect(() => {
    if (keys.length === 0) return;
    if (!keys.includes(selectedKey)) {
      const nextKey = keys[0];
      setSelectedKey(nextKey);
      setDraft(templates[nextKey] ?? {});
    }
  }, [keys, selectedKey, templates]);

  useDeferredEffect(() => {
    setDraft(templates[selectedKey] ?? {});
  }, [selectedKey, templates]);

  async function ensurePlatformDefaults(): Promise<EmailTemplatesMap | null> {
    if (platformDefaults) {
      return platformDefaults;
    }
    setLoadingDefaults(true);
    const result = await loadEmailTemplateDefaultsAction(applicationId);
    setLoadingDefaults(false);
    if (result.ok && result.templates) {
      setPlatformDefaults(result.templates);
      return result.templates;
    }
    setMessage(result.error ?? "Could not load platform defaults.");
    return null;
  }

  async function selectKey(key: string) {
    if (
      dirty &&
      !(await dialog.confirm({ title: "Discard unsaved changes?", message: "Your edits to this template are lost.", confirmLabel: "Discard" }))
    ) {
      return;
    }
    setSelectedKey(key);
    setMessage(null);
  }

  function updateDraft(patch: Partial<EmailTemplate>) {
    setDraft((prev) => ({ ...prev, ...patch }));
  }

  function discardChanges() {
    setDraft(templates[selectedKey] ?? {});
    setMessage("Changes discarded.");
  }

  async function importPlatformDefault() {
    const defaults = await ensurePlatformDefaults();
    const platformTemplate = defaults?.[selectedKey];
    if (!platformTemplate) {
      setMessage("No platform default found for this template.");
      return;
    }
    setDraft({ ...platformTemplate });
    setMessage("Platform default loaded into the editor. Click Save template to apply.");
  }

  async function resetToPlatformDefault() {
    if (
      !(await dialog.confirm({
        title: `Reset "${selectedKey}" to the platform default?`,
        message: "This cannot be undone.",
        confirmLabel: "Reset template",
      }))
    ) {
      return;
    }
    setResetting(true);
    setMessage(null);
    const result = await resetEmailTemplateAction(applicationId, selectedKey);
    if (result.ok && result.templates) {
      onSaved(result.templates);
      setDraft(result.templates[selectedKey] ?? {});
      setMessage("Template reset to platform default.");
    } else {
      setMessage(result.error ?? "Reset failed");
    }
    setResetting(false);
  }

  async function save() {
    setSaving(true);
    setMessage(null);
    const next: EmailTemplatesMap = { ...templates, [selectedKey]: draft };
    const result = await saveEmailTemplatesAction(applicationId, next);
    if (result.ok && result.templates) {
      onSaved(result.templates);
      setDraft(result.templates[selectedKey] ?? draft);
      setMessage("Template saved.");
    } else {
      setMessage(result.error ?? "Save failed");
    }
    setSaving(false);
  }

  async function addCustomTemplate() {
    const key = newTemplateKey.trim().toLowerCase().replace(/\s+/g, "_");
    if (!key) {
      setMessage("Enter a template key (e.g. welcome_email).");
      return;
    }
    if (!/^[a-z][a-z0-9_]*$/.test(key)) {
      setMessage("Template key must use lowercase letters, numbers, and underscores.");
      return;
    }
    if (templates[key]) {
      setMessage("A template with that key already exists.");
      return;
    }
    const created: EmailTemplate = {
      name: newTemplateName.trim() || key,
      description: "Custom notification template",
      subject: "Notification from {{appName}}",
      bodyText: "Hello {{userName}},\n\n{{body}}",
      bodyHtml: "<p>Hello {{userName}},</p><p>{{body}}</p>",
      cc: [],
      bcc: [],
      enabled: true,
      variables: ["userName", "appName", "body"],
    };
    setSaving(true);
    setMessage(null);
    const next: EmailTemplatesMap = { ...templates, [key]: created };
    const result = await saveEmailTemplatesAction(applicationId, next);
    if (result.ok && result.templates) {
      onSaved(result.templates);
      setSelectedKey(key);
      setDraft(result.templates[key] ?? created);
      setNewTemplateKey("");
      setNewTemplateName("");
      setMessage(`Custom template "${key}" created.`);
    } else {
      setMessage(result.error ?? "Could not create template.");
    }
    setSaving(false);
  }

  return (
    <Card padded={false}>
      <CardHeader
        title="Email templates"
        description="View and edit existing templates, import platform defaults, add custom templates, and use {{placeholders}} for dynamic values."
      />
      {message && <p className="px-5 text-sm text-brand">{message}</p>}
      <div className="grid grid-cols-1 gap-4 p-5 lg:grid-cols-[260px_1fr]">
        <FieldRow label="Template">
          <Select value={selectedKey} onChange={(e) => selectKey(e.target.value)}>
            {keys.map((key) => (
              <option key={key} value={key}>
                {templateLabel(key, templates[key])}
              </option>
            ))}
          </Select>
        </FieldRow>
        <div className="flex flex-wrap items-end justify-between gap-3">
          <p className="text-xs text-muted">{draft.description}</p>
          <div className="flex items-center gap-2">
            {dirty && <span className="text-xs text-amber-600">Unsaved changes</span>}
            <Toggle
              checked={draft.alwaysSent ? true : draft.enabled !== false}
              disabled={Boolean(draft.alwaysSent)}
              onChange={(v) => updateDraft({ enabled: v })}
              aria-label="Template enabled"
            />
          </div>
        </div>
      </div>
      <div className="grid grid-cols-1 gap-4 px-5 pb-5">
        <FieldRow label="Display name" hint="Shown in the template picker">
          <Input value={draft.name ?? ""} onChange={(e) => updateDraft({ name: e.target.value })} />
        </FieldRow>
        <FieldRow label="Description" hint="Internal note for admins">
          <Input
            value={draft.description ?? ""}
            onChange={(e) => updateDraft({ description: e.target.value })}
          />
        </FieldRow>
        <FieldRow label="Subject">
          <Input value={draft.subject ?? ""} onChange={(e) => updateDraft({ subject: e.target.value })} />
        </FieldRow>
        {draft.alwaysSent && (
          <p className="text-xs text-muted">
            A security notice: users always get it when their sign-in changes, so it cannot be switched off. You can
            change its wording.
          </p>
        )}
        {draft.carriesSecret ? (
          <p className="rounded-lg border border-ui bg-surface px-3 py-2 text-xs text-muted">
            This email carries a sign-in link or code, so it goes to the user only: no CC or BCC.
          </p>
        ) : (
          <>
        <FieldRow
          label="CC"
          hint="Admin recipients, recipient groups, or individual emails. Groups resolve when the email is sent."
        >
          <TemplateRecipientsPicker
            ariaLabel="CC recipients"
            selected={draft.cc ?? []}
            recipientGroups={recipientGroups}
            adminOptions={adminOptions}
            allUsers={allUsers}
            onChange={(cc) => updateDraft({ cc })}
          />
        </FieldRow>
        <FieldRow
          label="BCC"
          hint="Admin recipients, recipient groups, or individual emails. Groups resolve when the email is sent."
        >
          <TemplateRecipientsPicker
            ariaLabel="BCC recipients"
            selected={draft.bcc ?? []}
            recipientGroups={recipientGroups}
            adminOptions={adminOptions}
            allUsers={allUsers}
            onChange={(bcc) => updateDraft({ bcc })}
          />
        </FieldRow>
          </>
        )}
        <FieldRow
          label="Variables"
          hint="Comma-separated placeholder names used in subject/body (without braces)"
        >
          <Input
            value={variablesToText(draft.variables)}
            onChange={(e) => updateDraft({ variables: parseVariables(e.target.value) })}
            placeholder="userName, actionLink, appName"
          />
        </FieldRow>
        <FieldRow label="Plain text body">
          <Textarea
            className="min-h-40 font-mono text-xs"
            value={draft.bodyText ?? ""}
            onChange={(e) => updateDraft({ bodyText: e.target.value })}
          />
        </FieldRow>
        <FieldRow label="HTML body" hint="Full HTML markup; use {{variable}} placeholders">
          <Textarea
            className="min-h-72 font-mono text-xs"
            value={draft.bodyHtml ?? ""}
            onChange={(e) => updateDraft({ bodyHtml: e.target.value })}
          />
        </FieldRow>
        {draft.variables && draft.variables.length > 0 && (
          <p className="text-xs text-muted">
            Placeholders: {draft.variables.map((v) => `{{${v}}}`).join(", ")}
          </p>
        )}
      </div>
      <div className="flex flex-wrap gap-2 border-t border-ui px-5 py-4">
        <Button onClick={save} disabled={saving || !dirty}>
          {saving ? "Saving…" : "Save template"}
        </Button>
        <Button variant="secondary" onClick={discardChanges} disabled={!dirty}>
          Discard changes
        </Button>
        <Button variant="secondary" onClick={importPlatformDefault} disabled={loadingDefaults}>
          {loadingDefaults ? "Loading…" : "Import platform default"}
        </Button>
        <Button variant="secondary" onClick={resetToPlatformDefault} disabled={resetting}>
          {resetting ? "Resetting…" : "Reset to platform default"}
        </Button>
      </div>
      <div className="border-t border-ui p-5">
        <p className="mb-3 text-sm font-medium text-fg">Add custom template</p>
        <div className="grid grid-cols-1 gap-4 md:grid-cols-[1fr_1fr_auto]">
          <FieldRow label="Template key" hint="e.g. welcome_email">
            <Input
              value={newTemplateKey}
              onChange={(e) => setNewTemplateKey(e.target.value)}
              placeholder="Template key"
            />
          </FieldRow>
          <FieldRow label="Display name">
            <Input
              value={newTemplateName}
              onChange={(e) => setNewTemplateName(e.target.value)}
              placeholder="Template name"
            />
          </FieldRow>
          <div className="flex items-end">
            <Button variant="secondary" onClick={addCustomTemplate}>
              Add template
            </Button>
          </div>
        </div>
      </div>
    </Card>
  );
}
