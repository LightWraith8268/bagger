import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from scrape_mfr.innova import parse_innova_disc_page

FIXTURE = Path(__file__).parent / "fixtures" / "innova_sample.html"


def test_parses_innova_destroyer():
    html = FIXTURE.read_text()
    disc = parse_innova_disc_page(html)
    assert disc.brand == "Innova"
    assert disc.mold == "Destroyer"
    assert disc.speed == 12
    assert disc.glide == 5
    assert disc.turn == -1
    assert disc.fade == 3
