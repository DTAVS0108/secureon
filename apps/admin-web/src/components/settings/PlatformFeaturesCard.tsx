"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Card, CardHeader } from "@/components/ui/Card";
import { useToast } from "@/components/ui/Toast";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";
import { fetchPlatformFeatures, savePlatformFeatures, type PlatformFeatures } from "@/lib/api/settings";
import { cn } from "@/lib/cn";

const FEATURES: { key: keyof PlatformFeatures; label: string; description: string }[] = [
  {
    key: "connectApp",
    label: "Connect an app",
    description:
      "The guided wizard that registers an app with secure defaults and gives ready-to-paste setup code, for platform operators and tenant operators who manage clients. When off, its links are hidden and the page is not available; Register application & OAuth client and Add OAuth client still work.",
  },
  {
    key: "clientSetupCode",
    label: "Client setup code",
    description:
      "The Setup code section on each OAuth client's page: ready-to-use code for the client's framework, rebuilt from its settings (never the client secret). When off, the section is hidden.",
  },
  {
    key: "configAsCode",
    label: "Configuration as code",
    description:
      "Export tenants to files and preview or apply configuration files from the console (Configuration as code in the sidebar and its API). When off, the page and the API are not available. Files applied at start-up with SECUREONE_CONFIG_IMPORT are not affected.",
  },
];

/** Platform-only switches (Platform settings → Feature flags); applications cannot override them. */
export function PlatformFeaturesCard() {
  const router = useRouter();
  const { toast } = useToast();
  const [features, setFeatures] = useState<PlatformFeatures | null>(null);
  const [saving, setSaving] = useState<keyof PlatformFeatures | null>(null);
  const [error, setError] = useState<string | null>(null);

  useDeferredEffect(() => {
    fetchPlatformFeatures()
      .then(setFeatures)
      .catch((e) => setError(e instanceof Error ? e.message : "Could not load platform features"));
  }, []);

  async function toggle(key: keyof PlatformFeatures, enabled: boolean) {
    setSaving(key);
    try {
      const next = await savePlatformFeatures({ [key]: enabled });
      setFeatures(next);
      toast(enabled ? "Turned on" : "Turned off", "success");
      router.refresh(); // update the sidebar
    } catch (e) {
      toast(e instanceof Error ? e.message : "Could not save", "error");
    } finally {
      setSaving(null);
    }
  }

  return (
    <Card padded={false}>
      <CardHeader
        title="Platform features"
        description="Console features for platform operators. Applications cannot override these."
      />
      <div className="divide-y divide-[var(--ui-border)]">
        {error && <p className="px-5 py-4 text-sm text-red-600 dark:text-red-400">{error}</p>}
        {!features && !error && <p className="px-5 py-4 text-sm text-muted">Loading…</p>}
        {features &&
          FEATURES.map((feature) => {
            const on = features[feature.key];
            return (
              <div key={feature.key} className="flex items-start justify-between gap-6 px-5 py-4">
                <div className="min-w-0">
                  <p className="text-sm font-medium text-ui">{feature.label}</p>
                  <p className="mt-1 max-w-3xl text-xs leading-relaxed text-muted">{feature.description}</p>
                </div>
                <button
                  type="button"
                  role="switch"
                  aria-checked={on}
                  aria-label={feature.label}
                  disabled={saving === feature.key}
                  onClick={() => void toggle(feature.key, !on)}
                  className={cn(
                    "relative mt-0.5 inline-flex h-5 w-9 shrink-0 items-center rounded-full transition-colors disabled:opacity-60",
                    on ? "bg-[var(--ui-primary)]" : "bg-black/20 dark:bg-white/20",
                  )}
                >
                  <span
                    className={cn(
                      "inline-block h-4 w-4 rounded-full bg-white shadow transition-transform",
                      on ? "translate-x-4" : "translate-x-0.5",
                    )}
                  />
                </button>
              </div>
            );
          })}
      </div>
    </Card>
  );
}
