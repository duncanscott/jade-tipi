'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useTools } from '@/components/layout/ToolsContext';
import { listDocuments, DocumentSummary } from '@/lib/api';

export default function ListPage() {
  const router = useRouter();
  const [documents, setDocuments] = useState<DocumentSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Load document list on mount
  useEffect(() => {
    async function loadDocuments() {
      try {
        setLoading(true);
        const docs = await listDocuments();
        setDocuments(docs);
        setError(null);
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Failed to load documents');
      } finally {
        setLoading(false);
      }
    }
    loadDocuments();
  }, []);

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
      {loading && <p style={{ color: 'var(--muted)' }}>Loading...</p>}
      {error && <p style={{ color: '#ef4444' }}>{error}</p>}
      {!loading && documents.length === 0 && (
        <p style={{ color: 'var(--muted)' }}>No documents found</p>
      )}
      {!loading && documents.length > 0 && (
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
    [documents, loading, error]
  );

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
