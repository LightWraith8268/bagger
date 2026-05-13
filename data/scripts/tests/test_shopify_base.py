import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from scrape_mfr.shopify_base import ShopifyDiscScraper, extract_flight_from_html


class _FakeScraper(ShopifyDiscScraper):
    brand_slug = "fake"
    brand_display = "Fake"
    shopify_origin = "https://example.invalid"


def test_extract_mold_strips_plastic_words():
    s = _FakeScraper()
    assert s.extract_mold("Royal Grand Fire") == "Fire"
    assert s.extract_mold("Opto Pure") == "Pure"
    assert s.extract_mold("Lucid Burst Moonshine Treason") == "Treason"
    assert s.extract_mold("BioGold Fuse") == "Fuse"


def test_extract_mold_handles_collab_dash():
    s = _FakeScraper()
    # right half preferred
    assert s.extract_mold("Disc Golf Valley - Deadly Fuse") == "Deadly Fuse"


def test_extract_mold_drops_brand_word():
    s = _FakeScraper()
    assert s.extract_mold("Fake Aviar") == "Aviar"


def test_is_disc_product_filters_non_discs():
    s = _FakeScraper()
    assert s.is_disc_product("Royal Grand Fire") is True
    assert s.is_disc_product("Trilogy Challenge PVC Patch") is False
    assert s.is_disc_product("Disc Golf Backpack") is False
    assert s.is_disc_product("Gift Card") is False
    assert s.is_disc_product("NERF Soft Flight Ultimate Disc") is True   # is a disc


def test_extract_flight_from_html_parses_speed_glide_turn_fade():
    html = """
    <p>The Grand Fire delivers reliable flights.</p>
    <p>Speed: 5 Glide: 3 Turn: 0 Fade: 3</p>
    """
    out = extract_flight_from_html(html)
    assert out == {"speed": 5.0, "glide": 3.0, "turn": 0.0, "fade": 3.0}


def test_extract_flight_from_html_rejects_out_of_range():
    html = "Speed: 99 Glide: 5 Turn: 0 Fade: 3"
    assert extract_flight_from_html(html) is None
