'use client';

import { useEffect, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { useSession } from 'next-auth/react';
import { useTools } from '@/components/layout/ToolsContext';
import { listDocuments, getDocument, DocumentSummary } from '@/lib/api';
import AuthButton from '@/components/AuthButton';

export default function DocumentDetailPage() {
  const params = useParams();
  const router = useRouter();
  const [documents, setDocuments] = useState<DocumentSummary[]>([]);
  const [document, setDocument] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [loadingList, setLoadingList] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [listError, setListError] = useState<string | null>(null);
  const { data: session, status: authStatus } = useSession();
  const accessToken = session?.accessToken;

  const documentId = params.id as string;

  // Load document list on mount
  useEffect(() => {
    async function loadDocumentList() {
      if (!accessToken) {
        return;
      }

      try {
        setLoadingList(true);
        const docs = await listDocuments(accessToken);
        setDocuments(docs);
        setListError(null);
      } catch (err) {
        setListError(err instanceof Error ? err.message : 'Failed to load documents');
      } finally {
        setLoadingList(false);
      }
    }
    loadDocumentList();
  }, [accessToken]);

  // Load specific document
  useEffect(() => {
    if (!documentId || !accessToken) return;

    async function loadDocument() {
      try {
        setLoading(true);
        const doc = await getDocument(documentId, accessToken);
        setDocument(doc);
        setError(null);
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Failed to load document');
      } finally {
        setLoading(false);
      }
    }

    loadDocument();
  }, [documentId, accessToken]);

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
      {authStatus === 'authenticated' && loadingList && <p style={{ color: 'var(--muted)' }}>Loading...</p>}
      {authStatus === 'authenticated' && listError && <p style={{ color: '#ef4444' }}>{listError}</p>}
      {authStatus === 'authenticated' && !loadingList && documents.length === 0 && (
        <p style={{ color: 'var(--muted)' }}>No documents found</p>
      )}
      {authStatus === 'authenticated' && !loadingList && documents.length > 0 && (
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
                  background: documentId === doc._id ? 'rgba(59, 130, 246, 0.1)' : 'var(--surface)',
                  border: `1px solid ${documentId === doc._id ? '#3b82f6' : 'var(--border)'}`,
                  borderRadius: '0.5rem',
                  color: 'var(--text)',
                  cursor: 'pointer',
                  transition: 'all 0.15s ease',
                }}
                onMouseEnter={(e) => {
                  if (documentId !== doc._id) {
                    e.currentTarget.style.background = 'rgba(255, 255, 255, 0.03)';
                    e.currentTarget.style.borderColor = '#475569';
                  }
                }}
                onMouseLeave={(e) => {
                  if (documentId !== doc._id) {
                    e.currentTarget.style.background = 'var(--surface)';
                    e.currentTarget.style.borderColor = 'var(--border)';
                  }
                }}
              >
                <div style={{
                  fontSize: '0.875rem',
                  fontWeight: 600,
                  marginBottom: '0.25rem',
                  color: documentId === doc._id ? '#3b82f6' : 'var(--text)'
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
    [documents, loadingList, listError, documentId, authStatus]
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
          Authenticate with Keycloak to browse and inspect document contents.
        </p>
        <AuthButton />
      </div>
    );
  }

  if (loading) {
    return (
      <div style={{ padding: '1rem' }}>
        <p style={{ color: 'var(--muted)' }}>Loading document...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div style={{ padding: '1rem' }}>
        <p style={{ color: '#ef4444' }}>{error}</p>
        <button
          onClick={() => router.push('/list')}
          style={{
            marginTop: '1rem',
            padding: '0.5rem 1rem',
            background: 'var(--surface)',
            border: '1px solid var(--border)',
            borderRadius: '0.5rem',
            color: 'var(--text)',
            cursor: 'pointer'
          }}
        >
          Back to list
        </button>
      </div>
    );
  }

  if (!document) {
    return (
      <div style={{ padding: '1rem' }}>
        <p style={{ color: 'var(--muted)' }}>Document not found</p>
        <button
          onClick={() => router.push('/list')}
          style={{
            marginTop: '1rem',
            padding: '0.5rem 1rem',
            background: 'var(--surface)',
            border: '1px solid var(--border)',
            borderRadius: '0.5rem',
            color: 'var(--text)',
            cursor: 'pointer'
          }}
        >
          Back to list
        </button>
      </div>
    );
  }

  return (
    <div style={{ padding: '1rem' }}>
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
          {document.name || 'Untitled Document'}
        </h1>
        <p style={{
          margin: 0,
          fontSize: '0.875rem',
          color: 'var(--muted)',
          fontFamily: 'monospace'
        }}>
          ID: {document._id}
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
        {JSON.stringify(document, null, 2)}
      </pre>
    </div>
  );
}
