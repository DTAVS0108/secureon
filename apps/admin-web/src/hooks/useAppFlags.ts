"use client";

import { useAdminContext } from "@/components/AdminContextProvider";
import { appFlagOn, type AppFlagKey } from "@/lib/app-feature-flags";

/**
 * Console controls for client components (Platform settings → Feature flags → Console controls). They are
 * the same for every application.
 */
export function useAppFlags() {
  const { consoleFlags } = useAdminContext();
  return {
    loaded: true,
    on: (key: AppFlagKey) => appFlagOn(consoleFlags, key),
  };
}
