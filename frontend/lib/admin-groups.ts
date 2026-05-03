'use client';

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8765';

export interface GroupProvenance {
  txnId?: string;
  commitId?: string;
  msgUuid?: string;
  collection?: string;
  action?: string;
  committedAt?: string;
  materializedAt?: string;
}

export interface GroupHead {
  schemaVersion?: number;
  documentKind?: string;
  rootId?: string;
  provenance?: GroupProvenance;
}

export interface GroupRecord {
  id: string;
  collection: string;
  name: string;
  description?: string;
  permissions: Record<string, 'rw' | 'r'>;
  head?: GroupHead;
}

export interface GroupCreateRequest {
  id?: string;
  name: string;
  description?: string;
  permissions: Record<string, 'rw' | 'r'>;
}

export interface GroupUpdateRequest {
  name: string;
  description?: string;
  permissions: Record<string, 'rw' | 'r'>;
}

interface AdminGroupListResponse {
  items: GroupRecord[];
}

function ensureAccessToken(accessToken: string | undefined | null): string {
  if (!accessToken) {
    throw new Error('Admin group requests require a Keycloak access token');
  }
  return accessToken;
}

async function readErrorBody(response: Response): Promise<string> {
  try {
    const body = await response.json();
    if (body && typeof body === 'object' && 'message' in body && typeof body.message === 'string') {
      return body.message;
    }
    return JSON.stringify(body);
  } catch {
    return response.statusText;
  }
}

export async function listAdminGroups(accessToken: string): Promise<GroupRecord[]> {
  const token = ensureAccessToken(accessToken);
  const response = await fetch(`${API_BASE_URL}/api/admin/groups`, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });
  if (!response.ok) {
    throw new Error(`Failed to list groups: ${await readErrorBody(response)}`);
  }
  const body = (await response.json()) as AdminGroupListResponse;
  return body.items ?? [];
}

export async function getAdminGroup(id: string, accessToken: string): Promise<GroupRecord | null> {
  const token = ensureAccessToken(accessToken);
  const response = await fetch(`${API_BASE_URL}/api/admin/groups/${encodeURIComponent(id)}`, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });
  if (response.status === 404) {
    return null;
  }
  if (!response.ok) {
    throw new Error(`Failed to load group: ${await readErrorBody(response)}`);
  }
  return (await response.json()) as GroupRecord;
}

export async function createAdminGroup(request: GroupCreateRequest, accessToken: string): Promise<GroupRecord> {
  const token = ensureAccessToken(accessToken);
  const response = await fetch(`${API_BASE_URL}/api/admin/groups`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify(request),
  });
  if (!response.ok) {
    throw new Error(`Failed to create group: ${await readErrorBody(response)}`);
  }
  return (await response.json()) as GroupRecord;
}

export async function updateAdminGroup(id: string, request: GroupUpdateRequest, accessToken: string): Promise<GroupRecord> {
  const token = ensureAccessToken(accessToken);
  const response = await fetch(`${API_BASE_URL}/api/admin/groups/${encodeURIComponent(id)}`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify(request),
  });
  if (response.status === 404) {
    throw new Error('Group not found');
  }
  if (!response.ok) {
    throw new Error(`Failed to update group: ${await readErrorBody(response)}`);
  }
  return (await response.json()) as GroupRecord;
}
