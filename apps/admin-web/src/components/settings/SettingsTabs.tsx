"use client";

import { PlatformFeaturesCard } from "./PlatformFeaturesCard";
import { useCallback, useMemo, useRef, useState } from "react";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";
import { AppearanceSettings } from "@/components/settings/AppearanceSettings";
import { ApplicationAppearanceSettings } from "@/components/settings/ApplicationAppearanceSettings";
import { AppExposureSettings } from "@/components/settings/AppExposureSettings";
import { AttackProtectionSettings } from "@/components/settings/AttackProtectionSettings";
import { LogStreamingSettings } from "@/components/settings/LogStreamingSettings";
import { AuthenticationSettingsPanel } from "@/components/settings/AuthenticationSettingsPanel";
import { NotificationsSettings } from "@/components/settings/NotificationsSettings";
import { PlatformNotificationsSettings } from "@/components/settings/PlatformNotificationsSettings";
import { ApplicationIntegrationSettings } from "@/components/settings/ApplicationIntegrationSettings";
import { PolicySourceBar } from "@/components/settings/PolicySourceBar";
import { PublicManifestSettings } from "@/components/settings/PublicManifestSettings";
import { ScimSettingsPanel } from "@/components/settings/ScimSettingsPanel";
import { SettingsScopeBanner } from "@/components/settings/SettingsScopeBanner";
import { TokenClaimsCard } from "@/components/settings/TokenClaimsCard";
import { WebhooksPanel } from "@/components/tenants/WebhooksPanel";
import { SignupPolicyCard } from "@/components/settings/SignupPolicyCard";
import { SigningKeysCard } from "@/components/settings/SigningKeysCard";
import { TokenPolicySettings } from "@/components/settings/TokenPolicySettings";
import { VerificationSettingsPanel } from "@/components/settings/VerificationSettingsPanel";
import { UserDirectorySettings } from "@/components/settings/UserDirectorySettings";
import { loadApplicationExposureAction } from "@/lib/actions/app-exposure";
import {
  fetchApplicationMfaTabState,
  fetchApplicationTokenTabState,
} from "@/lib/api/application-settings";
import type { AppSettingsExposure } from "@/lib/api/app-exposure";
import { cn } from "@/lib/cn";
import {
  getCachedApplicationExposure,
  loadApplicationExposureDeduped,
} from "@/lib/application-exposure-cache";
import { coerceAppSettingsExposure } from "@/lib/settings-exposure";
import { useAdminContext } from "@/components/AdminContextProvider";

export type AuthSubTabId = "login" | "verification" | "mfa" | "password" | "signup";

export type SettingsTabId =
  | "integration"
  | "notifications"
  | "appearance"
  | "auth"
  | "password"
  | "mfa"
  | "flags"
  | "users"
  | "public-api"
  | "tokens"
  | "verification"
  | "protection"
  | "logs"
  | "apps";

type Tab = SettingsTabId;

/** Legacy top-level tab ids that now live under Authentication. */
const AUTH_NESTED_TAB_IDS: SettingsTabId[] = ["verification", "mfa", "password"];

function toAuthSubTab(tab: SettingsTabId): AuthSubTabId {
  if (tab === "verification") return "verification";
  if (tab === "mfa") return "mfa";
  if (tab === "password") return "password";
  return "login";
}

function resolveInitialTabState(initialTab?: SettingsTabId): { tab: Tab; authSubTab: AuthSubTabId } {
  const resolved = initialTab ?? "notifications";
  if (resolved === "auth" || AUTH_NESTED_TAB_IDS.includes(resolved)) {
    return { tab: "auth", authSubTab: toAuthSubTab(resolved) };
  }
  return { tab: resolved, authSubTab: "login" };
}

const AUTH_SUB_TABS: {
  id: AuthSubTabId;
  label: string;
  description: string;
  exposureKeys: string[] | null;
  requiresMfaTab?: boolean;
}[] = [
  {
    id: "login",
    label: "Sign-in methods",
    description: "How people sign in: password, magic link, passkeys, and external identity providers.",
    exposureKeys: ["auth-methods"],
  },
  {
    id: "verification",
    label: "Verification & OTP",
    description: "Codes and links for sign-up, passwordless sign-in and password recovery, and the SMS provider.",
    exposureKeys: ["verification-policy", "sms"],
  },
  {
    id: "mfa",
    label: "Two Factor Authentication",
    description: "Second-step options, whether they are required, setup reminders and the grace period.",
    exposureKeys: ["two-factor"],
    requiresMfaTab: true,
  },
  {
    id: "password",
    label: "Password policy",
    description: "Complexity, expiry, reuse and account lockout.",
    exposureKeys: ["password-policy"],
  },
  {
    id: "signup",
    label: "Sign-up",
    description: "Allowed and blocked email domains for new accounts, and extra fields on the sign-up page.",
    exposureKeys: null,
  },
];

export type SettingsScope =
  | { mode: "platform" }
  | { mode: "application"; applicationId: string };

const TAB_META: {
  id: Tab;
  label: string;
  exposureKeys: string[] | null;
}[] = [
  { id: "integration", label: "Integration", exposureKeys: null },
  { id: "notifications", label: "Notifications", exposureKeys: ["notifications", "email"] },
  { id: "appearance", label: "Appearance", exposureKeys: ["appearance"] },
  { id: "auth", label: "Authentication", exposureKeys: null },
  { id: "flags", label: "Feature flags", exposureKeys: ["feature-flags"] },
  { id: "users", label: "User directory", exposureKeys: ["user-directory"] },
  { id: "public-api", label: "Public API", exposureKeys: ["public-manifest"] },
  { id: "tokens", label: "OAuth tokens", exposureKeys: ["token-policy"] },
  { id: "protection", label: "Attack protection", exposureKeys: null },
  { id: "logs", label: "Logs & streaming", exposureKeys: null },
  { id: "apps", label: "For applications", exposureKeys: null },
];

/** Configured per app under Platform → For applications; not shown on platform settings. */
const APPLICATION_ONLY_TABS: Tab[] = ["integration", "users", "public-api", "tokens"];

function isAuthSubTabVisible(
  subTab: AuthSubTabId,
  scope: SettingsScope,
  exposure: AppSettingsExposure,
  exposureReady: boolean,
  exposureError: string | undefined,
  mfaTabEnabled: boolean,
): boolean {
  // Sign-up rules are each application's own (the platform has none).
  if (subTab === "signup") return scope.mode === "application";
  if (scope.mode === "platform") return true;
  if (!exposureReady || exposureError) return false;
  const meta = AUTH_SUB_TABS.find((t) => t.id === subTab);
  if (!meta?.exposureKeys?.length) return true;
  const platformAllows = meta.exposureKeys.some((key) => Boolean(exposure[key]));
  if (subTab === "mfa") {
    return platformAllows && mfaTabEnabled;
  }
  return platformAllows;
}

function isTabVisible(
  tab: Tab,
  scope: SettingsScope,
  exposure: AppSettingsExposure,
  exposureReady: boolean,
  exposureError: string | undefined,
  tokenTabEnabled: boolean,
  mfaTabEnabled: boolean,
): boolean {
  if (tab === "apps" || tab === "protection" || tab === "logs") return scope.mode === "platform";
  if (tab === "integration") return scope.mode === "application";
  if (tab === "auth") {
    if (scope.mode === "platform") return true;
    if (!exposureReady || exposureError) return false;
    return AUTH_SUB_TABS.some((sub) =>
      isAuthSubTabVisible(sub.id, scope, exposure, exposureReady, exposureError, mfaTabEnabled),
    );
  }
  if (scope.mode === "platform") return !APPLICATION_ONLY_TABS.includes(tab);
  if (!exposureReady || exposureError) return false;
  const meta = TAB_META.find((t) => t.id === tab);
  if (!meta?.exposureKeys?.length) return true;
  const platformAllows = meta.exposureKeys.some((key) => Boolean(exposure[key]));
  if (tab === "tokens") {
    return platformAllows && tokenTabEnabled;
  }
  if (tab === "flags") {
    // Also home of the application's settings-tab switches (Two Factor Authentication, OAuth tokens).
    return platformAllows || Boolean(exposure["two-factor"]) || Boolean(exposure["token-policy"]);
  }
  return platformAllows;
}

export function SettingsTabs({
  scope,
  initialTab,
}: {
  scope: SettingsScope;
  initialTab?: SettingsTabId;
}) {
  const scopeMode = scope.mode;
  const scopedApplicationId = scope.mode === "application" ? scope.applicationId : undefined;
  const adminContext = useAdminContext();

  const initial = resolveInitialTabState(initialTab);
  const [tab, setTab] = useState<Tab>(initial.tab);
  const [authSubTab, setAuthSubTab] = useState<AuthSubTabId>(initial.authSubTab);
  const [exposure, setExposure] = useState<AppSettingsExposure>({});
  const [exposureReady, setExposureReady] = useState(scopeMode === "platform");
  const [exposureError, setExposureError] = useState<string | undefined>();
  const [tokenTabEnabled, setTokenTabEnabled] = useState(false);
  const [mfaTabEnabled, setMfaTabEnabled] = useState(false);
  const [tokenGateReady, setTokenGateReady] = useState(scopeMode === "platform");
  const [mfaGateReady, setMfaGateReady] = useState(scopeMode === "platform");

  const platformExposesTokens = Boolean(exposure["token-policy"]);
  // Bumped by "Reset to platform" so the open section reloads the platform values.
  const [resetNonce, setResetNonce] = useState(0);
  const bumpReset = useCallback(() => setResetNonce((n) => n + 1), []);
  const loadedExposureAppRef = useRef<string | null>(null);

  useDeferredEffect(() => {
    if (scopeMode !== "application" || !scopedApplicationId) {
      loadedExposureAppRef.current = null;
      setExposureReady(true);
      setTokenGateReady(true);
      setMfaGateReady(true);
      return;
    }

    const cached = getCachedApplicationExposure(scopedApplicationId);
    if (cached) {
      setExposure(cached.exposure);
      setExposureError(cached.error);
      setExposureReady(true);
      setMfaTabEnabled(cached.mfaTabEnabled);
      setTokenTabEnabled(cached.tokenTabEnabled);
      setMfaGateReady(true);
      setTokenGateReady(true);
      loadedExposureAppRef.current = scopedApplicationId;
      return;
    }

    const switchingApp = loadedExposureAppRef.current !== scopedApplicationId;
    if (switchingApp) {
      setExposureReady(false);
      setTokenGateReady(false);
      setMfaGateReady(false);
      setExposureError(undefined);
    }

    let cancelled = false;

    loadApplicationExposureDeduped(scopedApplicationId, async () => {
      const { exposure: raw, error } = await loadApplicationExposureAction(scopedApplicationId);
      const exp = coerceAppSettingsExposure(raw);

      let mfaTabEnabled = false;
      let tokenTabEnabled = false;

      const twoFactorExposed = Boolean(exp["two-factor"]);
      if (twoFactorExposed) {
        try {
          const mfaState = await fetchApplicationMfaTabState(scopedApplicationId);
          mfaTabEnabled = mfaState.tabEnabled;
        } catch {
          mfaTabEnabled = false;
        }
      }

      if (exp["token-policy"]) {
        try {
          const tokenState = await fetchApplicationTokenTabState(scopedApplicationId);
          tokenTabEnabled = tokenState.tabEnabled;
        } catch {
          tokenTabEnabled = false;
        }
      }

      return {
        exposure: exp,
        error,
        mfaTabEnabled,
        tokenTabEnabled,
      };
    })
      .then((bundle) => {
        if (cancelled) return;
        setExposure(bundle.exposure);
        setExposureError(bundle.error);
        setExposureReady(true);
        setMfaTabEnabled(bundle.mfaTabEnabled);
        setMfaGateReady(true);
        setTokenTabEnabled(bundle.tokenTabEnabled);
        setTokenGateReady(true);
        loadedExposureAppRef.current = scopedApplicationId;
      })
      .catch(() => {
        if (cancelled) return;
        setExposureError("Failed to load enabled settings.");
        setExposureReady(true);
        setMfaGateReady(true);
        setTokenGateReady(true);
      });

    return () => {
      cancelled = true;
    };
  }, [scopeMode, scopedApplicationId]);

  const visibleTabs = useMemo(
    () =>
      TAB_META.filter((t) =>
        isTabVisible(
          t.id,
          scopeMode === "application"
            ? { mode: "application", applicationId: scopedApplicationId! }
            : { mode: "platform" },
          exposure,
          exposureReady,
          exposureError,
          tokenTabEnabled,
          mfaTabEnabled,
        ),
      ),
    [scopeMode, scopedApplicationId, exposure, exposureReady, exposureError, tokenTabEnabled, mfaTabEnabled],
  );

  const visibleAuthSubTabs = useMemo(
    () =>
      AUTH_SUB_TABS.filter((sub) =>
        isAuthSubTabVisible(
          sub.id,
          scopeMode === "application"
            ? { mode: "application", applicationId: scopedApplicationId! }
            : { mode: "platform" },
          exposure,
          exposureReady,
          exposureError,
          mfaTabEnabled,
        ),
      ),
    [scopeMode, scopedApplicationId, exposure, exposureReady, exposureError, mfaTabEnabled],
  );

  const openTab = useCallback(
    (next: SettingsTabId, subTab?: AuthSubTabId) => {
      if (next === "auth" || AUTH_NESTED_TAB_IDS.includes(next)) {
        setTab("auth");
        setAuthSubTab(subTab ?? toAuthSubTab(next));
        return;
      }
      setTab(next);
    },
    [],
  );

  useDeferredEffect(() => {
    if (tab !== "auth") return;
    if (scopeMode === "application" && !exposureReady) return;
    if (!visibleAuthSubTabs.some((sub) => sub.id === authSubTab)) {
      setAuthSubTab(visibleAuthSubTabs[0]?.id ?? "login");
    }
  }, [tab, authSubTab, visibleAuthSubTabs, scopeMode, exposureReady]);

  useDeferredEffect(() => {
    if (scopeMode === "application" && !exposureReady) return;
    if (!visibleTabs.some((t) => t.id === tab)) {
      setTab(visibleTabs[0]?.id ?? "notifications");
    }
  }, [visibleTabs, tab, scopeMode, exposureReady]);

  const applicationId = scopedApplicationId;
  const authPanelTab =
    authSubTab === "mfa" ? "mfa" : authSubTab === "password" ? "password" : "auth";

  if (scopeMode === "application" && exposureReady && visibleTabs.length === 0) {
    return (
      <>
        <SettingsScopeBanner scope={scope} />
        <p className="rounded-lg border border-ui bg-surface px-4 py-6 text-sm text-muted">
          {exposureError ? (
            exposureError
          ) : (
            <>
              No setting sections are enabled for applications. A platform super admin can enable them under{" "}
              <strong>Platform settings → For applications</strong>.
            </>
          )}
        </p>
      </>
    );
  }

  return (
    <div>
      <SettingsScopeBanner scope={scope} />
      {scopeMode === "application" && exposureError && (
        <p className="mb-4 rounded-lg border border-amber-500/40 bg-amber-500/10 px-4 py-3 text-sm text-ui">
          {exposureError}
        </p>
      )}
      {!exposureReady && scopeMode === "application" && (
        <p className="mb-4 text-sm text-muted">Loading enabled settings…</p>
      )}

      {scopeMode === "application" && exposureReady && Boolean(exposure["two-factor"]) && !mfaGateReady && (
        <p className="mb-4 text-sm text-muted">Loading Two Factor Authentication settings…</p>
      )}
      {scopeMode === "application" && exposureReady && platformExposesTokens && !tokenGateReady && (
        <p className="mb-4 text-sm text-muted">Loading OAuth token settings…</p>
      )}

      <div className="mb-6 flex flex-wrap gap-1 border-b border-ui">
        {visibleTabs.map((t) => (
          <button
            key={t.id}
            type="button"
            onClick={() => setTab(t.id)}
            className={cn(
              "-mb-px border-b-2 px-4 py-2 text-sm font-medium transition-colors",
              tab === t.id
                ? "border-ui-primary text-brand"
                : "border-transparent text-muted hover:text-ui",
            )}
          >
            {t.label}
          </button>
        ))}
      </div>

      {tab === "integration" && applicationId && (
        <>
          <ApplicationIntegrationSettings applicationId={applicationId} onOpenTab={openTab} />
          <TokenClaimsCard applicationId={applicationId} />
          <WebhooksPanel applicationId={applicationId} />
        </>
      )}
      {tab === "notifications" && scopeMode === "platform" && <PlatformNotificationsSettings />}
      {tab === "notifications" && applicationId && (
        <NotificationsSettings
          key={applicationId}
          applicationId={applicationId}
          settingsReady={exposureReady}
        />
      )}
      {exposureReady && tab === "appearance" && applicationId && (
        <>
          <PolicySourceBar applicationId={applicationId} exposureKeys={["appearance"]} label="Appearance" onReset={bumpReset} />
          <ApplicationAppearanceSettings key={resetNonce} applicationId={applicationId} />
        </>
      )}
      {exposureReady && tab === "appearance" && !applicationId && <AppearanceSettings />}
      {exposureReady && tab === "users" && applicationId && (
        <>
          <PolicySourceBar applicationId={applicationId} exposureKeys={["user-directory"]} label="User directory" onReset={bumpReset} />
          <UserDirectorySettings key={resetNonce} applicationId={applicationId} />
        </>
      )}
      {exposureReady && tab === "public-api" && applicationId && (
        <>
          <PolicySourceBar applicationId={applicationId} exposureKeys={["public-manifest"]} label="Public API" onReset={bumpReset} />
          <PublicManifestSettings key={resetNonce} applicationId={applicationId} />
        </>
      )}
      {exposureReady && tab === "tokens" && tokenTabEnabled && (
        <>
          <TokenPolicySettings applicationId={applicationId} />
          {scopeMode === "platform" && <SigningKeysCard />}
        </>
      )}
      {tab === "auth" && exposureReady && (
        <>
          <div className="mb-6">
            <div
              role="tablist"
              aria-label="Authentication sections"
              className="inline-flex max-w-full flex-wrap gap-1 rounded-xl border border-ui bg-ui-elevated/50 p-1"
            >
              {visibleAuthSubTabs.map((sub) => (
                <button
                  key={sub.id}
                  type="button"
                  role="tab"
                  aria-selected={authSubTab === sub.id}
                  onClick={() => setAuthSubTab(sub.id)}
                  className={cn(
                    "rounded-lg px-4 py-1.5 text-sm font-medium transition-colors",
                    authSubTab === sub.id
                      ? "bg-ui-surface text-brand shadow-sm ring-1 ring-[var(--ui-border)]"
                      : "text-muted hover:text-ui",
                  )}
                >
                  {sub.label}
                </button>
              ))}
            </div>
            <p className="mt-2 text-xs text-muted">
              {visibleAuthSubTabs.find((sub) => sub.id === authSubTab)?.description}
            </p>
          </div>
          {applicationId && authSubTab === "login" && (
            <PolicySourceBar applicationId={applicationId} exposureKeys={["auth-methods"]} label="Sign-in methods" hasMinimums onReset={bumpReset} />
          )}
          {applicationId && authSubTab === "verification" && (
            <PolicySourceBar applicationId={applicationId} exposureKeys={["verification-policy", "sms"]} label="Verification & OTP" hasMinimums onReset={bumpReset} />
          )}
          {applicationId && authSubTab === "password" && (
            <PolicySourceBar applicationId={applicationId} exposureKeys={["password-policy"]} label="Password policy" hasMinimums onReset={bumpReset} />
          )}
          {authSubTab === "verification" && (
            <VerificationSettingsPanel key={resetNonce} applicationId={applicationId} />
          )}
          {authSubTab === "signup" && applicationId && <SignupPolicyCard applicationId={applicationId} />}
          {authSubTab !== "verification" && authSubTab !== "signup" && (
            <AuthenticationSettingsPanel
              key={`${authPanelTab}-${resetNonce}`}
              applicationId={applicationId}
              tab={authPanelTab}
              mfaTabEnabled={scopeMode === "platform" ? true : mfaTabEnabled}
              onMfaTabEnabledChange={(enabled) => {
                setMfaTabEnabled(enabled);
                if (enabled) setAuthSubTab("mfa");
              }}
              platformExposesOAuthTokens={
                scopeMode === "application" && Boolean(exposure["token-policy"])
              }
              oauthTokensTabEnabled={tokenTabEnabled}
              onOAuthTokensTabEnabledChange={(enabled) => {
                setTokenTabEnabled(enabled);
                if (enabled) setTab("tokens");
              }}
            />
          )}
        </>
      )}
      {exposureReady && tab === "flags" && scopeMode === "platform" && <PlatformFeaturesCard />}
      {exposureReady && tab === "flags" && applicationId && Boolean(exposure["feature-flags"]) && (
        <PolicySourceBar applicationId={applicationId} exposureKeys={["feature-flags"]} label="Feature flags" onReset={bumpReset} />
      )}
      {exposureReady && tab === "flags" && (
        <AuthenticationSettingsPanel
          key={`flags-${resetNonce}`}
          applicationId={applicationId}
          tab="flags"
          platformExposesTwoFactor={scopeMode === "application" && Boolean(exposure["two-factor"])}
          mfaTabEnabled={scopeMode === "platform" ? true : mfaTabEnabled}
          onMfaTabEnabledChange={(enabled) => {
            setMfaTabEnabled(enabled);
            if (enabled) openTab("auth", "mfa");
          }}
          platformExposesFeatureFlags={scopeMode === "platform" || Boolean(exposure["feature-flags"])}
          platformExposesOAuthTokens={
            scopeMode === "application" && Boolean(exposure["token-policy"])
          }
          oauthTokensTabEnabled={tokenTabEnabled}
          onOAuthTokensTabEnabledChange={(enabled) => {
            setTokenTabEnabled(enabled);
            if (enabled) setTab("tokens");
          }}
        />
      )}
      {tab === "protection" && scopeMode === "platform" && <AttackProtectionSettings />}
      {tab === "logs" && scopeMode === "platform" && <LogStreamingSettings />}
      {tab === "apps" && scopeMode === "platform" && (
        <div className="space-y-6">
          <AppExposureSettings />
          {adminContext.platformSuperAdmin && <ScimSettingsPanel />}
        </div>
      )}
    </div>
  );
}
