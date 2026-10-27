"use client";

import { useCallback, useState } from "react";
import { useDialog } from "@/components/ui/Dialogs";
import { Button } from "@/components/ui/Button";
import { LocalTime } from "@/components/ui/LocalTime";
import { ActionNotice } from "@/components/users/SecuritySection";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";
import { fetchUserSessions, signOutUserApi, type UserActiveSessions } from "@/lib/api/users";

/**
 * Where the user is signed in, and signing them out. In an application's console (`applicationId`): that
 * application's grants, and signing out ends only those. Elsewhere: browser sessions and every application.
 */
export function UserSessionsCard({ userId, applicationId }: { userId: string; applicationId?: string }) {
  const [data, setData] = useState<UserActiveSessions | null>(null);
  const dialog = useDialog();
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<{ text: string; tone: "success" | "error" } | null>(null);

  const reload = useCallback(async () => {
    try {
      setData(await fetchUserSessions(userId, applicationId));
    } catch (e) {
      setNotice({ text: e instanceof Error ? e.message : "Could not load sessions.", tone: "error" });
    }
  }, [userId, applicationId]);

  useDeferredEffect(() => {
    void reload();
  }, [reload]);

  async function signOut() {
    const where = applicationId ? "out of this application" : "out everywhere (every device and application)";
    if (
      !(await dialog.confirm({
        title: `Sign this user ${where}?`,
        message: "Their account stays active; they can sign in again.",
        confirmLabel: "Sign out",
      }))
    )
      return;
    setBusy(true);
    setNotice(null);
    try {
      const r = await signOutUserApi(userId, applicationId);
      setNotice({
        text: applicationId
          ? `Signed out of this application (${r.applicationGrantsEnded} grant(s) ended).`
          : `Signed out: ${r.browserSessionsEnded} browser session(s) and ${r.applicationGrantsEnded} application grant(s) ended.`,
        tone: "success",
      });
      await reload();
    } catch (e) {
      setNotice({ text: e instanceof Error ? e.message : "Could not sign the user out.", tone: "error" });
    } finally {
      setBusy(false);
    }
  }

  const browser = data?.browserSessions ?? [];
  const grants = data?.applicationGrants ?? [];
  return (
    <div className="space-y-3">
      {!applicationId && (
        <div>
          <p className="text-xs font-medium text-ui">Browser sessions at SecureOne</p>
          {browser.length === 0 ? (
            <p className="text-xs text-muted">{data ? "None." : "Loading…"}</p>
          ) : (
            <ul className="mt-1 space-y-1 text-xs text-muted">
              {browser.map((s) => (
                <li key={s.id}>
                  Since <LocalTime value={s.createdAt} format="datetime" /> · last used{" "}
                  <LocalTime value={s.lastAccessedAt} format="datetime" />
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
      <div>
        <p className="text-xs font-medium text-ui">{applicationId ? "Signed in to this application" : "Applications"}</p>
        {grants.length === 0 ? (
          <p className="text-xs text-muted">{data ? "None." : "Loading…"}</p>
        ) : (
          <ul className="mt-1 space-y-1 text-xs text-muted">
            {grants.map((g) => (
              <li key={g.id}>
                <span className="font-medium text-ui">{g.name ?? g.clientId}</span> <code className="font-mono">{g.clientId}</code>
                {g.lastTokenAt && (
                  <>
                    {" "}· last token <LocalTime value={g.lastTokenAt} format="datetime" />
                  </>
                )}
                {g.refreshTokenExpiresAt && (
                  <>
                    {" "}· can refresh until <LocalTime value={g.refreshTokenExpiresAt} format="datetime" />
                  </>
                )}
              </li>
            ))}
          </ul>
        )}
      </div>
      <Button type="button" variant="danger" size="sm" disabled={busy} onClick={() => void signOut()}>
        {applicationId ? "Sign out of this application" : "Sign out everywhere"}
      </Button>
      {notice && <ActionNotice message={notice.text} tone={notice.tone} />}
    </div>
  );
}
