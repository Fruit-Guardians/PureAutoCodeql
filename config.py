"""Legacy script shim for the configuration CLI.

Runtime application code should import configuration helpers from
`pure_auto_codeql.configuration`. The `config/` package remains available for
legacy `from config import ...` imports, and this root-level file is kept so
`python config.py ...` continues to launch the configuration CLI.
"""

from config import *  # noqa: F401, F403

if __name__ == "__main__":
    from config.cli import main

    main()
