"use client";

import { useState } from "react";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { FieldRow, Input } from "@/components/ui/Field";
import { Toggle } from "@/components/ui/Toggle";
import {
  saveApplicationSms,
  saveSmsSettings,
  sendApplicationTestSms,
  sendPlatformTestSms,
  type SmsSettings,
} from "@/lib/api/verification-settings";
import { ensureApplicationPolicyScope } from "@/lib/api/ensure-application-policy";
import { SMS_HELP } from "@/lib/verification-settings-help";

type SmsProviderSettingsCardProps =
  | {
      scope: "platform";
      sms: SmsSettings;
      onSaved: (next: SmsSettings) => void;
      applicationId?: never;
    }
  | {
      scope?: "application";
      applicationId: string;
      sms: SmsSettings;
      onSaved: (next: SmsSettings) => void;
    };

const PROVIDER_LABELS: Record<string, string> = {
  log: "Log to console (dev)",
  twilio: "Twilio",
  aws_sns: "AWS SNS",
  msg91: "MSG91",
};

export function SmsProviderSettingsCard(props: SmsProviderSettingsCardProps) {
  const { sms, onSaved } = props;
  const isPlatform = props.scope === "platform";
  const applicationId = props.scope === "application" || props.scope === undefined ? props.applicationId : undefined;

  const [draft, setDraft] = useState<SmsSettings>(sms);
  const [twilioAuthToken, setTwilioAuthToken] = useState("");
  const [awsSecret, setAwsSecret] = useState("");
  const [msg91ApiKey, setMsg91ApiKey] = useState("");
  const [testTo, setTestTo] = useState("");
  const [saving, setSaving] = useState(false);
  const [testing, setTesting] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  useDeferredEffect(() => {
    setDraft(sms);
  }, [sms]);

  const provider = draft.provider ?? "log";
  const twilio = draft.twilio ?? {};
  const aws = draft.aws_sns ?? {};
  const msg91 = draft.msg91 ?? {};

  async function save() {
    setSaving(true);
    setMessage(null);
    try {
      const payload: SmsSettings = {
        enabled: draft.enabled,
        provider: draft.provider,
        twilio: {
          accountSid: twilio.accountSid,
          fromNumber: twilio.fromNumber,
          ...(twilioAuthToken.trim() ? { authToken: twilioAuthToken } : {}),
        },
        aws_sns: {
          region: aws.region,
          accessKeyId: aws.accessKeyId,
          senderId: aws.senderId,
          ...(awsSecret.trim() ? { secretAccessKey: awsSecret } : {}),
        },
        msg91: {
          senderId: msg91.senderId,
          templateId: msg91.templateId,
          ...(msg91ApiKey.trim() ? { apiKey: msg91ApiKey } : {}),
        },
      };
      if (!isPlatform && applicationId) {
        await ensureApplicationPolicyScope(applicationId, "sms");
      }
      const saved = isPlatform
        ? await saveSmsSettings(payload)
        : await saveApplicationSms(applicationId!, payload);
      onSaved(saved);
      setTwilioAuthToken("");
      setAwsSecret("");
      setMsg91ApiKey("");
      setMessage("SMS settings saved.");
    } catch (e) {
      setMessage(e instanceof Error ? e.message : "Save failed");
    } finally {
      setSaving(false);
    }
  }

  async function sendTest() {
    if (!testTo.trim()) {
      setMessage("Enter a phone number in E.164 format (e.g. +918851512349).");
      return;
    }
    setTesting(true);
    setMessage(null);
    try {
      if (isPlatform) {
        await sendPlatformTestSms(testTo.trim());
      } else if (applicationId) {
        await sendApplicationTestSms(applicationId, testTo.trim());
      }
      setMessage(`Test SMS sent to ${testTo.trim()}.`);
    } catch (e) {
      setMessage(e instanceof Error ? e.message : "Test send failed");
    } finally {
      setTesting(false);
    }
  }

  return (
    <Card padded={false}>
      <CardHeader
        title="SMS provider"
        description={
          isPlatform
            ? "Platform default SMS for OTP when an application has no override."
            : "Per-application SMS provider for login and signup OTP codes."
        }
        info={SMS_HELP.section}
      />
      {message && <p className="px-5 text-sm text-brand">{message}</p>}
      <div className="space-y-4 p-5">
        <FieldRow label="Enabled" info={SMS_HELP.enabled}>
          <Toggle
            checked={Boolean(draft.enabled)}
            onChange={(checked) => setDraft({ ...draft, enabled: checked })}
          />
        </FieldRow>
        <FieldRow label="Provider" info={SMS_HELP.provider}>
          <select
            className="w-full rounded-md border border-ui bg-surface px-3 py-2 text-sm"
            value={provider}
            onChange={(e) => setDraft({ ...draft, provider: e.target.value })}
          >
            {(draft.availableProviders ?? ["log", "twilio", "aws_sns", "msg91"]).map((id) => (
              <option key={id} value={id}>
                {PROVIDER_LABELS[id] ?? id}
              </option>
            ))}
          </select>
        </FieldRow>

        {provider === "twilio" && (
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
            <FieldRow label="Account SID" info={SMS_HELP.twilioAccountSid}>
              <Input
                value={twilio.accountSid ?? ""}
                onChange={(e) =>
                  setDraft({ ...draft, twilio: { ...twilio, accountSid: e.target.value } })
                }
              />
            </FieldRow>
            <FieldRow label="From number" info={SMS_HELP.twilioFromNumber}>
              <Input
                value={twilio.fromNumber ?? ""}
                onChange={(e) =>
                  setDraft({ ...draft, twilio: { ...twilio, fromNumber: e.target.value } })
                }
                placeholder="+15551234567"
              />
            </FieldRow>
            <FieldRow
              label="Auth token"
              info={SMS_HELP.twilioAuthToken}
              hint={twilio.authTokenConfigured ? "Leave blank to keep current token" : undefined}
            >
              <Input
                type="password"
                value={twilioAuthToken}
                onChange={(e) => setTwilioAuthToken(e.target.value)}
                placeholder={twilio.authTokenConfigured ? "••••••••" : "Twilio auth token"}
              />
            </FieldRow>
          </div>
        )}

        {provider === "aws_sns" && (
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
            <FieldRow label="Region" info={SMS_HELP.awsRegion}>
              <Input
                value={aws.region ?? "ap-south-1"}
                onChange={(e) =>
                  setDraft({ ...draft, aws_sns: { ...aws, region: e.target.value } })
                }
              />
            </FieldRow>
            <FieldRow label="Access key ID" info={SMS_HELP.awsAccessKeyId}>
              <Input
                value={aws.accessKeyId ?? ""}
                onChange={(e) =>
                  setDraft({ ...draft, aws_sns: { ...aws, accessKeyId: e.target.value } })
                }
              />
            </FieldRow>
            <FieldRow
              label="Secret access key"
              info={SMS_HELP.awsSecret}
              hint={aws.secretAccessKeyConfigured ? "Leave blank to keep current secret" : undefined}
            >
              <Input
                type="password"
                value={awsSecret}
                onChange={(e) => setAwsSecret(e.target.value)}
                placeholder={aws.secretAccessKeyConfigured ? "••••••••" : "AWS secret key"}
              />
            </FieldRow>
            <FieldRow label="Sender ID (optional)" info={SMS_HELP.awsSenderId}>
              <Input
                value={aws.senderId ?? ""}
                onChange={(e) =>
                  setDraft({ ...draft, aws_sns: { ...aws, senderId: e.target.value } })
                }
              />
            </FieldRow>
          </div>
        )}

        {provider === "msg91" && (
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
            <FieldRow label="Sender ID" info={SMS_HELP.msg91SenderId}>
              <Input
                value={msg91.senderId ?? ""}
                onChange={(e) =>
                  setDraft({ ...draft, msg91: { ...msg91, senderId: e.target.value } })
                }
              />
            </FieldRow>
            <FieldRow label="Template ID" info={SMS_HELP.msg91TemplateId}>
              <Input
                value={msg91.templateId ?? ""}
                onChange={(e) =>
                  setDraft({ ...draft, msg91: { ...msg91, templateId: e.target.value } })
                }
              />
            </FieldRow>
            <FieldRow
              label="API key"
              info={SMS_HELP.msg91ApiKey}
              hint={msg91.apiKeyConfigured ? "Leave blank to keep current key" : undefined}
            >
              <Input
                type="password"
                value={msg91ApiKey}
                onChange={(e) => setMsg91ApiKey(e.target.value)}
                placeholder={msg91.apiKeyConfigured ? "••••••••" : "MSG91 auth key"}
              />
            </FieldRow>
          </div>
        )}

        {provider === "log" && (
          <p className="text-sm text-muted">
            OTP codes are written to the auth-server log — use MailHog-style workflows for email and logs for SMS in
            local dev.
          </p>
        )}

        <div className="border-t border-ui pt-4">
          <FieldRow label="Test phone (E.164)" info={SMS_HELP.testPhone} hint="Send a test message using the saved provider">
            <div className="flex gap-2">
              <Input
                value={testTo}
                onChange={(e) => setTestTo(e.target.value)}
                placeholder="+918851512349"
              />
              <Button type="button" variant="secondary" onClick={sendTest} disabled={testing || saving}>
                {testing ? "Sending…" : "Send test"}
              </Button>
            </div>
          </FieldRow>
        </div>
      </div>
      <div className="border-t border-ui p-5">
        <Button onClick={save} disabled={saving}>
          {saving ? "Saving…" : "Save SMS"}
        </Button>
      </div>
    </Card>
  );
}
