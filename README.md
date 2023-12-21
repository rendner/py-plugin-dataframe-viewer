# Python: Styled DataFrame Viewer

Download from JetBrains Marketplace: [Python: Styled DataFrame Viewer](https://plugins.jetbrains.com/plugin/16050-python-styled-dataframe-viewer)

## What It Is
View, [sort](./docs/SORTING.md) and [filter](./docs/FILTERING.md) `DataFrames`, styled [pandas](https://pandas.pydata.org/docs/getting_started/index.html) `DataFrames` or a Python `dict` when debugging.

Note: The plugin requires one of the supported Python DataFrame libraries:
- `pandas`
- `polars` (experimental)

![preview of the plugin](./docs/images/plugin_preview.png)

## Supported DataFrame Libraries
- [pandas DataFrame](./docs/PANDAS_DATAFRAME.md)
- [polars DataFrame (experimental)](./docs/POLARS_DATAFRAME.md)

## Features
- [filtering](./docs/FILTERING.md)
- [sorting](./docs/SORTING.md)
- [keyboard shortcuts to work efficiently](./docs/KEYBOARD_SHORTCUTS.md)
- [settings to configure plugin behavior](./docs/SETTINGS.md)

## How Does It Work

### Quick Example:
Generate a `DataFrame`:
```python
# code for pandas
import pandas as pd

df = pd.DataFrame.from_dict({"a": range(200), "b": range(100, 300), "c": range(200, 400)})

breakpoint()
```

```python
# code for polars
import polars as pl

df = pl.from_dict({"a": range(200), "b": range(100, 300), "c": range(200, 400)})

breakpoint()
```

Run the code in debug mode in your JetBrains IDE. The program stops at the line with the `breakpoint()` command.

Select the `Threads & Variables` tab of the debugger (as seen in the screenshot below).
Right-click on `df` to open the context menu. Select `View as Styled DataFrame`.

![select view as action](./docs/images/quick_example-view_as_action.png)

This opens a new window which shows the `DataFrame` `df`:

![styled output](./docs/images/quick_example-dialog.png)