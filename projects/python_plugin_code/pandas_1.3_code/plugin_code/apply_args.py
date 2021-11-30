

# == copy after here ==
from typing import Callable, Tuple, Optional
from pandas._typing import Axis
from pandas.io.formats.style_render import Subset


class ApplyArgs:

    def __init__(self, args: Tuple[Callable, Optional[Axis], Optional[Subset]]):
        self.__args = args

    def func(self) -> Callable:
        return self.__args[0]

    def axis(self) -> Optional[Axis]:
        return self.__args[1]

    def subset(self) -> Optional[Subset]:
        return self.__args[2]
