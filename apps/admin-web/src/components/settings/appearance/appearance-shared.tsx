"use client";

import { useId } from "react";
import { FieldRow, Input } from "@/components/ui/Field";
import type { ClientAppearance, ClientWidgetColors } from "@/lib/theme/types";
import {
  applyGradientPreset,
  isGradientActive,
  resolveGradientStops,
  resolveThemeColors,
  resolveThemeMode,
} from "@/lib/theme/prefs";

export function ColorField({
  label,
  value,
  onChange,
  hint,
}: {
  label: string;
  value: string;
  onChange: (hex: string) => void;
  hint?: string;
}) {
  const id = useId();
  return (
    <FieldRow label={label} hint={hint} htmlFor={id}>
      <div className="flex gap-2">
        <input
          type="color"
          value={value?.startsWith("#") && value.length >= 7 ? value.slice(0, 7) : "#4f46e5"}
          onChange={(e) => onChange(e.target.value)}
          className="h-10 w-14 cursor-pointer rounded border border-ui"
        />
        <Input id={id} value={value} onChange={(e) => onChange(e.target.value)} />
      </div>
    </FieldRow>
  );
}

export function NotifyVariantFields({
  title,
  background,
  text,
  border,
  onBackground,
  onText,
  onBorder,
}: {
  title: string;
  background: string;
  text: string;
  border: string;
  onBackground: (hex: string) => void;
  onText: (hex: string) => void;
  onBorder: (hex: string) => void;
}) {
  return (
    <>
      <div className="md:col-span-2">
        <p className="text-xs font-semibold uppercase tracking-wide text-faint">{title}</p>
      </div>
      <ColorField label="Background" value={background} onChange={onBackground} />
      <ColorField label="Text" value={text} onChange={onText} />
      <ColorField label="Border" value={border} onChange={onBorder} />
    </>
  );
}

export function WidgetColorFields({
  widgets,
  onChange,
}: {
  widgets: ClientWidgetColors;
  onChange: (patch: Partial<ClientWidgetColors>) => void;
}) {
  return (
    <>
      <div className="md:col-span-2">
        <p className="mb-1 text-xs font-semibold uppercase tracking-wide text-faint">Dialogs & modals</p>
        <p className="mb-3 text-xs text-muted">Modal panels and confirmation dialogs in client apps.</p>
      </div>
      <ColorField
        label="Dialog background"
        value={widgets.dialogBackground}
        onChange={(dialogBackground) => onChange({ dialogBackground })}
      />
      <ColorField label="Dialog text" value={widgets.dialogText} onChange={(dialogText) => onChange({ dialogText })} />
      <ColorField
        label="Dialog border"
        value={widgets.dialogBorder}
        onChange={(dialogBorder) => onChange({ dialogBorder })}
      />
      <ColorField
        label="Backdrop overlay"
        value={widgets.dialogOverlay}
        hint="RGBA hex, e.g. #00000066"
        onChange={(dialogOverlay) => onChange({ dialogOverlay })}
      />

      <div className="md:col-span-2">
        <p className="mb-1 text-xs font-semibold uppercase tracking-wide text-faint">Inputs & forms</p>
      </div>
      <ColorField
        label="Input background"
        value={widgets.inputBackground}
        onChange={(inputBackground) => onChange({ inputBackground })}
      />
      <ColorField label="Input text" value={widgets.inputText} onChange={(inputText) => onChange({ inputText })} />
      <ColorField
        label="Input border"
        value={widgets.inputBorder}
        onChange={(inputBorder) => onChange({ inputBorder })}
      />
      <ColorField
        label="Placeholder text"
        value={widgets.inputPlaceholder}
        onChange={(inputPlaceholder) => onChange({ inputPlaceholder })}
      />

      <div className="md:col-span-2">
        <p className="mb-1 text-xs font-semibold uppercase tracking-wide text-faint">Common UI</p>
      </div>
      <ColorField label="Muted text" value={widgets.mutedText} onChange={(mutedText) => onChange({ mutedText })} />
      <ColorField label="Dividers" value={widgets.dividerColor} onChange={(dividerColor) => onChange({ dividerColor })} />
    </>
  );
}

export function ClientThemePreview({
  appearance,
  title = "Client app preview",
}: {
  appearance: ClientAppearance;
  title?: string;
}) {
  const previewMode = resolveThemeMode(appearance);
  const resolved = resolveThemeColors(appearance, previewMode);
  const gradient = resolveGradientStops(appearance);
  const gradientOn = isGradientActive(appearance);
  const displayName = appearance.appName?.trim() || "Your application";

  return (
    <div className="border-t border-ui p-5">
      <p className="mb-1 text-xs text-faint">
        {title} ({previewMode} mode{gradientOn ? ", gradient on" : ""})
      </p>
      <div
        className="rounded-xl border p-4"
        style={{
          background: resolved.background,
          color: resolved.text,
          borderRadius: resolved.radius,
        }}
      >
        <div className="flex items-center gap-3">
          {appearance.logoUrl ? (
            // eslint-disable-next-line @next/next/no-img-element
            <img src={appearance.logoUrl} alt="" className="h-8 w-8 rounded object-contain" />
          ) : (
            <div
              className="flex h-8 w-8 items-center justify-center rounded text-xs font-bold"
              style={{ background: `${resolved.primary}22`, color: resolved.primary }}
            >
              App
            </div>
          )}
          <p className="font-semibold" style={{ color: resolved.primary }}>
            {displayName}
          </p>
        </div>

        <div
          className="mt-4 rounded-lg border p-3"
          style={{
            background: resolved.surface,
            borderColor: appearance.dividerColor,
            borderRadius: resolved.radius,
          }}
        >
          <p className="text-sm" style={{ color: appearance.mutedText }}>
            Sign in to continue
          </p>
          <input
            readOnly
            className="mt-2 w-full rounded border px-3 py-2 text-sm"
            style={{
              background: appearance.inputBackground,
              color: appearance.inputText,
              borderColor: appearance.inputBorder,
              borderRadius: resolved.radius,
            }}
            placeholder="Email"
          />
          <p className="mt-1 text-xs" style={{ color: appearance.inputPlaceholder }}>
            Placeholder style
          </p>
          <button
            type="button"
            className="mt-3 rounded-lg px-3 py-1.5 text-sm font-medium"
            style={{
              background: resolved.brandFill,
              color: resolved.buttonText,
              borderRadius: resolved.radius,
            }}
          >
            Continue
          </button>
        </div>

        <div
          className="relative mt-4 rounded-lg border p-4"
          style={{
            background: appearance.dialogBackground,
            color: appearance.dialogText,
            borderColor: appearance.dialogBorder,
            borderRadius: resolved.radius,
            boxShadow: `0 8px 24px ${appearance.dialogOverlay}`,
          }}
        >
          <p className="text-sm font-medium">Dialog sample</p>
          <p className="mt-1 text-xs" style={{ color: appearance.mutedText }}>
            Modal content uses dialog colors.
          </p>
        </div>

        <div className="mt-4 flex flex-wrap items-center gap-3">
          <div
            className="rounded-lg border px-3 py-1.5 text-sm"
            style={{
              background: appearance.surfaceColor,
              color: appearance.textColor,
              borderColor: appearance.inputBorder,
            }}
          >
            Dropdown item
          </div>
          <span
            className="rounded px-2 py-1 text-xs"
            style={{
              background: appearance.surfaceColor,
              color: appearance.mutedText,
            }}
          >
            Tooltip
          </span>
        </div>
      </div>
      {gradientOn && (
        <div
          className="mt-3 h-6 rounded-lg"
          style={{ background: `linear-gradient(${gradient.angle}deg, ${gradient.from}, ${gradient.to})` }}
        />
      )}
    </div>
  );
}

export { applyGradientPreset, isGradientActive };
