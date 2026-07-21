"""Legacy re-export of ``pure_auto_codeql.information.nvd_info_fetch``."""

from pure_auto_codeql.information.nvd_info_fetch import *  # noqa: F403
from pure_auto_codeql.information import nvd_info_fetch as _impl

import sys as _sys

_sys.modules[__name__] = _impl
