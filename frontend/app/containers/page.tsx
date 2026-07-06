'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { useSession } from 'next-auth/react';
import AuthButton from '@/components/AuthButton';

export default function ContainersEntryPage() {
  const router = useRouter();
  const { status: authStatus } = useSession();
  const [containerId, setContainerId] = useState('');

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
    </div>
  );
}
