"""Discraft flight-number scraper.

Stub implementation: parser handles the fixture markup. Live scraping
against discraft.com requires updating the parser to match the real
site's actual HTML structure (selectors below are placeholders).
"""

from __future__ import annotations
from typing import Iterable

from bs4 import BeautifulSoup

from scrape_mfr.base import MfrScraper, MfrDisc


def parse_discraft_disc_page(html: str) -> MfrDisc:
    soup = BeautifulSoup(html, "lxml")
    title = soup.select_one("h1.product-title")
    mold = title.get_text(strip=True) if title else ""
    speed = glide = turn = fade = 0.0
    rows = soup.select("table.flight-numbers tr")
    if len(rows) >= 2:
        cells = rows[1].select("td")
        if len(cells) >= 4:
            speed = _to_float(cells[0].get_text(strip=True))
            glide = _to_float(cells[1].get_text(strip=True))
            turn = _to_float(cells[2].get_text(strip=True))
            fade = _to_float(cells[3].get_text(strip=True))
    cat_el = soup.select_one(".product-category")
    disc_type = cat_el.get_text(strip=True) if cat_el else None
    stamp_img = soup.select_one("img.product-stamp")
    stamp_url = stamp_img["src"] if stamp_img and stamp_img.has_attr("src") else None
    return MfrDisc(
        brand="Discraft",
        mold=mold,
        speed=speed,
        glide=glide,
        turn=turn,
        fade=fade,
        disc_type=disc_type,
        stamp_url=stamp_url,
    )


def _to_float(text: str) -> float:
    try:
        return float(text)
    except ValueError:
        return 0.0


class DiscraftScraper(MfrScraper):
    brand_slug = "discraft"
    brand_display = "Discraft"
    INDEX_URL = "https://www.discraft.com/collections/all-discs"

    def scrape(self) -> Iterable[MfrDisc]:
        # Stub: live HTML structure unknown at implementation time.
        return []


if __name__ == "__main__":
    DiscraftScraper().run()
