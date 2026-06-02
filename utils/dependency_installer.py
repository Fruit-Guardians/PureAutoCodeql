"""Conservative dependency-installation wrapper for C/C++ imports."""

from __future__ import annotations

from pathlib import Path
from typing import Callable, Optional, Tuple

from utils.logger import get_logger

logger = get_logger(__name__)


class DependencyInstaller:
    """Run a build function, optionally leaving room for future dependency fixes.

    The previous importer expected this helper to exist. This implementation is
    intentionally conservative: it does not install system packages by itself,
    which avoids surprising host mutations from API-triggered project imports.
    """

    def __init__(self, auto_install: bool = False, max_retries: int = 1):
        self.auto_install = auto_install
        self.max_retries = max(1, max_retries)
        self.installed_packages: list[str] = []

    def try_build_with_auto_deps(
        self,
        *,
        build_func: Callable[[], bool],
        log_path: Optional[Path] = None,
    ) -> Tuple[bool, Optional[str]]:
        try:
            return bool(build_func()), None
        except Exception as exc:  # pylint: disable=broad-except
            if self.auto_install:
                logger.warning(
                    "Automatic dependency installation is not performed. "
                    "Build log: %s",
                    log_path,
                )
            return False, str(exc)
