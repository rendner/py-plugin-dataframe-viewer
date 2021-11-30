
# == copy after here ==
from typing import Callable, Tuple, Optional
from pandas.io.formats.style_render import Subset


class ApplyMapArgs:

    def __init__(self, args: Tuple[Callable, Optional[Subset]]):
        self.__args = args

    def func(self) -> Callable:
        return self.__args[0]

    def subset(self) -> Optional[Subset]:
        return self.__args[1]
