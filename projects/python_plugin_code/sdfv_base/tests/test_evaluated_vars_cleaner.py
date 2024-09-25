import inspect
from cms_rendner_sdfv.base.temp import TEMP_VARS, EvaluatedVarsCleaner


def test_can_remove_stored_temp_var():
    TEMP_VARS['a'] = 1
    EvaluatedVarsCleaner.clear(['a'])
    assert 'a' not in TEMP_VARS


def test_can_clear_evaluated_var():
    frame = inspect.currentframe()
    # simulate an evaluated var
    # (as the PyCharm debugger does when using evaluate-expression feature)
    frame.f_locals['a'] = 1
    try:
        EvaluatedVarsCleaner.clear(['a'])
        assert frame.f_locals['a'] is None
    finally:
        del frame


def test_can_clear_evaluated_var_of_an_outer_frame():
    frame = inspect.currentframe()
    # simulate an evaluated var
    # (as the PyCharm debugger does when using evaluate-expression feature)
    frame.f_locals['a'] = 1

    def do_cleanup():
        def another_level():
            EvaluatedVarsCleaner.clear(['a'])
        another_level()

    try:
        do_cleanup()
        assert frame.f_locals['a'] is None
    finally:
        del frame


def test_can_remove_stored_temp_var_and_clear_evaluated_var():
    TEMP_VARS['a'] = 1
    frame = inspect.currentframe()
    # simulate an evaluated var
    # (as the PyCharm debugger does when using evaluate-expression feature)
    frame.f_locals['b'] = 1
    try:
        EvaluatedVarsCleaner.clear(['a', 'b'])
        assert frame.f_locals['b'] is None
        assert 'a' not in TEMP_VARS
    finally:
        del frame


def test_can_not_clear_local_var():
    a = 1
    frame = inspect.currentframe()
    try:
        EvaluatedVarsCleaner.clear(['a'])
        assert frame.f_locals['a'] is 1
    finally:
        del frame


def test_remove_stored_temp_var_and_leave_evaluated_var_with_same_name():
    TEMP_VARS['a'] = 1
    frame = inspect.currentframe()
    # simulate an evaluated var
    # (as the PyCharm debugger does when using evaluate-expression feature)
    frame.f_locals['a'] = 1
    try:
        EvaluatedVarsCleaner.clear(['a'])
        assert frame.f_locals['a'] is 1
        assert 'a' not in TEMP_VARS
    finally:
        del frame
