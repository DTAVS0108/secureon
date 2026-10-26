"use client";

import { useCallback, useState } from "react";
import Link from "next/link";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";
import { Card, CardHeader } from "@/components/ui/Card";
import { StatCard } from "@/components/ui/StatCard";
import { LocalTime } from "@/components/ui/LocalTime";
import { loadOverview, type Overview } from "@/lib/api/overview";

/** "user.login.locked" → "User login locked". */
function actionLabel(action: string): string {
  const words = action.replace(/[._]/g, " ").trim();
  return words.charAt(0).toUpperCase() + words.slice(1);
}

function percent(part: number, whole: number): string {
  return whole === 0 ? "0%" : `${Math.round((part * 100) / whole)}%`;
}

/** Console home: sign-ins, active users, two-factor use and recent security events. */
export function OverviewPanel({ tenantId }: { tenantId?: string }) {
  const [data, setData] = useState<Overview | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      setData(await loadOverview(tenantId));
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not load the overview");
    }
  }, [tenantId]);

  useDeferredEffect(() => {
    load();
  }, [load]);

  if (error) {
    return <p className="rounded-lg border border-ui bg-surface px-4 py-6 text-sm text-muted">{error}</p>;
  }
  if (!data) {
    return <p className="text-sm text-muted">Loading overview…</p>;
  }

  const { counts, signIns, daily, twoFactor } = data;
  const attempts24h = signIns.succeeded24h + signIns.failed24h;
  const peak = Math.max(1, ...daily.map((d) => d.succeeded + d.failed));

  return (
    <div className="space-y-6" data-testid="overview">
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <StatCard
          label="Sign-ins (24 h)"
          value={signIns.succeeded24h}
          hint={`${signIns.succeeded7d} in 7 days`}
        />
        <StatCard
          label="Failed sign-ins (24 h)"
          value={signIns.failed24h}
          hint={attempts24h > 0 ? `${percent(signIns.failed24h, attempts24h)} of attempts` : "No attempts"}
        />
        <StatCard label="Active users (30 days)" value={signIns.activeUsers30d} hint={`of ${counts.users} users`} />
        <StatCard
          label="Two-factor use"
          value={`${twoFactor.percent}%`}
          hint={`${twoFactor.usersWithTwoFactor} of ${counts.users} users`}
        />
      </div>

      <Card padded={false}>
        <CardHeader title="Sign-ins, last 14 days" description="Successful and failed sign-ins per day (UTC)." />
        <div className="px-5 pb-5">
          <div className="flex h-40 items-end gap-1.5" role="img" aria-label="Sign-ins per day">
            {daily.map((d) => (
              <div key={d.day} className="flex h-full flex-1 flex-col justify-end" title={`${d.day}: ${d.succeeded} ok, ${d.failed} failed`}>
                <div className="rounded-t-sm bg-red-500/70" style={{ height: `${(d.failed / peak) * 100}%` }} />
                <div className="bg-[var(--ui-primary)]" style={{ height: `${(d.succeeded / peak) * 100}%` }} />
              </div>
            ))}
          </div>
          <div className="mt-2 flex justify-between text-[11px] text-faint">
            <span>{daily[0]?.day}</span>
            <span className="flex gap-3">
              <span className="flex items-center gap-1">
                <span className="inline-block h-2 w-2 rounded-sm bg-[var(--ui-primary)]" /> Succeeded
              </span>
              <span className="flex items-center gap-1">
                <span className="inline-block h-2 w-2 rounded-sm bg-red-500/70" /> Failed
              </span>
            </span>
            <span>{daily[daily.length - 1]?.day}</span>
          </div>
        </div>
      </Card>

      <div className="grid gap-6 lg:grid-cols-[1fr_2fr]">
        <Card padded={false}>
          <CardHeader title="At a glance" />
          <ul className="divide-y divide-ui text-sm">
            {counts.tenants !== undefined && <Row label="Tenants" value={counts.tenants} href="/tenants" />}
            <Row label="Applications" value={counts.applications} />
            <Row label="OAuth clients" value={counts.oauthClients} href={tenantId ? undefined : "/applications"} />
            <Row label="Users" value={counts.users} />
            <Row label="Locked now" value={counts.lockedUsers} warn={counts.lockedUsers > 0} />
            <Row label="Suspended" value={counts.suspendedUsers} />
          </ul>
        </Card>
        <Card padded={false}>
          <CardHeader title="Recent security events" description="From the audit log." />
          {data.recentSecurityEvents.length === 0 ? (
            <p className="px-5 pb-5 text-sm text-muted">No security events yet.</p>
          ) : (
            <ul className="divide-y divide-ui text-sm">
              {data.recentSecurityEvents.map((e, i) => (
                <li key={`${e.at}-${i}`} className="flex flex-wrap items-baseline justify-between gap-2 px-5 py-3">
                  <span className="text-ui">
                    {actionLabel(e.action)}
                    <span className="text-muted">
                      {e.actor ? ` · ${e.actor}` : ""}
                      {e.application ? ` · ${e.application}` : e.tenant ? ` · ${e.tenant}` : ""}
                    </span>
                  </span>
                  <span className="text-xs text-faint">
                    <LocalTime value={e.at} format="datetime" />
                  </span>
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>
    </div>
  );
}

function Row({ label, value, href, warn }: { label: string; value: number; href?: string; warn?: boolean }) {
  const content = (
    <span className="flex items-center justify-between px-5 py-3">
      <span className="text-muted">{label}</span>
      <span className={warn ? "font-semibold text-amber-600" : "font-semibold text-ui"}>{value}</span>
    </span>
  );
  return <li>{href ? <Link href={href} className="block hover:bg-ui-elevated/40">{content}</Link> : content}</li>;
}
