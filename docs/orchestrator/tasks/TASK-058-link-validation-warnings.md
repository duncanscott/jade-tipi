# TASK-058 - Link validation warnings (UT-9)

ID: TASK-058
TYPE: implementation
ARTIFACT_INTENT: production-change
STATUS: READY_FOR_REVIEW
OWNER: claude
SOURCE_TASK:
  - TASK-046
OWNED_PATHS:
  - docs/orchestrator/tasks/TASK-058-link-validation-warnings.md
  - docs/uncomfortable-truths.md
  - docs/architecture/kafka-transaction-message-vocabulary.md
  - docs/jdtp-specification.md
  - jade-tipi/src/main/groovy/org/jadetipi/jadetipi/service/
  - jade-tipi/src/test/groovy/org/jadetipi/jadetipi/service/
REQUIRED_CAPABILITIES:
  - mongo-materialization
  - gradle-verification

GOAL:
Resolve UT-9's silence: a `lnk` referencing a nonexistent `type_id`, a
non-link-type type, nonexistent endpoints, forbidden endpoint
collections, or undeclared link properties currently materializes with
no signal at all. Add a warn-only validation layer at `lnk + create`
materialization — the first rung of the same enforcement ladder used for
object IDs (document → warn → enforce), with enforcement left as a
future director decision. The payoff is bulk-import forensics: dangling
references surface in the logs the moment they materialize.

DESIGN:
- At `lnk + create` materialization, before the insert, resolve and check
  (warn + count, never block):
  1. `type_id` present, resolves to a `typ` root, and that root carries
     `kind: "link_type"`;
  2. `left`/`right` present, conforming (so the collection segment names
     a lookup collection), and resolving to existing roots;
  3. endpoint collection segments respect the type's declared
     `allowed_left_collections`/`allowed_right_collections` (when
     declared non-empty);
  4. link `properties` keys respect the type's declared
     `assignable_properties` (when declared non-empty).
- One structured warning per issue (txn, msg, link id, offending value)
  and a `linkValidationWarnings` counter on MaterializeResult —
  deliberately excluded from the apply_state counter diff (a warned link
  still applies; warnings are not terminal outcomes).
- In-transaction ordering works naturally: processing is sequential, so
  endpoints and types created earlier in the same transaction resolve.
  Forward references within a transaction warn — a deliberate nudge
  toward declare-before-use, which every existing producer already
  follows and which the dependency-ordered bulk import (director ruling
  2026-07-05) will follow by construction.
- Cost: up to three extra reads per link; acceptable at current scale
  and warn-only, so trivially removable if it ever matters.

ACCEPTANCE_CRITERIA:
- A fully valid link warns zero times; each defect class (blank/
  unresolved/non-link-type type_id; blank/nonconforming/unresolved
  endpoint; forbidden endpoint collection; undeclared link property)
  warns and counts; defects accumulate per issue; a warned link still
  materializes.
- Existing unit and integration suites stay green (all existing
  producers declare before use).
- UT-9 is resolved in place (warn layer; enforcement escalation and the
  UT-3-shared validation machinery recorded as the open residual);
  spec §4's link-resolution row flips from Planned to the warn layer
  (version bump); the vocabulary doc's links section describes the
  checks.

OUT_OF_SCOPE:
- No enforcement (refusing or skipping invalid links) — a future
  director decision, like the ID-convention ladder's final rung.
- No value_schema validation (UT-3, ruled not needed before bulk
  import).
- No read-side validation surface or repair tooling.

VERIFICATION:
- `./gradlew :jade-tipi:test`
- `JADETIPI_IT_KAFKA=1 ./gradlew :jade-tipi:integrationTest`
- `git diff --check`

IMPLEMENTATION_REPORT (2026-07-05):
- `validateLinkReferences` runs before the `lnk` insert: resolves
  `type_id` (existence + `kind: "link_type"`), both endpoints
  (blank/nonconforming/unresolved — the ID's collection segment names
  the lookup collection), `allowed_*_collections` (when declared
  non-empty), and `assignable_properties` (undeclared link-property
  keys). One structured `Link validation (warn-only)` log line and one
  `linkValidationWarnings` count per issue; a warned link always still
  materializes. The counter is deliberately excluded from
  `MaterializeResult.counters()` — warnings are not terminal
  apply_state outcomes.
- `lookupRoot` tolerates a null Mono from partially stubbed test
  doubles (production templates never return null), which let the
  entire existing unit suite pass unchanged — no stubbing ripple this
  time.
- New `CommittedTransactionMaterializerLinkValidationSpec`: zero
  warnings on a fully valid link; a where-driven feature over seven
  defect classes (each warns once, link still applies); accumulation
  (three issues → three counts); and a declare-before-use feature
  materializing type + endpoints + link in one snapshot against an
  insert-backed in-memory store, proving same-transaction references
  resolve without warnings.
- Live proof, unplanned but welcome: the full Kafka suite run surfaced
  exactly two warnings — in ContentsHttpReadIntegrationSpec, whose link
  deliberately references endpoint roots it never creates (it tests
  link-row queries, which tolerate dangling references by design). The
  warn layer flagged precisely the dangling references and nothing
  else; every other spec's declare-before-use sequences ran warning-
  free.
- Docs: UT-9 resolved in place (warn layer; enforcement escalation is
  the recorded residual, a future director decision); spec 0.5.1-draft —
  the §4 link-resolution row flips to the Normative warn layer and the
  `value_schema` row records the director's 2026-07-05 ruling (deferred;
  schemas follow real data); the vocabulary doc's links section
  describes the checks.
- Verification results: `:jade-tipi:test` and full `JADETIPI_IT_KAFKA=1
  :jade-tipi:integrationTest` BUILD SUCCESSFUL (1m57s);
  `git diff --check` clean.
