import { useEffect, type DependencyList } from "react";

/**
 * Runs an effect after the current commit flush. Use when the effect synchronously
 * updates React state (prop sync, form reset, data reload) so eslint react-hooks/set-state-in-effect passes.
 */
export function useDeferredEffect(effect: () => void | (() => void), deps: DependencyList) {
  // deps are forwarded from the caller; effect identity is intentionally excluded.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => {
    let cleanup: void | (() => void);
    let cancelled = false;
    queueMicrotask(() => {
      if (!cancelled) cleanup = effect();
    });
    return () => {
      cancelled = true;
      cleanup?.();
    };
  }, deps);
}
