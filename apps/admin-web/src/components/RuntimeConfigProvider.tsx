"use client";

import { installPublicConfig, type PublicConfig } from "@/lib/config";

/**
 * Hands the server's runtime settings (lib/config.ts) to browser code. It renders before every other
 * console component, so publicConfig() already sees them during hydration; the value is the same on
 * every render, so recording it here is safe.
 */
export function RuntimeConfigProvider({ config, children }: { config: PublicConfig; children: React.ReactNode }) {
  installPublicConfig(config);
  return <>{children}</>;
}
