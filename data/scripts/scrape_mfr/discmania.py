"""Discmania flight-number scraper.

Stub implementation: parser handles the fixture markup. Live scraping
against discmania.net requires updating the parser to match the real
site's actual HTML structure (selectors below are placeholders).
"""

from __future__ import annotations

from bs4 import BeautifulSoup

from scrape_mfr.base import MfrDisc
from scrape_mfr.shopify_base import ShopifyDiscScraper


def parse_discmania_disc_page(html: str) -> MfrDisc:
    soup = BeautifulSoup(html, "lxml")
    title = soup.select_one("h1.product-title")
    mold = title.get_text(strip=True) if title else ""
    scope = soup.select_one(".flight-data")
    speed = _stat(scope, "speed")
    glide = _stat(scope, "glide")
    turn = _stat(scope, "turn")
    fade = _stat(scope, "fade")
    type_el = soup.select_one(".disc-class")
    disc_type = type_el.get_text(strip=True) if type_el else None
    return MfrDisc(
        brand="Discmania",
        mold=mold,
        speed=speed,
        glide=glide,
        turn=turn,
        fade=fade,
        disc_type=disc_type,
    )


def _stat(scope, name: str) -> float:
    if scope is None:
        return 0.0
    el = scope.select_one(f'[data-stat="{name}"]')
    if el is None:
        return 0.0
    try:
        return float(el.get_text(strip=True))
    except ValueError:
        return 0.0


class DiscmaniaScraper(ShopifyDiscScraper):
    brand_slug = "discmania"
    brand_display = "Discmania"
    shopify_origin = "https://discmaniagolf.com"


if __name__ == "__main__":
    DiscmaniaScraper().run()
