'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { createDocument, getDocument } from '@/lib/api';
import { generateUUID } from '@/lib/uuid';

export default function CreateDocument() {
  const router = useRouter();
  const [jsonInput, setJsonInput] = useState('{\n  "name": "example",\n  "value": 123\n}');
  const [status, setStatus] = useState<{ type: 'success' | 'error' | 'info' | null; message: string }>({ type: null, message: '' });
  const [isLoading, setIsLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);
    setStatus({ type: null, message: '' });

    try {
      // Validate JSON
      const jsonData = JSON.parse(jsonInput);

      // Generate UUID
      const id = generateUUID();

      // Save to MongoDB via API
      await createDocument(id, jsonData);

      // Verify document is readable (read-after-write verification)
      const verifiedDoc = await getDocument(id);

      if (!verifiedDoc) {
        setStatus({
          type: 'error',
          message: 'Document created but could not be verified. Please try again.'
        });
        setIsLoading(false);
        return;
      }

      // Document confirmed - safe to redirect
      router.push(`/document/edit/${id}`);
    } catch (error) {
      if (error instanceof SyntaxError) {
        setStatus({
          type: 'error',
          message: 'Invalid JSON format. Please check your input.'
        });
      } else {
        setStatus({
          type: 'error',
          message: `Error: ${error instanceof Error ? error.message : 'Unknown error'}`
        });
      }
      setIsLoading(false);
    }
  };

  return (
    <div className="min-h-screen p-8 bg-gray-50 dark:bg-gray-900">
      <main className="max-w-4xl mx-auto">
        <h1 className="text-4xl font-bold mb-8 text-gray-900 dark:text-white">
          Create New Document
        </h1>

        <form onSubmit={handleSubmit} className="space-y-6">
          <div>
            <label htmlFor="json-input" className="block text-sm font-medium mb-2 text-gray-700 dark:text-gray-300">
              Paste JSON Object
            </label>
            <textarea
              id="json-input"
              value={jsonInput}
              onChange={(e) => setJsonInput(e.target.value)}
              className="w-full h-64 p-4 font-mono text-sm border border-gray-300 dark:border-gray-600 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500 bg-white dark:bg-gray-800 text-gray-900 dark:text-white"
              placeholder='{\n  "key": "value"\n}'
            />
          </div>

          <button
            type="submit"
            disabled={isLoading}
            className="w-full px-6 py-3 bg-blue-600 hover:bg-blue-700 disabled:bg-gray-400 text-white font-medium rounded-lg transition-colors"
          >
            {isLoading ? 'Creating...' : 'Create Document'}
          </button>
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
