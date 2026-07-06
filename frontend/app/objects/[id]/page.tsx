'use client';

import { useEffect, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { useSession } from 'next-auth/react';
import Link from 'next/link';
import AuthButton from '@/components/AuthButton';
import {
  ObjectPropertyValues,
  TypeEffectiveProperties,
  ObjectLocations,
  displayName,
  getEntityPropertyValues,
  getTypeEffectiveProperties,
  getObjectLocations,
} from '@/lib/containers';

const panelStyle: React.CSSProperties = {
  background: 'var(--surface)',
  border: '1px solid var(--border)',
  borderRadius: '0.5rem',
  padding: '1rem',
  marginBottom: '1rem',
};

const thStyle: React.CSSProperties = {
  textAlign: 'left',
  padding: '0.4rem 0.6rem',
  borderBottom: '1px solid var(--border)',
  color: 'var(--muted)',
  fontWeight: 600,
  fontSize: '0.75rem',
  textTransform: 'uppercase',
  letterSpacing: '0.04em',
};

const tdStyle: React.CSSProperties = {
  padding: '0.4rem 0.6rem',
  borderBottom: '1px solid var(--border)',
  fontSize: '0.875rem',
  verticalAlign: 'top',
};

const monoStyle: React.CSSProperties = {
  fontFamily: 'monospace',
  fontSize: '0.75rem',
  color: 'var(--muted)',
  wordBreak: 'break-all',
};

function idSuffix(id: string | null | undefined): string {
  if (!id) return '';
  const segments = id.split('~');
  return segments[segments.length - 1] || id;
}

function ValueCell({ value }: { value: Record<string, unknown> }) {
  return (
    <code style={{ fontSize: '0.8rem', whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
      {JSON.stringify(value)}
    </code>
  );
}

export default function ObjectViewPage() {
  const params = useParams();
  const router = useRouter();
  const { data: session, status: authStatus } = useSession();
  const accessToken = session?.accessToken;

  const objectId = decodeURIComponent(params.id as string);

  const [root, setRoot] = useState<ObjectPropertyValues | null>(null);
  const [typeInfo, setTypeInfo] = useState<TypeEffectiveProperties | null>(null);
  const [objectLocations, setObjectLocations] = useState<ObjectLocations | null>(null);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!objectId || !accessToken) return;

    let cancelled = false;
    async function load() {
      try {
        setLoading(true);
        setError(null);
        setNotFound(false);
        const token = accessToken as string;
        const [rootResult, locationsResult] = await Promise.all([
          getEntityPropertyValues(objectId, token),
          getObjectLocations(objectId, token),
        ]);
        if (cancelled) return;
        if (!rootResult) {
          setNotFound(true);
          setRoot(null);
          setObjectLocations(null);
          return;
        }
        setRoot(rootResult);
        setObjectLocations(locationsResult);
        if (rootResult.type_id) {
          const typeResult = await getTypeEffectiveProperties(rootResult.type_id, token);
          if (!cancelled) setTypeInfo(typeResult);
        } else {
          setTypeInfo(null);
        }
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : 'Failed to load object');
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    load();
    return () => { cancelled = true; };
  }, [objectId, accessToken]);

  if (authStatus === 'loading') {
    return (
      <div style={{ padding: '1rem' }}>
        <p style={{ color: 'var(--muted)' }}>Checking your session...</p>
      </div>
    );
  }

  if (authStatus !== 'authenticated' || !accessToken) {
    return (
      <div style={{ padding: '1rem', textAlign: 'center' }}>
        <h1 style={{ fontSize: '1.75rem', fontWeight: 600, marginBottom: '1rem' }}>
          Sign in to view objects
        </h1>
        <p style={{ color: 'var(--muted)', marginBottom: '1.5rem' }}>
          Authenticate with Keycloak to inspect materialized objects.
        </p>
        <AuthButton />
      </div>
    );
  }

  if (loading) {
    return (
      <div style={{ padding: '1rem' }}>
        <p style={{ color: 'var(--muted)' }}>Loading object...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div style={{ padding: '1rem' }}>
        <p style={{ color: '#ef4444' }}>{error}</p>
        <button
          onClick={() => router.push('/containers')}
          style={{
            marginTop: '1rem',
            padding: '0.5rem 1rem',
            background: 'var(--surface)',
            border: '1px solid var(--border)',
            borderRadius: '0.5rem',
            color: 'var(--text)',
            cursor: 'pointer',
          }}
        >
          Back to containers
        </button>
      </div>
    );
  }

  if (notFound || !root) {
    return (
      <div style={{ padding: '1rem' }}>
        <h1 style={{ fontSize: '1.25rem', fontWeight: 600, marginBottom: '0.5rem' }}>
          Object not materialized
        </h1>
        <p style={{ color: 'var(--muted)' }}>
          No <code>ent</code> root exists for
        </p>
        <p style={monoStyle}>{objectId}</p>
        <button
          onClick={() => router.push('/containers')}
          style={{
            marginTop: '1rem',
            padding: '0.5rem 1rem',
            background: 'var(--surface)',
            border: '1px solid var(--border)',
            borderRadius: '0.5rem',
            color: 'var(--text)',
            cursor: 'pointer',
          }}
        >
          Back to containers
        </button>
      </div>
    );
  }

  const values = Object.values(root.property_values || {});
  const effectiveProps = typeInfo ? Object.values(typeInfo.effective_properties || {}) : [];
  const locations = objectLocations?.locations || [];

  return (
    <div style={{ padding: '1rem' }}>
      <div style={{ marginBottom: '1rem', paddingBottom: '1rem', borderBottom: '1px solid var(--border)' }}>
        <h1 style={{ margin: '0 0 0.35rem 0', fontSize: '1.5rem', fontWeight: 600 }}>
          {displayName(root.properties, root.object_id)}
        </h1>
        <p style={{ ...monoStyle, margin: '0 0 0.35rem 0' }}>{root.object_id}</p>
        <p style={{ margin: 0, fontSize: '0.875rem', color: 'var(--muted)' }}>
          collection <code>{root.collection}</code>
          {root.type_id && (
            <>
              {' · '}type{' '}
              <span title={root.type_id}>
                <code>{typeInfo?.type_name || idSuffix(root.type_id)}</code>
              </span>
            </>
          )}
        </p>
      </div>

      <section style={panelStyle} aria-label="Property values">
        <h2 style={{ margin: '0 0 0.75rem 0', fontSize: '1.05rem', fontWeight: 600 }}>
          Property values
        </h2>
        {values.length === 0 && (
          <p style={{ color: 'var(--muted)', margin: 0 }}>No projected property values.</p>
        )}
        {values.length > 0 && (
          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse' }}>
              <thead>
                <tr>
                  <th style={thStyle}>Property</th>
                  <th style={thStyle}>Value</th>
                  <th style={thStyle}>Transaction</th>
                  <th style={thStyle}>Commit</th>
                  <th style={thStyle}>Applied</th>
                </tr>
              </thead>
              <tbody>
                {values.map((entry) => (
                  <tr key={entry.property_id}>
                    <td style={tdStyle}>
                      <span title={entry.property_id}>
                        {entry.property_name || idSuffix(entry.property_id)}
                      </span>
                    </td>
                    <td style={tdStyle}><ValueCell value={entry.value} /></td>
                    <td style={{ ...tdStyle, ...monoStyle }}>{entry.txn_id}</td>
                    <td style={{ ...tdStyle, ...monoStyle }}>{entry.commit_id}</td>
                    <td style={{ ...tdStyle, ...monoStyle }}>{entry.applied_at}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {root.type_id && (
        <section style={panelStyle} aria-label="Type">
          <h2 style={{ margin: '0 0 0.75rem 0', fontSize: '1.05rem', fontWeight: 600 }}>
            Type
          </h2>
          {!typeInfo && (
            <p style={{ color: 'var(--muted)', margin: 0 }}>
              Type root not materialized: <span style={monoStyle}>{root.type_id}</span>
            </p>
          )}
          {typeInfo && (
            <>
              <p style={{ margin: '0 0 0.75rem 0', fontSize: '0.875rem' }}>
                {typeInfo.type_chain.map((typeId, index) => (
                  <span key={typeId} title={typeId}>
                    {index > 0 && <span style={{ color: 'var(--muted)' }}> → </span>}
                    <code>{idSuffix(typeId)}</code>
                  </span>
                ))}
              </p>
              {!typeInfo.chain_complete && (
                <p style={{
                  margin: '0 0 0.75rem 0',
                  padding: '0.5rem 0.75rem',
                  background: 'rgba(239, 68, 68, 0.1)',
                  border: '1px solid rgba(239, 68, 68, 0.4)',
                  borderRadius: '0.35rem',
                  color: '#ef4444',
                  fontSize: '0.8rem',
                }}>
                  Type chain incomplete: an ancestor type is not materialized, so the
                  effective properties below may be partial.
                </p>
              )}
              {effectiveProps.length === 0 && (
                <p style={{ color: 'var(--muted)', margin: 0 }}>No registered properties.</p>
              )}
              {effectiveProps.length > 0 && (
                <div style={{ overflowX: 'auto' }}>
                  <table style={{ width: '100%', borderCollapse: 'collapse' }}>
                    <thead>
                      <tr>
                        <th style={thStyle}>Property</th>
                        <th style={thStyle}>Registered by</th>
                        <th style={thStyle}>Reference</th>
                      </tr>
                    </thead>
                    <tbody>
                      {effectiveProps.map((prop) => (
                        <tr key={prop.property_id}>
                          <td style={tdStyle}>
                            <span title={prop.property_id}>
                              {prop.property_name || idSuffix(prop.property_id)}
                            </span>
                          </td>
                          <td style={tdStyle}>
                            <span title={prop.source_type_id}>
                              <code>{idSuffix(prop.source_type_id)}</code>
                            </span>
                          </td>
                          <td style={tdStyle}><ValueCell value={prop.reference} /></td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </>
          )}
        </section>
      )}

      <section style={panelStyle} aria-label="Located in">
        <h2 style={{ margin: '0 0 0.75rem 0', fontSize: '1.05rem', fontWeight: 600 }}>
          Located in
        </h2>
        {locations.length === 0 && (
          <p style={{ color: 'var(--muted)', margin: 0 }}>No containment links.</p>
        )}
        {locations.length > 0 && (
          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse' }}>
              <thead>
                <tr>
                  <th style={thStyle}>Container</th>
                  <th style={thStyle}>Position</th>
                  <th style={thStyle}>ID</th>
                </tr>
              </thead>
              <tbody>
                {locations.map((entry) => (
                  <tr key={entry.link_id}>
                    <td style={tdStyle}>
                      <Link
                        href={`/containers/${encodeURIComponent(entry.container_id)}`}
                        style={{ color: '#3b82f6' }}
                      >
                        {entry.container
                          ? displayName(entry.container.properties, entry.container_id)
                          : idSuffix(entry.container_id)}
                      </Link>
                    </td>
                    <td style={tdStyle}>
                      {entry.position ? <ValueCell value={entry.position} /> : '—'}
                    </td>
                    <td style={{ ...tdStyle, ...monoStyle }}>{entry.container_id}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
}
