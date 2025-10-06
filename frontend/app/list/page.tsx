'use client';

import { useEffect, useState } from 'react';
import { useTools } from '@/components/layout/ToolsContext';
import { listDocuments, getDocument, DocumentSummary } from '@/lib/api';

export default function ListPage() {
  const [documents, setDocuments] = useState<DocumentSummary[]>([]);
  const [selectedDoc, setSelectedDoc] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [loadingDoc, setLoadingDoc] = useState(false);
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

  // Load full document when clicked
  async function handleDocumentClick(id: string) {
    try {
      setLoadingDoc(true);
      const doc = await getDocument(id);
      setSelectedDoc(doc);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load document');
    } finally {
      setLoadingDoc(false);
    }
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
                  background: selectedDoc?._id === doc._id ? 'rgba(59, 130, 246, 0.1)' : 'var(--surface)',
                  border: `1px solid ${selectedDoc?._id === doc._id ? '#3b82f6' : 'var(--border)'}`,
                  borderRadius: '0.5rem',
                  color: 'var(--text)',
                  cursor: 'pointer',
                  transition: 'all 0.15s ease',
                }}
                onMouseEnter={(e) => {
                  if (selectedDoc?._id !== doc._id) {
                    e.currentTarget.style.background = 'rgba(255, 255, 255, 0.03)';
                    e.currentTarget.style.borderColor = '#475569';
                  }
                }}
                onMouseLeave={(e) => {
                  if (selectedDoc?._id !== doc._id) {
                    e.currentTarget.style.background = 'var(--surface)';
                    e.currentTarget.style.borderColor = 'var(--border)';
                  }
                }}
              >
                <div style={{
                  fontSize: '0.875rem',
                  fontWeight: 600,
                  marginBottom: '0.25rem',
                  color: selectedDoc?._id === doc._id ? '#3b82f6' : 'var(--text)'
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
    [documents, loading, error, selectedDoc]
  );

  return (
    <div style={{ padding: '1rem' }}>
      {loadingDoc && (
        <p style={{ color: 'var(--muted)' }}>Loading document...</p>
      )}
      {!selectedDoc && !loadingDoc && (
        <div style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          minHeight: '50vh',
          color: 'var(--muted)'
        }}>
          Select a document from the list to view its contents
        </div>
      )}
      {selectedDoc && !loadingDoc && (
        <div>
          <div style={{
            marginBottom: '1rem',
            paddingBottom: '1rem',
            borderBottom: '1px solid var(--border)'
          }}>
            <h1 style={{
              margin: '0 0 0.5rem 0',
              fontSize: '1.5rem',
              fontWeight: 600
            }}>
              {selectedDoc.name || 'Untitled Document'}
            </h1>
            <p style={{
              margin: 0,
              fontSize: '0.875rem',
              color: 'var(--muted)',
              fontFamily: 'monospace'
            }}>
              ID: {selectedDoc._id}
            </p>
          </div>
          <pre style={{
            background: 'var(--surface)',
            border: '1px solid var(--border)',
            borderRadius: '0.5rem',
            padding: '1rem',
            overflow: 'auto',
            fontSize: '0.875rem',
            lineHeight: '1.5'
          }}>
            {JSON.stringify(selectedDoc, null, 2)}
          </pre>
        </div>
      )}
    </div>
  );
}
