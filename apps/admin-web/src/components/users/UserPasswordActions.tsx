"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";
import { UserSetPasswordModal } from "@/components/users/UserSetPasswordModal";
import { ActionNotice } from "@/components/users/SecuritySection";
import {
  removeUserPasswordApi,
  sendUserPasswordResetEmailApi,
  sendUserSetPasswordInviteEmailApi,
} from "@/lib/api/users";

export function UserPasswordActions({
  userId,
  userEmail,
  hasPassword,
  applicationId,
  onChanged,
}: {
  userId: string;
  userEmail: string;
  hasPassword: boolean;
  applicationId?: string;
  onChanged?: () => void | Promise<void>;
}) {
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  const [confirmRemove, setConfirmRemove] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);

  async function run(label: string, action: () => Promise<unknown>) {
    setBusy(true);
    setNotice(null);
    try {
      await action();
      setNotice(label);
      if (onChanged) await onChanged();
      else router.refresh();
    } catch (e) {
      setNotice(e instanceof Error ? e.message : "Could not complete action.");
    } finally {
      setBusy(false);
    }
  }

  async function handleRemovePassword() {
    setConfirmRemove(false);
    await run("Password removed. User must set a new password via email or admin.", () =>
      removeUserPasswordApi(userId, applicationId),
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-2">
        <span className="text-xs text-muted">Password credential</span>
        {hasPassword ? (
          <Badge tone="success">Set</Badge>
        ) : (
          <Badge tone="warning">Not set</Badge>
        )}
      </div>

      <div className="space-y-2 rounded-lg border border-ui bg-ui-elevated/30 px-3 py-3">
        <p className="text-xs font-medium text-ui">Password emails</p>
        <p className="text-xs text-muted">
          Messages go to <strong className="font-medium text-ui">{userEmail}</strong>. Check MailHog
          in dev or your SMTP inbox in production.
        </p>
        <div className="flex flex-wrap gap-2 pt-1">
          <Button
            type="button"
            variant="primary"
            size="sm"
            disabled={busy}
            onClick={() =>
              run(
                "Password reset email sent. User can choose a new password from the link.",
                () => sendUserPasswordResetEmailApi(userId, applicationId),
              )
            }
          >
            Send password reset email
          </Button>
          <Button
            type="button"
            variant="secondary"
            size="sm"
            disabled={busy}
            onClick={() =>
              run(
                "Set-password email sent. User can create their password from the invite link.",
                () => sendUserSetPasswordInviteEmailApi(userId, applicationId),
              )
            }
          >
            Send set-password email
          </Button>
        </div>
        <ul className="mt-2 list-inside list-disc text-[11px] text-faint">
          <li>
            <span className="text-muted">Reset</span> — for users who already have a password
          </li>
          <li>
            <span className="text-muted">Set-password</span> — invite / first-time onboarding link
          </li>
        </ul>
      </div>

      <div className="space-y-2">
        <p className="text-xs font-medium text-ui">Admin password actions</p>
        <div className="flex flex-wrap gap-2">
          <UserSetPasswordModal
            userId={userId}
            applicationId={applicationId}
            triggerLabel={hasPassword ? "Set new password" : "Set password now"}
            onSuccess={async () => {
              setNotice("Password updated by admin.");
              await onChanged?.();
            }}
          />
          {hasPassword && (
            <Button
              type="button"
              variant="danger"
              size="sm"
              disabled={busy}
              onClick={() => setConfirmRemove(true)}
            >
              Remove password
            </Button>
          )}
        </div>
        <p className="text-xs text-muted">
          Setting a password here applies immediately (policy rules apply). Removing it forces email
          or admin setup before password sign-in works again.
        </p>
      </div>

      {notice && (
        <ActionNotice
          message={notice}
          tone={notice.includes("Could not") || notice.includes("failed") ? "error" : "success"}
        />
      )}

      {confirmRemove && (
        <div className="fixed inset-0 z-100 flex items-center justify-center p-4">
          <div
            className="fixed inset-0 bg-black/40 backdrop-blur-sm"
            onClick={() => !busy && setConfirmRemove(false)}
          />
          <div
            role="alertdialog"
            aria-modal="true"
            className="relative z-10 w-full max-w-md rounded-2xl border border-ui bg-[var(--background)] p-6 shadow-2xl"
          >
            <h2 className="text-base font-semibold text-ui">Remove password?</h2>
            <p className="mt-2 text-sm text-muted">
              Clears the stored password hash. Send a set-password or reset email afterward, or set a
              new password as admin.
            </p>
            <div className="mt-6 flex justify-end gap-2">
              <Button
                type="button"
                variant="secondary"
                disabled={busy}
                onClick={() => setConfirmRemove(false)}
              >
                Cancel
              </Button>
              <Button
                type="button"
                variant="danger"
                disabled={busy}
                onClick={() => void handleRemovePassword()}
              >
                {busy ? "Removing…" : "Remove password"}
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
