"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from "react";
import { useToast } from "@/components/ui/Toast";
import { loadAppearanceAction, saveAppearanceAction } from "@/lib/actions/settings";
import {
  applyUiPreferences,
  loadUiPreferences,
  mergeUiPreferences,
  normalizeUiPreferences,
  resolveThemeMode,
  saveUiPreferences,
} from "@/lib/theme/prefs";
import { DEFAULT_UI_PREFERENCES, type UiPreferences } from "@/lib/theme/types";

export type SaveStatus = "loading" | "idle" | "saving" | "saved" | "error";

type ThemeContextValue = {
  prefs: UiPreferences;
  setPrefs: (next: UiPreferences | ((prev: UiPreferences) => UiPreferences)) => void;
  resetPrefs: () => void;
  ready: boolean;
  saveStatus: SaveStatus;
  saveError: string | null;
  activeMode: "light" | "dark";
};

const ThemeContext = createContext<ThemeContextValue | null>(null);

export function ThemeProvider({
  children,
  platformSettingsAccess = false,
}: {
  children: React.ReactNode;
  /** When false, console theme uses browser storage only (no platform settings API). */
  platformSettingsAccess?: boolean;
}) {
  const { toast } = useToast();
  const toastRef = useRef(toast);
  useEffect(() => {
    toastRef.current = toast;
  }, [toast]);
  const [prefs, setPrefsState] = useState<UiPreferences>(DEFAULT_UI_PREFERENCES);
  const prevPrefs = useRef<UiPreferences | null>(null);
  const [ready, setReady] = useState(false);
  const [saveStatus, setSaveStatus] = useState<SaveStatus>("loading");
  const [saveError, setSaveError] = useState<string | null>(null);
  const saveTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const skipNextSave = useRef(true);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const local = loadUiPreferences();
      let api: Partial<UiPreferences> = {};
      let error: string | undefined;
      if (platformSettingsAccess) {
        const loaded = await loadAppearanceAction();
        api = loaded.prefs;
        error = loaded.error;
      }
      const merged = mergeUiPreferences(api, local);
      if (!cancelled) {
        setPrefsState(merged);
        applyUiPreferences(merged);
        saveUiPreferences(merged);
        prevPrefs.current = merged;
        if (error) {
          setSaveStatus("error");
          setSaveError(`${error} Using this browser only until the server is available.`);
          toastRef.current("Could not load theme from server", "error");
        } else {
          setSaveStatus("saved");
          setSaveError(null);
        }
        setReady(true);
        skipNextSave.current = true;
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [platformSettingsAccess]);

  const setPrefs = useCallback(
    (next: UiPreferences | ((prev: UiPreferences) => UiPreferences)) => {
      setPrefsState((prev) => {
        const resolved = normalizeUiPreferences(
          typeof next === "function" ? next(prev) : next,
        );
        applyUiPreferences(resolved);
        if (ready) prevPrefs.current = resolved;
        return resolved;
      });
    },
    [ready],
  );

  const persistPrefs = useCallback(
    async (next: UiPreferences) => {
      applyUiPreferences(next);
      saveUiPreferences(next);
      if (!platformSettingsAccess) {
        setSaveStatus("saved");
        setSaveError(null);
        return;
      }
      setSaveStatus("saving");
      setSaveError(null);
      const result = await saveAppearanceAction(next);
      if (result.ok) {
        setSaveStatus("saved");
        setSaveError(null);
        toastRef.current("Settings saved", "success");
      } else {
        setSaveStatus("error");
        const msg = result.error
          ? `${result.error} Changes kept in this browser only.`
          : "Failed to save appearance to database.";
        setSaveError(msg);
        toastRef.current("Save failed — using browser storage", "error");
      }
    },
    [platformSettingsAccess],
  );

  useEffect(() => {
    if (!ready) return;
    if (skipNextSave.current) {
      skipNextSave.current = false;
      return;
    }
    if (saveTimer.current) clearTimeout(saveTimer.current);
    saveTimer.current = setTimeout(() => {
      persistPrefs(prefs);
    }, 600);
    return () => {
      if (saveTimer.current) clearTimeout(saveTimer.current);
    };
  }, [prefs, ready, persistPrefs]);

  useEffect(() => {
    if (prefs.mode !== "system") return;
    const mq = window.matchMedia("(prefers-color-scheme: dark)");
    const handler = () => {
      applyUiPreferences(prefs);
      saveUiPreferences(prefs);
    };
    mq.addEventListener("change", handler);
    return () => mq.removeEventListener("change", handler);
  }, [prefs]);

  const resetPrefs = useCallback(() => setPrefs(DEFAULT_UI_PREFERENCES), [setPrefs]);
  const activeMode = resolveThemeMode(prefs);
  const value = useMemo<ThemeContextValue>(
    () => ({
      prefs,
      setPrefs,
      resetPrefs,
      ready,
      saveStatus,
      saveError,
      activeMode,
    }),
    [prefs, setPrefs, resetPrefs, ready, saveStatus, saveError, activeMode],
  );

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useThemePrefs() {
  const ctx = useContext(ThemeContext);
  if (!ctx) throw new Error("useThemePrefs must be used within ThemeProvider");
  return ctx;
}
