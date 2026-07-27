from urllib.parse import urlsplit
from urllib.robotparser import RobotFileParser

import httpx


class RobotsPolicy:
    def __init__(self, client: httpx.Client, user_agent: str) -> None:
        self._client = client
        self._user_agent = user_agent
        self._cache: dict[str, RobotFileParser | None] = {}

    def can_fetch(self, url: str) -> bool:
        parsed = urlsplit(url)
        origin = f"{parsed.scheme}://{parsed.netloc}"
        if origin not in self._cache:
            self._cache[origin] = self._load(origin)
        parser = self._cache[origin]
        return True if parser is None else parser.can_fetch(self._user_agent, url)

    def _load(self, origin: str) -> RobotFileParser | None:
        robots_url = f"{origin}/robots.txt"
        try:
            response = self._client.get(
                robots_url,
                headers={"User-Agent": self._user_agent},
            )
        except httpx.HTTPError:
            # Network failure is conservative: deny this origin for the run.
            parser = RobotFileParser()
            parser.set_url(robots_url)
            parser.parse(["User-agent: *", "Disallow: /"])
            return parser
        if response.status_code in {401, 403}:
            parser = RobotFileParser()
            parser.set_url(robots_url)
            parser.parse(["User-agent: *", "Disallow: /"])
            return parser
        if response.status_code >= 400:
            # RFC behavior for missing robots.txt: no explicit restrictions.
            return None
        parser = RobotFileParser()
        parser.set_url(robots_url)
        parser.parse(response.text.splitlines())
        return parser
