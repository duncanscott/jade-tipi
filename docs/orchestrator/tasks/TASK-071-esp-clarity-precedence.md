# TASK-071 - ESP-over-clarity precedence: container id-unification

ID: TASK-071
TYPE: implementation
ARTIFACT_INTENT: production-change
STATUS: READY_FOR_REVIEW
OWNER: unassigned
SOURCE_TASK:
  - TASK-069
  - TASK-061
OWNED_PATHS:
  - docs/orchestrator/tasks/TASK-071-esp-clarity-precedence.md
  - DIRECTION.md
  - importers/jgi-import/
REQUIRED_CAPABILITIES:
  - kafka-integration
  - mongo-materialization
  - gradle-verification

GOAL:
Implement the director's esp-over-clarity precedence ruling to the extent
the replicated data supports it: when the same physical entity exists in
both databases, the esp import must REUSE the clarity object's id rather
than mint a duplicate. Grounded in a live, adversarially-verified overlap
investigation (2026-07-07).

INVESTIGATION FINDINGS (the design driver):
- Overlap by identity exists ONLY for plate-type Containers: esp `name`
  == clarity container limsid == `containers_<name>` (same-entity
  verified on real pairs, e.g. esp 27-279088 ↔ clarity
  containers_27-279088).
- Sample-level entities (Nucleic Acid, Aliquot, Illumina Library) have
  NO clarity key in any field (0/90 matched); esp uses JGI ITS ids,
  clarity uses DES/2-NNNNN — disjoint namespaces. SOW Item / JgiProject
  likewise.
- ~11% of limsid-shaped esp container names have no clarity doc (the
  esp-native 27-810xxx block, and scattered gaps), so an existence gate
  against the clarity import is MANDATORY — shape alone is insufficient.
- Consequence: containers carry no rich esp values, and samples don't
  overlap, so there is no *value* to merge under precedence. The
  implementable, valuable slice is **identity dedup** (one physical
  plate → one root).

DESIGN:
- **Precedence rule** (`EspEntityImportMapper.isClarityContainerCandidate`
  + driver existence gate): overlay when class_name == "Container", name
  =~ `^[0-9]+-[0-9]+$`, and the clarity import_queue row
  `clarity~containers_<name>` carries a recorded jdtp_id.
- **Id reuse** (`ClarityImportDriver.applyClarityPrecedence`): pre-seed
  the id resolver so the esp uuid resolves to the clarity id, recorded on
  the esp queue row — so the container's own root AND every downstream
  esp reference (contents links from contained samples, begat links)
  resolve to the shared clarity plate.
- **No duplicate create** (`EspEntityImportMapper.mapEntity` overlay
  path): an overlay entity emits its links but not a root-create (the
  clarity root already exists; a duplicate create would be a counted
  conflict).
- Keyed on `name` only (rack barcode can be the esp UUID). Existence
  miss → normal esp mint path. Clarity-first ordering (the ratified
  order) is assumed.
- Driver loop fix: `markDone` moved out of the publish guard so overlay
  items that emit zero messages are still closed (else an all-overlay
  batch loops forever).

ACCEPTANCE_CRITERIA:
- Unit: `isClarityContainerCandidate` accepts clean limsids only (rejects
  `_X` suffix, free-text, tube-rack, Sample class); an overlay entity
  emits no root-create but still emits its links onto the reused id;
  driver reuses the clarity id and records it on the esp row with no
  duplicate create when the clarity row exists, and takes the mint path
  when it is absent.
- Live: with the clarity container 27-279088 imported first, planning +
  driving the matching esp container reuses the clarity root id (esp
  queue row jdtp_id == clarity root id) and mints no second loc root.
- Docs: design doc overlap section rewritten with the findings, the
  container dedup, and the recorded sample-value-precedence deficiency;
  DIRECTION.md updated.

OUT_OF_SCOPE:
- Sample/value-level precedence — not reconstructable from the replica
  (recorded deficiency); needs an external cross-reference.
- Contributing esp container variables onto the shared clarity root
  (thin value, needs property registration on the clarity type) —
  deferred.
- Non-container classes, suffixed/re-plate containers.

OPEN QUESTIONS (director-review):
- Should a full-population sweep quantify final reuse coverage before a
  production dedup run? (Sampling put container match at ~68% of esp
  containers; esp re-imported only ~0.7% of clarity containers.)
- Model `_X`/`_D` re-plate derivatives as plain new roots (current) or
  as children of the base clarity container?

VERIFICATION:
- `./gradlew :importers:jgi-import:test`
- `JADETIPI_IT_KAFKA=1 ./gradlew integrationTest` (both modules)
- `git diff --check`

IMPLEMENTATION_REPORT:

SUMMARY:
Implemented and proven live. Container-only, existence-gated id reuse:
an esp plate that re-imports a clarity container adopts the clarity root
id and attaches its esp graph to it instead of minting a duplicate. All
module suites green (unit + integration, `JADETIPI_IT_KAFKA=1`); the new
live itest imports the clarity container 27-279088 first, then the
matching esp container, and asserts the reused id and the absence of a
second loc root. The investigation that scoped this ran as an
adversarial fan-out workflow over live CouchDB; the implementation was
adversarially reviewed by a second workflow.

KEY REPORT-BACK FOR DIRECTOR: the esp-over-clarity ruling was framed as
*value* precedence, but the data shows the only cross-database overlap is
container *identity* — samples share no key. So this slice delivers
identity dedup (no duplicate plates), and sample value-precedence is
recorded as a deficiency (no replica join key exists).

ADVERSARIAL REVIEW (workflow, 3 reviewers → per-finding verify): two
real defects confirmed and fixed; three refuted (dropped-esp-properties
is intentional id-unification; orphan-link risk is prevented by the
existence gate; the order-dependence duplicate was the same as the
ordering finding).
- Fixed — phantom txn_id: a zero-message (all-overlay) batch published no
  transaction, yet `markDone` stamped the created-but-unpublished
  `txn.id`. Now a zero-message batch marks its rows done with a null
  txn_id. (Pinned by the driver spec.)
- Fixed — silent out-of-order split: esp-before-clarity minted a
  duplicate plate with no signal, and the null-jdtp_id check conflated
  "clarity absent" (expected, esp-native) with "clarity queued but not
  driven" (an ordering violation). Added `ImportQueueService.rowExists`
  and a WARN only for the genuine ordering violation (clarity-first stays
  the ratified order; no noise for expected esp-native plates). (Pinned
  by a driver spec feature.)