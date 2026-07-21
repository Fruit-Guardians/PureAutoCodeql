"""Vulnerability intelligence fetchers (GHSA / NVD).

Canonical import surface:

    from pure_auto_codeql.information import ghsa_fetch, nvd_info_fetch

Legacy ``from Information import ...`` remains available via top-level shims.
"""

from . import ghsa_fetch, nvd_info_fetch

__all__ = ["ghsa_fetch", "nvd_info_fetch"]
