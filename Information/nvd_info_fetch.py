"""Legacy re-export of ``pure_auto_codeql.information.nvd_info_fetch``.

``from Information import nvd_info_fetch`` and ``import Information.nvd_info_fetch``
both resolve to the canonical module object.
"""

from pure_auto_codeql.information import nvd_info_fetch as _impl
import sys as _sys

_sys.modules[__name__] = _impl
