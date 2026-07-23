"""At-least-once run distribution and resumable event streams."""

from __future__ import annotations

import asyncio
import json
from collections import defaultdict
from dataclasses import dataclass
from typing import Any, AsyncIterator, Protocol, cast

from redis.asyncio import Redis
from redis.exceptions import ResponseError

from .events import RunEvent


@dataclass(frozen=True)
class StreamMessage:
    message_id: str
    run_id: str
    payload: dict[str, Any]


class StreamBroker(Protocol):
    async def initialize(self) -> None: ...
    async def enqueue(self, run_id: str, payload: dict[str, Any]) -> str: ...
    async def consume(self, consumer: str, *, block_ms: int = 5000) -> list[StreamMessage]: ...
    async def acknowledge(self, message_id: str) -> None: ...
    async def reclaim(self, consumer: str, *, min_idle_ms: int) -> list[StreamMessage]: ...
    async def publish_event(self, event: RunEvent) -> RunEvent: ...
    def events(self, run_id: str, after_id: str = "0-0") -> AsyncIterator[RunEvent]: ...
    async def heartbeat_worker(self, worker_id: str, ttl_seconds: int) -> None: ...
    async def has_live_workers(self) -> bool: ...


class RedisStreamBroker:
    def __init__(
        self,
        redis: Redis,
        *,
        jobs_stream: str = "pure-auto-codeql:jobs",
        consumer_group: str = "pure-auto-codeql:workers",
        event_prefix: str = "pure-auto-codeql:events:",
        max_events: int = 5000,
    ):
        self.redis = redis
        self.jobs_stream = jobs_stream
        self.consumer_group = consumer_group
        self.event_prefix = event_prefix
        self.max_events = max_events

    async def initialize(self) -> None:
        try:
            await self.redis.xgroup_create(
                name=self.jobs_stream,
                groupname=self.consumer_group,
                id="0-0",
                mkstream=True,
            )
        except ResponseError as exc:
            if "BUSYGROUP" not in str(exc):
                raise

    @staticmethod
    def _decode(value: bytes | str) -> str:
        return value.decode() if isinstance(value, bytes) else value

    def _messages(self, response: Any) -> list[StreamMessage]:
        messages: list[StreamMessage] = []
        streams = response.items() if isinstance(response, dict) else response or []
        for _, entries in streams:
            for message_id, fields in entries:
                decoded = {self._decode(k): self._decode(v) for k, v in fields.items()}
                messages.append(
                    StreamMessage(
                        message_id=self._decode(message_id),
                        run_id=decoded["run_id"],
                        payload=json.loads(decoded.get("payload", "{}")),
                    )
                )
        return messages

    async def enqueue(self, run_id: str, payload: dict[str, Any]) -> str:
        message_id = await self.redis.xadd(
            self.jobs_stream,
            {"run_id": run_id, "payload": json.dumps(payload, ensure_ascii=False)},
        )
        return self._decode(message_id)

    async def consume(self, consumer: str, *, block_ms: int = 5000) -> list[StreamMessage]:
        response = await self.redis.xreadgroup(
            groupname=self.consumer_group,
            consumername=consumer,
            streams={self.jobs_stream: ">"},
            count=1,
            block=block_ms,
        )
        return self._messages(response)

    async def acknowledge(self, message_id: str) -> None:
        await self.redis.xack(self.jobs_stream, self.consumer_group, message_id)

    async def reclaim(self, consumer: str, *, min_idle_ms: int) -> list[StreamMessage]:
        response: Any = await self.redis.xautoclaim(
            self.jobs_stream,
            self.consumer_group,
            consumer,
            min_idle_ms,
            start_id="0-0",
            count=10,
        )
        entries = response[1] if response and len(response) > 1 else []
        return self._messages([(self.jobs_stream, entries)])

    async def publish_event(self, event: RunEvent) -> RunEvent:
        key = f"{self.event_prefix}{event.run_id}"
        payload = event.model_dump(mode="json", exclude={"event_id"})
        event_id = await self.redis.xadd(
            key,
            {"event": json.dumps(payload, ensure_ascii=False)},
            maxlen=self.max_events,
            approximate=True,
        )
        return event.model_copy(update={"event_id": self._decode(event_id)})

    async def heartbeat_worker(self, worker_id: str, ttl_seconds: int) -> None:
        await self.redis.set(
            f"pure-auto-codeql:worker-heartbeat:{worker_id}",
            "1",
            ex=ttl_seconds,
        )

    async def has_live_workers(self) -> bool:
        async for _ in self.redis.scan_iter(
            match="pure-auto-codeql:worker-heartbeat:*",
            count=1,
        ):
            return True
        return False

    async def events(self, run_id: str, after_id: str = "0-0") -> AsyncIterator[RunEvent]:
        key = f"{self.event_prefix}{run_id}"
        cursor = after_id
        while True:
            response = await self.redis.xread({key: cursor}, count=100, block=15000)
            if not response:
                continue
            streams = response.items() if isinstance(response, dict) else response
            for _, entries in streams:
                for raw_entry in entries:
                    event_id, fields = cast(tuple[bytes | str, dict[bytes | str, bytes | str]], raw_entry)
                    raw = fields[b"event"] if b"event" in fields else fields["event"]
                    payload = json.loads(self._decode(raw))
                    cursor = self._decode(event_id)
                    yield RunEvent.model_validate({**payload, "event_id": cursor})


class MemoryStreamBroker:
    """In-memory replacement that preserves delivery and replay semantics."""

    def __init__(self):
        self.jobs: asyncio.Queue[StreamMessage] = asyncio.Queue()
        self.pending: dict[str, StreamMessage] = {}
        self.event_history: dict[str, list[RunEvent]] = defaultdict(list)
        self.event_conditions: dict[str, asyncio.Condition] = defaultdict(asyncio.Condition)
        self.live_workers: set[str] = set()
        self._sequence = 0

    async def initialize(self) -> None:
        return None

    def _next_id(self) -> str:
        self._sequence += 1
        return f"{self._sequence}-0"

    async def enqueue(self, run_id: str, payload: dict[str, Any]) -> str:
        message = StreamMessage(self._next_id(), run_id, payload)
        await self.jobs.put(message)
        return message.message_id

    async def consume(self, consumer: str, *, block_ms: int = 5000) -> list[StreamMessage]:
        del consumer
        try:
            message = await asyncio.wait_for(self.jobs.get(), timeout=block_ms / 1000)
        except asyncio.TimeoutError:
            return []
        self.pending[message.message_id] = message
        return [message]

    async def acknowledge(self, message_id: str) -> None:
        self.pending.pop(message_id, None)

    async def reclaim(self, consumer: str, *, min_idle_ms: int) -> list[StreamMessage]:
        del consumer, min_idle_ms
        return list(self.pending.values())

    async def publish_event(self, event: RunEvent) -> RunEvent:
        stored = event.model_copy(update={"event_id": self._next_id()})
        condition = self.event_conditions[event.run_id]
        async with condition:
            self.event_history[event.run_id].append(stored)
            condition.notify_all()
        return stored

    async def heartbeat_worker(self, worker_id: str, ttl_seconds: int) -> None:
        del ttl_seconds
        self.live_workers.add(worker_id)

    async def has_live_workers(self) -> bool:
        return bool(self.live_workers)

    async def events(self, run_id: str, after_id: str = "0-0") -> AsyncIterator[RunEvent]:
        position = int(after_id.split("-", 1)[0]) if after_id else 0
        condition = self.event_conditions[run_id]
        while True:
            available = [
                event
                for event in self.event_history[run_id]
                if event.event_id and int(event.event_id.split("-", 1)[0]) > position
            ]
            if not available:
                async with condition:
                    await condition.wait()
                continue
            for event in available:
                assert event.event_id is not None
                position = int(event.event_id.split("-", 1)[0])
                yield event
