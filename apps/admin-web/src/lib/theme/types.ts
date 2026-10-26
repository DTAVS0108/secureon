export type ThemeMode = "light" | "dark" | "system";

export type GradientPreset = "none" | "brand" | "sunset" | "ocean" | "violet" | "custom";

/** Where the gradient is painted when enabled. */
export type GradientScope = "page" | "brand" | "both";

export type UiPreferences = {
  mode: ThemeMode;
  /** Buttons, active nav, primary actions */
  primaryColor: string;
  /** Text on primary buttons */
  buttonTextColor: string;
  /** Links, badges, secondary highlights */
  accentColor: string;
  /** Light mode */
  backgroundColor: string;
  surfaceColor: string;
  textColor: string;
  /** Dark mode */
  darkBackgroundColor: string;
  darkSurfaceColor: string;
  darkTextColor: string;
  gradientPreset: GradientPreset;
  gradientFrom: string;
  gradientTo: string;
  gradientAngle: number;
  gradientScope: GradientScope;
  borderRadius: number;
  fontScale: number;
  /** Popup toasts (bottom-right) */
  notifyInfoBackground: string;
  notifyInfoText: string;
  notifyInfoBorder: string;
  notifySuccessBackground: string;
  notifySuccessText: string;
  notifySuccessBorder: string;
  notifyErrorBackground: string;
  notifyErrorText: string;
  notifyErrorBorder: string;
};

export const DARK_THEME_COLORS = {
  backgroundColor: "#121216",
  surfaceColor: "#1c1c24",
  textColor: "#ededed",
} as const;

export const GRADIENT_PRESETS: Record<
  Exclude<GradientPreset, "none" | "custom">,
  { label: string; from: string; to: string; angle: number }
> = {
  brand: { label: "Primary → Accent", from: "", to: "", angle: 135 },
  sunset: { label: "Sunset", from: "#f97316", to: "#ec4899", angle: 120 },
  ocean: { label: "Ocean", from: "#06b6d4", to: "#4f46e5", angle: 140 },
  violet: { label: "Violet dusk", from: "#7c3aed", to: "#2563eb", angle: 150 },
};

/** Client-app widgets (dialogs, inputs) — public API only, not admin console. */
export type ClientWidgetColors = {
  dialogBackground: string;
  dialogText: string;
  dialogBorder: string;
  dialogOverlay: string;
  inputBackground: string;
  inputText: string;
  inputBorder: string;
  inputPlaceholder: string;
  mutedText: string;
  dividerColor: string;
};

/** Languages SecureOne's hosted pages are translated into; "auto" follows the person's browser. */
export const HOSTED_PAGE_LANGUAGES = [
  { code: "auto", name: "Browser language (automatic)" },
  { code: "en", name: "English" },
  { code: "es", name: "Español" },
  { code: "fr", name: "Français" },
  { code: "de", name: "Deutsch" },
  { code: "pt", name: "Português" },
  { code: "hi", name: "हिन्दी" },
] as const;

export type ClientBranding = {
  appName: string;
  logoUrl: string;
  /** Default language of the hosted sign-in pages. */
  language: string;
};

export type ClientAppearance = UiPreferences & ClientWidgetColors & ClientBranding;

export const DEFAULT_CLIENT_WIDGET_COLORS: ClientWidgetColors = {
  dialogBackground: "#ffffff",
  dialogText: "#171717",
  dialogBorder: "#e5e7eb",
  dialogOverlay: "#00000066",
  inputBackground: "#ffffff",
  inputText: "#171717",
  inputBorder: "#d1d5db",
  inputPlaceholder: "#9ca3af",
  mutedText: "#6b7280",
  dividerColor: "#e5e7eb",
};

export const DEFAULT_CLIENT_BRANDING: ClientBranding = {
  appName: "",
  logoUrl: "",
  language: "auto",
};

export const DEFAULT_UI_PREFERENCES: UiPreferences = {
  mode: "system",
  primaryColor: "#4f46e5",
  buttonTextColor: "#ffffff",
  accentColor: "#06b6d4",
  backgroundColor: "#f7f7f8",
  surfaceColor: "#ffffff",
  textColor: "#171717",
  darkBackgroundColor: DARK_THEME_COLORS.backgroundColor,
  darkSurfaceColor: DARK_THEME_COLORS.surfaceColor,
  darkTextColor: DARK_THEME_COLORS.textColor,
  gradientPreset: "none",
  gradientFrom: "#4f46e5",
  gradientTo: "#06b6d4",
  gradientAngle: 135,
  gradientScope: "both",
  borderRadius: 12,
  fontScale: 1,
  notifyInfoBackground: "#ffffff",
  notifyInfoText: "#171717",
  notifyInfoBorder: "#4f46e533",
  notifySuccessBackground: "#ecfdf5",
  notifySuccessText: "#065f46",
  notifySuccessBorder: "#10b98166",
  notifyErrorBackground: "#fef2f2",
  notifyErrorText: "#991b1b",
  notifyErrorBorder: "#ef444466",
};

export const DEFAULT_CLIENT_APPEARANCE: ClientAppearance = {
  ...DEFAULT_UI_PREFERENCES,
  ...DEFAULT_CLIENT_WIDGET_COLORS,
  ...DEFAULT_CLIENT_BRANDING,
};
