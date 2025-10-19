const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8765';

export async function createDocument(id: string, data: object) {
  const response = await fetch(`${API_BASE_URL}/api/documents/${id}`, {
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
  const response = await fetch(`${API_BASE_URL}/api/documents/${id}`);

  if (!response.ok) {
    if (response.status === 404) {
      return null;
    }
    throw new Error(`Failed to fetch document: ${response.statusText}`);
  }

  return response.json();
}

export async function updateDocument(id: string, data: object) {
  const response = await fetch(`${API_BASE_URL}/api/documents/${id}`, {
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
  const response = await fetch(`${API_BASE_URL}/api/documents/${id}`, {
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
  const response = await fetch(`${API_BASE_URL}/api/documents`);

  if (!response.ok) {
    throw new Error(`Failed to list documents: ${response.statusText}`);
  }

  return response.json();
}
