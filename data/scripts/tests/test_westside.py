import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from scrape_mfr.westside import parse_westside_disc_page

FIXTURE = Path(__file__).parent / "fixtures" / "westside_sample.html"


def test_parses_westside_harp():
    html = FIXTURE.read_text()
    disc = parse_westside_disc_page(html)
    assert disc.brand == "Westside"
    assert disc.mold == "Harp"
    assert disc.speed == 4
    assert disc.glide == 3
    assert disc.turn == 0
    assert disc.fade == 3
