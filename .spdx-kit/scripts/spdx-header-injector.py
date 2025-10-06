#!/usr/bin/env python3
import sys, os, re, argparse
from pathlib import Path

HEADER_PATH_DEFAULT = "config/header-JavaLike.txt"
SPDX_PATTERN = re.compile(r"SPDX-License-Identifier\s*:\s*", re.IGNORECASE)

def detect_eol(text: str) -> str:
    if "\r\n" in text:
        return "\r\n"
    if "\r" in text:
        return "\r"
    return "\n"

def has_spdx(text: str) -> bool:
    return bool(SPDX_PATTERN.search(text[:1000]))

def insert_header_java_like(src: str, header: str) -> str:
    lines = src.splitlines(keepends=True)
    idx = 0
    if lines and lines[0].startswith("#!"):
        idx = 1
    return "".join(lines[:idx]) + header + "".join(lines[idx:])

def process_file(path: Path, header: str, dry_run: bool, verbose: bool) -> bool:
    text = path.read_text(encoding="utf-8", errors="replace")
    if has_spdx(text):
        if verbose:
            print(f"SKIP (has SPDX): {path}")
        return False
    eol = detect_eol(text)
    new_text = insert_header_java_like(text, header.replace("\n", eol))
    if dry_run:
        print(f"WOULD UPDATE: {path}")
        return True
    path.write_text(new_text, encoding="utf-8")
    if verbose:
        print(f"UPDATED: {path}")
    return True

def main():
    ap = argparse.ArgumentParser(description="Inject SPDX license headers into source files.")
    ap.add_argument("--root", default=".", help="Repo root to scan (default: .)")
    ap.add_argument("--header", default=HEADER_PATH_DEFAULT, help="Header template path")
    ap.add_argument("--exts", default=".java,.groovy,.kt", help="Comma-separated extensions to include")
    ap.add_argument("--dry-run", action="store_true", help="Show what would change without writing")
    ap.add_argument("--verbose", "-v", action="store_true", help="Verbose output")
    ap.add_argument("--exclude-dirs", default=".git,target,build,out,node_modules,.idea,.gradle", help="Comma-separated dirs to skip")
    args = ap.parse_args()

    header = Path(args.header).read_text(encoding="utf-8")
    include_exts = {e if e.startswith('.') else '.'+e for e in args.exts.split(',') if e.strip()}
    exclude_dirs = set([d.strip() for d in args.exclude_dirs.split(',') if d.strip()])

    changed = 0
    for root, dirs, files in os.walk(args.root):
        dirs[:] = [d for d in dirs if d not in exclude_dirs]
        for fn in files:
            p = Path(root) / fn
            if p.suffix.lower() in include_exts:
                try:
                    if process_file(p, header, args.dry_run, args.verbose):
                        changed += 1
                except Exception as e:
                    print(f"ERROR processing {p}: {e}", file=sys.stderr)
    if args.dry_run:
        print(f"[DRY-RUN] Files that would be updated: {changed}")
    else:
        print(f"Files updated: {changed}")

if __name__ == "__main__":
    main()
