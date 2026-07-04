"use client";

import { ENVIRONMENT, IS_DEV, IS_PROD, IS_STAGING } from "@/lib/config";

export function EnvironmentBanner() {
  if (IS_PROD) {
    return null;
  }

  const label = IS_STAGING ? "Staging" : IS_DEV ? "Development" : ENVIRONMENT;
  const tone = IS_STAGING
    ? "border-sky-500/40 bg-sky-500/10 text-sky-100"
    : "border-amber-500/40 bg-amber-500/10 text-amber-100";

  return (
    <div
      className={`border-b px-4 py-1.5 text-center text-xs font-medium tracking-wide ${tone}`}
      role="status"
      aria-label={`${label} environment`}
    >
      {label} environment — not for production use
    </div>
  );
}
