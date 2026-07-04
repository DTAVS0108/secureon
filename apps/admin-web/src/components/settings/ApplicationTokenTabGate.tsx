"use client";

import { useCallback, useState } from "react";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";
import { Card, CardHeader } from "@/components/ui/Card";
import { Toggle } from "@/components/ui/Toggle";
import { useToast } from "@/components/ui/Toast";
import {
  fetchApplicationTokenTabState,
  setApplicationTokenTabEnabled,
} from "@/lib/api/application-settings";

/** Shown in app settings when platform allows OAuth tokens but this app has not enabled the tab yet. */
export function ApplicationTokenTabGate({
  applicationId,
  onTabEnabledChange,
}: {
  applicationId: string;
  onTabEnabledChange: (enabled: boolean) => void;
}) {
  const { toast } = useToast();
  const [tabEnabled, setTabEnabled] = useState(false);
  const [loaded, setLoaded] = useState(false);
  const [pending, setPending] = useState(false);

  const load = useCallback(async () => {
    try {
      const state = await fetchApplicationTokenTabState(applicationId);
      setTabEnabled(state.tabEnabled);
      onTabEnabledChange(state.tabEnabled);
    } catch (e) {
      toast(e instanceof Error ? e.message : "Failed to load token tab state", "error");
    } finally {
      setLoaded(true);
    }
  }, [applicationId, onTabEnabledChange, toast]);

  useDeferredEffect(() => {
    load();
  }, [load]);

  async function toggle(enabled: boolean) {
    setPending(true);
    try {
      const state = await setApplicationTokenTabEnabled(applicationId, enabled);
      setTabEnabled(state.tabEnabled);
      onTabEnabledChange(state.tabEnabled);
      toast("Settings saved", "success");
    } catch (e) {
      toast(e instanceof Error ? e.message : "Update failed", "error");
    } finally {
      setPending(false);
    }
  }

  if (!loaded || tabEnabled) {
    return null;
  }

  return (
    <Card padded={false} className="mb-6">
      <CardHeader
        title="OAuth token management"
        description="Platform allows this application to customize OAuth token lifetimes. Enable the tab to configure access tokens, refresh tokens, and expiry."
      />
      <div className="flex items-center justify-between gap-4 px-5 py-4">
        <div>
          <p className="text-sm font-medium text-ui">Enable OAuth tokens tab</p>
          <p className="text-xs text-muted">
            When enabled, the <strong>OAuth tokens</strong> tab appears so you can manage policy for this
            application.
          </p>
        </div>
        <Toggle
          checked={tabEnabled}
          disabled={pending}
          onChange={toggle}
          aria-label="Enable OAuth tokens tab"
        />
      </div>
    </Card>
  );
}
