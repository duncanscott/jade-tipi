#!/usr/bin/env python3
"""Regenerate the vendored ESP workflow-configuration snapshot (TASK-070).

The ESP LIMS workflow configs live in a separate JGI repo
(``esplims/content/workflows/*.yml``). Each declares a workflow's protocol
sequence (``protocols:``) and accepted input entity types (``sample_types:``).
The importer needs that map to reconstruct procedures from sample sheets:
the protocol count tells us how many sheets merge into one procedure
instance, and the input types identify the procedure's input.

This script extracts a self-contained JSON snapshot vendored into the
importer's resources, so the excisable module carries no dependency on the
esplims repo at build or run time. Re-run when the lab adds/changes
workflows:

    python3 regen_esp_workflow_config.py \
        --esplims /Users/duncanscott/git-code/pps/esp/esplims \
        --out ../src/main/resources/esp-workflow-config.json

The ``lab_procedure`` flag is a PROPOSED classification (director-review):
administrative CRUD workflows (Edit / Add Create / Label Printing) are
excluded from procedure reconstruction; everything operating on physical
samples is included. Adjust the snapshot directly or the heuristic here.
"""
import argparse, glob, json, os, re, sys
import yaml

ADMIN_NAME = re.compile(r'\b(Edit|Add Create|Label Printing)\b', re.IGNORECASE)


def workflow_from_doc(doc):
    # configs are either a top-level map {Name: {...}} or a one-item list
    if isinstance(doc, list):
        doc = doc[0]
    if not isinstance(doc, dict) or len(doc) != 1:
        return None, None
    (name, body), = doc.items()
    return name, (body or {})


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--esplims', required=True, help='path to the esplims repo root')
    ap.add_argument('--out', required=True, help='snapshot JSON output path')
    args = ap.parse_args()

    wf_dir = os.path.join(args.esplims, 'content', 'workflows')
    if not os.path.isdir(wf_dir):
        sys.exit(f'workflows dir not found: {wf_dir}')

    workflows = {}
    for path in sorted(glob.glob(os.path.join(wf_dir, '*.yml'))):
        with open(path) as fh:
            doc = yaml.safe_load(fh)
        name, body = workflow_from_doc(doc)
        if not name:
            print(f'  skipped (unparseable): {os.path.basename(path)}', file=sys.stderr)
            continue
        protocols = list(body.get('protocols') or [])
        inputs = list(body.get('sample_types') or [])
        workflows[name] = {
            'protocols': protocols,
            'protocol_count': len(protocols),
            'input_types': inputs,
            # PROPOSED — director-review; see module docs
            'lab_procedure': not bool(ADMIN_NAME.search(name)),
            'source_file': os.path.basename(path),
        }

    snapshot = {
        '_comment': ('Vendored ESP workflow config snapshot (TASK-070). '
                     'Regenerate with scripts/regen_esp_workflow_config.py. '
                     'lab_procedure is a proposed classification for director review.'),
        'workflows': dict(sorted(workflows.items())),
    }
    with open(args.out, 'w') as fh:
        json.dump(snapshot, fh, indent=2, ensure_ascii=False)
        fh.write('\n')
    lab = sum(1 for w in workflows.values() if w['lab_procedure'])
    print(f'wrote {len(workflows)} workflows ({lab} lab, {len(workflows)-lab} administrative) to {args.out}')


if __name__ == '__main__':
    main()
