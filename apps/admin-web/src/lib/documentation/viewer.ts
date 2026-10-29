import "server-only";

import { cache } from "react";
import { fetchAdminContext } from "@/lib/api/context";
import type { DocsViewer } from "./types";

/** Who is reading: documentation is public, so no session simply means a signed-out reader. */
export const getDocsViewer = cache(async (): Promise<DocsViewer> => {
  const ctx = await fetchAdminContext().catch(() => null);
  return { signedIn: Boolean(ctx?.principal), platformOperator: Boolean(ctx?.platformSuperAdmin) };
});
