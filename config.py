"""Legacy script shim for the configuration CLI.

Runtime application code should import configuration helpers from
`pure_auto_codeql.configuration`. User keys live in repo-root `config/keys.toml`.
This file only keeps `python config.py ...` working.
"""

if __name__ == "__main__":
    from pure_auto_codeql.config.cli import main

    main()
