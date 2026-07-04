"use client";

import { useCallback, useState } from "react";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { FieldRow, Input, Select } from "@/components/ui/Field";
import { useToast } from "@/components/ui/Toast";
import {
  createOrganization,
  deleteOrganization,
  listOrganizations,
  type Organization,
} from "@/lib/api/organizations";

export function OrganizationsPanel({ tenantId }: { tenantId: string }) {
  const { toast } = useToast();
  const [rows, setRows] = useState<Organization[]>([]);
  const [loaded, setLoaded] = useState(false);
  const [name, setName] = useState("");
  const [slug, setSlug] = useState("");
  const [parentId, setParentId] = useState("");
  const [creating, setCreating] = useState(false);

  const reload = useCallback(async () => {
    setLoaded(false);
    try {
      setRows(await listOrganizations(tenantId));
    } catch (e) {
      toast(e instanceof Error ? e.message : "Failed to load organizations", "error");
    } finally {
      setLoaded(true);
    }
  }, [tenantId, toast]);

  useDeferredEffect(() => {
    reload();
  }, [reload]);

  async function handleCreate() {
    if (!name.trim()) return;
    setCreating(true);
    try {
      await createOrganization(tenantId, {
        name: name.trim(),
        slug: slug.trim() || undefined,
        parentId: parentId || undefined,
      });
      setName("");
      setSlug("");
      setParentId("");
      toast("Organization created", "success");
      await reload();
    } catch (e) {
      toast(e instanceof Error ? e.message : "Create failed", "error");
    } finally {
      setCreating(false);
    }
  }

  async function handleDelete(org: Organization) {
    try {
      await deleteOrganization(tenantId, org.id);
      toast("Organization deleted", "success");
      await reload();
    } catch (e) {
      toast(e instanceof Error ? e.message : "Delete failed", "error");
    }
  }

  return (
    <Card padded={false} className={!loaded ? "pointer-events-none opacity-60" : ""}>
      <CardHeader
        title="Organizations"
        description="Nested org hierarchy within a tenant (departments, business units)."
      />
      <div className="space-y-4 px-5 pb-5">
        <div className="grid gap-3 sm:grid-cols-3">
          <FieldRow label="Name">
            <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="Engineering" />
          </FieldRow>
          <FieldRow label="Slug">
            <Input value={slug} onChange={(e) => setSlug(e.target.value)} placeholder="engineering (optional)" />
          </FieldRow>
          <FieldRow label="Parent org">
            <Select value={parentId} onChange={(e) => setParentId(e.target.value)} className="w-full">
              <option value="">None (root)</option>
              {rows.map((org) => (
                <option key={org.id} value={org.id}>
                  {org.name}
                </option>
              ))}
            </Select>
          </FieldRow>
        </div>
        <Button onClick={() => void handleCreate()} disabled={creating || !name.trim()}>
          {creating ? "Creating…" : "Create organization"}
        </Button>

        {rows.length === 0 ? (
          <p className="text-sm text-muted">No organizations yet.</p>
        ) : (
          <ul className="divide-y divide-ui rounded-xl border border-ui">
            {rows.map((org) => (
              <li key={org.id} className="flex items-center justify-between gap-3 px-4 py-3">
                <div className="min-w-0">
                  <p className="text-sm font-medium text-ui">{org.name}</p>
                  <p className="text-xs text-muted">
                    {org.slug}
                    {org.parentId ? ` · parent ${org.parentId.slice(0, 8)}…` : " · root"}
                  </p>
                </div>
                <Button variant="ghost" size="sm" onClick={() => void handleDelete(org)}>
                  Delete
                </Button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </Card>
  );
}
