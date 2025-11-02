import { auth } from '@/auth';

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8765';

// Helper to get access token from session
async function getAccessToken(): Promise<string | null> {
  const session = await auth();
  return session?.accessToken ?? null;
}

async function fetchWithAuth(url: string, options: RequestInit = {}) {
  const token = await getAccessToken();

  const headers: HeadersInit = {
    ...options.headers,
  };

  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  return fetch(url, { ...options, headers });
}

export async function createDocument(id: string, data: object) {
  const response = await fetchWithAuth(`${API_BASE_URL}/api/documents/${id}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(data),
  });

  if (!response.ok) {
    throw new Error(`Failed to create document: ${response.statusText}`);
  }

  return response.json();
}

export async function getDocument(id: string) {
  const response = await fetchWithAuth(`${API_BASE_URL}/api/documents/${id}`);

  if (!response.ok) {
    if (response.status === 404) {
      return null;
    }
    throw new Error(`Failed to fetch document: ${response.statusText}`);
  }

  return response.json();
}

export async function updateDocument(id: string, data: object) {
  const response = await fetchWithAuth(`${API_BASE_URL}/api/documents/${id}`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(data),
  });

  if (!response.ok) {
    throw new Error(`Failed to update document: ${response.statusText}`);
  }

  return response.json();
}

export async function deleteDocument(id: string) {
  const response = await fetchWithAuth(`${API_BASE_URL}/api/documents/${id}`, {
    method: 'DELETE',
  });

  if (!response.ok) {
    throw new Error(`Failed to delete document: ${response.statusText}`);
  }

  return response.status === 204;
}

export interface DocumentSummary {
  _id: string;
  name?: string;
}

export async function listDocuments(): Promise<DocumentSummary[]> {
  const response = await fetchWithAuth(`${API_BASE_URL}/api/documents`);

  if (!response.ok) {
    throw new Error(`Failed to list documents: ${response.statusText}`);
  }

  return response.json();
}
