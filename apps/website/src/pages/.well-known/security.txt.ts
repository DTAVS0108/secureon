import type { APIRoute } from "astro";

// RFC 9116. Expires one year after the site was built; every rebuild renews it.
export const GET: APIRoute = ({ site }) => {
  const expires = new Date(Date.now() + 365 * 24 * 3600 * 1000).toISOString();
  const body = [
    "Contact: https://github.com/DTAVS0108/secureon/security/advisories/new",
    `Expires: ${expires}`,
    "Preferred-Languages: en",
    `Canonical: ${new URL("/.well-known/security.txt", site).href}`,
    `Policy: ${new URL("/security/", site).href}`,
    "",
  ].join("\n");
  return new Response(body, { headers: { "Content-Type": "text/plain; charset=utf-8" } });
};
