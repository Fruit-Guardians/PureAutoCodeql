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
