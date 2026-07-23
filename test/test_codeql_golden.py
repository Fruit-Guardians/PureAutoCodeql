import json
import shutil
import subprocess
from pathlib import Path

import pytest

GOLDEN_ROOT = Path(__file__).parent / "golden"

CASES = [
    ("java_path_traversal", "java", None),
    ("python_command_injection", "python", None),
    ("cpp_buffer_overflow", "cpp", None),
]


@pytest.mark.real_codeql
@pytest.mark.parametrize(("case_name", "language", "build_command"), CASES)
def test_real_codeql_golden_path_query(tmp_path, case_name, language, build_command):
    if not shutil.which("codeql"):
        pytest.skip("CodeQL CLI is not installed")
    fixture = GOLDEN_ROOT / case_name
    source = fixture / "src"
    database = tmp_path / "database"
    create = [
        "codeql",
        "database",
        "create",
        str(database),
        f"--language={language}",
        f"--source-root={source}",
        "--overwrite",
    ]
    if build_command:
        create.append(f"--command={build_command}")
    else:
        create.append("--build-mode=none")
    subprocess.run(create, cwd=source, check=True, timeout=300)

    sarif = tmp_path / "result.sarif"
    subprocess.run(
        [
            "codeql",
            "database",
            "analyze",
            str(database),
            str(fixture / "query.ql"),
            "--format=sarif-latest",
            f"--output={sarif}",
            "--rerun",
        ],
        check=True,
        timeout=300,
    )
    payload = json.loads(sarif.read_text(encoding="utf-8"))
    results = payload["runs"][0]["results"]
    assert results
    assert results[0]["locations"][0]["physicalLocation"]["artifactLocation"]["uri"]
    assert results[0].get("codeFlows"), f"{case_name} did not produce a data-flow path"
