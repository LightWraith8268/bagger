import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from scrape_mfr.prodigy import parse_prodigy_disc_page

FIXTURE = Path(__file__).parent / "fixtures" / "prodigy_sample.html"


def test_parses_prodigy_h3v2():
    html = FIXTURE.read_text()
    disc = parse_prodigy_disc_page(html)
    assert disc.brand == "Prodigy"
    assert disc.mold == "H3 V2"
    assert disc.speed == 7
    assert disc.glide == 5
    assert disc.turn == 0
    assert disc.fade == 2
