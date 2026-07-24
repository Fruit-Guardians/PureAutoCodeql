import hashlib
import importlib.util
from pathlib import Path

import pytest


def _load_bootstrap_module():
    path = Path(__file__).resolve().parents[1] / "scripts" / "bootstrap.py"
    spec = importlib.util.spec_from_file_location("pure_auto_codeql_bootstrap", path)
    assert spec is not None
    assert spec.loader is not None
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def test_install_wrapper_rejects_recursive_target(tmp_path: Path, monkeypatch) -> None:
    bootstrap = _load_bootstrap_module()
    bin_dir = tmp_path / "bin"
    monkeypatch.setattr(bootstrap, "venv_bin_dir", lambda: bin_dir)
    wrapper = bin_dir / "mcp-language-server"
    wrapper.parent.mkdir(parents=True)
    wrapper.write_text("#!/usr/bin/env sh\n", encoding="utf-8")

    with pytest.raises(RuntimeError, match="recursive wrapper"):
        bootstrap.install_wrapper("mcp-language-server", wrapper)


def test_verify_sha256_accepts_release_checksum(tmp_path: Path) -> None:
    bootstrap = _load_bootstrap_module()
    archive = tmp_path / "bundle.tar.gz"
    archive.write_bytes(b"portable-codeql-bundle")
    checksum = tmp_path / "bundle.tar.gz.checksum.txt"
    checksum.write_text(
        f"{hashlib.sha256(archive.read_bytes()).hexdigest()}  {archive.name}\n",
        encoding="utf-8",
    )

    bootstrap.verify_sha256(archive, checksum)


def test_verify_sha256_rejects_corrupt_archive(tmp_path: Path) -> None:
    bootstrap = _load_bootstrap_module()
    archive = tmp_path / "bundle.tar.gz"
    archive.write_bytes(b"corrupt")
    checksum = tmp_path / "bundle.tar.gz.checksum.txt"
    checksum.write_text(f"{'0' * 64}  {archive.name}\n", encoding="utf-8")

    with pytest.raises(RuntimeError, match="SHA-256 mismatch"):
        bootstrap.verify_sha256(archive, checksum)


def test_verify_sha256_rejects_malformed_checksum_file(tmp_path: Path) -> None:
    bootstrap = _load_bootstrap_module()
    archive = tmp_path / "bundle.tar.gz"
    archive.write_bytes(b"content")
    checksum = tmp_path / "bundle.tar.gz.checksum.txt"
    checksum.write_text("unexpected response body", encoding="utf-8")

    with pytest.raises(RuntimeError, match="Invalid SHA-256 checksum file"):
        bootstrap.verify_sha256(archive, checksum)
