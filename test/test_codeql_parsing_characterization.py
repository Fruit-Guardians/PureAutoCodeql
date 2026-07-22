"""Characterization tests for the pure parsing helpers in utils/codeql.py.

These lock in the *current* behavior before the codeql.py refactor (task 4b).
They intentionally assert what the code does today, not what it "should" do,
so any behavior drift during the stream-reader extraction is caught.
"""

import json

from pure_auto_codeql.utils.codeql import (
    _format_db_error,
    count_dataflow_paths,
    detect_language_from_query,
    is_database_error,
    is_empty_result,
    normalize_language,
    parse_codeql_results,
)


class TestDetectLanguageFromQuery:
    def test_java(self):
        assert detect_language_from_query("import java\nfrom X") == "java"

    def test_python(self):
        assert detect_language_from_query("import python") == "python"

    def test_cpp_variants(self):
        assert detect_language_from_query("import cpp") == "cpp"
        assert detect_language_from_query("import cplusplus") == "cpp"
        assert detect_language_from_query("\nimport c\n") == "cpp"

    def test_default_is_java(self):
        assert detect_language_from_query("") == "java"
        assert detect_language_from_query("no imports here") == "java"

    def test_none_input_defaults_java(self):
        assert detect_language_from_query(None) == "java"


class TestNormalizeLanguage:
    def test_cpp_aliases(self):
        for alias in ("c", "cplusplus", "cpp", "CPP", " Cpp "):
            assert normalize_language(alias) == "cpp"

    def test_passthrough(self):
        assert normalize_language("python") == "python"
        assert normalize_language("java") == "java"

    def test_default_and_none(self):
        assert normalize_language(None) == "java"
        assert normalize_language("rust") == "java"


class TestIsDatabaseError:
    def test_empty_is_false(self):
        assert is_database_error("") is False
        assert is_database_error(None) is False

    def test_known_patterns(self):
        for msg in (
            "Not a recognized CodeQL database",
            "path is not a codeql database",
            "database does not exist",
            "invalid database",
            "无法识别的数据库",
            "不是有效的数据库",
        ):
            assert is_database_error(msg) is True

    def test_unrelated_error_is_false(self):
        assert is_database_error("syntax error on line 5") is False


class TestFormatDbError:
    def test_includes_path_and_combined(self):
        out = _format_db_error("boom", "/tmp/db")
        assert "boom" in out
        assert "/tmp/db" in out
        assert "codeql database info /tmp/db" in out


class TestParseCodeqlResults:
    def test_empty(self):
        assert parse_codeql_results("") == []
        assert parse_codeql_results("   ") == []

    def test_sarif_runs(self):
        payload = json.dumps(
            {"runs": [{"results": [{"a": 1}, {"b": 2}]}, {"results": [{"c": 3}]}]}
        )
        assert parse_codeql_results(payload) == [{"a": 1}, {"b": 2}, {"c": 3}]

    def test_plain_list(self):
        assert parse_codeql_results(json.dumps([{"x": 1}])) == [{"x": 1}]

    def test_dict_without_runs_is_empty(self):
        assert parse_codeql_results(json.dumps({"foo": "bar"})) == []

    def test_pipe_delimited_fallback(self):
        out = parse_codeql_results("a | b | c\n# comment\nnopipe")
        assert out == [{"data": ["a", "b", "c"]}]


class TestIsEmptyResult:
    def test_none_and_missing(self, tmp_path):
        assert is_empty_result(None) is True
        assert is_empty_result(str(tmp_path / "nope.sarif")) is True

    def test_empty_runs(self, tmp_path):
        f = tmp_path / "empty.sarif"
        f.write_text(json.dumps({"runs": [{"results": []}]}), encoding="utf-8")
        assert is_empty_result(str(f)) is True

    def test_with_results(self, tmp_path):
        f = tmp_path / "hit.sarif"
        f.write_text(json.dumps({"runs": [{"results": [{"x": 1}]}]}), encoding="utf-8")
        assert is_empty_result(str(f)) is False

    def test_malformed_returns_true(self, tmp_path):
        f = tmp_path / "bad.sarif"
        f.write_text("{not json", encoding="utf-8")
        assert is_empty_result(str(f)) is True


class TestCountDataflowPaths:
    def test_none_inputs(self):
        assert count_dataflow_paths() == 0

    def test_sarif_counts_results(self, tmp_path):
        f = tmp_path / "r.sarif"
        f.write_text(
            json.dumps({"runs": [{"results": [{"a": 1}, {"b": 2}]}]}), encoding="utf-8"
        )
        assert count_dataflow_paths(sarif_path=str(f)) == 2

    def test_json_list(self, tmp_path):
        f = tmp_path / "p.json"
        f.write_text(json.dumps([{"a": 1}, {"b": 2}, {"c": 3}]), encoding="utf-8")
        assert count_dataflow_paths(json_path=str(f)) == 3

    def test_json_dict_with_paths(self, tmp_path):
        f = tmp_path / "p.json"
        f.write_text(json.dumps({"paths": [{"a": 1}]}), encoding="utf-8")
        assert count_dataflow_paths(json_path=str(f)) == 1


class TestStreamSubprocess:
    """Real tests for the extracted _stream_subprocess helper (task 4b).

    Uses /bin/sh + echo instead of codeql so it runs without the CodeQL CLI.
    Confirms return-code capture, stdout/stderr separation, and log tee-ing.
    """

    def test_stdout_capture_and_log(self, tmp_path):
        from pure_auto_codeql.utils.codeql import _stream_subprocess

        log = tmp_path / "run.log"
        rc, out, err = _stream_subprocess(
            ["sh", "-c", "echo hello"], log, timeout=10
        )
        assert rc == 0
        assert out == "hello\n"
        assert err == ""
        # output is tee'd to the log file
        assert "hello" in log.read_text(encoding="utf-8")

    def test_stderr_separation(self, tmp_path):
        from pure_auto_codeql.utils.codeql import _stream_subprocess

        log = tmp_path / "run.log"
        rc, out, err = _stream_subprocess(
            ["sh", "-c", "echo out; echo boom 1>&2"], log, timeout=10
        )
        assert rc == 0
        assert out == "out\n"
        assert err == "boom\n"
        logged = log.read_text(encoding="utf-8")
        assert "out" in logged and "boom" in logged

    def test_nonzero_returncode(self, tmp_path):
        from pure_auto_codeql.utils.codeql import _stream_subprocess

        log = tmp_path / "run.log"
        rc, out, err = _stream_subprocess(["sh", "-c", "exit 3"], log, timeout=10)
        assert rc == 3

    def test_timeout_propagates(self, tmp_path):
        import subprocess

        import pytest

        from pure_auto_codeql.utils.codeql import _stream_subprocess

        log = tmp_path / "run.log"
        with pytest.raises(subprocess.TimeoutExpired):
            _stream_subprocess(["sh", "-c", "sleep 5"], log, timeout=1)

    def test_missing_binary_raises_filenotfound(self, tmp_path):
        import pytest

        from pure_auto_codeql.utils.codeql import _stream_subprocess

        log = tmp_path / "run.log"
        with pytest.raises(FileNotFoundError):
            _stream_subprocess(["definitely-not-a-real-binary-xyz"], log, timeout=10)
