#!/usr/bin/env bash
# Source this file to define the kli shell function.
#
# Add to your .bashrc or .zshrc:
#   source /path/to/jade-tipi/bin/kli.sh
#
# Then use:
#   kli login
#   kli status
#   kli publish --topic <topic> --file <message.json>
#   kli logout

_KLI_SOURCE="${BASH_SOURCE[0]:-${(%):-%x}}"
_KLI_BIN="$(cd "$(dirname "$_KLI_SOURCE")/.." && pwd)/clients/kafka-kli/build/install/kli/bin/kli"
unset _KLI_SOURCE

kli() {
    if [ ! -x "$_KLI_BIN" ]; then
        echo "ERROR kli not found at $_KLI_BIN" >&2
        echo "Run: ./gradlew :clients:kafka-kli:installDist" >&2
        return 1
    fi

    case "$1" in
        login|logout)
            eval "$("$_KLI_BIN" "$@")"
            ;;
        *)
            "$_KLI_BIN" "$@"
            ;;
    esac
}
