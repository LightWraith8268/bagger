"""Dynamic Discs flight-number scraper.

Stub implementation: parser handles the fixture markup. Live scraping
against dynamicdiscs.com requires updating the parser to match the real
site's actual HTML structure (selectors below are placeholders).
"""

from __future__ import annotations
from typing import Iterable

from bs4 import BeautifulSoup

from scrape_mfr.base import MfrScraper, MfrDisc


def parse_dynamic_discs_disc_page(html: str) -> MfrDisc:
    soup = BeautifulSoup(html, "lxml")
    title = soup.select_one("h1.disc-name")
    mold = title.get_text(strip=True) if title else ""
    row = soup.select_one(".flight-row")
    speed = _num(row, ".stat-speed")
    glide = _num(row, ".stat-glide")
    turn = _num(row, ".stat-turn")
    fade = _num(row, ".stat-fade")
    cat_el = soup.select_one(".category-tag")
    disc_type = cat_el.get_text(strip=True) if cat_el else None
    return MfrDisc(
        brand="Dynamic Discs",
        mold=mold,
        speed=speed,
        glide=glide,
        turn=turn,
        fade=fade,
        disc_type=disc_type,
    )


def _num(scope, selector: str) -> float:
    if scope is None:
        return 0.0
    el = scope.select_one(selector)
    if el is None:
        return 0.0
    try:
        return float(el.get_text(strip=True))
    except ValueError:
        return 0.0


class DynamicDiscsScraper(MfrScraper):
    brand_slug = "dynamic_discs"
    brand_display = "Dynamic Discs"
    INDEX_URL = "https://www.dynamicdiscs.com/discs/"

    def scrape(self) -> Iterable[MfrDisc]:
        # Stub: live HTML structure unknown at implementation time.
        return []


if __name__ == "__main__":
    DynamicDiscsScraper().run()
