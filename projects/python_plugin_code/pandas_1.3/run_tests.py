from tools.run_pytest import run_pytest

if __name__ == "__main__":
    run_pytest("[tests with no-jinja guard]", ["--no-jinja"])
    run_pytest("[tests]")
