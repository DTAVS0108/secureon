import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Served by the auth-server at /admin (one domain, one deployable). Keep in sync with BASE_PATH in src/lib/config.ts.
  basePath: "/admin",
  // Self-contained server (server.js + only the node_modules it needs) for the single container image.
  output: "standalone",
};

export default nextConfig;
