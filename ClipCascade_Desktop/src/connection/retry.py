"""Deterministic capped exponential retry policy."""

from dataclasses import dataclass


@dataclass(frozen=True)
class RetryPolicy:
    """Calculate a bounded retry delay with controlled jitter.

    ``attempt`` is one-based: attempt 1 is the first scheduled retry.
    ``random_value`` must be in the inclusive range [0.0, 1.0].
    """

    base_delay_seconds: float = 1.0
    cap_delay_seconds: float = 30.0
    minimum_jitter_ratio: float = 0.5
    stable_reset_seconds: float = 60.0

    def __post_init__(self) -> None:
        if self.base_delay_seconds <= 0:
            raise ValueError("base_delay_seconds must be positive")
        if self.cap_delay_seconds < self.base_delay_seconds:
            raise ValueError("cap_delay_seconds must be at least base_delay_seconds")
        if not 0.0 <= self.minimum_jitter_ratio <= 1.0:
            raise ValueError("minimum_jitter_ratio must be within [0, 1]")
        if self.stable_reset_seconds < 0:
            raise ValueError("stable_reset_seconds cannot be negative")

    def delay_for_attempt(self, attempt: int, random_value: float) -> float:
        if attempt < 1:
            raise ValueError("attempt must be one-based and positive")
        if not 0.0 <= random_value <= 1.0:
            raise ValueError("random_value must be within [0, 1]")

        raw_delay = min(
            self.cap_delay_seconds,
            self.base_delay_seconds * (2 ** (attempt - 1)),
        )
        jitter_ratio = self.minimum_jitter_ratio + (
            (1.0 - self.minimum_jitter_ratio) * random_value
        )
        return raw_delay * jitter_ratio
