#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:-.}"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
KIT_ROOT="$(cd "$HERE/.." && pwd)"

python3 "$KIT_ROOT/scripts/spdx-header-injector.py"   --root "$ROOT"   --header "$KIT_ROOT/config/header-JavaLike.txt"   --exts ".java,.groovy,.kt"   --exclude-dirs ".git,target,build,out,node_modules,.idea,.gradle"   "${@:2}"
