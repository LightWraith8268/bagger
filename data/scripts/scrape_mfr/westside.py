"""Westside Discs flight-number scraper.

Stub implementation: parser handles the fixture markup. Live scraping
against westsidediscs.com requires updating the parser to match the real
site's actual HTML structure (selectors below are placeholders).
"""

from __future__ import annotations

from bs4 import BeautifulSoup

from scrape_mfr.base import MfrDisc
from scrape_mfr.shopify_base import ShopifyDiscScraper


def parse_westside_disc_page(html: str) -> MfrDisc:
    soup = BeautifulSoup(html, "lxml")
    title = soup.select_one("h1.product-name")
    mold = title.get_text(strip=True) if title else ""
    stats: dict[str, float] = {"speed": 0.0, "glide": 0.0, "turn": 0.0, "fade": 0.0}
    for tr in soup.select("table.flight-numbers tr"):
        label_el = tr.select_one(".label")
        value_el = tr.select_one(".val")
        if label_el is None or value_el is None:
            continue
        key = label_el.get_text(strip=True).lower()
        if key in stats:
            try:
                stats[key] = float(value_el.get_text(strip=True))
            except ValueError:
                pass
    type_el = soup.select_one(".disc-type")
    disc_type = type_el.get_text(strip=True) if type_el else None
    return MfrDisc(
        brand="Westside",
        mold=mold,
        speed=stats["speed"],
        glide=stats["glide"],
        turn=stats["turn"],
        fade=stats["fade"],
        disc_type=disc_type,
    )


class WestsideScraper(ShopifyDiscScraper):
    brand_slug = "westside"
    brand_display = "Westside"
    shopify_origin = "https://westsidediscs.com"


if __name__ == "__main__":
    WestsideScraper().run()
