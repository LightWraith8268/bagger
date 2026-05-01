"""Innova Champion Discs flight-number scraper.

Stub implementation: parser handles the fixture markup. Live scraping
against innovadiscs.com requires updating the parser to match the real
site's actual HTML structure (selectors below are placeholders).
"""

from __future__ import annotations
from typing import Iterable

from bs4 import BeautifulSoup

from scrape_mfr.base import MfrScraper, MfrDisc


def parse_innova_disc_page(html: str) -> MfrDisc:
    soup = BeautifulSoup(html, "lxml")
    h1 = soup.find("h1")
    mold = h1.get_text(strip=True) if h1 else ""
    fn = soup.select_one(".flight-numbers")
    speed = _num(fn, ".speed")
    glide = _num(fn, ".glide")
    turn = _num(fn, ".turn")
    fade = _num(fn, ".fade")
    disc_type_el = soup.select_one(".disc-type")
    disc_type = disc_type_el.get_text(strip=True) if disc_type_el else None
    stamp_img = soup.select_one("img.primary-stamp")
    stamp_url = stamp_img["src"] if stamp_img and stamp_img.has_attr("src") else None
    return MfrDisc(
        brand="Innova",
        mold=mold,
        speed=speed,
        glide=glide,
        turn=turn,
        fade=fade,
        disc_type=disc_type,
        stamp_url=stamp_url,
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


class InnovaScraper(MfrScraper):
    brand_slug = "innova"
    brand_display = "Innova"
    INDEX_URL = "https://www.innovadiscs.com/disc-golf-discs/all-discs"

    def scrape(self) -> Iterable[MfrDisc]:
        # Stub: live HTML structure unknown at implementation time.
        # When iterating against the real site, fetch INDEX_URL, find product links,
        # fetch each, and yield parse_innova_disc_page(html). Until then, yield empty
        # so enrich.py + manual_overrides.json provide initial coverage.
        return []


if __name__ == "__main__":
    InnovaScraper().run()
