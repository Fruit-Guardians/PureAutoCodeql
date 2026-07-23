import os


def vulnerable() -> int:
    command = os.getenv("USER_COMMAND")
    return os.system(command)
