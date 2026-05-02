# claude-1 Next Step

The developer writes pre-work plans here before implementation begins.

STATUS: PRESENT

## TASK-020 — Define and materialize group records (pre-work, revision 2)

### Directive summary

`DIRECTIVES.md` global signal is `REQUEST_NEXT_STEP`. The active task
file `docs/orchestrator/tasks/TASK-020-group-record-model-and-materialization.md`
is `READY_FOR_PREWORK` with `OWNER: claude-1`. The director's
`PREWORK_REVIEW` block (2026-05-02) keeps the task at
`READY_FOR_PREWORK`, accepts that revision-1's pre-work stayed inside
the base owned-path boundary, but rejects revision-1's
`data.permissions` list-of-`{grp_id, level}` shape because:

1. The accepted task acceptance line requires "a simple group
   permissions **map** that grants other group IDs either `rw` or `r`."
2. `DIRECTION.md`, `docs/README.md`, `docs/Jade-Tipi.md`, and
   `docs/user-authentication.md` already describe a permissions **map**
   for other groups; a list-only implementation would create model
   drift across the project's product direction docs.

This revision answers the three concrete questions the director posed
and recommends a single default path while keeping a backup option open
in case the human/director rejects the schema-scope expansion. It
produces a plan only — no schema, example, materializer, test, or doc
edit will be made on this turn (no `READY_FOR_IMPLEMENTATION` /
`PROCEED_TO_IMPLEMENTATION` signal is present).

### Resolution of the map / schema compatibility issue

The blocker is concrete: `message.schema.json` types `Message.data` as
`SnakeCaseObject`, whose recursive `additionalProperties` reference to
`SnakeCaseValue` re-applies the `propertyNames: ^[a-z][a-z0-9_]*$` rule
to every nested object. World-unique grp IDs are
`<org>~<grp>~<uuid>~grp~<short>`, so they contain `~` and `-` and
cannot be used as JSON object keys inside any `SnakeCaseObject`-typed
subtree without a schema change.

Three candidate physical shapes were considered for the wire payload:

- **Shape A — grp-id-keyed permissions map (RECOMMENDED).** Wire
  payload places a `permissions` object inside `data` whose keys are
  world-unique grp IDs and whose values are `"rw"` or `"r"`. This is
  the shape the existing docs already describe and is the most
  natural reading of "permissions map ... grants other group IDs
  either `rw` or `r`." It does NOT validate against today's
  `message.schema.json` because the inner keys violate the
  `SnakeCaseObject.propertyNames` rule. Adopting Shape A requires a
  bounded schema edit plus a director-approved owned-path expansion
  (see "Schema edit sketch" and "Requested scope expansion" below).

- **Shape B — level-keyed permissions map (BACKUP).** Wire payload
  places a `permissions` object inside `data` whose keys are
  exactly `"rw"` and `"r"` and whose values are arrays of
  world-unique grp IDs. Both outer keys are valid `snake_case`, the
  values are string arrays (allowed by `SnakeCaseValue`), so this
  shape validates against today's schema with no schema edit. It is
  still a map (only two stable keys, indexed by access level), so it
  satisfies "permissions map" literally and lossless-converts to a
  grp-id-keyed map at query time. It does require small wording
  tweaks in `DIRECTION.md`,
  `docs/README.md`, `docs/Jade-Tipi.md`, and
  `docs/user-authentication.md` because text such as "use only two
  permission **values**: `rw`, `r`" implies the values of the map
  are `rw`/`r` — under Shape B the `rw`/`r` strings move from
  values to keys, which is a slight semantic shift the docs would
  need to acknowledge.

- **Shape C — list of `{grp_id, level}` rows (REJECTED).** This was
  the revision-1 default proposal. The director has explicitly
  rejected it in `PREWORK_REVIEW` because it contradicts the "map"
  language already in the accepted task file and four product-
  direction docs. Not proposed here.

The recommendation is **Shape A with a director-approved owned-path
expansion** because it is the only candidate that exactly matches
every existing doc sentence with no rewording, and because the
required schema edit is small, well-bounded, and reversible.

### Concrete answers to the PREWORK_REVIEW questions

**Q1 — What exact canonical `grp + create` payload shape will preserve
a permissions map granting other group IDs `rw` or `r` while still
passing `MessageSpec` validation against `message.schema.json`?**

Two shapes pass `MessageSpec` validation while preserving the "map"
semantics:

- Shape A passes only after a bounded schema change that adds a
  `Permissions` `$def` and conditionally overrides `data.permissions`
  for `collection: grp` so its inner property names are not subject to
  the `SnakeCaseObject.propertyNames` rule. The wire payload would
  read:
  ```json
  "data": {
    "id": "jade-tipi-org~dev~018fd849-2a4d-7d0d-8d0d-cccccccccccc~grp~analytics",
    "name": "analytics",
    "description": "analytics team",
    "permissions": {
      "jade-tipi-org~dev~018fd849-2a4d-7d0d-8d0d-aaaaaaaaaaaa~grp~lab_ops": "rw",
      "jade-tipi-org~dev~018fd849-2a4d-7d0d-8d0d-bbbbbbbbbbbb~grp~viewers": "r"
    }
  }
  ```

- Shape B passes today's schema unchanged. The wire payload would
  read:
  ```json
  "data": {
    "id": "jade-tipi-org~dev~018fd849-2a4d-7d0d-8d0d-cccccccccccc~grp~analytics",
    "name": "analytics",
    "description": "analytics team",
    "permissions": {
      "rw": [
        "jade-tipi-org~dev~018fd849-2a4d-7d0d-8d0d-aaaaaaaaaaaa~grp~lab_ops"
      ],
      "r":  [
        "jade-tipi-org~dev~018fd849-2a4d-7d0d-8d0d-bbbbbbbbbbbb~grp~viewers"
      ]
    }
  }
  ```

**Q2 — Should this task request an explicit owned-path expansion to
update `message.schema.json` and its focused tests, or should the task
be revised by the director/human to accept a list-shaped permissions
representation?**

Recommendation: **request the owned-path expansion.** The expansion
is small (one schema file plus one test file) and lets us deliver the
documented direction with no doc rewording. Shape A is the cleanest
fit for every existing sentence in `DIRECTION.md`,
`docs/README.md`, `docs/Jade-Tipi.md`, and
`docs/user-authentication.md`, and it is the shape any future
permission-enforcement code will want at read time anyway.

The fallback (without an expansion) is **Shape B with bounded doc
rewording** — also acceptable, also a true "map," requires no schema
change, and stays inside today's owned paths. List-shaped
representation (Shape C) is not proposed.

**Q3 — Which relevant docs will be updated so `DIRECTION.md`,
`docs/README.md`, `docs/Jade-Tipi.md`, `docs/user-authentication.md`,
and `docs/architecture/kafka-transaction-message-vocabulary.md` do
not contradict each other on map versus list semantics?**

Under Shape A (recommended):

- `docs/architecture/kafka-transaction-message-vocabulary.md` — new
  section ("Group Records And First-Pass Permissions") that documents
  the canonical `grp + create` wire payload (grp-id-keyed map),
  references the conditional schema rule, names the canonical example
  `13-create-group.json`, documents the materialized root-document
  layout (`_id`, `id`, `collection: "grp"`, `type_id`,
  `properties.permissions` copied verbatim, `links: {}`,
  `_head.provenance`), and states explicitly that no enforcement is
  added in this task.
- `DIRECTION.md` — no contradiction with Shape A as currently
  written; add at most one sentence in the existing
  "Groups And Permissions" section noting that the wire and storage
  shape uses a map keyed by world-unique grp IDs with values
  restricted to `rw`/`r`.
- `docs/README.md` — no contradiction; the existing
  "Groups and Permissions" subsection already describes the same map
  shape. No edit required unless review surfaces a contradiction.
- `docs/Jade-Tipi.md` — no contradiction; existing prose refers to
  groups as ownership and permission units in general terms. No edit
  required.
- `docs/user-authentication.md` — the existing "Group Permission
  Direction" section already documents the map-with-`rw`/`r`-values
  shape and matches Shape A exactly. No edit required.

Under Shape B (backup):

- All five docs (the four above plus `kafka-transaction-message-
  vocabulary.md`) need a small, consistent rewording so each refers
  to a "permissions map keyed by access level (`rw`, `r`) with values
  that are arrays of grp IDs holding that level," instead of the
  current "permissions map ... use only two permission values:
  `rw`, `r`." The reword is mechanical, but it is a genuine semantic
  shift and should not be undertaken silently.

In both paths, `docs/agents/claude-1-changes.md` is updated only after
implementation is approved and only to record what was done.

### Schema edit sketch (Shape A only)

The minimal change to `libraries/jade-tipi-dto/src/main/resources/schema/message.schema.json`:

1. Add a new `$def`:
   ```json
   "Permissions": {
     "title": "Permissions",
     "description": "Map keyed by world-unique grp IDs, values constrained to read/write or read-only access.",
     "type": "object",
     "additionalProperties": {
       "type": "string",
       "enum": ["rw", "r"]
     }
   }
   ```
2. Append one conditional rule to the existing `allOf`:
   ```json
   {
     "description": "Group create/update messages may carry a permissions map keyed by world-unique grp IDs.",
     "if": {
       "properties": { "collection": { "const": "grp" } },
       "required": ["collection"]
     },
     "then": {
       "properties": {
         "data": {
           "properties": {
             "permissions": { "$ref": "#/$defs/Permissions" }
           }
         }
       }
     }
   }
   ```

JSON Schema 2020-12 semantics: when `data.properties.permissions` is
named explicitly, it takes precedence over `data.additionalProperties`
(SnakeCaseValue) for that property only, so the inner map keys are
exempt from the `propertyNames` snake_case rule. All other `data`
properties continue to flow through `SnakeCaseValue` recursively. The
outer key `"permissions"` itself remains snake_case, satisfying the
top-level `propertyNames` rule.

Test impact is bounded: `MessageSpec.groovy` already iterates a
constant `EXAMPLE_PATHS` list and validates each against the schema.
Adding `13-create-group.json` to that list exercises the new
conditional. One focused feature method confirms `grp + create`
semantics. No other DTO or schema test file changes are anticipated.

### Requested scope expansion (Shape A)

If the director adopts the recommendation, please add the following
two paths to TASK-020's `OWNED_PATHS` (or to `DIRECTIVES.md` as a
TASK-020 scope expansion):

- `libraries/jade-tipi-dto/src/main/resources/schema/message.schema.json`
- `libraries/jade-tipi-dto/src/test/resources/` (only if a fixture
  or schema-text change is needed; expected to be unchanged because
  the example resource lives under `src/main/resources/example/...`
  which is already in OWNED_PATHS)

If the director adopts Shape B instead, no owned-path expansion is
needed.

### Revised file changes

All paths below are inside TASK-020's `OWNED_PATHS` plus the requested
schema expansion (Shape A only).

1. **(Shape A only)** `libraries/jade-tipi-dto/src/main/resources/schema/message.schema.json`
   — add the `Permissions` `$def` and the one conditional `allOf`
   entry described above. No other schema fields change.

2. `libraries/jade-tipi-dto/src/main/resources/example/message/13-create-group.json`
   — new canonical example. Same `txn` envelope shape as the existing
   examples. World-unique grp id of the form
   `jade-tipi-org~dev~018fd849-2a4d-7d0d-8d0d-cccccccccccc~grp~analytics`
   (UUIDv7 distinct from the existing example messages). Payload
   carries `id`, `name`, `description`, and a two-entry
   `permissions` payload using whichever shape is approved (A or B).

3. `libraries/jade-tipi-dto/src/test/groovy/org/jadetipi/dto/message/MessageSpec.groovy`
   — append `13-create-group.json` to `EXAMPLE_PATHS` (covers the
   parameterized round-trip and schema-validation features). Add one
   focused feature ("grp create example carries a permissions map
   keyed by grp ids" under Shape A, or "... keyed by access level"
   under Shape B) that asserts collection, action, id format, and
   the two `rw`/`r` rows. No other changes to this file.

4. `docs/architecture/kafka-transaction-message-vocabulary.md`
   — append a "Group Records And First-Pass Permissions" section
   that documents the wire payload (Shape A or B), the materialized
   root-document layout, the explicit non-enforcement boundary, and
   a reference to the canonical example.

5. `jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializer.groovy`
   — single targeted edit:
   - Add `static final String COLLECTION_GRP = 'grp'` next to the
     existing collection constants.
   - Add `case COLLECTION_GRP: return true` in the `isSupported(...)`
     switch on `message.collection` (returns true for
     `Action.CREATE` only, matching the `loc`/`lnk`/`typ` precedent).
   - Update the class Javadoc bullet list to include
     `grp + create` → `grp` collection.
   - No change to `buildDocument`, `buildInlineProperties`,
     `handleInsertError`, or any helper. The `default` branch in
     `buildDocument` already produces the correct root shape with
     `permissions` (Shape A or B) copied through
     `properties.permissions` verbatim.

6. `jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/CommittedTransactionMaterializerSpec.groovy`
   — add focused features:
   - "materializes a grp create as a root document with permissions
     under properties and `_head.provenance`" — asserts shared root
     fields (`_id`, `id`, `collection == 'grp'`,
     `type_id == null` when omitted), `properties.name`,
     `properties.description`, `properties.permissions` shape (A or
     B), `links == [:]`, `_head.provenance.collection == 'grp'`,
     `_head.provenance.action == 'create'`, no `_jt_provenance`.
   - "skips a grp update message" — asserts
     `result.skippedUnsupported == 1` and zero Mongo writes.
   - "skips a grp delete message" — same shape for `grp + delete`.
   - "skips a grp create with missing data.id" — asserts
     `result.skippedInvalid == 1` and zero Mongo writes.
   - "skips a grp create with blank data.id" — same with whitespace
     id.
   - The existing matching/conflicting/insert-error duplicate
     features (which already exercise the generic `handleInsertError`
     path through `loc`) are not duplicated for `grp` per the task
     wording "where existing coverage does not already cover the
     generic path."

7. `DIRECTION.md`, `docs/README.md`, `docs/Jade-Tipi.md`,
   `docs/user-authentication.md` — under Shape A, no edits expected
   (existing prose already matches the recommended wire shape). Under
   Shape B, small mechanical reword to relocate `rw`/`r` from
   "values" to "keys" of the permissions map. Either way, edits in
   these files are kept to the minimum needed for non-contradiction
   with the new vocabulary section.

### Files intentionally not touched

- `docs/orchestrator/tasks/TASK-020-group-record-model-and-materialization.md`
  — listed in `OWNED_PATHS` but director-owned in practice. The
  developer report file is `docs/agents/claude-1-changes.md`.
- Anything under `clients/`, `frontend/`, `docker/`, Kafka topic
  configuration, OAuth / SASL hardening, security-policy code,
  `ContentsLinkReadService`, controllers, `_jt_provenance` migration
  paths, or other read/write paths — explicitly out of scope by the
  task file's `OUT_OF_SCOPE` block.

### Verification plan

For this pre-work turn:

- Static review only. No Gradle, Docker, MongoDB, or Kafka commands
  executed.
- Branch-state check: working tree clean at start of turn (per the
  orchestrator pre-amble's `git status` snapshot).

For the implementation turn (only after
`READY_FOR_IMPLEMENTATION` or `PROCEED_TO_IMPLEMENTATION`):

```sh
./gradlew :libraries:jade-tipi-dto:test --tests '*MessageSpec*'
./gradlew :jade-tipi:compileGroovy
./gradlew :jade-tipi:compileTestGroovy
./gradlew :jade-tipi:test --tests '*CommittedTransactionMaterializerSpec*'
```

If local tooling, Gradle wrapper-cache locks under
`/Users/duncanscott/.gradle`, Docker, or MongoDB block verification,
the documented setup commands are:

```sh
docker compose -f docker/docker-compose.yml --profile mongodb up -d
./gradlew --stop
```

These are setup steps, not product blockers.

### Stay-in-scope check for this pre-work turn

This turn edits exactly:

- `docs/agents/claude-1-next-step.md` — base owned path.

No other paths are modified. No code, schema, test, Docker, MongoDB,
CouchDB, Kafka, frontend, security, DTO, HTTP-endpoint,
`_jt_provenance`, doc, or example changes are made or proposed for
this turn.

### Open questions / blockers

The following remain for director resolution before implementation
can begin. Each has a default proposal so the director can accept or
redirect with one signal change.

- **Q-20-A — Permissions wire shape (Shape A vs Shape B).**
  Default proposal: Shape A (grp-id-keyed map) with a director-
  approved owned-path expansion to edit
  `libraries/jade-tipi-dto/src/main/resources/schema/message.schema.json`.
  Backup: Shape B (level-keyed map) with mechanical reword in the
  four product-direction docs. Either is implementable; the schema-
  expansion path is preferred because it preserves every current doc
  sentence verbatim.
- **Q-20-B — Owned-path expansion request.** If the director adopts
  Shape A, please add `libraries/jade-tipi-dto/src/main/resources/schema/message.schema.json`
  to TASK-020 `OWNED_PATHS` (and to the `Scope Expansion` section of
  `DIRECTIVES.md` if that is the preferred publication channel).
  Without this expansion, only Shape B is reachable.
- **Q-20-C — `grp` `type_id` in the canonical example.** Default
  proposal: omit `type_id` to mirror the existing
  `loc`/`typ` examples and avoid introducing a group-typing taxonomy
  in this task. If the director wants the first canonical example to
  demonstrate a `grp` `type_id`, I would need direction on whether
  to also include a companion `typ + create` example for that group
  type.
- **Q-20-D — End-to-end coverage scope.** Default proposal: focused
  unit tests on `MessageSpec` and
  `CommittedTransactionMaterializerSpec` only — no
  `JADETIPI_IT_KAFKA`-gated integration spec. The TASK-020
  acceptance criteria list materialization plus DTO/example
  validation; HTTP/Kafka end-to-end is not requested. Confirming
  because TASK-016 had a parallel integration spec.
- **Q-20-E — `_id` shape for `grp` records.** No change requested.
  The generic materializer sets `_id == data.id`, consistent with
  `loc` and `typ`. Calling this out only because the permissions
  map references peer grp `data.id` values, so the `_id` choice
  matters for any future reverse lookup. The current plan does not
  add reverse-lookup indexing — that is future work.

STOP.
