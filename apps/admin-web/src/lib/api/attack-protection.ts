import { browserApiFetch as apiFetch } from "./browser-client";

export type CaptchaProvider = "none" | "turnstile" | "hcaptcha" | "recaptcha";
export type SignInChallenge = "never" | "after_failures" | "always";

export type AttackProtection = {
  botProtection: {
    provider: CaptchaProvider;
    siteKey: string;
    /** Write-only: a new secret key; blank keeps the saved one. */
    secret?: string;
    secretSet?: boolean;
    signUp: boolean;
    passwordReset: boolean;
    magicLink: boolean;
    oneTimeCodes: boolean;
    signIn: SignInChallenge;
    signInFailures: number;
    /** Host names the CAPTCHA must have been solved on; empty accepts any. */
    allowedHostnames: string[];
    /** Let requests through when the CAPTCHA provider cannot be reached (default: refuse them). */
    failOpen: boolean;
  };
  ipRules: {
    blocked: string[];
    consoleAllowed: string[];
  };
  /** Email users about sign-ins from a browser and system their account has not used before. */
  newDeviceAlerts: boolean;
};

const PATH = "/api/admin/v1/settings/attack-protection";

export function loadAttackProtection(): Promise<AttackProtection> {
  return apiFetch<AttackProtection>(PATH);
}

export function saveAttackProtection(body: AttackProtection): Promise<AttackProtection> {
  return apiFetch<AttackProtection>(PATH, { method: "PUT", body: JSON.stringify(body) });
}
