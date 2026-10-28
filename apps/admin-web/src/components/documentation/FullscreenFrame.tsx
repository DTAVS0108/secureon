"use client";

import { useEffect, useRef, useState } from "react";
import { cn } from "@/lib/cn";

/**
 * Wraps a diagram or image with a "Full screen" button (browser full screen; Esc leaves it). The
 * children get `fullscreen` so they can size themselves to the screen. Built from spans so it is
 * also valid inside a paragraph (markdown images).
 */
export function FullscreenFrame({
  children,
  className,
}: {
  children: (fullscreen: boolean) => React.ReactNode;
  className?: string;
}) {
  const frameRef = useRef<HTMLSpanElement>(null);
  const [fullscreen, setFullscreen] = useState(false);

  useEffect(() => {
    const onChange = () => setFullscreen(document.fullscreenElement === frameRef.current);
    document.addEventListener("fullscreenchange", onChange);
    return () => document.removeEventListener("fullscreenchange", onChange);
  }, []);

  function toggle() {
    if (document.fullscreenElement) {
      void document.exitFullscreen();
    } else {
      void frameRef.current?.requestFullscreen?.();
    }
  }

  return (
    <span
      ref={frameRef}
      className={cn(
        "group/fs relative block",
        fullscreen ? "fullscreen-frame flex overflow-auto bg-white p-6" : className,
      )}
    >
      <button
        type="button"
        onClick={toggle}
        title={fullscreen ? "Exit full screen (Esc)" : "Full screen"}
        className={cn(
          "absolute right-2 top-2 z-20 rounded-md border border-black/15 bg-white/90 px-2 py-1 text-xs font-medium text-black/70 shadow-sm transition-opacity hover:bg-white hover:text-black focus:opacity-100",
          fullscreen ? "fixed right-4 top-4" : "opacity-70 group-hover/fs:opacity-100",
        )}
      >
        {fullscreen ? "✕ Exit full screen" : "⤢ Full screen"}
      </button>
      {children(fullscreen)}
    </span>
  );
}
