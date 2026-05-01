import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from scrape_mfr.dynamic_discs import parse_dynamic_discs_disc_page

FIXTURE = Path(__file__).parent / "fixtures" / "dynamic_discs_sample.html"


def test_parses_dynamic_discs_judge():
    html = FIXTURE.read_text()
    disc = parse_dynamic_discs_disc_page(html)
    assert disc.brand == "Dynamic Discs"
    assert disc.mold == "Judge"
    assert disc.speed == 2
    assert disc.glide == 4
    assert disc.turn == 0
    assert disc.fade == 1
