"use client";

import { useCallback, useState } from "react";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { useToast } from "@/components/ui/Toast";
import { fetchSignInThemes, reloadSignInThemes, type SignInThemes } from "@/lib/api/settings";

/** Platform settings → Appearance: the sign-in page themes installed on this server. */
export function SignInThemesCard() {
  const { toast } = useToast();
  const [data, setData] = useState<SignInThemes | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      setData(await fetchSignInThemes());
    } catch (e) {
      toast(e instanceof Error ? e.message : "Could not load sign-in themes", "error");
    }
  }, [toast]);

  useDeferredEffect(() => {
    load();
  }, [load]);

  const reload = async () => {
    setBusy(true);
    try {
      setData(await reloadSignInThemes());
      toast("Themes reloaded", "success");
    } catch (e) {
      toast(e instanceof Error ? e.message : "Reload failed", "error");
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card padded={false}>
      <CardHeader
        title="Sign-in themes"
        description="Designs for the hosted sign-in, sign-up, password reset, account security and consent pages. Each application picks one under Settings → Appearance."
        action={
          <Button size="sm" variant="secondary" onClick={reload} disabled={busy}>
            {busy ? "Reloading…" : "Reload themes"}
          </Button>
        }
      />
      <div className="space-y-4 p-5 text-sm">
        {data && (
          <div className="grid grid-cols-1 gap-3 md:grid-cols-3">
            <Fact label="Themes folder" value={data.dir ?? "Not set (SECUREONE_THEMES_DIR)"} mono={!!data.dir} />
            <Fact label="Default theme" value={data.defaultTheme} mono />
            <Fact
              label="Changes to theme files"
              value={data.cache ? "Shown after Reload themes" : "Shown on the next page load"}
            />
          </div>
        )}
        {data?.problems.map((p) => (
          <p key={p} className="rounded-md border border-amber-500/40 bg-amber-500/10 px-3 py-2 text-xs text-amber-800 dark:text-amber-200">
            {p}
          </p>
        ))}
        <ul className="divide-y divide-[var(--ui-border)] rounded-lg border border-ui">
          {data?.themes.map((t) => (
            <li key={t.name} className="space-y-1.5 px-4 py-3">
              <div className="flex flex-wrap items-center gap-2">
                <span className="font-mono font-medium">{t.name}</span>
                {t.builtIn && <Badge>built-in</Badge>}
                {t.name === data.defaultTheme && <Badge tone="indigo">default</Badge>}
                {!t.builtIn && t.parent && <span className="text-xs text-muted">based on {t.parent}</span>}
                {t.outdated.length > 0 && <Badge tone="warning">may be out of date</Badge>}
                {t.problems.length > 0 && <Badge tone="danger">{t.problems.length} problem{t.problems.length > 1 ? "s" : ""}</Badge>}
                <a
                  href={`/login.html?theme=${encodeURIComponent(t.name)}`}
                  target="_blank"
                  rel="noreferrer"
                  className="ml-auto text-xs font-medium text-brand hover:underline"
                >
                  Preview ↗
                </a>
              </div>
              {t.description && <p className="text-xs text-muted">{t.description}</p>}
              {!t.builtIn && (
                <p className="text-xs text-muted">
                  {[
                    t.stylesheet && "stylesheet",
                    t.messageLanguages.length > 0 && `texts (${t.messageLanguages.join(", ")})`,
                    t.pages.length > 0 && `${t.pages.length} page${t.pages.length > 1 ? "s" : ""}`,
                    t.templates.length > 0 && `${t.templates.length} server page template${t.templates.length > 1 ? "s" : ""}`,
                    t.assets > 0 && `${t.assets} asset${t.assets > 1 ? "s" : ""}`,
                  ]
                    .filter(Boolean)
                    .join(" · ") || "Nothing changed yet"}
                  {t.madeFor ? ` · made for SecureOne ${t.madeFor}` : ""}
                </p>
              )}
              {t.outdated.map((o) => (
                <p key={o} className="text-xs text-amber-700 dark:text-amber-300">
                  {o}: compare your copy with the built-in page.
                </p>
              ))}
              {t.problems.map((p) => (
                <p key={p} className="text-xs text-red-700 dark:text-red-300">
                  {p}
                </p>
              ))}
            </li>
          ))}
        </ul>
        <p className="text-xs text-muted">
          Add a theme by putting a folder in the themes folder (for example a Docker volume at{" "}
          <code>/opt/secureone/themes</code>). Start from the example in the image:{" "}
          <code>docker cp secureone:/opt/secureone/theme-example ./themes/my-theme</code>. See the Sign-in themes guide.
        </p>
      </div>
    </Card>
  );
}

function Fact({ label, value, mono = false }: { label: string; value: string; mono?: boolean }) {
  return (
    <div className="rounded-lg border border-ui px-3 py-2">
      <div className="text-xs text-muted">{label}</div>
      <div className={mono ? "font-mono text-xs break-all" : "text-xs"}>{value}</div>
    </div>
  );
}
