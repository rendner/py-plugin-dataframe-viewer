# Pandas
The plugin allows you to view Python `dicts`, pandas `DataFrames` and styled ones by using a `DataFrame.style` object.

**Supported pandas Versions:**
* 1.1.x - 1.5.x
* 2.0.x - 2.2.x

**Features:**
- [sortable](./SORTING.md)
- [filterable](./PANDAS_FILTERING.md)
- [keyboard shortcuts to work efficiently](./KEYBOARD_SHORTCUTS.md)
- [automatic detection of not chunk aware styling functions](./PANDAS_VALIDATING_STYLE_FUNCTIONS.md)


## Data Formatting
The plugin uses the following pandas options when generating the output of a `DataFrame`:

- `display.float_format`
- `display.precision`
- `display.max_seq_items` (but maximal 42 items)

You can also use the `set_eng_float_format()` function to alter the floating-point formatting.
These options are ignored when generating the output of styled `DataFrames`, use `Styler::format` instead.

For column specific formatting use a styler object:
```python
import numpy as np
import pandas as pd

np.random.seed(123456789)

# create a DataFrame
df = pd.DataFrame(np.random.randn(500, 4))

# the Styler formats all values of the first column
styler = df.style\
    .format({0: '{:+.2f}'})\
    .set_properties(**{'text-align': 'center', 'background-color': 'yellow'}, subset=[0])

breakpoint()
```

![formatted output](./images/example_formatted_output.png)


## Styled DataFrames
> A good overview about styling `DataFrames` can be found on the pandas website: [pandas User Guide: Styling](https://pandas.pydata.org/pandas-docs/stable/user_guide/style.html)

### Supported Styler Methods
The following `Styler` methods are supported:

| pandas Styler method                                                                                                                             | supported pandas version     |
|--------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------|
| [Styler.apply](https://pandas.pydata.org/pandas-docs/stable/reference/api/pandas.io.formats.style.Styler.apply.html)                             | 1.1.x - 1.5.x, 2.0.x - 2.2.x |
| [Styler.applymap](https://pandas.pydata.org/pandas-docs/stable/reference/api/pandas.io.formats.style.Styler.applymap.html)                       | 1.1.x - 1.5.x, 2.0.x - 2.2.x |
| [Styler.background_gradient](https://pandas.pydata.org/pandas-docs/stable/reference/api/pandas.io.formats.style.Styler.background_gradient.html) | 1.1.x - 1.5.x, 2.0.x - 2.2.x |
| [Styler.format](https://pandas.pydata.org/pandas-docs/stable/reference/api/pandas.io.formats.style.Styler.format.html)                           | 1.1.x - 1.5.x, 2.0.x - 2.2.x |
| [Styler.hide_columns](https://pandas.pydata.org/pandas-docs/version/1.5/reference/api/pandas.io.formats.style.Styler.hide_columns.html)          | 1.1.x - 1.5.x                |
| [Styler.hide_index](https://pandas.pydata.org/pandas-docs/version/1.5/reference/api/pandas.io.formats.style.Styler.hide_index.html)              | 1.1.x - 1.5.x                |
| [Styler.hide](https://pandas.pydata.org/pandas-docs/stable/reference/api/pandas.io.formats.style.Styler.hide.html)                               | 1.4.x - 1.5.x, 2.0.x - 2.2.x |
| [Styler.highlight_between](https://pandas.pydata.org/pandas-docs/stable/reference/api/pandas.io.formats.style.Styler.highlight_between.html)     | 1.3.x - 1.5.x, 2.0.x - 2.2.x |
| [Styler.highlight_max](https://pandas.pydata.org/pandas-docs/stable/reference/api/pandas.io.formats.style.Styler.highlight_max.html)             | 1.1.x - 1.5.x, 2.0.x - 2.2.x |
| [Styler.highlight_min](https://pandas.pydata.org/pandas-docs/stable/reference/api/pandas.io.formats.style.Styler.highlight_min.html)             | 1.1.x - 1.5.x, 2.0.x - 2.2.x |
| [Styler.highlight_null](https://pandas.pydata.org/pandas-docs/stable/reference/api/pandas.io.formats.style.Styler.highlight_null.html)           | 1.1.x - 1.5.x, 2.0.x - 2.2.x |
| [Styler.highlight_quantile](https://pandas.pydata.org/pandas-docs/stable/reference/api/pandas.io.formats.style.Styler.highlight_quantile.html)   | 1.3.x - 1.5.x, 2.0.x - 2.2.x |
| [Styler.map](https://pandas.pydata.org/pandas-docs/stable/reference/api/pandas.io.formats.style.Styler.map.html)                                 | 2.1.x - 2.2.x                |
| [Styler.set_properties](https://pandas.pydata.org/pandas-docs/stable/reference/api/pandas.io.formats.style.Styler.set_properties.html)           | 1.1.x - 1.5.x, 2.0.x - 2.2.x |
| [Styler.text_gradient](https://pandas.pydata.org/pandas-docs/stable/reference/api/pandas.io.formats.style.Styler.text_gradient.html)             | 1.3.x - 1.5.x, 2.0.x - 2.2.x |


### Supported CSS Properties
The following CSS properties are supported by the plugin:

| CSS property       | mapping                                                              |
|--------------------|----------------------------------------------------------------------|
| `color`            | table cell text color                                                |
| `background-color` | table cell background color                                          |
| `text-align`       | align cell text (supported values are: `left`, `right` and `center`) |

All other CSS properties are ignored.

*Note:*
Inheritance of CSS properties is not supported. It would require a complete CSS engine which can also handle partial results (chunks).

## Styling a DataFrame

> You can stop reading from here if you only use the plugin to view normal pandas `DataFrames`.
>
> Please read the following section carefully to understand how custom styles must be written to support chunks. Otherwise, it can lead to incorrect output.

In general, a custom styling function is passed into one of the following methods:

- `Styler.applymap` (renamed to `Styler.map` in pandas 2.1.0)
- `Styler.apply`

Both of those methods take a function (and some other keyword arguments) and applies your function to the `DataFrame` in
a certain way.

The configured styling is only applied when `style.to_html()` is called to generate the HTML representation of the styled `DataFrame`.
The time it takes to generate the HTML depends on the size of the `DataFrame` and the complexity of the styling functions.

## The Plugin (under the hood)
Fetching the styled output for `DataFrames` and converting it is very time-consuming.
There are two expensive steps involved when fetching the data:
- generating the styled output by calling the styled functions on Python side
- parsing and converting the fetched data in the plugin

In most cases this can't be done for the whole `DataFrame` in one step. Instead, the plugin fetches the data of smaller areas of the `DataFrame`.
Such areas are called chunks which are also `DataFrames`. 
Fetching styled data in chunks is way faster and less memory consuming.
But of course there is one problem when it comes to custom styling functions.

#### The Problem
As we already know, `Styler.applymap` (`Styler.map`) works through the `DataFrame` elementwise, therefore it is safe to use in combination with chunks.

`Styler.apply` passes each column or row of a `DataFrame` one-at-a-time or the entire `DataFrame` at once, depending on the axis keyword argument.
In case of chunks, a passed row or column is taken from the chunk and not from the original `DataFrame`.
Same for `axis=None`, here the chunk is passed instead of the original `DataFrame`.
This leads to problems if you want for example highlight the largest value in each column of a `DataFrame`.
Because this value can't be evaluated if your custom styling function only receives a part of the entire column.

> All builtin styles listed under [supported-styler-methods](#supported-styler-methods), except `apply` and `applymap` (`map`), are automatically handled by the plugin and can therefore be used without any changes.

##### The Problem (Example)
To get a better understanding of the problem, you can run the following example in debug mode:
```python
import numpy as np
import pandas as pd

np.random.seed(123456789)

# create a DataFrame
df = pd.DataFrame(np.random.randint(1, 100, size=(2800, 4)))


def my_highlight_max(series):
  is_max = series == series.max()
  return ['background-color: red' if cell else '' for cell in is_max]


# the Styler highlights the maximum value in each column
# (spoiler: this is not the case)
styler = df.style.apply(my_highlight_max, axis='index')

breakpoint()
```
Right-click on styler in the `Threads & Variables` tab of the debugger to open the context menu. Select `View as Styled DataFrame` and scroll to the place where you can see the rows `597` and `605`:

![wrong max value](./images/example_chunked_wrong_max_value.png)

You can clearly see that in column `3` there are two different values highlighted, which is wrong.

##### Solving The Problem
You can signal the plugin that you also need the un-chunked part which is normally used when calling your custom styling
function. To do this you have to adjust the custom style function `my_highlight_max`.
```python
def my_highlight_max(series, chunk_parent=None):
    max = (series if chunk_parent is None else chunk_parent).max()
    return ['background-color: red' if cell == max else '' for cell in series]
```
Add an optional argument named `chunk_parent`. The name of this argument has to be `chunk_parent`, otherwise the
plugin can't detect that the un-chunked data should be provided.

> The `chunk_parent` is only provided by the plugin. Therefore, it is a good idea to always make it optional so that the custom styler also work when used without the plugin.

Right-click on styler in the `Debugger` tab to open the context menu. Select `View as Styled DataFrame` and scroll to the place where you can see the rows `597` and `605`:

![right max value](./images/example_chunked_right_max_value.png)

The output now displays the expected result.

Instead of the optional `chunk_parent` you could also use `**kwargs` to tell the plugin that you want to have the un-chunked data.

```python
def my_highlight_max_using_kwargs(series, **kwargs):
    max = kwargs.get("chunk_parent", series).max()
    return ['background-color: red' if cell == max else '' for cell in series]
```

##### Automatic Problem Detection
Depending on the complexity of a custom styling function it can be hard to determine if the displayed result is always correct.

The plugin can do some of the work for you and automatically try to detect problems in the background.
Check [Validating Style Functions](./PANDAS_VALIDATING_STYLE_FUNCTIONS.md)
