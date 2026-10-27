"use client";

import { useCallback, useState } from "react";
import { useDialog } from "@/components/ui/Dialogs";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { LocalTime } from "@/components/ui/LocalTime";
import { useToast } from "@/components/ui/Toast";
import { browserApiFetch as apiFetch } from "@/lib/api/browser-client";

type SigningKeys = {
  source: "stored" | "environment";
  currentKid: string;
  currentSince: string;
  nextKid: string | null;
  previousKid: string | null;
  previousRetiredAt: string | null;
};

const PATH = "/api/admin/v1/settings/signing-keys";

/** Platform settings › OAuth tokens: rotate the key that signs tokens, and retire the old one. */
export function SigningKeysCard() {
  const { toast } = useToast();
  const [keys, setKeys] = useState<SigningKeys | null>(null);
  const dialog = useDialog();
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      setKeys(await apiFetch<SigningKeys>(PATH));
    } catch (e) {
      toast(e instanceof Error ? e.message : "Could not load signing keys", "error");
    }
  }, [toast]);

  useDeferredEffect(() => {
    load();
  }, [load]);

  async function run(action: "rotate" | "retire-previous", title: string, message: string, done: string) {
    if (!(await dialog.confirm({ title, message, confirmLabel: action === "rotate" ? "Rotate key" : "Retire key" }))) return;
    setBusy(true);
    try {
      setKeys(await apiFetch<SigningKeys>(`${PATH}/${action}`, { method: "POST" }));
      toast(done, "success");
    } catch (e) {
      toast(e instanceof Error ? e.message : "That did not work", "error");
    } finally {
      setBusy(false);
    }
  }

  if (!keys) return null;
  const fromEnv = keys.source === "environment";
  return (
    <Card padded={false}>
      <CardHeader
        title="Signing keys"
        description="The key that signs access and ID tokens. The next key is always published in JWKS ahead of time, so apps already have it when you rotate."
      />
      <dl className="grid gap-3 px-5 pb-3 text-sm sm:grid-cols-3">
        <div>
          <dt className="text-xs text-muted">Signing now</dt>
          <dd className="font-mono text-xs text-ui">{keys.currentKid}</dd>
          {keys.currentSince && (
            <dd className="text-xs text-muted">
              since <LocalTime value={keys.currentSince} format="date" />
            </dd>
          )}
        </div>
        <div>
          <dt className="text-xs text-muted">Next (published)</dt>
          <dd className="font-mono text-xs text-ui">{keys.nextKid ?? "—"}</dd>
        </div>
        <div>
          <dt className="text-xs text-muted">Previous (still accepted)</dt>
          <dd className="font-mono text-xs text-ui">{keys.previousKid ?? "—"}</dd>
          {keys.previousRetiredAt && (
            <dd className="text-xs text-muted">
              replaced <LocalTime value={keys.previousRetiredAt} format="datetime" />
            </dd>
          )}
        </div>
      </dl>
      <div className="flex flex-wrap items-center gap-2 border-t border-ui px-5 py-4">
        {fromEnv ? (
          <p className="text-xs text-muted">
            The key comes from SECUREONE_JWK_PRIVATE_KEY_PEM. Rotate it there: set the new key, and the old one as
            SECUREONE_JWK_PREVIOUS_PRIVATE_KEY_PEM until its tokens expire.
          </p>
        ) : (
          <>
            <Button
              disabled={busy}
              onClick={() =>
                void run(
                  "rotate",
                  "Rotate the signing key?",
                  "New tokens are signed with the next key at once. Tokens already issued keep working until you retire the previous key.",
                  "Signing key rotated",
                )
              }
            >
              Rotate signing key
            </Button>
            {keys.previousKid && (
              <Button
                variant="secondary"
                disabled={busy}
                onClick={() =>
                  void run(
                    "retire-previous",
                    "Retire the previous key?",
                    "Tokens it signed stop working. Do this once they have expired (after the longest token lifetime).",
                    "Previous key retired",
                  )
                }
              >
                Retire previous key
              </Button>
            )}
          </>
        )}
      </div>
    </Card>
  );
}
