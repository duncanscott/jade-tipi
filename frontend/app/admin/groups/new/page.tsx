'use client';

import { useRouter } from 'next/navigation';
import { useSession } from 'next-auth/react';
import { useState } from 'react';
import { createAdminGroup, GroupCreateRequest } from '@/lib/admin-groups';
import GroupFormFields, { GroupFormState } from '@/components/admin/GroupFormFields';
import AuthButton from '@/components/AuthButton';

export default function NewGroupPage() {
  const router = useRouter();
  const { data: session, status: authStatus } = useSession();
  const accessToken = session?.accessToken;
  const isAdmin = session?.isAdmin === true;

  const [form, setForm] = useState<GroupFormState>({
    id: '',
    name: '',
    description: '',
    permissionEntries: [],
  });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!accessToken) return;
    setSubmitting(true);
    setError(null);
    try {
      const permissions: Record<string, 'rw' | 'r'> = {};
      for (const entry of form.permissionEntries) {
        if (!entry.grpId.trim()) continue;
        permissions[entry.grpId.trim()] = entry.access;
      }
      const request: GroupCreateRequest = {
        name: form.name.trim(),
        description: form.description.trim() || undefined,
        permissions,
      };
      if (form.id.trim()) {
        request.id = form.id.trim();
      }
      const created = await createAdminGroup(request, accessToken);
      router.push(`/admin/groups/${encodeURIComponent(created.id)}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to create group');
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

  return (
    <div style={{ padding: '2rem', maxWidth: '720px' }}>
      <h1 style={{ fontSize: '1.75rem', fontWeight: 600, marginBottom: '1rem' }}>Create group</h1>

      <form onSubmit={handleSubmit} data-testid="admin-group-create-form">
        <GroupFormFields form={form} onChange={setForm} mode="create" />

        {error && (
          <p style={{ color: '#ef4444', marginTop: '1rem' }} data-testid="admin-group-create-error">
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
            data-testid="admin-group-create-submit"
          >
            {submitting ? 'Creating...' : 'Create group'}
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
            Cancel
          </button>
        </div>
      </form>
    </div>
  );
}
