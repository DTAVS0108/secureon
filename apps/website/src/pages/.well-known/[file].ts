import type { APIRoute, GetStaticPaths } from "astro";
import { SUPPORT_EMAIL } from "../../site";

// RFC 9116 security.txt, published only when a contact is configured (SUPPORT_EMAIL at build time).
export const getStaticPaths: GetStaticPaths = () => (SUPPORT_EMAIL ? [{ params: { file: "security.txt" } }] : []);

export const GET: APIRoute = ({ site }) => {
  const expires = new Date(Date.now() + 365 * 24 * 3600 * 1000).toISOString(); // renewed by every build
  const body = [
    `Contact: mailto:${SUPPORT_EMAIL}`,
    `Expires: ${expires}`,
    "Preferred-Languages: en",
    `Canonical: ${new URL("/.well-known/security.txt", site).href}`,
    `Policy: ${new URL("/security/", site).href}`,
    "",
  ].join("\n");
  return new Response(body, { headers: { "Content-Type": "text/plain; charset=utf-8" } });
};
