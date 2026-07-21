"""Legacy re-export of `pure_auto_codeql.core.pipeline`."""

from importlib import import_module
import sys as _sys

_sys.modules[__name__] = import_module("pure_auto_codeql.core.pipeline")
