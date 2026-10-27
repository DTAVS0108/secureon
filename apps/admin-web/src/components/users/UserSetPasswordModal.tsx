"use client";

import { useActionState, useEffect, useState } from "react";
import { PasswordPolicyChecklist } from "@/components/auth/PasswordPolicyChecklist";
import { Modal } from "@/components/ui/Modal";
import { FieldRow, Input } from "@/components/ui/Field";
import { FormSection } from "@/components/forms/FormSection";
import { userAdminSetPasswordAction, type FormState } from "@/lib/actions";
import { loadAuthSettingsAction } from "@/lib/actions/auth-settings";
import { FormActions, FormError, useCloseOnSuccess } from "@/components/forms/form-utils";
import type { PasswordPolicy } from "@/lib/types";

const initial: FormState = { ok: false };

export function UserSetPasswordModal({
  userId,
  applicationId,
  triggerLabel = "Set password",
  onSuccess,
}: {
  userId: string;
  applicationId?: string;
  triggerLabel?: string;
  onSuccess?: () => void | Promise<void>;
}) {
  return (
    <Modal
      triggerLabel={triggerLabel}
      triggerVariant="secondary"
      triggerSize="sm"
      title="Set password"
      description="Sets a new password now (policy rules apply). Give it to the user by a separate, secure channel."
    >
      {(close) => (
        <SetPasswordForm
          userId={userId}
          applicationId={applicationId}
          close={close}
          onSuccess={onSuccess}
        />
      )}
    </Modal>
  );
}

function SetPasswordForm({
  userId,
  applicationId,
  close,
  onSuccess,
}: {
  userId: string;
  applicationId?: string;
  close: () => void;
  onSuccess?: () => void | Promise<void>;
}) {
  const [state, formAction, pending] = useActionState(userAdminSetPasswordAction, initial);
  const [password, setPassword] = useState("");
  const [policy, setPolicy] = useState<PasswordPolicy | null>(null);

  useCloseOnSuccess(state, close, async () => {
    await onSuccess?.();
  });

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const settings = await loadAuthSettingsAction(applicationId);
      if (!cancelled) setPolicy(settings.passwordPolicy);
    })();
    return () => {
      cancelled = true;
    };
  }, [applicationId]);

  return (
    <form action={formAction} className="space-y-4 pb-2">
      <input type="hidden" name="id" value={userId} />
      {applicationId && <input type="hidden" name="applicationId" value={applicationId} />}
      <FormError state={state} />
      <FormSection title="New password">
        <FieldRow
          label="Password"
          hint={policy ? "Must meet the password policy below" : "Loading password policy…"}
        >
          <Input
            name="password"
            type="password"
            required
            minLength={policy?.minLength ?? 12}
            autoComplete="new-password"
            disabled={pending}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </FieldRow>
        {policy && <PasswordPolicyChecklist policy={policy} password={password} className="mt-2" />}
        <label className="mt-3 flex items-start gap-2 text-sm text-ui">
          <input type="checkbox" name="temporary" defaultChecked className="mt-0.5 rounded border-ui" />
          <span>
            Temporary: the user must choose their own password the first time they sign in
            <span className="block text-xs text-muted">Recommended. Only the user then knows their password.</span>
          </span>
        </label>
      </FormSection>
      <FormActions pending={pending} close={close} submitLabel="Set password" sticky />
    </form>
  );
}
