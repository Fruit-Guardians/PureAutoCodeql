"""Legacy re-export of ``pure_auto_codeql.information.ghsa_fetch``."""

from pure_auto_codeql.information.ghsa_fetch import *  # noqa: F403
from pure_auto_codeql.information import ghsa_fetch as _impl

# Preserve module-level identity for ``from Information import ghsa_fetch``
# callers that access attributes on the submodule.
import sys as _sys

_sys.modules[__name__] = _impl
