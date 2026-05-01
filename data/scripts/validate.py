"""Validate data/discs.json against schema + integrity rules."""

from __future__ import annotations
import argparse
import json
import sys
from pathlib import Path

import jsonschema
import requests

ROOT = Path(__file__).resolve().parents[2]


def validate(discs_path: Path, schema_path: Path, check_urls: bool = True) -> list[str]:
    issues: list[str] = []

    try:
        discs = json.loads(discs_path.read_text())
    except json.JSONDecodeError as e:
        return [f"discs.json parse error: {e}"]
    try:
        schema = json.loads(schema_path.read_text())
    except json.JSONDecodeError as e:
        return [f"schema.json parse error: {e}"]

    # Schema validation
    validator = jsonschema.Draft202012Validator(schema)
    for err in validator.iter_errors(discs):
        path = "/".join(str(p) for p in err.absolute_path)
        issues.append(f"schema: {path}: {err.message}")

    # Duplicate id check
    seen: dict[str, int] = {}
    for i, d in enumerate(discs):
        if not isinstance(d, dict):
            continue
        oid = d.get("id")
        if not oid:
            continue
        if oid in seen:
            issues.append(f"duplicate id '{oid}' at indices {seen[oid]} and {i}")
        else:
            seen[oid] = i

    # URL reachability check (HEAD)
    if check_urls:
        urls = {d.get("primaryStampUrl") for d in discs if isinstance(d, dict) and d.get("primaryStampUrl")}
        for url in urls:
            try:
                resp = requests.head(url, timeout=10, allow_redirects=True)
                if resp.status_code >= 400:
                    issues.append(f"stamp URL {url} returned {resp.status_code}")
            except requests.RequestException as e:
                issues.append(f"stamp URL {url} unreachable: {e}")

    return issues


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--discs", type=Path, default=ROOT / "data" / "discs.json")
    parser.add_argument("--schema", type=Path, default=ROOT / "data" / "schema.json")
    parser.add_argument("--no-net", action="store_true", help="skip URL HEAD checks")
    args = parser.parse_args()

    issues = validate(args.discs, args.schema, check_urls=not args.no_net)
    if not issues:
        print(f"OK {args.discs.name} valid")
        return 0
    for i in issues:
        print(f"FAIL {i}", file=sys.stderr)
    return 1


if __name__ == "__main__":
    sys.exit(main())
