"use client";

import { useRef, useState } from "react";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/Button";
import { useToast } from "@/components/ui/Toast";
import { isFeatureFlagComingSoon } from "@/lib/coming-soon";
import { fetchApplicationFeatureFlags } from "@/lib/api/application-settings";
import {
  exportUsersCsv,
  importUsersFile,
  previewLdapImport,
  type UserDirectorySettings,
  type UserDirectorySourceKey,
} from "@/lib/api/user-directory";
import { normalizeFeatureFlags } from "@/lib/auth-settings-normalize";
import { isFeatureEnabled } from "@/lib/feature-flags";

export function UserImportExportMenu({
  applicationId,
  directory,
}: {
  applicationId: string;
  directory: UserDirectorySettings | null;
}) {
  const router = useRouter();
  const { toast } = useToast();
  const fileRef = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [importSource, setImportSource] = useState<UserDirectorySourceKey>("csv");
  const [ldapFeatureEnabled, setLdapFeatureEnabled] = useState(false);

  useDeferredEffect(() => {
    fetchApplicationFeatureFlags(applicationId)
      // LDAP sync is not connected yet ("Coming soon"), so it is never offered here.
      .then((raw) =>
        setLdapFeatureEnabled(
          !isFeatureFlagComingSoon("ldap") && isFeatureEnabled(normalizeFeatureFlags(raw), "ldap"),
        ),
      )
      .catch(() => setLdapFeatureEnabled(false));
  }, [applicationId]);

  useDeferredEffect(() => {
    if (importSource === "ldap" && !ldapFeatureEnabled) {
      setImportSource("csv");
    }
  }, [importSource, ldapFeatureEnabled]);

  if (!directory) return null;

  const canExport = directory.exportEnabled;
  const canImport = directory.importEnabled;
  const sources = directory.sources ?? {};

  if (!canExport && !canImport) return null;

  async function handleExport() {
    setBusy("export");
    try {
      const blob = await exportUsersCsv(applicationId);
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `users-${applicationId}.csv`;
      a.click();
      URL.revokeObjectURL(url);
      toast("User export downloaded", "success");
    } catch (e) {
      toast(e instanceof Error ? e.message : "Export failed", "error");
    } finally {
      setBusy(null);
    }
  }

  async function handleFile(file: File) {
    setBusy("import");
    try {
      const result = await importUsersFile(applicationId, file, importSource);
      toast(
        `Import finished: ${result.created} created, ${result.skipped} skipped`,
        result.errors.length > 0 ? "info" : "success",
      );
      if (result.errors.length > 0) {
        console.warn("Import errors", result.errors);
      }
      router.refresh();
    } catch (e) {
      toast(e instanceof Error ? e.message : "Import failed", "error");
    } finally {
      setBusy(null);
      if (fileRef.current) fileRef.current.value = "";
    }
  }

  async function handleLdapPreview() {
    setBusy("ldap");
    try {
      const preview = await previewLdapImport(applicationId);
      toast(String(preview.message ?? "LDAP preview complete"), preview.ready ? "success" : "info");
    } catch (e) {
      toast(e instanceof Error ? e.message : "LDAP preview failed", "error");
    } finally {
      setBusy(null);
    }
  }

  const enabledSources = (["csv", "excel", "ldap"] as const).filter(
    (k) => sources[k]?.enabled && (k !== "ldap" || ldapFeatureEnabled),
  );

  return (
    <div className="flex flex-wrap items-center gap-2">
      {canExport && (
        <Button
          type="button"
          variant="secondary"
          size="sm"
          disabled={busy !== null}
          onClick={() => void handleExport()}
        >
          {busy === "export" ? "Exporting…" : "Export CSV"}
        </Button>
      )}
      {canImport && enabledSources.length > 0 && (
        <>
          {enabledSources.length > 1 && (
            <select
              value={importSource}
              onChange={(e) => setImportSource(e.target.value as UserDirectorySourceKey)}
              className="rounded-lg border border-ui bg-ui-surface px-2 py-1.5 text-xs text-ui"
              aria-label="Import source"
            >
              {sources.csv?.enabled && <option value="csv">CSV</option>}
              {sources.excel?.enabled && <option value="excel">Excel (CSV)</option>}
              {sources.ldap?.enabled && ldapFeatureEnabled && <option value="ldap">LDAP</option>}
            </select>
          )}
          {importSource !== "ldap" ? (
            <>
              <input
                ref={fileRef}
                type="file"
                accept=".csv,.txt,text/csv"
                className="hidden"
                onChange={(e) => {
                  const f = e.target.files?.[0];
                  if (f) void handleFile(f);
                }}
              />
              <Button
                type="button"
                variant="secondary"
                size="sm"
                disabled={busy !== null}
                onClick={() => fileRef.current?.click()}
              >
                {busy === "import" ? "Importing…" : "Import users"}
              </Button>
            </>
          ) : (
            <Button
              type="button"
              variant="secondary"
              size="sm"
              disabled={busy !== null}
              onClick={() => void handleLdapPreview()}
            >
              {busy === "ldap" ? "Checking…" : "Test LDAP config"}
            </Button>
          )}
        </>
      )}
    </div>
  );
}
