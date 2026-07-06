'use client';

// Typed client for the TASK-041/042 read APIs consumed by the Track-1
// container view (TASK-053). Shapes mirror the backend read records; a
// 404 resolves to null so callers can distinguish "not materialized"
// from transport failures.

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8765';

export interface PropertyValueEntry {
  property_id: string;
  property_name: string | null;
  value: Record<string, unknown>;
  txn_id: string | null;
  commit_id: string | null;
  msg_uuid: string | null;
  applied_at: string | null;
}

export interface ObjectPropertyValues {
  object_id: string;
  collection: string;
  type_id: string | null;
  properties: Record<string, unknown>;
  links: Record<string, unknown>;
  provenance: Record<string, unknown> | null;
  property_values: Record<string, PropertyValueEntry>;
}

export interface TypeEffectiveProperty {
  property_id: string;
  property_name: string | null;
  source_type_id: string;
  reference: Record<string, unknown>;
}

export interface TypeEffectiveProperties {
  type_id: string;
  type_name: string | null;
  type_chain: string[];
  chain_complete: boolean;
  effective_properties: Record<string, TypeEffectiveProperty>;
}

export interface PlateContentsEntry {
  link_id: string;
  type_id: string | null;
  object_id: string;
  position: Record<string, unknown> | null;
  unplaced_reason: string | null;
  link_provenance: Record<string, unknown> | null;
  entity: ObjectPropertyValues | null;
}

export interface PlateContentsWell {
  label: string;
  row: string;
  column: number;
  contents: PlateContentsEntry[];
}

export interface PlateContents {
  container_id: string;
  row_count: number | null;
  column_count: number | null;
  row_labels: string[];
  column_labels: number[];
  wells: PlateContentsWell[];
  unplaced_contents: PlateContentsEntry[];
}

export interface LocationRoot {
  location_id: string;
  type_id: string | null;
  properties: Record<string, unknown>;
  links: Record<string, unknown>;
  provenance: Record<string, unknown> | null;
}

export interface LocationContentsEntry {
  link_id: string;
  type_id: string | null;
  container_id: string;
  content_id: string;
  position: Record<string, unknown> | null;
  link_provenance: Record<string, unknown> | null;
  content_location: LocationRoot | null;
  content_entity: ObjectPropertyValues | null;
}

export interface LocationContents {
  location_id: string;
  location: LocationRoot | null;
  contents: LocationContentsEntry[];
}

export interface LocationSummary {
  location_id: string;
  type_id: string | null;
  name: string | null;
  description: string | null;
}

export interface LocationBrowsePage {
  items: LocationSummary[];
  page: number;
  size: number;
  total: number;
}

export interface ObjectLocationEntry {
  link_id: string;
  type_id: string | null;
  container_id: string;
  position: Record<string, unknown> | null;
  link_provenance: Record<string, unknown> | null;
  container: LocationRoot | null;
}

export interface ObjectLocations {
  object_id: string;
  locations: ObjectLocationEntry[];
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

/** Query read: always 200; an unknown object simply has no locations. */
export async function getObjectLocations(id: string,
                                         accessToken: string): Promise<ObjectLocations> {
  const result = await getOrNull<ObjectLocations>(
    `/api/contents/by-content/${encodeURIComponent(id)}/locations`, accessToken);
  if (!result) {
    throw new Error('Object locations unexpectedly returned 404');
  }
  return result;
}

/** Query read: always a page envelope, never 404 (empty collection = empty page). */
export async function listLocations(page: number, size: number,
                                    accessToken: string): Promise<LocationBrowsePage> {
  const result = await getOrNull<LocationBrowsePage>(
    `/api/locations?page=${page}&size=${size}`, accessToken);
  if (!result) {
    throw new Error('Location browse unexpectedly returned 404');
  }
  return result;
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
