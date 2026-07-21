"""Legacy re-export of ``pure_auto_codeql.information.ghsa_fetch``.

``from Information import ghsa_fetch`` and ``import Information.ghsa_fetch``
both resolve to the canonical module object.
"""

from pure_auto_codeql.information import ghsa_fetch as _impl
import sys as _sys

_sys.modules[__name__] = _impl
