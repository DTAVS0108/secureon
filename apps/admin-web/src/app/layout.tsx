import type { Metadata } from "next";
import type { CSSProperties } from "react";
import { headers } from "next/headers";
import { Geist, Geist_Mono } from "next/font/google";
import "./globals.css";
// Registers the per-request auth lookup used by shared API helpers during server rendering.
import "@/lib/api/server-auth";
import { ConsoleProviders } from "@/components/ConsoleProviders";
import { loadAdminContextSafe } from "@/lib/api/app-workspace";
import type { AdminContext } from "@/lib/api/context";
import { ThemeProvider } from "@/components/ThemeProvider";
import { ToastProvider } from "@/components/ui/Toast";
import { getServerThemeBootstrap } from "@/lib/theme/server";

const EMPTY_ADMIN_CONTEXT: AdminContext = {
  platformSuperAdmin: false,
  operatorTier: "application",
  principal: "",
  email: null,
  displayName: "",
  tenantId: null,
  tenantSlug: null,
  tenantName: null,
  userId: null,
  actAsEmail: null,
  oauthClientCount: 0,
  applications: [],
  breakGlass: false,
  sessionExpiresAt: null,
  sessionIdleTimeoutSeconds: 1800,
};

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "SecureOne · Admin",
  description: "Admin console for the SecureOne Identity & Access Management platform.",
};

export default async function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  const pathname = (await headers()).get("x-pathname") ?? "";
  const isBareRoute =
    pathname === "/login" ||
    pathname.startsWith("/login/") ||
    pathname.startsWith("/api/");
  const [ctx, theme] = await Promise.all([
    isBareRoute ? Promise.resolve(EMPTY_ADMIN_CONTEXT) : loadAdminContextSafe(),
    getServerThemeBootstrap(),
  ]);

  return (
    <html
      lang="en"
      data-theme={theme.dataTheme}
      data-gradient={theme.dataGradient}
      suppressHydrationWarning
      className={`${geistSans.variable} ${geistMono.variable} h-full antialiased`}
      style={theme.style as CSSProperties}
    >
      <body className="min-h-full">
        <ToastProvider>
          <ConsoleProviders adminContext={ctx}>
            <ThemeProvider platformSettingsAccess={ctx.platformSuperAdmin}>{children}</ThemeProvider>
          </ConsoleProviders>
        </ToastProvider>
      </body>
    </html>
  );
}
