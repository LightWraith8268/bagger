"""Join PDGA + manufacturer scraper outputs + manual overrides → data/discs.json."""

from __future__ import annotations
import argparse
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


# Map raw manufacturer disc_type free text → schema discType enum
DISC_TYPE_MAP_KEYWORDS: list[tuple[str, str]] = [
    # Order matters: more specific first
    ("putt & approach", "Putter"),
    ("putt and approach", "Putter"),
    ("approach", "Approach"),
    ("putter", "Putter"),
    ("mid-range", "Mid"),
    ("midrange", "Mid"),
    ("mid range", "Mid"),
    ("hybrid driver", "Driver"),
    ("distance driver", "Driver"),
    ("control driver", "Fairway"),
    ("fairway driver", "Fairway"),
    ("fairway", "Fairway"),
    ("driver", "Driver"),
    ("mid", "Mid"),
]


def slugify(brand: str, mold: str) -> str:
    text = f"{brand}-{mold}".lower()
    text = re.sub(r"[^a-z0-9]+", "-", text).strip("-")
    return text


def normalize_brand(raw: str) -> str:
    """Map PDGA's verbose names to short canonical brand."""
    mapping = {
        "Innova Champion Discs": "Innova",
        "Innova": "Innova",
        "Discraft, Inc.": "Discraft",
        "Discraft": "Discraft",
        "MVP Disc Sports": "MVP",
        "MVP": "MVP",
        "Dynamic Discs": "Dynamic Discs",
        "Latitude 64": "Latitude 64",
        "Latitude 64°": "Latitude 64",
        "Discmania": "Discmania",
        "Prodigy Disc": "Prodigy",
        "Prodigy": "Prodigy",
        "Westside Discs": "Westside",
        "Westside": "Westside",
    }
    return mapping.get(raw.strip(), raw.strip())


def infer_disc_type(raw: str | None) -> str:
    """Map manufacturer free-text disc_type label → schema enum."""
    if not raw:
        return "Driver"  # safe default
    needle = raw.lower().strip()
    for keyword, disc_type in DISC_TYPE_MAP_KEYWORDS:
        if keyword in needle:
            return disc_type
    return "Driver"


def stability_from_turn_fade(turn: float, fade: float) -> str:
    """Heuristic: total = turn + fade. <=0.5 understable, >=3 overstable, else stable."""
    total = turn + fade
    if total <= 0.5:
        return "understable"
    if total >= 2.0:
        return "overstable"
    return "stable"


def _parse_year(raw: str | None) -> int | None:
    if not raw:
        return None
    try:
        return int(raw[:4])
    except (ValueError, TypeError):
        return None


def enrich(root: Path) -> list[dict]:
    pdga_path = root / "intermediate" / "pdga_raw.json"
    mfr_dir = root / "intermediate" / "mfr"
    overrides_path = root / "scripts" / "manual_overrides.json"

    pdga: list[dict] = json.loads(pdga_path.read_text()) if pdga_path.exists() else []
    overrides: list[dict] = json.loads(overrides_path.read_text()) if overrides_path.exists() else []

    # Index manufacturer rows by (brand, mold) lower-cased
    mfr_index: dict[tuple[str, str], dict] = {}
    if mfr_dir.exists():
        for f in mfr_dir.glob("*.json"):
            try:
                rows = json.loads(f.read_text())
            except json.JSONDecodeError:
                continue
            if not isinstance(rows, list):
                continue
            for row in rows:
                key = (row["brand"].strip().lower(), row["mold"].strip().lower())
                mfr_index[key] = row

    out: list[dict] = []
    seen_ids: set[str] = set()

    # Pass 1: PDGA × manufacturer join
    for p in pdga:
        brand = normalize_brand(p.get("manufacturer", ""))
        mold = p.get("mold", "").strip()
        if not brand or not mold:
            continue
        disc_id = slugify(brand, mold)
        if disc_id in seen_ids:
            continue
        key = (brand.lower(), mold.lower())
        m = mfr_index.get(key)
        if m is None:
            # PDGA has identity but no flight numbers from any tracked brand scraper.
            # Skip — manual_overrides.json can fill this in if needed.
            continue
        speed = float(m.get("speed", 0.0))
        glide = float(m.get("glide", 0.0))
        turn = float(m.get("turn", 0.0))
        fade = float(m.get("fade", 0.0))
        out.append({
            "id": disc_id,
            "brand": brand,
            "mold": mold,
            "speed": speed,
            "glide": glide,
            "turn": turn,
            "fade": fade,
            "discType": infer_disc_type(m.get("disc_type")),
            "stability": stability_from_turn_fade(turn, fade),
            "pdgaApproved": True,
            "yearReleased": _parse_year(p.get("approved_date")),
            "primaryStampUrl": m.get("stamp_url"),
            "aliases": m.get("aliases", []),
            "schemaVersion": 1,
        })
        seen_ids.add(disc_id)

    # Pass 2: apply manual overrides
    out_by_id = {d["id"]: d for d in out}
    for ov in overrides:
        oid = ov.get("id")
        if not oid:
            continue
        if oid in out_by_id:
            # patch existing record — only update keys that are present
            existing = out_by_id[oid]
            for k, v in ov.items():
                if k != "id":
                    existing[k] = v
        else:
            # Full disc from override — must include required fields
            required = ("brand", "mold", "speed", "glide", "turn", "fade")
            if not all(k in ov for k in required):
                continue  # silently skip incomplete records
            disc_type = ov.get("discType") or infer_disc_type(ov.get("disc_type"))
            stability = ov.get("stability") or stability_from_turn_fade(ov["turn"], ov["fade"])
            record = {
                "id": oid,
                "brand": ov["brand"],
                "mold": ov["mold"],
                "speed": float(ov["speed"]),
                "glide": float(ov["glide"]),
                "turn": float(ov["turn"]),
                "fade": float(ov["fade"]),
                "discType": disc_type,
                "stability": stability,
                "pdgaApproved": ov.get("pdgaApproved", False),
                "yearReleased": ov.get("yearReleased"),
                "primaryStampUrl": ov.get("primaryStampUrl"),
                "aliases": ov.get("aliases", []),
                "schemaVersion": 1,
            }
            out_by_id[oid] = record

    final = sorted(out_by_id.values(), key=lambda d: (d["brand"].lower(), d["mold"].lower()))
    return final


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=ROOT / "data")
    parser.add_argument("--output", type=Path, default=ROOT / "data" / "discs.json")
    args = parser.parse_args()

    discs = enrich(args.root)
    args.output.write_text(json.dumps(discs, indent=2, ensure_ascii=False) + "\n")
    print(f"Wrote {len(discs)} discs to {args.output}")


if __name__ == "__main__":
    main()
