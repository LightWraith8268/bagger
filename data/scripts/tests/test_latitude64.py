import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from scrape_mfr.latitude64 import parse_latitude64_disc_page

FIXTURE = Path(__file__).parent / "fixtures" / "latitude64_sample.html"


def test_parses_latitude64_pure():
    html = FIXTURE.read_text()
    disc = parse_latitude64_disc_page(html)
    assert disc.brand == "Latitude 64"
    assert disc.mold == "Pure"
    assert disc.speed == 3
    assert disc.glide == 3
    assert disc.turn == -1
    assert disc.fade == 1
