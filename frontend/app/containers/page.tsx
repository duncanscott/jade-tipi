'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useSession } from 'next-auth/react';
import Link from 'next/link';
import AuthButton from '@/components/AuthButton';
import { LocationBrowsePage, displayName, listLocations } from '@/lib/containers';

const PAGE_SIZE = 25;

export default function ContainersEntryPage() {
  const router = useRouter();
  const { data: session, status: authStatus } = useSession();
  const accessToken = session?.accessToken;
  const [containerId, setContainerId] = useState('');
  const [browsePage, setBrowsePage] = useState<LocationBrowsePage | null>(null);
  const [page, setPage] = useState(0);
  const [browseLoading, setBrowseLoading] = useState(true);
  const [browseError, setBrowseError] = useState<string | null>(null);

  useEffect(() => {
    if (!accessToken) return;

    let cancelled = false;
    async function loadPage() {
      try {
        setBrowseLoading(true);
        setBrowseError(null);
        const result = await listLocations(page, PAGE_SIZE, accessToken as string);
        if (!cancelled) setBrowsePage(result);
      } catch (err) {
        if (!cancelled) {
          setBrowseError(err instanceof Error ? err.message : 'Failed to browse containers');
        }
      } finally {
        if (!cancelled) setBrowseLoading(false);
      }
    }
    loadPage();
    return () => { cancelled = true; };
  }, [accessToken, page]);

  function openContainer() {
    const id = containerId.trim();
    if (id) {
      router.push(`/containers/${encodeURIComponent(id)}`);
    }
  }

  if (authStatus === 'loading') {
    return (
      <div style={{ padding: '1rem' }}>
        <p style={{ color: 'var(--muted)' }}>Checking your session...</p>
      </div>
    );
  }

  if (authStatus !== 'authenticated') {
    return (
      <div style={{ padding: '1rem', textAlign: 'center' }}>
        <h1 style={{ fontSize: '1.75rem', fontWeight: 600, marginBottom: '1rem' }}>
          Sign in to view containers
        </h1>
        <p style={{ color: 'var(--muted)', marginBottom: '1.5rem' }}>
          Authenticate with Keycloak to inspect materialized containers and their contents.
        </p>
        <AuthButton />
      </div>
    );
  }

  return (
    <div style={{ padding: '1rem', maxWidth: '52rem' }}>
      <h1 style={{ fontSize: '1.5rem', fontWeight: 600, marginBottom: '0.5rem' }}>
        Containers
      </h1>
      <p style={{ color: 'var(--muted)', marginBottom: '1.5rem' }}>
        Open a materialized container (a <code>loc</code> object) to see its typed root,
        its property values with full transaction provenance, and its contents — as a
        plate grid for plate-typed containers, or a flat list otherwise.
      </p>
      <div style={{ display: 'flex', gap: '0.5rem', marginBottom: '1rem' }}>
        <input
          value={containerId}
          onChange={(e) => setContainerId(e.target.value)}
          onKeyDown={(e) => { if (e.key === 'Enter') openContainer(); }}
          placeholder="org~grp~<uuidv7>~loc~suffix"
          aria-label="Container ID"
          style={{
            flex: 1,
            padding: '0.6rem 0.75rem',
            background: 'var(--surface)',
            border: '1px solid var(--border)',
            borderRadius: '0.5rem',
            color: 'var(--text)',
            fontFamily: 'monospace',
            fontSize: '0.875rem',
          }}
        />
        <button
          onClick={openContainer}
          disabled={!containerId.trim()}
          style={{
            padding: '0.6rem 1.25rem',
            background: containerId.trim() ? '#3b82f6' : 'var(--surface)',
            border: '1px solid var(--border)',
            borderRadius: '0.5rem',
            color: containerId.trim() ? '#ffffff' : 'var(--muted)',
            cursor: containerId.trim() ? 'pointer' : 'default',
            fontWeight: 600,
          }}
        >
          Open
        </button>
      </div>
      <p style={{ color: 'var(--muted)', fontSize: '0.875rem', lineHeight: 1.6 }}>
        Container IDs follow the object identifier convention
        (<code>&lt;org&gt;~&lt;grp&gt;~&lt;uuidv7&gt;~loc~&lt;suffix&gt;</code>).
        The kli plate runbook (<code>docs/kli-plate-runbook.md</code>) and the
        Clarity/ESP review seed both create containers you can open here.
      </p>

      <section style={{ marginTop: '2rem' }} aria-label="Browse containers">
        <h2 style={{ fontSize: '1.15rem', fontWeight: 600, marginBottom: '0.75rem' }}>
          Materialized containers
        </h2>
        {browseLoading && <p style={{ color: 'var(--muted)' }}>Loading containers...</p>}
        {browseError && <p style={{ color: '#ef4444' }}>{browseError}</p>}
        {!browseLoading && !browseError && browsePage && browsePage.items.length === 0 && (
          <p style={{ color: 'var(--muted)' }}>No containers materialized yet.</p>
        )}
        {!browseLoading && !browseError && browsePage && browsePage.items.length > 0 && (
          <>
            <ul style={{
              listStyle: 'none',
              padding: 0,
              margin: 0,
              display: 'flex',
              flexDirection: 'column',
              gap: '0.4rem',
            }}>
              {browsePage.items.map((item) => (
                <li key={item.location_id}>
                  <Link
                    href={`/containers/${encodeURIComponent(item.location_id)}`}
                    style={{
                      display: 'block',
                      padding: '0.6rem 0.75rem',
                      background: 'var(--surface)',
                      border: '1px solid var(--border)',
                      borderRadius: '0.5rem',
                      color: 'var(--text)',
                      textDecoration: 'none',
                    }}
                  >
                    <span style={{ fontWeight: 600, fontSize: '0.9rem' }}>
                      {displayName(item.name ? { name: item.name } : null, item.location_id)}
                    </span>
                    {item.description && (
                      <span style={{ color: 'var(--muted)', fontSize: '0.8rem' }}>
                        {' — '}{item.description}
                      </span>
                    )}
                    <span style={{
                      display: 'block',
                      fontFamily: 'monospace',
                      fontSize: '0.7rem',
                      color: 'var(--muted)',
                      wordBreak: 'break-all',
                    }}>
                      {item.location_id}
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
            <div style={{
              display: 'flex',
              alignItems: 'center',
              gap: '0.75rem',
              marginTop: '0.75rem',
              fontSize: '0.875rem',
            }}>
              <button
                onClick={() => setPage((current) => Math.max(current - 1, 0))}
                disabled={page === 0}
                style={{
                  padding: '0.35rem 0.85rem',
                  background: 'var(--surface)',
                  border: '1px solid var(--border)',
                  borderRadius: '0.5rem',
                  color: page === 0 ? 'var(--muted)' : 'var(--text)',
                  cursor: page === 0 ? 'default' : 'pointer',
                }}
              >
                Previous
              </button>
              <span style={{ color: 'var(--muted)' }}>
                Page {browsePage.page + 1} of {Math.max(Math.ceil(browsePage.total / browsePage.size), 1)}
                {' · '}{browsePage.total} container{browsePage.total === 1 ? '' : 's'}
              </span>
              <button
                onClick={() => setPage((current) => current + 1)}
                disabled={(browsePage.page + 1) * browsePage.size >= browsePage.total}
                style={{
                  padding: '0.35rem 0.85rem',
                  background: 'var(--surface)',
                  border: '1px solid var(--border)',
                  borderRadius: '0.5rem',
                  color: (browsePage.page + 1) * browsePage.size >= browsePage.total
                    ? 'var(--muted)' : 'var(--text)',
                  cursor: (browsePage.page + 1) * browsePage.size >= browsePage.total
                    ? 'default' : 'pointer',
                }}
              >
                Next
              </button>
            </div>
          </>
        )}
      </section>
    </div>
  );
}
