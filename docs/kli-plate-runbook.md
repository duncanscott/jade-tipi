# Runbook: plate-96-well type hierarchy and barcode via kafka-kli

This runbook drives the TASK-040 milestone end to end with the `kli` client:
one Kafka transaction that creates a `barcode` property definition, a
`container` → `plate` → `plate_96_well` type hierarchy, a typed
`plate_96_well` location instance, and a barcode value projected onto that
instance's root document. The barcode is registered only on `container`, so
the projected value proves the inheritance-aware registration walk.

The step payloads live in `clients/kafka-kli/examples/plate-96-well/` as
data-only JSON files (the `data` portion of each message; `kli` builds the
envelope from your session).

## Prerequisites

1. Local stack up (MongoDB, Keycloak, Kafka):

   ```sh
   docker compose -f docker/docker-compose.yml up -d
   ```

2. Backend running — the Kafka listener and materializer live in the app:

   ```sh
   ./gradlew bootRun
   ```

3. kli built and the shell function sourced:

   ```sh
   ./gradlew :clients:kafka-kli:installDist
   source bin/kli.sh
   ```

## Login

```sh
kli login
```

Open the printed URL, sign in (locally: `dnscott` / `dnscott`; the local
`kli` client injects `tipi_org`/`tipi_group`/`orcid` claims). The wrapper
function exports `KLI_SESSION` for you. `kli status` confirms the session.
The default topic `jdtp_cli_kli` is already in the backend's listener
pattern, so no `kli config` is needed.

## The sequence

Run from the repository root:

```sh
cd clients/kafka-kli/examples/plate-96-well

kli open   --data @00-open.json
kli create --collection ppy --data @01-property-barcode.json
kli create --collection typ --data @02-type-container.json
kli update --collection typ --data @03-container-add-barcode.json
kli create --collection typ --data @04-type-plate.json
kli create --collection typ --data @05-type-plate-96-well.json
kli create --collection loc --data @06-plate-instance.json
kli create --collection ppy --data @07-assign-barcode.json
kli commit --data @08-commit.json
```

Each command prints the published message ID. The commit triggers the
post-commit materializer in the backend.

## Inspect the result

MongoDB (database `jdtp` for `bootRun`'s default profile):

```sh
docker exec jade-tipi-mongo mongosh jdtp --quiet --eval '
  printjson(db.typ.findOne({_id: "jade-tipi-org~dev~plate96-demo~typ~plate_96_well"}));
  printjson(db.loc.findOne({_id: "jade-tipi-org~dev~plate96-demo~loc~plate_0001"}));
'
```

Expected on the `loc` root:

- `type_id` pointing at the `plate_96_well` type;
- `properties.name == "demo plate 0001"` (first-pass inline bag);
- `property_values` keyed by the barcode `ppy` ID, each entry carrying
  `value`, `txn_id`, `commit_id`, `msg_uuid`, and `applied_at` — the
  transaction provenance that answers "who wrote this value?" through the
  durable `txn` record.

Expected on the `plate` and `plate_96_well` typ roots:
`properties.parent_type_id` pointing at the supertype. Only `container`
carries `properties.property_refs` for the barcode.

The committed transaction itself:

```sh
docker exec jade-tipi-mongo mongosh jdtp --quiet --eval '
  printjson(db.txn.findOne({_id: {$regex: "~kli$"}, state: "committed"}));
'
```

## Re-running

The materializer is idempotent per payload, not per run:

- Re-delivering the identical messages counts as `duplicateMatching` and
  changes nothing.
- A *new* transaction re-submitting the same IDs with the same payloads is a
  `conflictingDuplicate` (the provenance differs) — logged and counted,
  never overwritten.

To repeat the demo cleanly, either change the `plate96-demo` segment in
every ID, or drop the demo documents first:

```sh
docker exec jade-tipi-mongo mongosh jdtp --quiet --eval '
  ["typ","ppy","loc"].forEach(c =>
    db[c].deleteMany({_id: {$regex: "plate96-demo"}}));
'
```

## Troubleshooting

- `kli open` fails with missing `tipi_org`/`tipi_group`: re-login; the local
  realm's `kli` client supplies both claims.
- Nothing materializes after commit: confirm the backend is running and its
  log shows `Transaction committed` and `Materialized ...` lines; the
  listener pattern must include the topic (`jdtp_cli_kli` is included by
  default).
- Assignment skipped as unregistered: check the `container` typ root carries
  `properties.property_refs` for the barcode ID and that the parent chain
  (`properties.parent_type_id`) is intact — the walk is bounded and
  cycle-safe, and a broken chain is treated as unregistered.
