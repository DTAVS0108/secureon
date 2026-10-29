import { APP_NAME, withBasePath } from "@/lib/config";
import { cn } from "@/lib/cn";

/**
 * The SecureOne mark. A fixed file (public/brand/secureone-mark.svg), deliberately not tied to Appearance settings
 * or tenant/application branding: replace that file to change the mark everywhere in the console.
 */
export function BrandMark({ size = 36, className }: { size?: number; className?: string }) {
  return (
    // eslint-disable-next-line @next/next/no-img-element -- a small static SVG; next/image adds nothing here
    <img
      src={withBasePath("/brand/secureone-mark.svg")}
      alt={APP_NAME}
      width={size}
      height={size}
      className={cn("shrink-0 rounded-xl shadow-sm", className)}
    />
  );
}
