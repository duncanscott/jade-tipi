'use client';

import { useEffect, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { useSession } from 'next-auth/react';
import Link from 'next/link';
import AuthButton from '@/components/AuthButton';
import {
  ObjectPropertyValues,
  TypeEffectiveProperties,
  PlateContents,
  PlateContentsEntry,
  LocationContents,
  displayName,
  getLocationPropertyValues,
  getTypeEffectiveProperties,
  getPlateContents,
  getLocationContents,
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

function WellEntryChip({ entry }: { entry: PlateContentsEntry }) {
  const label = entry.entity
    ? displayName(entry.entity.properties, entry.object_id)
    : idSuffix(entry.object_id);
  return (
    <span
      title={entry.object_id}
      style={{
        display: 'inline-block',
        padding: '0.1rem 0.35rem',
        margin: '0.1rem',
        background: 'rgba(59, 130, 246, 0.12)',
        border: '1px solid rgba(59, 130, 246, 0.4)',
        borderRadius: '0.35rem',
        fontSize: '0.7rem',
        color: 'var(--text)',
        maxWidth: '9rem',
        overflow: 'hidden',
        textOverflow: 'ellipsis',
        whiteSpace: 'nowrap',
        verticalAlign: 'middle',
      }}
    >
      {label}
    </span>
  );
}

export default function ContainerViewPage() {
  const params = useParams();
  const router = useRouter();
  const { data: session, status: authStatus } = useSession();
  const accessToken = session?.accessToken;

  const containerId = decodeURIComponent(params.id as string);

  const [root, setRoot] = useState<ObjectPropertyValues | null>(null);
  const [typeInfo, setTypeInfo] = useState<TypeEffectiveProperties | null>(null);
  const [plate, setPlate] = useState<PlateContents | null>(null);
  const [flatContents, setFlatContents] = useState<LocationContents | null>(null);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!containerId || !accessToken) return;

    let cancelled = false;
    async function load() {
      try {
        setLoading(true);
        setError(null);
        setNotFound(false);
        const token = accessToken as string;
        const [rootResult, plateResult, contentsResult] = await Promise.all([
          getLocationPropertyValues(containerId, token),
          getPlateContents(containerId, token),
          getLocationContents(containerId, token),
        ]);
        if (cancelled) return;
        if (!rootResult) {
          setNotFound(true);
          setRoot(null);
          setPlate(null);
          setFlatContents(null);
          return;
        }
        setRoot(rootResult);
        setPlate(plateResult);
        setFlatContents(contentsResult);
        if (rootResult.type_id) {
          const typeResult = await getTypeEffectiveProperties(rootResult.type_id, token);
          if (!cancelled) setTypeInfo(typeResult);
        } else {
          setTypeInfo(null);
        }
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : 'Failed to load container');
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    load();
    return () => { cancelled = true; };
  }, [containerId, accessToken]);

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
          Sign in to view containers
        </h1>
        <p style={{ color: 'var(--muted)', marginBottom: '1.5rem' }}>
          Authenticate with Keycloak to inspect materialized containers and their contents.
        </p>
        <AuthButton />
      </div>
    );
  }

  if (loading) {
    return (
      <div style={{ padding: '1rem' }}>
        <p style={{ color: 'var(--muted)' }}>Loading container...</p>
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
          Container not materialized
        </h1>
        <p style={{ color: 'var(--muted)' }}>
          No <code>loc</code> root exists for
        </p>
        <p style={monoStyle}>{containerId}</p>
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
  const isPlate = !!(plate && plate.row_count && plate.column_count);
  const flatEntries = flatContents?.contents || [];

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

      <section style={panelStyle} aria-label="Contents">
        <h2 style={{ margin: '0 0 0.75rem 0', fontSize: '1.05rem', fontWeight: 600 }}>
          Contents
        </h2>

        {isPlate && plate && (
          <>
            <div style={{ overflowX: 'auto' }}>
              <table style={{ borderCollapse: 'collapse' }}>
                <thead>
                  <tr>
                    <th style={{ ...thStyle, borderBottom: 'none' }} />
                    {plate.column_labels.map((column) => (
                      <th key={column} style={{ ...thStyle, textAlign: 'center', borderBottom: 'none' }}>
                        {column}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {plate.row_labels.map((row) => (
                    <tr key={row}>
                      <th style={{ ...thStyle, borderBottom: 'none' }}>{row}</th>
                      {plate.column_labels.map((column) => {
                        const well = plate.wells.find(
                          (candidate) => candidate.row === row && candidate.column === column);
                        const occupants = well?.contents || [];
                        return (
                          <td
                            key={`${row}${column}`}
                            style={{
                              border: '1px solid var(--border)',
                              minWidth: '4.5rem',
                              height: '2.6rem',
                              padding: '0.15rem',
                              textAlign: 'center',
                              background: occupants.length > 0
                                ? 'rgba(59, 130, 246, 0.05)'
                                : 'transparent',
                            }}
                          >
                            {occupants.map((entry) => (
                              <WellEntryChip key={entry.link_id} entry={entry} />
                            ))}
                          </td>
                        );
                      })}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            {plate.unplaced_contents.length > 0 && (
              <div style={{ marginTop: '0.75rem' }}>
                <h3 style={{ margin: '0 0 0.4rem 0', fontSize: '0.875rem', color: 'var(--muted)' }}>
                  Unplaced contents
                </h3>
                {plate.unplaced_contents.map((entry) => (
                  <p key={entry.link_id} style={{ margin: '0.2rem 0', fontSize: '0.875rem' }}>
                    <WellEntryChip entry={entry} />
                    {entry.unplaced_reason && (
                      <span style={{ color: 'var(--muted)', fontSize: '0.75rem' }}>
                        {' '}({entry.unplaced_reason})
                      </span>
                    )}
                  </p>
                ))}
              </div>
            )}
          </>
        )}

        {!isPlate && (
          <>
            {flatEntries.length === 0 && (
              <p style={{ color: 'var(--muted)', margin: 0 }}>No contents.</p>
            )}
            {flatEntries.length > 0 && (
              <div style={{ overflowX: 'auto' }}>
                <table style={{ width: '100%', borderCollapse: 'collapse' }}>
                  <thead>
                    <tr>
                      <th style={thStyle}>Content</th>
                      <th style={thStyle}>Kind</th>
                      <th style={thStyle}>Position</th>
                      <th style={thStyle}>ID</th>
                    </tr>
                  </thead>
                  <tbody>
                    {flatEntries.map((entry) => {
                      const isLocation = !!entry.content_location;
                      const label = isLocation
                        ? displayName(entry.content_location?.properties, entry.content_id)
                        : displayName(entry.content_entity?.properties, entry.content_id);
                      return (
                        <tr key={entry.link_id}>
                          <td style={tdStyle}>
                            {isLocation ? (
                              <Link
                                href={`/containers/${encodeURIComponent(entry.content_id)}`}
                                style={{ color: '#3b82f6' }}
                              >
                                {label}
                              </Link>
                            ) : (
                              label
                            )}
                          </td>
                          <td style={tdStyle}>
                            <code>{isLocation ? 'loc' : 'ent'}</code>
                          </td>
                          <td style={tdStyle}>
                            {entry.position ? <ValueCell value={entry.position} /> : '—'}
                          </td>
                          <td style={{ ...tdStyle, ...monoStyle }}>{entry.content_id}</td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </>
        )}
      </section>
    </div>
  );
}
