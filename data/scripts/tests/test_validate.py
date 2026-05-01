import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from validate import validate

VALID_DISC = {
    "id": "innova-aviar",
    "brand": "Innova",
    "mold": "Aviar",
    "speed": 2, "glide": 3, "turn": 0, "fade": 1,
    "discType": "Putter",
    "stability": "stable",
    "pdgaApproved": True,
    "yearReleased": 1985,
    "primaryStampUrl": None,
    "aliases": [],
    "schemaVersion": 1
}


def _write_pair(tmp_path: Path, discs: list[dict], schema: dict) -> tuple[Path, Path]:
    discs_path = tmp_path / "discs.json"
    schema_path = tmp_path / "schema.json"
    discs_path.write_text(json.dumps(discs))
    schema_path.write_text(json.dumps(schema))
    return discs_path, schema_path


def test_validates_clean_input(tmp_path):
    schema = json.loads((Path(__file__).resolve().parents[2] / "schema.json").read_text())
    discs_path, schema_path = _write_pair(tmp_path, [VALID_DISC], schema)
    issues = validate(discs_path, schema_path, check_urls=False)
    assert issues == []


def test_catches_duplicate_ids(tmp_path):
    schema = json.loads((Path(__file__).resolve().parents[2] / "schema.json").read_text())
    discs_path, schema_path = _write_pair(tmp_path, [VALID_DISC, VALID_DISC], schema)
    issues = validate(discs_path, schema_path, check_urls=False)
    assert any("duplicate id" in i.lower() for i in issues)


def test_catches_schema_violation_speed_too_high(tmp_path):
    schema = json.loads((Path(__file__).resolve().parents[2] / "schema.json").read_text())
    bad = {**VALID_DISC, "speed": 99}  # exceeds maximum 15
    discs_path, schema_path = _write_pair(tmp_path, [bad], schema)
    issues = validate(discs_path, schema_path, check_urls=False)
    assert any("speed" in i.lower() for i in issues)


def test_catches_missing_required_field(tmp_path):
    schema = json.loads((Path(__file__).resolve().parents[2] / "schema.json").read_text())
    bad = {k: v for k, v in VALID_DISC.items() if k != "speed"}
    discs_path, schema_path = _write_pair(tmp_path, [bad], schema)
    issues = validate(discs_path, schema_path, check_urls=False)
    assert any("speed" in i.lower() or "required" in i.lower() for i in issues)


def test_catches_invalid_disc_type_enum(tmp_path):
    schema = json.loads((Path(__file__).resolve().parents[2] / "schema.json").read_text())
    bad = {**VALID_DISC, "discType": "Frisbee"}
    discs_path, schema_path = _write_pair(tmp_path, [bad], schema)
    issues = validate(discs_path, schema_path, check_urls=False)
    assert any("discType" in i or "frisbee" in i.lower() for i in issues)
