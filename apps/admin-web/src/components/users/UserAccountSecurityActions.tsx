"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { ActionNotice } from "@/components/users/SecuritySection";
import { setUserStatusApi, unlockUserApi } from "@/lib/api/users";
import { statusTone } from "@/lib/status";
import type { User } from "@/lib/types";

export function UserAccountSecurityActions({
  user,
  onChanged,
}: {
  user: User;
  onChanged?: () => void | Promise<void>;
}) {
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<{ text: string; tone: "success" | "error" } | null>(null);

  async function run(label: string, action: () => Promise<unknown>) {
    setBusy(true);
    setNotice(null);
    try {
      await action();
      setNotice({ text: label, tone: "success" });
      if (onChanged) await onChanged();
      else router.refresh();
    } catch (e) {
      setNotice({
        text: e instanceof Error ? e.message : "Action failed.",
        tone: "error",
      });
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center gap-2">
        <Badge tone={statusTone(user.status)} dot className="capitalize">
          {user.status}
        </Badge>
        {user.locked && <Badge tone="danger">Locked</Badge>}
        {user.emailVerified ? (
          <Badge tone="success">Email verified</Badge>
        ) : (
          <Badge tone="warning">Email unverified</Badge>
        )}
      </div>
      <dl className="grid gap-2 text-xs sm:grid-cols-2">
        <div>
          <dt className="text-muted">Failed login attempts</dt>
          <dd className="font-medium text-ui">{user.failedLoginCount ?? 0}</dd>
        </div>
        <div>
          <dt className="text-muted">{user.email ? "Sign-in email" : "Signs in with"}</dt>
          <dd className="truncate font-medium text-ui">{user.email || user.phone}</dd>
        </div>
      </dl>
      <div className="flex flex-wrap gap-2">
        {user.status === "active" ? (
          <Button
            type="button"
            variant="danger"
            size="sm"
            disabled={busy}
            onClick={() => run("Account suspended.", () => setUserStatusApi(user.id, "suspended"))}
          >
            Suspend account
          </Button>
        ) : user.status !== "disabled" ? (
          <Button
            type="button"
            variant="secondary"
            size="sm"
            disabled={busy}
            onClick={() => run("Account activated.", () => setUserStatusApi(user.id, "active"))}
          >
            Activate account
          </Button>
        ) : null}
        {user.locked && (
          <Button
            type="button"
            variant="primary"
            size="sm"
            disabled={busy}
            onClick={() => run("Account unlocked.", () => unlockUserApi(user.id))}
          >
            Unlock account
          </Button>
        )}
      </div>
      {notice && <ActionNotice message={notice.text} tone={notice.tone} />}
    </div>
  );
}
