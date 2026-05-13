"""Pick a representative ~30-disc subset of data/discs.json for APK bundling."""

from __future__ import annotations
import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

POPULAR_IDS = [
    "innova-aviar",
    "innova-destroyer",
    "innova-leopard",
    "innova-roc",
    "innova-teebird",
    "innova-wraith",
    "innova-firebird",
    "discraft-buzzz",
    "discraft-zone",
    "discraft-luna",
    "discraft-undertaker",
    "discraft-thrasher",
    "mvp-tesla",
    "mvp-volt",
    "mvp-photon",
    "mvp-wave",
    "dynamic-discs-judge",
    "dynamic-discs-escape",
    "dynamic-discs-trespass",
    "latitude-64-pure",
    "latitude-64-river",
    "latitude-64-saint",
    "discmania-fd",
    "discmania-md3",
    "discmania-p2",
    "prodigy-h3-v2",
    "prodigy-d2",
    "prodigy-pa-3",
    "westside-harp",
    "westside-king",
]


def build_baseline(source: Path, output: Path) -> int:
    full = json.loads(source.read_text(encoding="utf-8"))
    by_id = {d["id"]: d for d in full}

    picked = [by_id[i] for i in POPULAR_IDS if i in by_id]

    if len(picked) < 10:
        picked = full[:30]

    output.write_text(json.dumps(picked, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    return len(picked)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, default=ROOT / "data" / "discs.json")
    parser.add_argument("--output", type=Path, default=ROOT / "app" / "src" / "main" / "assets" / "discs-baseline.json")
    args = parser.parse_args()

    n = build_baseline(args.source, args.output)
    print(f"Wrote {n} baseline discs to {args.output}")


if __name__ == "__main__":
    main()
