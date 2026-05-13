"""Latitude 64 — Shopify storefront at latitude64.com.

Fixture-driven `parse_latitude64_disc_page` for tests. Live scraping uses
ShopifyDiscScraper which fetches /products.json then each product detail
page and regex-extracts flight numbers.
"""

from __future__ import annotations
import re

from bs4 import BeautifulSoup

from scrape_mfr.base import MfrDisc
from scrape_mfr.shopify_base import ShopifyDiscScraper


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


class Latitude64Scraper(ShopifyDiscScraper):
    brand_slug = "latitude64"
    brand_display = "Latitude 64"
    shopify_origin = "https://latitude64.com"


if __name__ == "__main__":
    Latitude64Scraper().run()
