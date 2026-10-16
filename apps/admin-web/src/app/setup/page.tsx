import { redirect } from "next/navigation";
import { SetupForm } from "./SetupForm";
import { authServerInternalUrl } from "@/lib/config";

export const dynamic = "force-dynamic";

/** First-run setup: only while SecureOne has no platform operator. */
export default async function SetupPage() {
  let status: { setupRequired?: boolean; codeFromEnvironment?: boolean } = {};
  try {
    const res = await fetch(`${authServerInternalUrl()}/api/v1/setup/status`, { cache: "no-store" });
    status = await res.json();
  } catch {
    return (
      <main className="flex min-h-screen items-center justify-center p-6 text-sm text-muted">
        The auth-server is not reachable yet. Reload in a moment.
      </main>
    );
  }
  if (!status.setupRequired) redirect("/login");
  return (
    <main className="flex min-h-screen items-center justify-center bg-ui p-6">
      <SetupForm codeFromEnvironment={!!status.codeFromEnvironment} />
    </main>
  );
}
