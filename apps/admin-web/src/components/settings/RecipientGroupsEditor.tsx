"use client";

import { Button } from "@/components/ui/Button";
import { FieldRow, Input } from "@/components/ui/Field";
import type { RecipientGroups } from "@/lib/api/settings";

export function RecipientGroupsEditor({
  groups,
  onChange,
  disabled,
}: {
  groups: RecipientGroups;
  onChange: (next: RecipientGroups) => void;
  disabled?: boolean;
}) {
  const entries = Object.entries(groups);

  function updateGroup(name: string, emails: string) {
    const list = emails
      .split(/[,;\s]+/)
      .map((e) => e.trim().toLowerCase())
      .filter(Boolean);
    onChange({ ...groups, [name]: list });
  }

  function addGroup() {
    let index = entries.length + 1;
    let name = `group-${index}`;
    while (groups[name]) {
      index += 1;
      name = `group-${index}`;
    }
    onChange({ ...groups, [name]: [] });
  }

  function removeGroup(name: string) {
    const next = { ...groups };
    delete next[name];
    onChange(next);
  }

  function renameGroup(oldName: string, newName: string) {
    const trimmed = newName.trim().toLowerCase().replace(/\s+/g, "-");
    if (!trimmed || trimmed === oldName) return;
    const next: RecipientGroups = {};
    for (const [key, value] of Object.entries(groups)) {
      next[key === oldName ? trimmed : key] = value;
    }
    onChange(next);
  }

  return (
    <div className="space-y-3">
      {entries.length === 0 && (
        <p className="text-xs text-muted">No groups yet. Add a named list for CC/BCC or admin alerts.</p>
      )}
      {entries.map(([name, emails]) => (
        <div key={name} className="rounded-lg border border-ui p-3">
          <div className="mb-2 flex flex-wrap items-center gap-2">
            <Input
              className="max-w-[200px]"
              value={name}
              disabled={disabled}
              onChange={(e) => renameGroup(name, e.target.value)}
              aria-label="Group name"
            />
            <Button
              variant="ghost"
              size="sm"
              disabled={disabled}
              onClick={() => removeGroup(name)}
            >
              Remove
            </Button>
          </div>
          <FieldRow label="Emails" hint="Comma-separated">
            <Input
              value={(emails ?? []).join(", ")}
              disabled={disabled}
              onChange={(e) => updateGroup(name, e.target.value)}
              placeholder="Email addresses, comma-separated"
            />
          </FieldRow>
        </div>
      ))}
      <Button variant="secondary" size="sm" disabled={disabled} onClick={addGroup}>
        Add group
      </Button>
    </div>
  );
}
