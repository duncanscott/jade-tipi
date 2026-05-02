#!/bin/sh
# CouchDB local-stack bootstrap.
#
# Idempotent: ensures the local node's couchdb.max_document_size matches the
# source ceiling, the system DBs (_users, _replicator, _global_changes) and
# the local target DBs (clarity, esp-entity, dap-seq) exist, then upserts
# one _replicator document per target. The replicator-doc upsert rewrites
# only when meaningful fields differ to avoid scheduler churn on existing
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
#   JADE_TIPI_COUCHDB_DAP_SEQ_URL      remote source for dap-seq
#
# Optional environment:
#   COUCHDB_MAX_DOCUMENT_SIZE          local couchdb.max_document_size in
#                                      bytes. Defaults to 8589934592 (8 GiB)
#                                      to match the JGI source. The CouchDB
#                                      3.x default of ~8 MB rejects oversized
#                                      source docs as doc_write_failures, and
#                                      the replicator never retries them.
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
: "${JADE_TIPI_COUCHDB_DAP_SEQ_URL:?JADE_TIPI_COUCHDB_DAP_SEQ_URL is required}"

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

# Idempotently set a CouchDB runtime config value on the local node. Values
# are JSON-encoded strings on the wire; CouchDB persists writes to
# /opt/couchdb/etc/local.d/ which lives on the couchdb_config volume.
ensure_config() {
  section="$1"
  key="$2"
  desired="$3"
  url="${COUCH_URL}/_node/_local/_config/${section}/${key}"
  body=$(curl -sS -o /tmp/cfg.body -w '%{http_code}' \
    --user "$LOCAL_AUTH" "$url")
  case "$body" in
    200)
      have=$(jq -r '.' </tmp/cfg.body)
      if [ "$have" = "$desired" ]; then
        echo "config ${section}.${key}: already ${desired}"
        return 0
      fi
      ;;
    404) ;;
    *)
      echo "config ${section}.${key}: GET unexpected status ${body}" >&2
      return 1
      ;;
  esac
  payload=$(jq -n --arg v "$desired" '$v')
  put_code=$(curl -sS -o /dev/null -w '%{http_code}' \
    --user "$LOCAL_AUTH" \
    -H 'Content-Type: application/json' \
    -X PUT --data "$payload" "$url")
  if [ "$put_code" = "200" ]; then
    echo "config ${section}.${key}: set to ${desired}"
    return 0
  fi
  echo "config ${section}.${key}: PUT unexpected status ${put_code}" >&2
  return 1
}

COUCHDB_MAX_DOCUMENT_SIZE="${COUCHDB_MAX_DOCUMENT_SIZE:-8589934592}"
ensure_config couchdb max_document_size "$COUCHDB_MAX_DOCUMENT_SIZE"

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

for db in clarity esp-entity dap-seq; do
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

# Probe whether each replication source is reachable from this sidecar. The
# replicator runs inside the CouchDB container, which shares the host's
# network stack via Docker Desktop, so failures here predict "crashing" jobs
# in /_scheduler/jobs (replication_auth_error / econnrefused) that are
# otherwise invisible from the docker compose log.
probe_source() {
  label="$1"
  url="$2"
  rc=0
  out=$(curl -sS -o /dev/null -m 8 \
    --user "${JADE_TIPI_COUCHDB_ADMIN_USERNAME}:${JADE_TIPI_COUCHDB_ADMIN_PASSWORD}" \
    -w '%{http_code} in %{time_total}s' "$url" 2>&1) || rc=$?
  if [ "$rc" -ne 0 ]; then
    echo "${label} source: unreachable (curl exit ${rc}): ${out}" >&2
    echo "  the replicator will register but crash on each retry until this is fixed;" >&2
    echo "  inspect /_scheduler/jobs for the live failure reason" >&2
    return 0
  fi
  echo "${label} source: reachable (HTTP ${out})"
}

probe_source "bootstrap-clarity"    "$JADE_TIPI_COUCHDB_CLARITY_URL"
probe_source "bootstrap-esp-entity" "$JADE_TIPI_COUCHDB_ESP_ENTITY_URL"
probe_source "bootstrap-dap-seq"    "$JADE_TIPI_COUCHDB_DAP_SEQ_URL"

upsert_replicator "bootstrap-clarity"    "$JADE_TIPI_COUCHDB_CLARITY_URL"    "${COUCH_TARGET_BASE_URL}/clarity"
upsert_replicator "bootstrap-esp-entity" "$JADE_TIPI_COUCHDB_ESP_ENTITY_URL" "${COUCH_TARGET_BASE_URL}/esp-entity"
upsert_replicator "bootstrap-dap-seq"    "$JADE_TIPI_COUCHDB_DAP_SEQ_URL"    "${COUCH_TARGET_BASE_URL}/dap-seq"

echo "couchdb-bootstrap: done"
