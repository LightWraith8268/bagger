"""MVP Disc Sports flight-number scraper.

Stub implementation: parser handles the fixture markup. Live scraping
against mvpdiscsports.com requires updating the parser to match the real
site's actual HTML structure (selectors below are placeholders).
"""

from __future__ import annotations
from typing import Iterable

from bs4 import BeautifulSoup

from scrape_mfr.base import MfrScraper, MfrDisc


def parse_mvp_disc_page(html: str) -> MfrDisc:
    soup = BeautifulSoup(html, "lxml")
    title = soup.select_one("h1.disc-title")
    mold = title.get_text(strip=True) if title else ""
    stats: dict[str, float] = {"speed": 0.0, "glide": 0.0, "turn": 0.0, "fade": 0.0}
    dl = soup.select_one("dl.flight-stats")
    if dl is not None:
        dts = dl.find_all("dt")
        dds = dl.find_all("dd")
        for dt, dd in zip(dts, dds):
            label = dt.get_text(strip=True).lower()
            if label in stats:
                try:
                    stats[label] = float(dd.get_text(strip=True))
                except ValueError:
                    pass
    cat_el = soup.select_one(".disc-class")
    disc_type = cat_el.get_text(strip=True) if cat_el else None
    return MfrDisc(
        brand="MVP",
        mold=mold,
        speed=stats["speed"],
        glide=stats["glide"],
        turn=stats["turn"],
        fade=stats["fade"],
        disc_type=disc_type,
    )


class MvpScraper(MfrScraper):
    brand_slug = "mvp"
    brand_display = "MVP"
    INDEX_URL = "https://mvpdiscsports.com/disc-models/"

    def scrape(self) -> Iterable[MfrDisc]:
        # TODO(scraper): mvpdiscsports.com /collections/discs returns 404. /products.json also 404. Site may have moved or be JS-rendered.
        return []


if __name__ == "__main__":
    MvpScraper().run()
