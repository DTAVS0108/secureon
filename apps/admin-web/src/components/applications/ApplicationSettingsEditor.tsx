"use client";

import { useMemo } from "react";
import { useSearchParams } from "next/navigation";
import { SettingsTabs, type SettingsScope, type SettingsTabId } from "@/components/settings/SettingsTabs";

const TAB_QUERY_VALUES: SettingsTabId[] = [
  "integration",
  "notifications",
  "appearance",
  "auth",
  "password",
  "mfa",
  "flags",
  "users",
  "public-api",
  "tokens",
  "verification",
];

export function ApplicationSettingsEditor({ appId }: { appId: string }) {
  const searchParams = useSearchParams();
  const tabParam = searchParams.get("tab");
  const initialTab = TAB_QUERY_VALUES.includes(tabParam as SettingsTabId)
    ? (tabParam as SettingsTabId)
    : "integration";

  const scope = useMemo<SettingsScope>(
    () => ({ mode: "application", applicationId: appId }),
    [appId],
  );
  return <SettingsTabs scope={scope} initialTab={initialTab} />;
}
