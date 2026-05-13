"""Prodigy Disc flight-number scraper.

Stub implementation: parser handles the fixture markup. Live scraping
against prodigydisc.com requires updating the parser to match the real
site's actual HTML structure (selectors below are placeholders).
"""

from __future__ import annotations

from bs4 import BeautifulSoup

from scrape_mfr.base import MfrDisc
from scrape_mfr.shopify_base import ShopifyDiscScraper


def parse_prodigy_disc_page(html: str) -> MfrDisc:
    soup = BeautifulSoup(html, "lxml")
    title = soup.select_one("h1.disc-title")
    mold = title.get_text(strip=True) if title else ""
    stats: dict[str, float] = {"speed": 0.0, "glide": 0.0, "turn": 0.0, "fade": 0.0}
    for block in soup.select(".flight-numbers .stat-block"):
        label_el = block.select_one(".label")
        value_el = block.select_one(".value")
        if label_el is None or value_el is None:
            continue
        key = label_el.get_text(strip=True).lower()
        if key in stats:
            try:
                stats[key] = float(value_el.get_text(strip=True))
            except ValueError:
                pass
    type_el = soup.select_one(".disc-category")
    disc_type = type_el.get_text(strip=True) if type_el else None
    return MfrDisc(
        brand="Prodigy",
        mold=mold,
        speed=stats["speed"],
        glide=stats["glide"],
        turn=stats["turn"],
        fade=stats["fade"],
        disc_type=disc_type,
    )


class ProdigyScraper(ShopifyDiscScraper):
    brand_slug = "prodigy"
    brand_display = "Prodigy"
    shopify_origin = "https://prodigydisc.com"


if __name__ == "__main__":
    ProdigyScraper().run()
