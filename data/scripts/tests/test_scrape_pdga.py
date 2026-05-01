import sys
from pathlib import Path

# Make scripts dir importable when running pytest from data/scripts/
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from scrape_pdga import parse_pdga_table

FIXTURE = Path(__file__).parent / "fixtures" / "pdga_sample.html"


def test_parses_disc_rows_from_pdga_table():
    html = FIXTURE.read_text()
    rows = parse_pdga_table(html)
    assert len(rows) >= 1
    sample = rows[0]
    assert "manufacturer" in sample
    assert "mold" in sample
    assert "approved_date" in sample


def test_extracts_5_rows_from_full_fixture():
    html = FIXTURE.read_text()
    rows = parse_pdga_table(html)
    assert len(rows) == 5


def test_extracts_innova_destroyer_correctly():
    html = FIXTURE.read_text()
    rows = parse_pdga_table(html)
    destroyer = next(r for r in rows if r["mold"] == "Destroyer")
    assert "Innova" in destroyer["manufacturer"]
    assert destroyer["approved_date"] == "2008-04-21"
    # Listing page does not expose flight specs — they're fetched from the
    # per-disc detail page in a follow-up task.
    assert destroyer["disc_class"] is None
    assert destroyer["max_weight_g"] is None
    assert destroyer["detail_url"].endswith("/discs/destroyer")
