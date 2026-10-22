"use client";

import { useCallback, useSyncExternalStore } from "react";

const STORAGE_KEY = "secureone_last_app";
const listeners = new Set<() => void>();

function readStoredApplicationId(): string | undefined {
  try {
    return sessionStorage.getItem(STORAGE_KEY) || undefined;
  } catch {
    return undefined;
  }
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

/**
 * The application the operator last opened in this tab. The server cannot see sessionStorage, so the first
 * browser render (hydration) uses no value like the server did; right after, the stored one is used. Reading
 * it during hydration made the sidebar's application links differ from the server HTML (React #418).
 */
export function useLastApplicationId() {
  const lastApplicationId = useSyncExternalStore(subscribe, readStoredApplicationId, () => undefined);

  const rememberApplicationId = useCallback((applicationId: string) => {
    try {
      sessionStorage.setItem(STORAGE_KEY, applicationId);
    } catch {
      // ignore
    }
    listeners.forEach((listener) => listener());
  }, []);

  return { lastApplicationId, rememberApplicationId };
}
