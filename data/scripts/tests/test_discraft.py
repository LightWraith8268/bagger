import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from scrape_mfr.discraft import parse_discraft_disc_page

FIXTURE = Path(__file__).parent / "fixtures" / "discraft_sample.html"


def test_parses_discraft_buzzz():
    html = FIXTURE.read_text()
    disc = parse_discraft_disc_page(html)
    assert disc.brand == "Discraft"
    assert disc.mold == "Buzzz"
    assert disc.speed == 5
    assert disc.glide == 4
    assert disc.turn == -1
    assert disc.fade == 1
