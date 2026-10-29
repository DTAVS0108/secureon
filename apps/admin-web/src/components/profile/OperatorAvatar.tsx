"use client";

import { useState } from "react";
import { cn } from "@/lib/cn";
import { initials } from "@/lib/format";
import { operatorAvatarUrl } from "@/lib/api/profile";

/** The signed-in operator's profile picture, or their initials when there is none (or it fails to load). */
export function OperatorAvatar({
  name,
  version,
  size = 32,
  className,
}: {
  name: string;
  version?: number | null;
  size?: number;
  className?: string;
}) {
  const [failed, setFailed] = useState<number | null>(null);
  const showImage = version != null && failed !== version;
  const box = { width: size, height: size };
  if (showImage) {
    return (
      // eslint-disable-next-line @next/next/no-img-element -- served by the API proxy with the session cookie
      <img
        src={operatorAvatarUrl(version)}
        alt={name}
        style={box}
        className={cn("shrink-0 rounded-full object-cover", className)}
        onError={() => setFailed(version)}
      />
    );
  }
  return (
    <div
      style={{ ...box, fontSize: Math.max(10, Math.round(size * 0.36)) }}
      className={cn(
        "flex shrink-0 items-center justify-center rounded-full bg-brand-muted font-semibold text-brand",
        className,
      )}
    >
      {initials(name)}
    </div>
  );
}
