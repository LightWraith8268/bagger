"""Latitude 64 flight-number scraper.

Stub implementation: parser handles the fixture markup. Live scraping
against latitude64.se requires updating the parser to match the real
site's actual HTML structure (selectors below are placeholders).
"""

from __future__ import annotations
from typing import Iterable

from bs4 import BeautifulSoup

from scrape_mfr.base import MfrScraper, MfrDisc


def parse_latitude64_disc_page(html: str) -> MfrDisc:
    soup = BeautifulSoup(html, "lxml")
    title = soup.select_one("h1.product-name")
    mold = title.get_text(strip=True) if title else ""
    stats: dict[str, float] = {"speed": 0.0, "glide": 0.0, "turn": 0.0, "fade": 0.0}
    for li in soup.select("ul.flight-numbers li"):
        text = li.get_text(strip=True)
        if ":" in text:
            label, value = text.split(":", 1)
            key = label.strip().lower()
            if key in stats:
                try:
                    stats[key] = float(value.strip())
                except ValueError:
                    pass
    type_el = soup.select_one(".disc-type")
    disc_type = type_el.get_text(strip=True) if type_el else None
    return MfrDisc(
        brand="Latitude 64",
        mold=mold,
        speed=stats["speed"],
        glide=stats["glide"],
        turn=stats["turn"],
        fade=stats["fade"],
        disc_type=disc_type,
    )


class Latitude64Scraper(MfrScraper):
    brand_slug = "latitude64"
    brand_display = "Latitude 64"
    INDEX_URL = "https://latitude64.se/disc-golf/discs/"

    def scrape(self) -> Iterable[MfrDisc]:
        # TODO(scraper): root domain redirects from latitude64.se to latitude64.com (Shopify). /collections/discs returns 200 with 1.5MB of product links; /products.json gives 30 products with handles but flight numbers are not in body_html. Real impl needs per-product page scraping with selectors yet to be identified.
        return []


if __name__ == "__main__":
    Latitude64Scraper().run()
