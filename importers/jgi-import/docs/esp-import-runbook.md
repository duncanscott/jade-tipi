# ESP-entity import runbook

Operational steps for importing ESP-entity data into Jade-Tipi through the
production path (plan → drive → materialize). Companion to
[`bulk-import-design.md`](bulk-import-design.md) (the *why*) and TASK-069 /
TASK-072 (the design decisions). This is the *how*: the exact commands,
config, and the non-obvious gotchas discovered running it end to end.

The importer is a **scriptable CLI**, not an always-on service. Nothing here
runs unless you invoke it.

---

## What one import does

Given one or more ESP entity UUIDs (or a type name), the importer:

1. **plan** — walks the entity's begat ancestry + containers recursively and
   fills a dependency-ordered queue in MongoDB (`jadetipi` db,
   `import_queue`): bootstrap link types → dynamic entity/procedure types →
   parents-before-children.
2. **drive** — maps each queued document to JDTP messages, aggregates the
   run's procedures by `workflow_instance_uuid` (TASK-072), and publishes one
   Kafka transaction per batch.
3. **materialize** — the Jade-Tipi backend consumes the transaction topic and
   projects the messages into the domain collections (`ent`/`loc`/`tsk`/
   `prc`/`lnk`/`typ` …) in MongoDB (`jdtp` db).

`import` mode does plan + drive in one run. Planning and driving are
**resumable**: rerunning continues from pending queue rows and reuses recorded
ids.

---

## Prerequisites

| Component | Where | Notes |
|---|---|---|
| CouchDB `esp-entity` replica | `http://localhost:5984` (admin/admin) | The source. The bulk replica is a point-in-time snapshot. **Do not** start jade-tipi's compose `couchdb` service — it would collide on port 5984 with this replica. |
| MongoDB | `localhost:27017` | Two dbs: `jadetipi` (import queue), `jdtp` (materialized objects). |
| Kafka | `localhost:9092` | Single-partition transaction topic. |
| Jade-Tipi backend | `:8765` | The materialization worker. Consumes topics matching `jdtp-txn-.*|jdtp_cli_kli`. |
| Artifactory creds | `~/.env` | `source ~/.env` before any Gradle command. |
| LBNL VPN + `AUTH_HEADER` | for procedures | Needed only to source `workflow_instance_uuid` from the enriched service (see below). |

### Bring the infra up

```bash
cd <jade-tipi>
# Mongo + Kafka (kafka pulls keycloak via depends_on). NOT couchdb.
docker compose -f docker/docker-compose.yml up -d mongodb kafka
# The backend (materialization worker), backgrounded:
source ~/.env
./gradlew :jade-tipi:bootRun --console=plain > /tmp/jadetipi-backend.log 2>&1 &
# wait for health
until curl -sf http://localhost:8765/actuator/health >/dev/null; do sleep 3; done
```

The backend consumes from a topic **pattern** (`jdtp-txn-.*|jdtp_cli_kli`);
drive to a topic that matches it (e.g. `jdtp-txn-esp`, or reuse the
already-assigned `jdtp_cli_kli`).

---

## Procedures need the enriched endpoint

The bulk replica's sample sheets **predate `workflow_instance_uuid`** — the id
that groups the sheets of one workflow run into a single procedure. Without
it, entities/containers/tasks/links still import, but **no procedures are
reconstructed**. The importer sources the id per entity from the pps-esp-entity
enriched service (the V2 cache-first, self-backfilling route). Enable it with
two env vars (Spring relaxed-binds them to `jadetipi.import.esp-api.*`):

```bash
export JADETIPI_IMPORT_ESP_API_BASE_URL="https://ws-access-espprd.jgi.lbl.gov/pps-esp-entity"
export JADETIPI_IMPORT_ESP_API_AUTH_HEADER="$AUTH_HEADER"   # from ~/.env; requires LBNL VPN
```

- One V2 call per entity during **drive** (~1–2s each). It also **backfills
  production CouchDB** as a side effect, so repeat imports converge toward
  zero endpoint calls once the replica is enriched.
- Unset → the importer falls back to local (workflow-instance-less) sheets and
  emits no procedures. Everything else still imports.

---

## Run it

```bash
cd <jade-tipi> && source ~/.env
export JADETIPI_IMPORT_ESP_API_BASE_URL="https://ws-access-espprd.jgi.lbl.gov/pps-esp-entity"
export JADETIPI_IMPORT_ESP_API_AUTH_HEADER="$AUTH_HEADER"

./gradlew :importers:jgi-import:run --args='\
    --jgi-import.mode=import \
    --jgi-import.esp-entity=019c207c-1a14-7e25-b132-c663efa54bd6 \
    --jgi-import.org=jgi --jgi-import.grp=pps \
    --jgi-import.kafka.bootstrap-servers=localhost:9092 \
    --jgi-import.kafka.topic=jdtp_cli_kli'
```

- `--jgi-import.esp-entity=<uuid,uuid,…>` — import these entities and their
  full ancestry. Or `--jgi-import.esp-type-name='Illumina Physical Run Unit'
  [--jgi-import.limit=N]` to sweep a type.
- `--jgi-import.org` / `--jgi-import.grp` — **required to drive**; ids are
  minted under `<org>~<grp>~…`.
- Split into two runs when useful: `--jgi-import.mode=plan …` then
  `--jgi-import.mode=drive …` (drive needs no entity arg — it drains the
  queue).

---

## Verify

```bash
# materialized graph (jdtp db)
docker exec jade-tipi-mongo mongosh jdtp --quiet --eval '
  ["ent","loc","tsk","prc","lnk","typ"].forEach(c=>print(c+": "+db.getCollection(c).countDocuments({})));'

# procedures and their aggregated input sets (TASK-072)
docker exec jade-tipi-mongo mongosh jdtp --quiet --eval '
  db.prc.find({}).forEach(p=>print(p.properties.esp_workflow+"  inputs="+Object.keys(p.inputs||{}).length));'
```

A pooling run should show many inputs (all pooled members), each `inputs`
entry carrying a `task_id` back-reference to its SOW Item.

---

## Re-running is safe (idempotency)

Re-running an import of already-migrated entities is a **clean no-op, not an
error**. Planning an existing row is a caught duplicate ("0 newly enqueued");
driving only picks up `pending` rows, so `done` rows are never re-driven; and
the materializer is create-only — a same-id re-create is a tolerated
`duplicateMatching`, a differing one is a `conflictingDuplicate` that is
counted and **never overwrites**. Nothing aborts.

Idempotency is anchored on the **persistent queue** and the jdtp id recorded on
each row. So:

- **To resume / retry** a partial or failed import: just rerun the same
  command. Pending rows continue; done rows are skipped; recorded ids are
  reused. Inspect failures with `db.import_queue.find({state:'failed'})`.
- **To genuinely re-import** (e.g. after a mapper change): clear **both** the
  queue and the materialized objects — dropping only one causes trouble.
  Dropping just the queue re-mints **new** ids on the next plan → **duplicate
  objects** (no error, just duplicated data). Full reset:
  ```bash
  docker exec jade-tipi-mongo mongosh jadetipi --quiet --eval 'db.import_queue.drop()'
  docker exec jade-tipi-mongo mongosh jdtp --quiet --eval \
    '["ent","loc","tsk","prc","lnk","typ","txn","hst"].forEach(c=>db.getCollection(c).deleteMany({}))'
  ```

## Config reference

| Property (`--flag` or `ENV`) | Default | Purpose |
|---|---|---|
| `jgi-import.mode` | — | `types` \| `plan` \| `drive` \| `import` (required) |
| `jgi-import.esp-entity` | — | comma-separated UUIDs to import |
| `jgi-import.esp-type-name` | — | import all entities of a type |
| `jgi-import.limit` | 0 (all) | cap the type sweep |
| `jgi-import.org` / `.grp` | — | id-minting namespace (required to drive) |
| `jgi-import.kafka.bootstrap-servers` / `.topic` | — | required to drive |
| `jgi-import.batch-size` | 200 | one transaction per batch |
| `jadetipi.import.couchdb.url` / `.username` / `.password` | `localhost:5984` / admin / admin | source replica |
| `jadetipi.import.couchdb.max-in-memory-mb` | 64 | reader codec limit (raise for very large pool/plate docs) |
| `jadetipi.import.esp-api.base-url` / `.auth-header` | unset | enriched service for `workflow_instance_uuid` |
| `spring.data.mongodb.uri` | `mongodb://localhost:27017/jadetipi` | the import queue |

---

## Gotchas & troubleshooting

- **Rebuild the backend after any wire-schema change.** A backend running an
  older schema silently rejects the new message shape (e.g. the `prc` `inputs`
  map) and leaves the referencing links dangling — you'll see `prc: 0` but
  `procedure_input` links present, and `does not resolve to a prc root` warnings
  in the backend log. Restart `bootRun`.
- **Drive topic must match the backend's pattern** (`jdtp-txn-.*|jdtp_cli_kli`).
  A non-matching topic means the messages are never consumed (silent no-op
  materialization).
- **Don't start the compose `couchdb` service** — it binds port 5984, colliding
  with the local `esp-entity` replica the importer reads.
- **`prc: 0` with the endpoint configured** usually means the lineage genuinely
  has no sample sheets (early-migrated / pre-workflow-tracking data — e.g. the
  `019a3ea4…` era), not a bug. Confirm with a fresh regeneration:
  `curl -H "$AUTH_HEADER" .../api/esp-entity-enriched/<uuid>` (V1 always
  regenerates; use it only for one-off diagnosis, never in bulk — it hits the
  production DB on every call).
- **`clarity document not found`** during the TASK-071 container-precedence path
  means the local CouchDB has no `clarity` database. Precedence dedup needs the
  clarity replica present; without it, esp containers just mint fresh ids.
- **Resuming after a failure**: rerun the same command. Failed items are marked
  `failed` in `import_queue`; inspect with
  `db.import_queue.find({state:'failed'})`. A non-zero exit code flags failures.
- **A `RequestBodyLoggingFilter` / DNS-native warning** on macOS
  (`MacOSDnsServerAddressStreamProvider`) is benign.

---

## Scale-out notes

- The queue is persistent and dependency-ordered, so a large sweep can be
  planned in stages and driven incrementally; batches are independent
  transactions.
- Endpoint sourcing is the current bottleneck for procedure-bearing imports
  (one V2 call per entity). Once the pps-esp-entity backfill has populated
  `workflow_instance_uuid` across the replica, the importer reads it locally
  and the endpoint calls drop to zero — plan the large sweep for after the
  backfill, or let early imports warm the cache.
