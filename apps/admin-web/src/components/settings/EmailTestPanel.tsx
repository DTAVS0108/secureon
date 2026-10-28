"use client";

import { useMemo, useState } from "react";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { FieldRow, Input, Select, Textarea } from "@/components/ui/Field";
import { TemplateRecipientsPicker } from "@/components/settings/TemplateRecipientsPicker";
import {
  sendTestEmailAction,
  type AdminRecipientOption,
  type RecipientUserOption,
} from "@/lib/actions/settings";
import type { EmailTemplatesMap, RecipientGroups } from "@/lib/api/settings";

export function EmailTestPanel({
  applicationId,
  templates,
  recipientGroups,
  adminOptions,
  allUsers,
}: {
  applicationId: string;
  templates: EmailTemplatesMap;
  recipientGroups: RecipientGroups;
  adminOptions: AdminRecipientOption[];
  allUsers: RecipientUserOption[];
}) {
  const templateKeys = useMemo(() => Object.keys(templates), [templates]);
  const [to, setTo] = useState("");
  const [cc, setCc] = useState<string[]>([]);
  const [bcc, setBcc] = useState<string[]>([]);
  const [templateKey, setTemplateKey] = useState(templateKeys.includes("test") ? "test" : templateKeys[0] ?? "test");
  const [customDataJson, setCustomDataJson] = useState('{\n  "customMessage": "Hello from SecureOne"\n}');
  const [sending, setSending] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  async function send() {
    setSending(true);
    setMessage(null);
    let customData: Record<string, string> | undefined;
    try {
      customData = customDataJson.trim() ? (JSON.parse(customDataJson) as Record<string, string>) : undefined;
    } catch {
      setMessage("Custom data must be valid JSON object.");
      setSending(false);
      return;
    }
    const result = await sendTestEmailAction(applicationId, {
      to,
      cc,
      bcc,
      templateKey,
      customData,
    });
    setMessage(
      result.ok
        ? `Test email sent to ${to}.`
        : (result.error ?? "Send failed"),
    );
    setSending(false);
  }

  return (
    <Card padded={false}>
      <CardHeader
        title="Send test email"
        description='Uses this application&apos;s SMTP and templates. To hide this panel, disable "Email test console" under the Feature flags tab.'
      />
      {message && <p className="px-5 text-sm text-brand">{message}</p>}
      <div className="grid grid-cols-1 gap-4 p-5 md:grid-cols-2">
        <FieldRow label="To">
          <Input type="email" value={to} onChange={(e) => setTo(e.target.value)} placeholder="Email address" />
        </FieldRow>
        <FieldRow label="Template">
          <Select value={templateKey} onChange={(e) => setTemplateKey(e.target.value)}>
            {templateKeys.map((key) => (
              <option key={key} value={key}>
                {templates[key]?.name ?? key}
              </option>
            ))}
          </Select>
        </FieldRow>
        <FieldRow label="CC" hint="Optional — admin recipients, groups, or custom emails">
          <TemplateRecipientsPicker
            ariaLabel="Test email CC recipients"
            selected={cc}
            recipientGroups={recipientGroups}
            adminOptions={adminOptions}
            allUsers={allUsers}
            onChange={setCc}
          />
        </FieldRow>
        <FieldRow label="BCC" hint="Optional — admin recipients, groups, or custom emails">
          <TemplateRecipientsPicker
            ariaLabel="Test email BCC recipients"
            selected={bcc}
            recipientGroups={recipientGroups}
            adminOptions={adminOptions}
            allUsers={allUsers}
            onChange={setBcc}
          />
        </FieldRow>
        <div className="md:col-span-2">
          <FieldRow label="Custom template data" hint="JSON key/value pairs for {{placeholders}}">
            <Textarea
              className="min-h-24 font-mono text-xs"
              value={customDataJson}
              onChange={(e) => setCustomDataJson(e.target.value)}
            />
          </FieldRow>
        </div>
      </div>
      <div className="border-t border-ui p-5">
        <Button onClick={send} disabled={!to || sending}>
          {sending ? "Sending…" : "Send test"}
        </Button>
      </div>
    </Card>
  );
}
