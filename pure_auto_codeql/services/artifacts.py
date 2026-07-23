"""Run artifact storage and registration.

The analysis pipeline only depends on this interface.  Local development stores
files below the run directory; production can replace the store with an
S3-compatible implementation without changing pipeline code.
"""

from __future__ import annotations

import hashlib
import mimetypes
import shutil
from dataclasses import dataclass, field
from pathlib import Path
from typing import BinaryIO, Protocol

from pure_auto_codeql.analysis_models import Artifact


class ArtifactStore(Protocol):
    def put_file(self, run_id: str, relative_path: str, source: Path) -> Artifact:
        """Persist a file and return immutable metadata."""
        ...

    def open(self, artifact: Artifact) -> BinaryIO:
        """Open an artifact for reading."""
        ...


class S3Client(Protocol):
    def upload_file(self, filename: str, bucket: str, key: str) -> None: ...


def artifact_from_file(path: Path, *, root: Path, name: str | None = None) -> Artifact:
    payload = path.read_bytes()
    media_type = (
        {
            ".sarif": "application/sarif+json",
            ".ql": "text/plain",
            ".md": "text/markdown",
        }.get(path.suffix.lower())
        or mimetypes.guess_type(path.name)[0]
        or "application/octet-stream"
    )
    return Artifact(
        name=name or path.name,
        path=path.relative_to(root).as_posix(),
        media_type=media_type,
        sha256=hashlib.sha256(payload).hexdigest(),
        size=len(payload),
    )


class LocalArtifactStore:
    """Filesystem implementation with path-containment enforcement."""

    def __init__(self, root: Path | str):
        self.root = Path(root).resolve()
        self.root.mkdir(parents=True, exist_ok=True)

    def _target(self, run_id: str, relative_path: str) -> Path:
        target = (self.root / run_id / relative_path).resolve()
        run_root = (self.root / run_id).resolve()
        if target != run_root and run_root not in target.parents:
            raise ValueError("artifact path escapes the run directory")
        return target

    def put_file(self, run_id: str, relative_path: str, source: Path) -> Artifact:
        target = self._target(run_id, relative_path)
        source = source.resolve()
        target.parent.mkdir(parents=True, exist_ok=True)
        if source != target:
            shutil.copy2(source, target)
        return artifact_from_file(target, root=(self.root / run_id).resolve())

    def open(self, artifact: Artifact) -> BinaryIO:
        target = (self.root / artifact.path).resolve()
        if target != self.root and self.root not in target.parents:
            raise ValueError("artifact path escapes the store")
        return target.open("rb")


class S3ArtifactStore:
    """S3-compatible implementation; the client is injected for easy testing."""

    def __init__(self, client: S3Client, bucket: str, prefix: str = ""):
        self.client = client
        self.bucket = bucket
        self.prefix = prefix.strip("/")

    def _key(self, run_id: str, relative_path: str) -> str:
        clean = Path(relative_path)
        if clean.is_absolute() or ".." in clean.parts:
            raise ValueError("invalid artifact key")
        parts = [part for part in (self.prefix, run_id, clean.as_posix()) if part]
        return "/".join(parts)

    def put_file(self, run_id: str, relative_path: str, source: Path) -> Artifact:
        key = self._key(run_id, relative_path)
        metadata = artifact_from_file(source, root=source.parent)
        self.client.upload_file(str(source), self.bucket, key)
        return Artifact(
            name=metadata.name,
            path=f"s3://{self.bucket}/{key}",
            media_type=metadata.media_type,
            sha256=metadata.sha256,
            size=metadata.size,
        )

    def open(self, artifact: Artifact) -> BinaryIO:
        raise NotImplementedError("S3 artifacts are streamed by the API storage adapter")


@dataclass
class ArtifactRegistry:
    """Deduplicated metadata registry for one analysis run."""

    root: Path
    artifacts: dict[str, Artifact] = field(default_factory=dict)

    def register(self, path: Path, *, name: str | None = None) -> Artifact:
        resolved_root = self.root.resolve()
        resolved_path = path.resolve()
        if resolved_path != resolved_root and resolved_root not in resolved_path.parents:
            raise ValueError("artifact is outside the run directory")
        artifact = artifact_from_file(resolved_path, root=resolved_root, name=name)
        self.artifacts[artifact.path] = artifact
        return artifact

    def scan(self, *, exclude: set[str] | None = None) -> list[Artifact]:
        excluded = exclude or set()
        for path in sorted(self.root.rglob("*")):
            if path.is_file() and path.name not in excluded:
                self.register(path)
        return self.list()

    def list(self) -> list[Artifact]:
        return [self.artifacts[key] for key in sorted(self.artifacts)]


__all__ = [
    "ArtifactRegistry",
    "ArtifactStore",
    "LocalArtifactStore",
    "S3ArtifactStore",
    "artifact_from_file",
]
