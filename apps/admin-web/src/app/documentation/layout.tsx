import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "SecureOne Documentation",
  description: "Platform documentation, architecture, and API integration guides.",
};

export default function DocsLayout({ children }: { children: React.ReactNode }) {
  return children;
}
