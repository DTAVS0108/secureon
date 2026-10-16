"use client";

import { useSyncExternalStore } from "react";
import { formatDate, formatDateTime, timeAgo } from "@/lib/format";

const noSubscription = () => () => {};

/**
 * A timestamp in the viewer's own time zone. The server (often UTC in a container) and the first
 * browser render both show UTC, so hydration matches; right after, the browser switches to local
 * time. Use it instead of calling the format helpers in JSX.
 */
export function LocalTime({
  value,
  format = "date",
}: {
  value: string | null | undefined;
  format?: "date" | "datetime" | "ago";
}) {
  const inBrowser = useSyncExternalStore(noSubscription, () => true, () => false);
  if (!value) return <>—</>;
  let text: string;
  if (!inBrowser) {
    const d = new Date(value);
    text =
      format === "date"
        ? d.toLocaleDateString("en-US", { year: "numeric", month: "short", day: "numeric", timeZone: "UTC" })
        : `${d.toLocaleString("en-US", { month: "short", day: "numeric", hour: "2-digit", minute: "2-digit", timeZone: "UTC" })} UTC`;
  } else {
    text = format === "datetime" ? formatDateTime(value) : format === "ago" ? timeAgo(value) : formatDate(value);
  }
  return (
    <time dateTime={value} title={inBrowser ? new Date(value).toString() : undefined}>
      {text}
    </time>
  );
}
