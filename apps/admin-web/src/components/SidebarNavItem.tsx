import Link from "next/link";
import { cn } from "@/lib/cn";
import { withBasePath } from "@/lib/config";
import type { NavItem } from "./nav";

function OpenInNewTabIcon({ className }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 20 20"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.75"
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
      aria-hidden
    >
      <path d="M11 3h6v6" />
      <path d="M17 3 9 11" />
      <path d="M7 5H4a1 1 0 0 0-1 1v10a1 1 0 0 0 1 1h10a1 1 0 0 0 1-1v-3" />
    </svg>
  );
}

export function SidebarNavItem({
  item,
  href,
  active,
}: {
  item: NavItem;
  href?: string;
  active: boolean;
}) {
  const url = href ?? item.href;
  const className = cn(
    "rounded-lg px-2.5 py-2 text-sm transition-colors",
    active && !item.openInNewTab
      ? "bg-brand font-medium text-on-brand"
      : "text-soft hover:bg-ui-elevated",
  );

  const label = (
    <span className="flex items-center justify-between gap-2">
      <span className="min-w-0 truncate">{item.label}</span>
      {item.openInNewTab && (
        <OpenInNewTabIcon
          className={cn(
            "h-3.5 w-3.5 shrink-0",
            active && !item.openInNewTab ? "opacity-90" : "opacity-55",
          )}
        />
      )}
    </span>
  );

  if (item.openInNewTab) {
    return (
      // A plain link skips Next.js's base path: console pages need /admin in front, or the auth-server answers.
      <a
        href={url.startsWith("/") ? withBasePath(url) : url}
        target="_blank"
        rel="noopener noreferrer"
        className={className}
        title="Opens in a new tab"
      >
        {label}
      </a>
    );
  }

  return (
    <Link href={url} className={className}>
      {label}
    </Link>
  );
}
