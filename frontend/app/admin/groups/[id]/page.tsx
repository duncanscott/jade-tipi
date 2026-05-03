'use client';

import { useRouter } from 'next/navigation';
import { useEffect, useState, use } from 'react';
import { useSession } from 'next-auth/react';
import {
  getAdminGroup,
  updateAdminGroup,
  GroupRecord,
  GroupUpdateRequest,
} from '@/lib/admin-groups';
import GroupFormFields, { GroupFormState } from '@/components/admin/GroupFormFields';
import AuthButton from '@/components/AuthButton';

export default function EditGroupPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const router = useRouter();
  const { data: session, status: authStatus } = useSession();
  const accessToken = session?.accessToken;
  const isAdmin = session?.isAdmin === true;

  const [record, setRecord] = useState<GroupRecord | null>(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [form, setForm] = useState<GroupFormState>({
    id: '',
    name: '',
    description: '',
    permissionEntries: [],
  });

  useEffect(() => {
    async function load() {
      if (!accessToken || !isAdmin) {
        return;
      }
      try {
        setLoading(true);
        setError(null);
        const fetched = await getAdminGroup(id, accessToken);
        if (!fetched) {
          setError('Group not found');
          setRecord(null);
          return;
        }
        setRecord(fetched);
        setForm({
          id: fetched.id,
          name: fetched.name ?? '',
          description: fetched.description ?? '',
          permissionEntries: Object.entries(fetched.permissions ?? {}).map(([grpId, access]) => ({
            grpId,
            access,
          })),
        });
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Failed to load group');
      } finally {
        setLoading(false);
      }
    }
    load();
  }, [id, accessToken, isAdmin]);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!accessToken || !record) return;
    setSubmitting(true);
    setError(null);
    try {
      const permissions: Record<string, 'rw' | 'r'> = {};
      for (const entry of form.permissionEntries) {
        if (!entry.grpId.trim()) continue;
        permissions[entry.grpId.trim()] = entry.access;
      }
      const request: GroupUpdateRequest = {
        name: form.name.trim(),
        description: form.description.trim() || undefined,
        permissions,
      };
      const updated = await updateAdminGroup(record.id, request, accessToken);
      setRecord(updated);
      setForm({
        id: updated.id,
        name: updated.name ?? '',
        description: updated.description ?? '',
        permissionEntries: Object.entries(updated.permissions ?? {}).map(([grpId, access]) => ({
          grpId,
          access,
        })),
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to update group');
    } finally {
      setSubmitting(false);
    }
  }

  if (authStatus === 'loading') {
    return <div style={{ padding: '2rem' }}><p>Checking your session...</p></div>;
  }

  if (authStatus !== 'authenticated' || !accessToken) {
    return (
      <div style={{ padding: '2rem', textAlign: 'center' }}>
        <h1>Sign in required</h1>
        <AuthButton />
      </div>
    );
  }

  if (!isAdmin) {
    return (
      <div style={{ padding: '2rem', textAlign: 'center' }}>
        <h1>Forbidden</h1>
        <p>Your account does not have the <code>jade-tipi-admin</code> role.</p>
      </div>
    );
  }

  if (loading) {
    return <div style={{ padding: '2rem' }}><p>Loading group...</p></div>;
  }

  if (!record) {
    return (
      <div style={{ padding: '2rem' }}>
        <p style={{ color: '#ef4444' }} data-testid="admin-group-edit-not-found">
          {error ?? 'Group not found'}
        </p>
        <button
          type="button"
          onClick={() => router.push('/admin/groups')}
          style={{
            marginTop: '1rem',
            padding: '0.5rem 1rem',
            background: 'transparent',
            border: '1px solid var(--border, #d1d5db)',
            borderRadius: '0.375rem',
            cursor: 'pointer',
          }}
        >
          Back to groups
        </button>
      </div>
    );
  }

  return (
    <div style={{ padding: '2rem', maxWidth: '720px' }}>
      <h1 style={{ fontSize: '1.75rem', fontWeight: 600, marginBottom: '0.25rem' }}>Edit group</h1>
      <p style={{ fontFamily: 'monospace', fontSize: '0.875rem', color: 'var(--muted, #6b7280)', marginBottom: '1rem' }}>
        {record.id}
      </p>

      <form onSubmit={handleSubmit} data-testid="admin-group-edit-form">
        <GroupFormFields form={form} onChange={setForm} mode="edit" />

        {error && (
          <p style={{ color: '#ef4444', marginTop: '1rem' }} data-testid="admin-group-edit-error">
            {error}
          </p>
        )}

        <div style={{ marginTop: '1.5rem', display: 'flex', gap: '0.75rem' }}>
          <button
            type="submit"
            disabled={submitting || !form.name.trim()}
            style={{
              padding: '0.5rem 1rem',
              background: '#2563eb',
              color: 'white',
              border: 'none',
              borderRadius: '0.375rem',
              fontWeight: 500,
              cursor: 'pointer',
              opacity: submitting || !form.name.trim() ? 0.6 : 1,
            }}
            data-testid="admin-group-edit-submit"
          >
            {submitting ? 'Saving...' : 'Save changes'}
          </button>
          <button
            type="button"
            onClick={() => router.push('/admin/groups')}
            style={{
              padding: '0.5rem 1rem',
              background: 'transparent',
              border: '1px solid var(--border, #d1d5db)',
              borderRadius: '0.375rem',
              cursor: 'pointer',
            }}
          >
            Back
          </button>
        </div>
      </form>

      {record.head?.provenance && (
        <details style={{ marginTop: '2rem' }}>
          <summary style={{ cursor: 'pointer', fontWeight: 500 }}>Audit / provenance</summary>
          <pre style={{
            marginTop: '0.5rem',
            padding: '0.75rem',
            background: 'var(--surface, #f3f4f6)',
            borderRadius: '0.375rem',
            fontSize: '0.75rem',
            overflowX: 'auto',
          }} data-testid="admin-group-edit-provenance">
            {JSON.stringify(record.head.provenance, null, 2)}
          </pre>
        </details>
      )}
    </div>
  );
}
