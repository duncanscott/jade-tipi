#!/bin/sh
# CouchDB local-stack bootstrap.
#
# Idempotent: ensures the system DBs (_users, _replicator, _global_changes)
# and the local target DBs (clarity, esp-entity) exist, then upserts one
# _replicator document per target. The replicator-doc upsert rewrites only
# when meaningful fields differ to avoid scheduler churn on existing
# continuous replications.
#
# Required environment (validated below). Names only; values must never be
# echoed by this script:
#   COUCH_URL                          local CouchDB base URL (no creds)
#   COUCHDB_USER, COUCHDB_PASSWORD     local single-node admin
#   JADE_TIPI_COUCHDB_ADMIN_USERNAME   remote JGI source basic-auth user
#   JADE_TIPI_COUCHDB_ADMIN_PASSWORD   remote JGI source basic-auth password
#   JADE_TIPI_COUCHDB_CLARITY_URL      remote source for clarity
#   JADE_TIPI_COUCHDB_ESP_ENTITY_URL   remote source for esp-entity
#
# Tools: curl, jq (installed by the sidecar entrypoint).

set -eu

: "${COUCH_URL:?COUCH_URL is required}"
: "${COUCHDB_USER:?COUCHDB_USER is required}"
: "${COUCHDB_PASSWORD:?COUCHDB_PASSWORD is required}"
: "${JADE_TIPI_COUCHDB_ADMIN_USERNAME:?JADE_TIPI_COUCHDB_ADMIN_USERNAME is required}"
: "${JADE_TIPI_COUCHDB_ADMIN_PASSWORD:?JADE_TIPI_COUCHDB_ADMIN_PASSWORD is required}"
: "${JADE_TIPI_COUCHDB_CLARITY_URL:?JADE_TIPI_COUCHDB_CLARITY_URL is required}"
: "${JADE_TIPI_COUCHDB_ESP_ENTITY_URL:?JADE_TIPI_COUCHDB_ESP_ENTITY_URL is required}"

LOCAL_AUTH="${COUCHDB_USER}:${COUCHDB_PASSWORD}"

# CouchDB 3.x rejects replicator docs whose source/target is a bare local DB
# name ("local_endpoints_not_supported"). The replicator client runs inside
# the CouchDB process, so 127.0.0.1 reaches itself; an override is exposed for
# topologies where that isn't true.
COUCH_TARGET_BASE_URL="${COUCH_TARGET_BASE_URL:-http://127.0.0.1:5984}"

# Bounded retry against /_up. The compose healthcheck already gates this
# sidecar, but tolerate a brief startup window after an unclean restart.
i=0
while [ "$i" -lt 6 ]; do
  if [ "$(curl -sS -o /dev/null -w '%{http_code}' "${COUCH_URL}/_up")" = "200" ]; then
    break
  fi
  i=$((i + 1))
  sleep 5
done

put_status() {
  curl -sS -o /dev/null -w '%{http_code}' \
    --user "$LOCAL_AUTH" -X PUT "$1"
}

ensure_db() {
  label="$1"
  db="$2"
  code=$(put_status "${COUCH_URL}/${db}")
  case "$code" in
    201|202) echo "${label} ${db}: created" ;;
    412)     echo "${label} ${db}: already present" ;;
    *)
      echo "${label} ${db}: unexpected status ${code}" >&2
      exit 1
      ;;
  esac
}

for db in _users _replicator _global_changes; do
  ensure_db "system db" "$db"
done

for db in clarity esp-entity; do
  ensure_db "local db" "$db"
done

# Build a desired _replicator JSON document with jq-escaped fields.
build_desired() {
  jq -n \
    --arg id        "$1" \
    --arg url       "$2" \
    --arg user      "$JADE_TIPI_COUCHDB_ADMIN_USERNAME" \
    --arg pass      "$JADE_TIPI_COUCHDB_ADMIN_PASSWORD" \
    --arg tgturl    "$3" \
    --arg localuser "$COUCHDB_USER" \
    --arg localpass "$COUCHDB_PASSWORD" \
    '{
      _id: $id,
      source: { url: $url,    auth: { basic: { username: $user,      password: $pass      } } },
      target: { url: $tgturl, auth: { basic: { username: $localuser, password: $localpass } } },
      continuous: true,
      create_target: false,
      use_checkpoints: true
    }'
}

# Project the meaningful replication intent to compare desired vs. existing.
project_intent() {
  jq '{
    source_url:      .source.url,
    source_username: .source.auth.basic.username,
    source_password: .source.auth.basic.password,
    target_url:      .target.url,
    target_username: .target.auth.basic.username,
    target_password: .target.auth.basic.password,
    continuous:      .continuous,
    create_target:   .create_target,
    use_checkpoints: .use_checkpoints
  }'
}

upsert_replicator() {
  doc_id="$1"
  src_url="$2"
  tgt="$3"

  desired=$(build_desired "$doc_id" "$src_url" "$tgt")
  put_url="${COUCH_URL}/_replicator/${doc_id}"

  code=$(curl -sS -o /dev/null -w '%{http_code}' \
    --user "$LOCAL_AUTH" \
    -H 'Content-Type: application/json' \
    -X PUT --data "$desired" "$put_url")

  if [ "$code" = "201" ] || [ "$code" = "202" ]; then
    echo "${doc_id}: created"
    return 0
  fi

  if [ "$code" != "409" ]; then
    echo "${doc_id}: unexpected status ${code}" >&2
    return 1
  fi

  existing=$(curl -fsS --user "$LOCAL_AUTH" "$put_url")
  desired_proj=$(printf '%s' "$desired"  | project_intent)
  existing_proj=$(printf '%s' "$existing" | project_intent)

  if printf '%s' "$desired_proj" \
       | jq -e --argjson o "$existing_proj" '. == $o' >/dev/null 2>&1; then
    echo "${doc_id}: already configured (no change)"
    return 0
  fi

  rev=$(printf '%s' "$existing" | jq -r '._rev')
  merged=$(printf '%s' "$desired" | jq --arg rev "$rev" '. + {_rev: $rev}')
  code2=$(curl -sS -o /dev/null -w '%{http_code}' \
    --user "$LOCAL_AUTH" \
    -H 'Content-Type: application/json' \
    -X PUT --data "$merged" "$put_url")

  if [ "$code2" = "201" ] || [ "$code2" = "202" ]; then
    short_rev=$(printf '%s' "$rev" | cut -c1-7)
    echo "${doc_id}: updated (prev rev=${short_rev})"
    return 0
  fi

  echo "${doc_id}: update failed status ${code2}" >&2
  return 1
}

upsert_replicator "bootstrap-clarity"    "$JADE_TIPI_COUCHDB_CLARITY_URL"    "${COUCH_TARGET_BASE_URL}/clarity"
upsert_replicator "bootstrap-esp-entity" "$JADE_TIPI_COUCHDB_ESP_ENTITY_URL" "${COUCH_TARGET_BASE_URL}/esp-entity"

echo "couchdb-bootstrap: done"
