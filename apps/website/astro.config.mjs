// @ts-check
import { defineConfig } from "astro/config";
import starlight from "@astrojs/starlight";

const space = (label, directory) => ({ label, autogenerate: { directory: `docs/${directory}` } });

export default defineConfig({
  // Public address of the website (canonical links, sitemap). Set SITE_URL when it moves to its own domain.
  site: process.env.SITE_URL || "http://localhost:8080",
  trailingSlash: "always",
  integrations: [
    starlight({
      title: "SecureOne",
      description: "Sign-in, single sign-on, MFA, users, roles and permissions for every application.",
      logo: { src: "./src/assets/secureone-mark.svg", alt: "SecureOne" },
      favicon: "/favicon.svg",
      customCss: ["./src/styles/docs.css"],
      disable404Route: true,
      lastUpdated: false,
      pagination: true,
      components: {
        SocialIcons: "./src/components/DocsNav.astro",
        Head: "./src/components/DocsHead.astro",
      },
      sidebar: [
        { label: "Documentation home", link: "/docs/" },
        space("Get started", "get-started"),
        space("Install and operate", "install"),
        space("Integrate applications", "integrate"),
        space("Admin console guide", "guide"),
        space("API reference", "api"),
        space("Architecture and security", "architecture"),
      ],
    }),
  ],
});
