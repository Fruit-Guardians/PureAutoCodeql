"""Shared constants for the project-import package."""

from __future__ import annotations

import re

SUPPORTED_LANGUAGES = {"python", "java", "cpp"}
CPP_AUTOGEN_BUILD_DIR = "build"
SAFE_CASE_ID_PATTERN = re.compile(r"^[A-Za-z0-9][A-Za-z0-9_.-]{0,127}$")
