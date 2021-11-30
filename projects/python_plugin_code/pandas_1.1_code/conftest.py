import pytest

# https://docs.pytest.org/en/6.2.x/writing_plugins.html
# activate pytest asserts introspection for our asserts helpers
pytest.register_assert_rewrite('tests.helpers.asserts')


def pytest_make_parametrize_id(config, val, argname):
    return f'{argname}: {str(val)}'
