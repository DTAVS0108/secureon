"use client";

import { useState } from "react";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { FieldRow, Input, Select } from "@/components/ui/Field";
import { Toggle } from "@/components/ui/Toggle";
import { savePlatformSmtpAction, saveSmtpAction } from "@/lib/actions/settings";
import type { SmtpSettings } from "@/lib/api/settings";

type SmtpSettingsCardProps =
  | {
      scope: "platform";
      smtp: SmtpSettings;
      onSaved: (next: SmtpSettings) => void;
      applicationId?: never;
    }
  | {
      scope?: "application";
      applicationId: string;
      smtp: SmtpSettings;
      onSaved: (next: SmtpSettings) => void;
    };

export function SmtpSettingsCard(props: SmtpSettingsCardProps) {
  const { smtp, onSaved } = props;
  const isPlatform = props.scope === "platform";
  const applicationId = props.scope === "application" || props.scope === undefined ? props.applicationId : undefined;

  const [draft, setDraft] = useState<SmtpSettings>(smtp);
  const [password, setPassword] = useState("");
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  useDeferredEffect(() => {
    setDraft(smtp);
  }, [smtp]);

  async function save() {
    setSaving(true);
    setMessage(null);
    const payload: SmtpSettings = { ...draft };
    if (password.trim()) {
      payload.password = password;
    }
    const result = isPlatform
      ? await savePlatformSmtpAction(payload)
      : await saveSmtpAction(applicationId!, payload);
    if (result.ok && result.smtp) {
      onSaved(result.smtp);
      setPassword("");
      setMessage("SMTP settings saved.");
    } else {
      setMessage(result.error ?? "Save failed");
    }
    setSaving(false);
  }

  return (
    <Card padded={false}>
      <CardHeader
        title="SMTP server"
        description={
          isPlatform
            ? "Platform operator alert delivery (password encrypted at rest)."
            : "Application SMTP for user and app admin emails (password encrypted at rest)."
        }
      />
      {message && <p className="px-5 text-sm text-brand">{message}</p>}
      <div className="grid grid-cols-1 gap-4 p-5 md:grid-cols-2">
        <FieldRow label="Host">
          <Input
            value={draft.host ?? ""}
            onChange={(e) => setDraft({ ...draft, host: e.target.value })}
            placeholder="SMTP host"
          />
        </FieldRow>
        <FieldRow label="Port">
          <Input
            type="number"
            value={draft.port ?? 465}
            onChange={(e) => setDraft({ ...draft, port: Number(e.target.value) || 465 })}
          />
        </FieldRow>
        <FieldRow label="Security">
          <Select
            value={draft.security ?? "ssl"}
            onChange={(e) =>
              setDraft({ ...draft, security: e.target.value as SmtpSettings["security"] })
            }
          >
            <option value="ssl">SSL (port 465)</option>
            <option value="starttls">STARTTLS (port 587)</option>
            <option value="none">None</option>
          </Select>
        </FieldRow>
        <FieldRow label="Username">
          <Input
            value={draft.username ?? ""}
            onChange={(e) => setDraft({ ...draft, username: e.target.value })}
            placeholder="Username"
          />
        </FieldRow>
        <FieldRow
          label="Password"
          hint={draft.passwordConfigured ? "Leave blank to keep current password" : "SMTP password or app password"}
        >
          <Input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder={draft.passwordConfigured ? "••••••••" : "App password"}
          />
        </FieldRow>
        <FieldRow label="SMTP authentication">
          <div className="flex h-[38px] items-center">
            <Toggle
              checked={draft.authEnabled !== false}
              onChange={(v) => setDraft({ ...draft, authEnabled: v })}
              aria-label="SMTP authentication"
            />
          </div>
        </FieldRow>
      </div>
      <div className="border-t border-ui p-5">
        <Button onClick={save} disabled={saving}>
          {saving ? "Saving…" : "Save SMTP"}
        </Button>
      </div>
    </Card>
  );
}
