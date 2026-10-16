"use client";

import { useEffect, useState } from "react";
import { useAdminContext } from "./AdminContextProvider";
import { stripBasePath, withBasePath } from "@/lib/config";

/** Start warning this long before the session's absolute end. */
const WARN_BEFORE_MS = 10 * 60 * 1000;

function formatRemaining(ms: number) {
  const minutes = Math.max(0, Math.ceil(ms / 60000));
  if (minutes >= 60) {
    const h = Math.floor(minutes / 60);
    const m = minutes % 60;
    return m ? `${h} h ${m} min` : `${h} h`;
  }
  return `${minutes} min`;
}

/**
 * Session awareness for the console: a persistent banner while signed in with the emergency
 * (break-glass) account, a warning shortly before the session's maximum lifetime, and a redirect to
 * sign-in (with the reason) when it ends. Inactivity is enforced by the server; the next request
 * after an idle timeout returns to sign-in the same way.
 */
export function SessionNotice() {
  const ctx = useAdminContext();
  const expiresAt = ctx.sessionExpiresAt ? Date.parse(ctx.sessionExpiresAt) : null;
  // Time is read in the browser only: server and browser clocks would render different minutes
  // and break hydration.
  const [now, setNow] = useState<number | null>(null);

  useEffect(() => {
    if (!expiresAt) return;
    const tick = () => {
      const t = Date.now();
      setNow(t);
      if (t >= expiresAt) {
        window.clearInterval(timer);
        const here = `${stripBasePath(window.location.pathname)}${window.location.search}`;
        window.location.assign(withBasePath(`/login?reason=expired&next=${encodeURIComponent(here)}`));
      }
    };
    const first = window.setTimeout(tick, 0);
    const timer = window.setInterval(tick, 15000);
    return () => {
      window.clearTimeout(first);
      window.clearInterval(timer);
    };
  }, [expiresAt]);

  const remaining = expiresAt && now !== null ? expiresAt - now : null;
  const ending = remaining !== null && remaining <= WARN_BEFORE_MS;

  if (!ctx.breakGlass && !ending) {
    return null;
  }

  return (
    <div className="space-y-px">
      {ctx.breakGlass && (
        <div
          role="alert"
          className="flex flex-wrap items-center justify-between gap-2 bg-red-600 px-8 py-2 text-xs text-white"
        >
          <span>
            <strong>Emergency account.</strong> Every action is audited and platform admins were
            notified. Use it only to restore access, then sign in with your named account.
          </span>
          {remaining !== null && <span>Session ends in {formatRemaining(remaining)}</span>}
        </div>
      )}
      {!ctx.breakGlass && ending && remaining !== null && (
        <div
          role="status"
          className="bg-amber-500/15 px-8 py-2 text-xs text-amber-800 dark:text-amber-200"
        >
          Your session ends in {formatRemaining(remaining)} (maximum session length). Save your work;
          you will need to sign in again.
        </div>
      )}
    </div>
  );
}
