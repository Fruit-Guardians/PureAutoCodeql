"""Legacy GHSA/NVD intelligence package.

Implementation lives under ``pure_auto_codeql.information``. This top-level
package re-exports the same modules for compatibility.
"""

from pure_auto_codeql.information import ghsa_fetch, nvd_info_fetch

__all__ = ["ghsa_fetch", "nvd_info_fetch"]
