"""Authentication, token rotation and bounded per-client rate limiting."""

from __future__ import annotations

import hashlib
import secrets
import time
from collections import defaultdict, deque


class TokenVerifier:
    """Accept comma-separated rotating tokens or ``sha256:<hex>`` hashes."""

    def __init__(self, configured_tokens: str):
        self.tokens = [token.strip() for token in configured_tokens.split(",") if token.strip()]

    def verify(self, authorization: str) -> bool:
        if not authorization.startswith("Bearer "):
            return False
        presented = authorization.removeprefix("Bearer ")
        digest = hashlib.sha256(presented.encode()).hexdigest()
        for token in self.tokens:
            expected = token.removeprefix("sha256:")
            candidate = digest if token.startswith("sha256:") else presented
            if secrets.compare_digest(candidate, expected):
                return True
        return False


class SlidingWindowRateLimiter:
    def __init__(self, requests: int, window_seconds: int = 60):
        self.requests = requests
        self.window_seconds = window_seconds
        self._hits: dict[str, deque[float]] = defaultdict(deque)

    def allow(self, identity: str) -> bool:
        now = time.monotonic()
        hits = self._hits[identity]
        cutoff = now - self.window_seconds
        while hits and hits[0] <= cutoff:
            hits.popleft()
        if len(hits) >= self.requests:
            return False
        hits.append(now)
        return True
