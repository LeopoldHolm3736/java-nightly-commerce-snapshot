#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
OUT="$ROOT/build/classes"
rm -rf "$ROOT/build"
mkdir -p "$OUT"
javac -d "$OUT" $(find "$ROOT/src/main/java" "$ROOT/src/test/java" -name '*.java' -print)

case "${1:-test}" in
  test) exec java -cp "$OUT" dev.infrai.snapshot.NightlySnapshotServiceTest ;;
  setup) exec java -cp "$OUT" dev.infrai.snapshot.NightlySnapshotApplication setup ;;
  run)
    if [ "$#" -ge 2 ]; then
      exec java -cp "$OUT" dev.infrai.snapshot.NightlySnapshotApplication "$2"
    fi
    exec java -cp "$OUT" dev.infrai.snapshot.NightlySnapshotApplication
    ;;
  *) echo "usage: ./run.sh [test|setup|run [YYYY-MM-DD]]" >&2; exit 2 ;;
esac
