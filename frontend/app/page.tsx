'use client';

import Link from 'next/link';
import { useState } from 'react';
import AuthButton from '@/components/AuthButton';

export default function Home() {
  const [documentId, setDocumentId] = useState('');

  const handleViewDocument = (e: React.FormEvent) => {
    e.preventDefault();
    if (documentId.trim()) {
      window.location.href = `/document/edit/${documentId.trim()}`;
    }
  };

  return (
    <div className="min-h-screen p-8 bg-gray-50 dark:bg-gray-900">
      <main className="max-w-4xl mx-auto">
        <div className="flex justify-between items-center mb-8">
          <h1 className="text-4xl font-bold text-gray-900 dark:text-white">
            Jade Tipi - MongoDB Document Manager
          </h1>
          <AuthButton />
        </div>

        <div className="grid gap-6 md:grid-cols-2 mb-12">
          <Link
            href="/document/create"
            className="p-8 bg-white dark:bg-gray-800 rounded-lg border-2 border-gray-200 dark:border-gray-700 hover:border-blue-500 dark:hover:border-blue-500 transition-colors"
          >
            <div className="text-4xl mb-4">📝</div>
            <h2 className="text-2xl font-bold mb-2 text-gray-900 dark:text-white">
              Create Document
            </h2>
            <p className="text-gray-600 dark:text-gray-400">
              Create a new JSON document and store it in MongoDB
            </p>
          </Link>

          <div className="p-8 bg-white dark:bg-gray-800 rounded-lg border-2 border-gray-200 dark:border-gray-700">
            <div className="text-4xl mb-4">✏️</div>
            <h2 className="text-2xl font-bold mb-2 text-gray-900 dark:text-white">
              Edit Document
            </h2>
            <p className="text-gray-600 dark:text-gray-400 mb-4">
              View or edit an existing document by ID
            </p>
            <form onSubmit={handleViewDocument} className="space-y-3">
              <input
                type="text"
                value={documentId}
                onChange={(e) => setDocumentId(e.target.value)}
                placeholder="Enter document ID"
                className="w-full px-4 py-2 border border-gray-300 dark:border-gray-600 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500 bg-white dark:bg-gray-700 text-gray-900 dark:text-white"
              />
              <button
                type="submit"
                className="w-full px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white font-medium rounded-lg transition-colors"
              >
                View Document
              </button>
            </form>
          </div>
        </div>

        <div className="p-6 bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700">
          <h2 className="text-lg font-semibold mb-3 text-gray-900 dark:text-white">About</h2>
          <p className="text-gray-700 dark:text-gray-300 mb-3">
            Jade Tipi is a full-stack application for managing JSON documents in MongoDB.
          </p>
          <ul className="list-disc list-inside space-y-2 text-gray-700 dark:text-gray-300">
            <li>Create new documents with auto-generated UUIDs</li>
            <li>View and edit existing documents</li>
            <li>Delete documents when no longer needed</li>
            <li>Built with Next.js 15, React 19, and Spring Boot</li>
          </ul>
        </div>
      </main>
    </div>
  );
}
