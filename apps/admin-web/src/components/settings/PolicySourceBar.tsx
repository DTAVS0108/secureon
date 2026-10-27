"use client";

import { useCallback, useEffect, useState } from "react";
import { useDialog } from "@/components/ui/Dialogs";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";
import { Button } from "@/components/ui/Button";
import { useToast } from "@/components/ui/Toast";
import {
  fetchApplicationPolicySources,
  setApplicationPolicySource,
  type PolicySource,
  type SettingsExposureKey,
} from "@/lib/api/application-policy";
import { POLICY_SOURCE_EVENT, announcePolicySource } from "@/lib/api/ensure-application-policy";

/**
 * Whether an application section follows the platform or has its own copy, with "Reset to platform". A section
 * gets its own copy on its first saved change, never by being opened.
 */
export function PolicySourceBar({
  applicationId,
  exposureKeys,
  label,
  hasMinimums = false,
  onReset,
}: {
  applicationId: string;
  exposureKeys: SettingsExposureKey[];
  /** "Password policy", "Sign-in methods", … */
  label: string;
  /** The platform values stay a floor for this section (password, verification, sign-in methods, tokens). */
  hasMinimums?: boolean;
  /** Called after a reset so the page reloads the platform values. */
  onReset: () => void;
}) {
  const { toast } = useToast();
  const [sources, setSources] = useState<Partial<Record<SettingsExposureKey, PolicySource>> | null>(null);
  const [pending, setPending] = useState(false);
  const dialog = useDialog();
  const keysId = exposureKeys.join(",");

  const load = useCallback(async () => {
    try {
      setSources(await fetchApplicationPolicySources(applicationId));
    } catch {
      setSources(null);
    }
  }, [applicationId]);

  useDeferredEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    const onChange = (e: Event) => {
      const detail = (e as CustomEvent<{ applicationId: string; exposureKey: SettingsExposureKey }>).detail;
      if (detail?.applicationId === applicationId && keysId.split(",").includes(detail.exposureKey)) load();
    };
    window.addEventListener(POLICY_SOURCE_EVENT, onChange);
    return () => window.removeEventListener(POLICY_SOURCE_EVENT, onChange);
  }, [applicationId, keysId, load]);

  if (!sources) return null;
  const present = exposureKeys.filter((k) => sources[k] !== undefined);
  if (present.length === 0) return null;
  const own = present.filter((k) => sources[k] === "application");

  async function reset() {
    if (
      !(await dialog.confirm({
        title: `Reset ${label} to the platform settings?`,
        message: "This application's own values are removed and platform changes apply here again.",
        confirmLabel: "Reset",
      }))
    ) {
      return;
    }
    setPending(true);
    try {
      for (const key of own) {
        await setApplicationPolicySource(applicationId, key, "platform");
        announcePolicySource(applicationId, key);
      }
      toast(`${label} follows the platform again`, "success");
      onReset();
    } catch (e) {
      toast(e instanceof Error ? e.message : "Reset failed", "error");
    } finally {
      setPending(false);
    }
  }

  return (
    <div
      className="mb-4 flex flex-wrap items-center justify-between gap-3 rounded-lg border border-ui bg-surface px-4 py-3 text-sm"
      data-testid="policy-source"
      data-source={own.length > 0 ? "application" : "platform"}
    >
      <p className="text-muted">
        {own.length > 0 ? (
          <>
            <strong className="text-ui">This application&apos;s own settings.</strong> Platform changes to{" "}
            {label.toLowerCase()} no longer reach it
            {hasMinimums ? ", except the platform minimums: it can be stricter than the platform, never weaker" : ""}.
          </>
        ) : (
          <>
            <strong className="text-ui">Follows the platform.</strong> Platform changes apply here. Your first change
            gives this application its own {label.toLowerCase()}
            {hasMinimums ? " (stricter than the platform, never weaker)" : ""}.
          </>
        )}
      </p>
      {own.length > 0 && (
        <Button variant="secondary" size="sm" disabled={pending} onClick={reset}>
          {pending ? "Resetting…" : "Reset to platform"}
        </Button>
      )}
    </div>
  );
}
