'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useSession } from 'next-auth/react';
import { useTools } from '@/components/layout/ToolsContext';
import { listDocuments, DocumentSummary } from '@/lib/api';
import AuthButton from '@/components/AuthButton';

export default function ListPage() {
  const router = useRouter();
  const [documents, setDocuments] = useState<DocumentSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const { data: session, status: authStatus } = useSession();
  const accessToken = session?.accessToken;

  // Load document list on mount
  useEffect(() => {
    async function loadDocuments() {
      if (!accessToken) {
        return;
      }

      try {
        setLoading(true);
        const docs = await listDocuments(accessToken);
        setDocuments(docs);
        setError(null);
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Failed to load documents');
      } finally {
        setLoading(false);
      }
    }
    loadDocuments();
  }, [accessToken]);

  // Navigate to document detail page when clicked
  function handleDocumentClick(id: string) {
    router.push(`/list/${id}`);
  }

  // Set tools sidebar content
  useTools(
    <div>
      <h2 style={{ margin: '0 0 1rem 0', fontSize: '1.25rem', fontWeight: 600 }}>
        Documents
      </h2>
      {authStatus === 'loading' && <p style={{ color: 'var(--muted)' }}>Checking session...</p>}
      {authStatus !== 'authenticated' && authStatus !== 'loading' && (
        <p style={{ color: 'var(--muted)' }}>Sign in to view your documents.</p>
      )}
      {authStatus === 'authenticated' && loading && <p style={{ color: 'var(--muted)' }}>Loading...</p>}
      {authStatus === 'authenticated' && error && <p style={{ color: '#ef4444' }}>{error}</p>}
      {authStatus === 'authenticated' && !loading && documents.length === 0 && (
        <p style={{ color: 'var(--muted)' }}>No documents found</p>
      )}
      {authStatus === 'authenticated' && !loading && documents.length > 0 && (
        <ul style={{
          listStyle: 'none',
          padding: 0,
          margin: 0,
          display: 'flex',
          flexDirection: 'column',
          gap: '0.5rem'
        }}>
          {documents.map((doc) => (
            <li key={doc._id}>
              <button
                onClick={() => handleDocumentClick(doc._id)}
                style={{
                  width: '100%',
                  textAlign: 'left',
                  padding: '0.75rem',
                  background: 'var(--surface)',
                  border: '1px solid var(--border)',
                  borderRadius: '0.5rem',
                  color: 'var(--text)',
                  cursor: 'pointer',
                  transition: 'all 0.15s ease',
                }}
                onMouseEnter={(e) => {
                  e.currentTarget.style.background = 'rgba(255, 255, 255, 0.03)';
                  e.currentTarget.style.borderColor = '#475569';
                }}
                onMouseLeave={(e) => {
                  e.currentTarget.style.background = 'var(--surface)';
                  e.currentTarget.style.borderColor = 'var(--border)';
                }}
              >
                <div style={{
                  fontSize: '0.875rem',
                  fontWeight: 600,
                  marginBottom: '0.25rem',
                  color: 'var(--text)'
                }}>
                  {doc.name || 'Untitled'}
                </div>
                <div style={{
                  fontSize: '0.75rem',
                  color: 'var(--muted)',
                  fontFamily: 'monospace'
                }}>
                  {doc._id}
                </div>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>,
    [documents, loading, error, authStatus]
  );

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
          Sign in to view documents
        </h1>
        <p style={{ color: 'var(--muted)', marginBottom: '1.5rem' }}>
          Authenticate with Keycloak to list and browse stored documents.
        </p>
        <AuthButton />
      </div>
    );
  }

  return (
    <div style={{ padding: '1rem' }}>
      <div style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        minHeight: '50vh',
        color: 'var(--muted)'
      }}>
        Select a document from the list to view its contents
      </div>
    </div>
  );
}
