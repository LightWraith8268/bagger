import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from scrape_mfr.mvp import parse_mvp_disc_page

FIXTURE = Path(__file__).parent / "fixtures" / "mvp_sample.html"


def test_parses_mvp_tesla():
    html = FIXTURE.read_text()
    disc = parse_mvp_disc_page(html)
    assert disc.brand == "MVP"
    assert disc.mold == "Tesla"
    assert disc.speed == 9
    assert disc.glide == 5
    assert disc.turn == -1
    assert disc.fade == 2
