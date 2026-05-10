import json
import sys
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from enrich import enrich, slugify, normalize_brand, infer_disc_type, stability_from_turn_fade


def _write(path: Path, data) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data))


def test_slugify_strips_special_chars():
    assert slugify("Innova", "H3 V2") == "innova-h3-v2"
    assert slugify("Latitude 64", "Pure") == "latitude-64-pure"


def test_normalize_brand_maps_pdga_long_names():
    assert normalize_brand("Innova Champion Discs") == "Innova"
    assert normalize_brand("Discraft, Inc.") == "Discraft"
    assert normalize_brand("MVP Disc Sports") == "MVP"
    assert normalize_brand("Westside Discs") == "Westside"


def test_infer_disc_type_from_mfr_label():
    assert infer_disc_type("Distance Driver") == "Driver"
    assert infer_disc_type("Fairway Driver") == "Fairway"
    assert infer_disc_type("Mid-Range") == "Mid"
    assert infer_disc_type("Mid-range") == "Mid"
    assert infer_disc_type("Putt & Approach") == "Putter"
    assert infer_disc_type("Putter") == "Putter"
    assert infer_disc_type("Approach") == "Approach"
    assert infer_disc_type("Hybrid Driver") == "Driver"
    assert infer_disc_type(None) == "Driver"  # default


def test_stability_heuristic():
    assert stability_from_turn_fade(-1, 3) == "overstable"  # total = 2, >= 2
    assert stability_from_turn_fade(0, 3) == "overstable"   # total = 3, >= 2
    assert stability_from_turn_fade(-2, 1) == "understable" # total = -1, <= 0.5
    assert stability_from_turn_fade(0, 1) == "stable"    # total = 1, mid range


def test_enrich_joins_pdga_with_mfr():
    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)
        pdga = [
            {"manufacturer": "Innova Champion Discs", "mold": "Destroyer", "approved_date": "2008-04-21", "detail_url": "https://www.pdga.com/x"},
        ]
        innova = [
            {"brand": "Innova", "mold": "Destroyer", "speed": 12, "glide": 5, "turn": -1, "fade": 3, "disc_type": "Distance Driver", "stamp_url": None, "year_released": None, "aliases": []},
        ]
        _write(root / "intermediate" / "pdga_raw.json", pdga)
        _write(root / "intermediate" / "mfr" / "innova.json", innova)
        _write(root / "scripts" / "manual_overrides.json", [])

        out = enrich(root)
        assert len(out) == 1
        d = out[0]
        assert d["id"] == "innova-destroyer"
        assert d["brand"] == "Innova"
        assert d["mold"] == "Destroyer"
        assert d["speed"] == 12
        assert d["fade"] == 3
        assert d["pdgaApproved"] is True
        assert d["discType"] == "Driver"
        assert d["stability"] == "overstable"
        assert d["yearReleased"] == 2008
        assert d["schemaVersion"] == 2


def test_enrich_uses_manual_override_for_full_disc():
    """Manual override can introduce a disc not in PDGA + mfr scrape."""
    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)
        _write(root / "intermediate" / "pdga_raw.json", [])
        _write(root / "intermediate" / "mfr" / ".keep", [])  # need dir to exist for glob
        overrides = [
            {
                "id": "innova-aviar",
                "brand": "Innova",
                "mold": "Aviar",
                "speed": 2, "glide": 3, "turn": 0, "fade": 1,
                "discType": "Putter",
                "yearReleased": 1985
            }
        ]
        _write(root / "scripts" / "manual_overrides.json", overrides)

        out = enrich(root)
        assert len(out) == 1
        assert out[0]["id"] == "innova-aviar"
        assert out[0]["discType"] == "Putter"
        assert out[0]["pdgaApproved"] is False  # default when not in PDGA
        assert out[0]["schemaVersion"] == 2


def test_manual_override_patches_existing_disc():
    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)
        pdga = [
            {"manufacturer": "Innova", "mold": "Aviar", "approved_date": "1985-01-01", "detail_url": "https://x"},
        ]
        innova = [
            {"brand": "Innova", "mold": "Aviar", "speed": 2, "glide": 3, "turn": 0, "fade": 1, "disc_type": "Putter", "stamp_url": None, "year_released": None, "aliases": []},
        ]
        overrides = [{"id": "innova-aviar", "yearReleased": 1985}]
        _write(root / "intermediate" / "pdga_raw.json", pdga)
        _write(root / "intermediate" / "mfr" / "innova.json", innova)
        _write(root / "scripts" / "manual_overrides.json", overrides)

        out = enrich(root)
        assert out[0]["yearReleased"] == 1985
