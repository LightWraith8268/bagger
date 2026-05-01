import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from scrape_mfr.discmania import parse_discmania_disc_page

FIXTURE = Path(__file__).parent / "fixtures" / "discmania_sample.html"


def test_parses_discmania_fd():
    html = FIXTURE.read_text()
    disc = parse_discmania_disc_page(html)
    assert disc.brand == "Discmania"
    assert disc.mold == "FD"
    assert disc.speed == 7
    assert disc.glide == 6
    assert disc.turn == -1
    assert disc.fade == 1
