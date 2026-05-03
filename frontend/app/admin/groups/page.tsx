'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { useSession } from 'next-auth/react';
import { listAdminGroups, GroupRecord } from '@/lib/admin-groups';
import AuthButton from '@/components/AuthButton';

export default function AdminGroupsPage() {
  const { data: session, status: authStatus } = useSession();
  const accessToken = session?.accessToken;
  const isAdmin = session?.isAdmin === true;

  const [groups, setGroups] = useState<GroupRecord[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    async function load() {
      if (!accessToken || !isAdmin) {
        return;
      }
      try {
        setLoading(true);
        setError(null);
        const items = await listAdminGroups(accessToken);
        setGroups(items);
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Failed to load groups');
      } finally {
        setLoading(false);
      }
    }
    load();
  }, [accessToken, isAdmin]);

  if (authStatus === 'loading') {
    return (
      <div style={{ padding: '2rem' }} data-testid="admin-groups-loading">
        <p>Checking your session...</p>
      </div>
    );
  }

  if (authStatus !== 'authenticated' || !accessToken) {
    return (
      <div style={{ padding: '2rem', textAlign: 'center' }} data-testid="admin-groups-signin">
        <h1>Sign in required</h1>
        <p>Authenticate with Keycloak to manage groups.</p>
        <AuthButton />
      </div>
    );
  }

  if (!isAdmin) {
    return (
      <div style={{ padding: '2rem', textAlign: 'center' }} data-testid="admin-groups-forbidden">
        <h1>Forbidden</h1>
        <p>Your account does not have the <code>jade-tipi-admin</code> role.</p>
      </div>
    );
  }

  return (
    <div style={{ padding: '2rem' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem' }}>
        <h1 style={{ fontSize: '1.75rem', fontWeight: 600 }}>Groups</h1>
        <Link
          href="/admin/groups/new"
          style={{
            padding: '0.5rem 1rem',
            background: '#2563eb',
            color: 'white',
            borderRadius: '0.375rem',
            textDecoration: 'none',
            fontWeight: 500,
          }}
          data-testid="admin-groups-new-link"
        >
          New group
        </Link>
      </div>

      {loading && <p data-testid="admin-groups-loading-list">Loading groups...</p>}
      {error && <p style={{ color: '#ef4444' }} data-testid="admin-groups-error">{error}</p>}

      {!loading && !error && groups.length === 0 && (
        <p style={{ color: 'var(--muted, #6b7280)' }} data-testid="admin-groups-empty">
          No groups yet. Create the first one.
        </p>
      )}

      {!loading && groups.length > 0 && (
        <ul
          data-testid="admin-groups-list"
          style={{ listStyle: 'none', padding: 0, margin: 0, display: 'flex', flexDirection: 'column', gap: '0.5rem' }}
        >
          {groups.map((g) => (
            <li
              key={g.id}
              style={{
                padding: '0.75rem',
                border: '1px solid var(--border, #e5e7eb)',
                borderRadius: '0.5rem',
              }}
            >
              <Link
                href={`/admin/groups/${encodeURIComponent(g.id)}`}
                style={{ textDecoration: 'none', color: 'inherit' }}
              >
                <div style={{ fontWeight: 600 }}>{g.name}</div>
                <div style={{ fontSize: '0.75rem', fontFamily: 'monospace', color: 'var(--muted, #6b7280)' }}>
                  {g.id}
                </div>
                {g.description && (
                  <div style={{ fontSize: '0.875rem', marginTop: '0.25rem' }}>{g.description}</div>
                )}
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
