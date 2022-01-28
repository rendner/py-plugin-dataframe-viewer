import inspect
from typing import Callable


def assert_style_func_parameters(styling_func: Callable, known_params: [str]):
    unused_params = known_params.copy()
    new_params = []

    sig = inspect.signature(styling_func)
    for param in sig.parameters.values():
        if param.name not in known_params:
            new_params.append(param.name)
        if param.name in unused_params:
            unused_params.remove(param.name)

    assert new_params == [], f'new styling parameters found: {new_params}'
    assert unused_params == [], f'unused styling parameters found: {unused_params}'
