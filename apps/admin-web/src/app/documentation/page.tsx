import { redirect } from "next/navigation";
import { getDefaultDocsSlug } from "@/lib/documentation/catalog";

export default function DocsIndexPage() {
  redirect(`/documentation/${getDefaultDocsSlug()}`);
}
