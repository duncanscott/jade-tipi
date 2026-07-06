'use client';

// Typed client for the TASK-041/042 read APIs consumed by the Track-1
// container view (TASK-053). Shapes mirror the backend read records; a
// 404 resolves to null so callers can distinguish "not materialized"
// from transport failures.

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8765';

export interface PropertyValueEntry {
  propertyId: string;
  propertyName: string | null;
  value: Record<string, unknown>;
  txnId: string | null;
  commitId: string | null;
  msgUuid: string | null;
  appliedAt: string | null;
}

export interface ObjectPropertyValues {
  objectId: string;
  collection: string;
  typeId: string | null;
  properties: Record<string, unknown>;
  links: Record<string, unknown>;
  provenance: Record<string, unknown> | null;
  propertyValues: Record<string, PropertyValueEntry>;
}

export interface TypeEffectiveProperty {
  propertyId: string;
  propertyName: string | null;
  sourceTypeId: string;
  reference: Record<string, unknown>;
}

export interface TypeEffectiveProperties {
  typeId: string;
  typeName: string | null;
  typeChain: string[];
  chainComplete: boolean;
  effectiveProperties: Record<string, TypeEffectiveProperty>;
}

export interface PlateContentsEntry {
  linkId: string;
  typeId: string | null;
  objectId: string;
  position: Record<string, unknown> | null;
  unplacedReason: string | null;
  linkProvenance: Record<string, unknown> | null;
  entity: ObjectPropertyValues | null;
}

export interface PlateContentsWell {
  label: string;
  row: string;
  column: number;
  contents: PlateContentsEntry[];
}

export interface PlateContents {
  containerId: string;
  rowCount: number | null;
  columnCount: number | null;
  rowLabels: string[];
  columnLabels: number[];
  wells: PlateContentsWell[];
  unplacedContents: PlateContentsEntry[];
}

export interface LocationRoot {
  locationId: string;
  typeId: string | null;
  properties: Record<string, unknown>;
  links: Record<string, unknown>;
  provenance: Record<string, unknown> | null;
}

export interface LocationContentsEntry {
  linkId: string;
  typeId: string | null;
  containerId: string;
  contentId: string;
  position: Record<string, unknown> | null;
  linkProvenance: Record<string, unknown> | null;
  contentLocation: LocationRoot | null;
  contentEntity: ObjectPropertyValues | null;
}

export interface LocationContents {
  locationId: string;
  location: LocationRoot | null;
  contents: LocationContentsEntry[];
}

async function getOrNull<T>(path: string, accessToken: string): Promise<T | null> {
  if (!accessToken) {
    throw new Error('Request requires a Keycloak access token');
  }
  const response = await fetch(`${API_BASE_URL}${path}`, {
    headers: {
      Authorization: `Bearer ${accessToken}`,
    },
  });
  if (response.status === 404) {
    return null;
  }
  if (!response.ok) {
    throw new Error(`Request failed (${response.status}): ${path}`);
  }
  return response.json() as Promise<T>;
}

export function getLocationPropertyValues(id: string, accessToken: string) {
  return getOrNull<ObjectPropertyValues>(
    `/api/locations/${encodeURIComponent(id)}/property-values`, accessToken);
}

export function getEntityPropertyValues(id: string, accessToken: string) {
  return getOrNull<ObjectPropertyValues>(
    `/api/entities/${encodeURIComponent(id)}/property-values`, accessToken);
}

export function getTypeEffectiveProperties(typeId: string, accessToken: string) {
  return getOrNull<TypeEffectiveProperties>(
    `/api/types/${encodeURIComponent(typeId)}/effective-properties`, accessToken);
}

export function getPlateContents(id: string, accessToken: string) {
  return getOrNull<PlateContents>(
    `/api/contents/plate/${encodeURIComponent(id)}`, accessToken);
}

export function getLocationContents(id: string, accessToken: string) {
  return getOrNull<LocationContents>(
    `/api/locations/${encodeURIComponent(id)}/contents`, accessToken);
}

/** Human-facing label: the inline name when present, else the ID suffix. */
export function displayName(properties: Record<string, unknown> | undefined | null,
                            id: string): string {
  const name = properties?.['name'];
  if (typeof name === 'string' && name.trim().length > 0) {
    return name;
  }
  const segments = id.split('~');
  return segments[segments.length - 1] || id;
}
