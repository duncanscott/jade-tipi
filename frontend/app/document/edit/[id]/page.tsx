'use client';

import { useState, useEffect } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { getDocument, updateDocument, deleteDocument } from '@/lib/api';

export default function EditDocument() {
  const params = useParams();
  const router = useRouter();
  const id = params.id as string;

  const [jsonInput, setJsonInput] = useState('');
  const [status, setStatus] = useState<{ type: 'success' | 'error' | 'info' | null; message: string }>({ type: null, message: '' });
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);

  useEffect(() => {
    loadDocument();
  }, [id]);

  const loadDocument = async () => {
    try {
      setIsLoading(true);
      const document = await getDocument(id);

      if (!document) {
        setStatus({
          type: 'error',
          message: 'Document not found'
        });
        setJsonInput('');
      } else {
        // Remove _id from display
        const { _id, ...docWithoutId } = document;
        setJsonInput(JSON.stringify(docWithoutId, null, 2));
      }
    } catch (error) {
      setStatus({
        type: 'error',
        message: `Error loading document: ${error instanceof Error ? error.message : 'Unknown error'}`
      });
    } finally {
      setIsLoading(false);
    }
  };

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsSaving(true);
    setStatus({ type: null, message: '' });

    try {
      // Validate JSON
      const jsonData = JSON.parse(jsonInput);

      // Update document
      await updateDocument(id, jsonData);

      setStatus({
        type: 'success',
        message: 'Document updated successfully'
      });
    } catch (error) {
      if (error instanceof SyntaxError) {
        setStatus({
          type: 'error',
          message: 'Invalid JSON format. Please check your input.'
        });
      } else {
        setStatus({
          type: 'error',
          message: `Failed to save: ${error instanceof Error ? error.message : 'Unknown error'}. Please try again.`
        });
      }
    } finally {
      setIsSaving(false);
    }
  };

  const handleDelete = async () => {
    if (!confirm('Are you sure you want to delete this document?')) {
      return;
    }

    setIsDeleting(true);
    setStatus({ type: null, message: '' });

    try {
      await deleteDocument(id);
      router.push('/');
    } catch (error) {
      setStatus({
        type: 'error',
        message: `Failed to delete: ${error instanceof Error ? error.message : 'Unknown error'}. Please try again.`
      });
      setIsDeleting(false);
    }
  };

  if (isLoading) {
    return (
      <div className="min-h-screen p-8 bg-gray-50 dark:bg-gray-900">
        <main className="max-w-4xl mx-auto">
          <div className="text-center py-12">
            <p className="text-gray-600 dark:text-gray-400">Loading document...</p>
          </div>
        </main>
      </div>
    );
  }

  return (
    <div className="min-h-screen p-8 bg-gray-50 dark:bg-gray-900">
      <main className="max-w-4xl mx-auto">
        <div className="flex justify-between items-center mb-8">
          <h1 className="text-4xl font-bold text-gray-900 dark:text-white">
            Edit Document
          </h1>
          <button
            onClick={() => router.push('/')}
            className="px-4 py-2 text-gray-600 dark:text-gray-400 hover:text-gray-900 dark:hover:text-white"
          >
            ← Back
          </button>
        </div>

        <div className="mb-4 p-3 bg-blue-50 dark:bg-blue-900/30 rounded-lg">
          <p className="text-sm text-gray-700 dark:text-gray-300">
            <span className="font-medium">Document ID:</span>{' '}
            <span className="font-mono">{id}</span>
          </p>
        </div>

        <form onSubmit={handleSave} className="space-y-6">
          <div>
            <label htmlFor="json-input" className="block text-sm font-medium mb-2 text-gray-700 dark:text-gray-300">
              Document Content (JSON)
            </label>
            <textarea
              id="json-input"
              value={jsonInput}
              onChange={(e) => setJsonInput(e.target.value)}
              className="w-full h-64 p-4 font-mono text-sm border border-gray-300 dark:border-gray-600 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500 bg-white dark:bg-gray-800 text-gray-900 dark:text-white"
              placeholder='{\n  "key": "value"\n}'
            />
          </div>

          <div className="flex gap-4">
            <button
              type="submit"
              disabled={isSaving}
              className="flex-1 px-6 py-3 bg-blue-600 hover:bg-blue-700 disabled:bg-gray-400 text-white font-medium rounded-lg transition-colors"
            >
              {isSaving ? 'Saving...' : 'Save Changes'}
            </button>
            <button
              type="button"
              onClick={handleDelete}
              disabled={isDeleting}
              className="px-6 py-3 bg-red-600 hover:bg-red-700 disabled:bg-gray-400 text-white font-medium rounded-lg transition-colors"
            >
              {isDeleting ? 'Deleting...' : 'Delete'}
            </button>
          </div>
        </form>

        {status.type && (
          <div className={`mt-6 p-4 rounded-lg ${
            status.type === 'success' ? 'bg-green-100 dark:bg-green-900 text-green-800 dark:text-green-100' :
            status.type === 'error' ? 'bg-red-100 dark:bg-red-900 text-red-800 dark:text-red-100' :
            'bg-blue-100 dark:bg-blue-900 text-blue-800 dark:text-blue-100'
          }`}>
            <p className="font-medium">{status.message}</p>
          </div>
        )}
      </main>
    </div>
  );
}
