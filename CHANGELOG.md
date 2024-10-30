# Changelog
## v0.17.0
- `Released`: 2024-10-30
- `Supported pandas Versions`: 1.1.x - 1.5.x, 2.0.x - 2.2.x
- `Min Required IntelliJ Version`: 2022.3

### Added
#### [plugin] Copy Selected Cell Value
The value of the selected cell can now be copied via:
- <kbd>Ctrl</kbd> + <kbd>C</kbd> / <kbd>Command</kbd> + <kbd>C</kbd> (macOS)

#### [Python Console] Filtering
In the Python Console you can now filter the displayed `DataFrame` by specifying a Python filter expression.
The filter input for the Python Console uses runtime code completion.

A detailed description can be found here: 
- [pandas](./docs/PANDAS_FILTERING.md)
- [polars](./docs/POLARS_FILTERING.md)

#### [polars] Filtering
You can now filter your polars `DataFrame` by specifying a Python filter expression.

A detailed description can be found [here](./docs/POLARS_FILTERING.md).

#### [pandas] Code Completing For Column Names (experimental)
A basic code completion was added to complete column names of a `DataFrame`.

A detailed description can be found [here](./docs/PANDAS_FILTERING.md).

## v0.16.0
- `Released`: 2024-05-06
- `Supported pandas Versions`: 1.1.x - 1.5.x, 2.0.x - 2.2.x
- `Min Required IntelliJ Version`: 2022.3

### Changed
#### [pandas] Validation of Pandas Styling Functions
Problems detected by the validation are now reported only once for an unstable styling function.
Regardless if the styling function raised an exception or produced unstable styling results.
Already reported unstable styling functions are now excluded from the validation to further improve the validation speed.

The validation strategies `PRECISION`, `FAST`, `DISABLED` are replaced by the options `enabled` or `disabled`.
This was possible because of several improvements.

>If you had validation strategy `PRECISION` or `FAST` configured you have to manually enable the validation again in the plugin settings.

#### [polars] Formatting of Cell Values
The leading and trailing `"` are now removed from all stringlike values (`pl.String`, `pl.Categorical`, `pl.Enum` and `pl.Utf8`) - excluding nested data types.
Stringlike values are truncated in case they are longer than 200 chars.
Lists are truncated if they contain more than 60 entries.

Truncated values can be longer as the documented max chars.
Their length depends on the data type of the value.
Lists, for example, always have a closing `]`, even if the shortened value exceeds 60 characters.

### Fixed
- [pandas][validation] invalid styling function from docs was not detected if strategy `FAST` was used
- [pandas] Styler::highlight_between failed in case `left` or `right` was provided as 2-dimensional array

## v0.15.1
- `Released`: 2024-03-17
- `Supported pandas Versions`: 1.1.x - 1.5.x, 2.0.x - 2.2.x
- `Min Required IntelliJ Version`: 2022.3

### Fixed
- column statistics not updated after filtering ([issue 10](https://github.com/rendner/py-plugin-dataframe-viewer/issues/10))

## v0.15.0
- `Released`: 2024-03-07
- `Supported pandas Versions`: 1.1.x - 1.5.x, 2.0.x - 2.2.x
- `Min Required IntelliJ Version`: 2022.3

### Added
#### Column Statistics In Tooltip
The column tooltip now includes summary statistics about the column data.
The output will vary depending on the used version of `pandas`/`polars`.

![x](./docs/images/changelog/0.15.x/quick_statistics_tooltip.png)

#### Experimental Support For Python Console
You can view and sort polars and pandas `DataFrames`.
It is also possible to view Python dictionaries as a `DataFrame`.

### Fixed
- [pandas] respect `display.max_seq_items` if it is less then 42
- [pandas] truncate `DataFrame` values in case they are longer than 200 chars
- [pandas] allow to view a `dict` of scalars as `DataFrame`
- broken detection of installed `DataFrame` packages (used to view `dict` as `DataFrame`)

## v0.14.0
- `Released`: 2024-01-20
- `Supported pandas Versions`: 1.1.x - 1.5.x, 2.0.x - 2.2.x
- `Min Required IntelliJ Version`: 2022.3

### Added
#### Support For pandas 2.2
All already supported `Styler` methods as well as sorting and filtering are now also usable with pandas 2.2.

### Fixed
- [polars] broken string detection when formatting string values

### Other
- configure plugin compatibility "until 2024.3"

## v0.13.0
- `Released`: 2023-12-21
- `Supported pandas Versions`: 1.1.x - 1.5.x, 2.0.x - 2.1.x
- `Min Required IntelliJ Version`: 2022.3

### Added
#### Experimental Support For polars DataFrames
Experimental support for the lighting-fast DataFrame library [polars](https://pola.rs/).
You can view and sort polars `DataFrames`. 

It is also possible to view Python dictionaries as a polars `DataFrame`.

#### Column DataType In Tooltip
The column data type is now included in the column header tooltip.
This feature is available for pandas and polars `DataFrames`.

![x](./docs/images/changelog/0.13.x/dtype_in_tooltip.png)

## v0.12.0
- `Released`: 2023-11-15
- `Supported pandas Versions`: 1.1.x - 1.5.x, 2.0.x - 2.1.x
- `Min Required IntelliJ Version`: 2021.3

### Added
#### No Jinja2 Required
You can now view a pandas `DataFrame` without having `Jinja2` installed.
In the previous versions, a `Styler` was created from the `DataFrame`, for which `Jinja2` had to be installed.

### Changed
- replace custom value formatting logic with pandas `pprint_thing`

### Removed
- internal helper project `projects/html_from_styler` used to validate fetched data

### Fixed
- filter input: broken code completion
  - The previous "filter input"-fix broke other use cases.

### Other
- use unified data structure to retrieve version-independent data from pandas
- restructure loading of Python plugin code

## v0.11.0
- `Released`: 2023-09-18
- `Supported pandas Versions`: 1.1.x - 1.5.x, 2.0.x - 2.1.x
- `Min Required IntelliJ Version`: 2021.3

### Added
#### Support For pandas 2.1
All already supported `Styler` methods as well as sorting and filtering are now also usable with pandas 2.1.

#### Display Python dicts as DataFrame
If pandas is installed, Python dictionaries can be displayed as a pandas DataFrame that can be sorted and filtered.

Select a Python dictionary in the debugger variable view, open the context menu and select `View as Styled DataFrame`.
You can display the `Keys as Columns` (default behavior) or `Keys as Rows`. If the dictionary contains the keys
`index`, `columns`, `data`, `index_names` and `column_names` it is displayed with `orient='tight'`.

This new feature is available for all supported pandas versions.
A detailed description about the behavior can be found in the pandas-docs [DataFrame.from_dict](https://pandas.pydata.org/pandas-docs/stable/reference/api/pandas.DataFrame.from_dict.html#pandas-dataframe-from-dict).

### Fixed
- plugin fails with Python 3.10
- filter input: in some cases attribute references couldn't be resolved

## v0.10.0
- `Released`: 2023-03-01
- `Supported pandas Versions`: 1.1.x - 1.5.x, 2.0.x
- `Min Required IntelliJ Version`: 2021.3

### Added
#### Support For pandas 2.0
All already supported `Styler` methods as well as sorting and filtering are now also usable with pandas 2.0.

### Other
- fix Intellij-Plugin-API warnings (deprecated API)
- configure plugin compatibility "until 2023.3"

## v0.9.1
- `Released`: 2022-11-29
- `Supported pandas Versions`: 1.1.x - 1.5.x
- `Min Required IntelliJ Version`: 2021.3

### Added
#### Support For pandas 1.5
All already supported `Styler` methods are now also usable with pandas 1.5.

### Fixed
- don't open dialog for unsupported pandas version

## v0.9.0
- `Released`: 2022-11-22
- `Supported pandas Versions`: 1.1.x - 1.4.x
- `Min Required IntelliJ Version`: 2021.3

### Added
#### Filtering
The `DataFrame` can now be filtered by specifying a Python filter expression.
A detailed description about filtering can be found [here](./docs/PANDAS_FILTERING.md).

#### Auto Closing
The dialog is now automatically closed when the debug session is terminated or the viewed `DataFrame` isn't
reachable anymore by the plugin.

#### Fixed Column Width
The column width can be changed via shortcuts or by resizing the column.
You can also mark a column as fixed.
Marked columns keep their width even if the content would need more space or the width of the dialog is changed.

#### Truncated Cell Values
Cell values are now truncated in case they are longer than 300 chars.
In the past the full cell value was displayed.

Truncated values can be longer than 300 chars.
Their length depends on the data type of the value. 
Lists, for example, always have a closing `]`, even if the shortened value exceeds 300 characters.

### Removed
- deprecated way of data fetching (HTML string) and the feature switch to re-enable it
- dependencies to parse HTML and CSS strings
- internal helper project `projects/extract_computed_css` used to validate computed css

### Fixed
- no data fetching from `DataFrame` after continue from breakpoint
- false positive when validating styling functions in combination with hidden rows/columns

### Other
- plugin logo added to the notifications sent by the plugin
- name of viewed variable is displayed as dialog title
- number of rows and columns of the `DataFrame` are displayed on the left side below the displayed data

## v0.8.0
- `Released`: 2022-07-25
- `Supported pandas Versions`: 1.1.x - 1.4.x
- `Min Required IntelliJ Version`: 2020.3

### Added
#### Sorting
The `DataFrame` columns are now sortable. The multi-column sorting supports up to 9 columns.
Sorting can be performed by mouse click on the column headers or by keyboard shortcuts.

A detailed description about sorting can be found [here](./docs/SORTING.md).

#### Documentation of Keyboard Shortcuts
The plugin already had some very useful keyboard shortcuts.
These are now documented: [Keyboard Shortcuts](./docs/KEYBOARD_SHORTCUTS.md)

### Fixed
- use `Disposer.dispose` instead of calling directly `dispose`
- restrict width of error message dialog
- wrong truncated python error message
- broken detection of "debugger disconnected" exception

### Other
- upgrade `kotlinx-serialization-json` for better performance

## v0.7.1
- `Released`: 2022-07-03
- `Supported pandas Versions`: 1.1.x - 1.4.x
- `Min Required IntelliJ Version`: 2020.3

### Changed
#### Fetch Data From Python As JSON (experimental)
To get rid of custom parsing code, nearly all data is now fetched from Python as a JSON string.
This also affects the fetching of the rendered HTML, generated by pandas, for a chunk.

Switching from HTML to a custom JSON format may cause some problems.
This is the reason why this feature is implemented with a feature switch.
It is enabled by default. If you disable the feature, the HTML will be loaded as before.

>In case you run into any problem please let me know and open an issue on GitHub.
It is planned to switch completely to JSON in one of the next versions, this depends on feedback.

To switch back to the old behavior, you have to open the settings dialog, select *IntelliJ IDEA | Preferences* for macOS or *File | Settings* for Windows and Linux.
Alternatively, press <kbd>Ctrl</kbd> + <kbd>Alt</kbd> + <kbd>S</kbd>.

Under the section `Tools` you will find the entry `Styled DataFrame Viewer`.
The feature switch is called: `Use new data structure when loading chunks`

### Deprecated
- fetching data from Python as HTML strings

### Fixed
- exclude custom styling functions registered via `Styler.applymap` from styling-function validation
  - these styling functions take a scalar and do not get a chunk at runtime
- minor fixes in python validation code

## v0.7.0
- `Released`: 2022-06-24
- `Supported pandas Versions`: 1.1.x - 1.4.x
- `Min Required IntelliJ Version`: 2020.3

### Added
#### Validation of styling functions
Since the plugin generates the rendered output of a `DataFrame` chunk-wise, custom styling functions have to handle chunks correctly.
You can read more about it here: [The Problem](./docs/PANDAS_DATAFRAME.md#the-problem)

Ensuring that custom functions, registered via `Styler.apply`, work properly can be time-consuming and in some cases very cumbersome.
The plugin now offers the possibility to validate styling functions automatically in the background.

The feature is currently in experimental status. Please let me know if anything is not working, feedback of any kind is welcome.

Curious? [Here we go](./docs/PANDAS_VALIDATING_STYLE_FUNCTIONS.md)

### Fixed
- small cell height (cells now have an extra top and bottom padding)

### Other
- fix Intellij-Plugin-API warnings
- upgrade dependency `jsoup` to 1.15.1

## v0.6.0
- `Released`: 2022-02-23
- `Supported pandas Versions`: 1.1.x - 1.4.x
- `Min Required IntelliJ Version`: 2020.3

### Added
#### Initial Support For pandas 1.4
All already supported `Styler` methods are now also usable with pandas 1.4.
Some of them got additional parameters, which are also supported.
- `Styler.apply`
- `Styler.applymap`
- `Styler.background_gradient`
- `Styler.format`
- `Styler.highlight_max`
- `Styler.highlight_min`
- `Styler.highlight_null`
- `Styler.set_properties`
- `Styler.highlight_between`
- `Styler.highlight_quantile`
- `Styler.text_gradient`
- `Styler.hide_index` (deprecated in pandas 1.4)
- `Styler.hide_columns` (deprecated in pandas 1.4)
- `Styler.hide` (added in pandas 1.4)

### Changed
#### Performance Improvement (background_gradient)
Pandas builtin [Styler::background_gradient](https://pandas.pydata.org/docs/reference/api/pandas.io.formats.style.Styler.background_gradient.html)
generates a background color for each cell of the DataFrame.

To reduce the time needed to fetch and parse the displayed data, the number of elements which are fetched from the underlying `DataFrame` in one step was reduced by half from 60x20 (`rows`x`cols`) to 30x20.
There are also some minor improvements, such as fixing multiple parsing of same data.

The changes apply for all supported builtin style methods and not only for `Styler::background_gradient`.
However, the difference is hardly noticeable with the other supported builtin methods.

### Fixed
- terminate ExecutorService, used for data fetching, on window close

### Other
#### Changed Release Cycle
In the past, a new version of the plugin had to be released for each new IntelliJ minor-version to ensure that there were no breaking API changes.
So far I could not find any plugin issue related to a new IntelliJ version.
As of this release, plugin compatibility will be configured to work until the next IntelliJ major release (next will be 2023).
In case of an incompatibility problem I will release a new version.

## v0.5.1
- `Released`: 2021-12-01
- `Supported pandas Versions`: 1.1.x - 1.3.x
- `Min Required IntelliJ Version`: 2020.3

The source code of the plugin is finally available.
This was planned for quite a while, but it took some time to improve the testability of the plugin beforehand.

### Other
- compatibility with IntelliJ 2021.3
- upgrade dependency `jsoup` to 1.14.3
## v0.5.0
- `Released`: 2021-09-19
- `Supported pandas Versions`: 1.1.x - 1.3.x
- `Min Required IntelliJ Version`: 2020.3

### Added
#### Support For `Styler.hide_columns` And `Styler.hide_index`
It's now possible to hide specific rows and columns from a `DataFrame` by using these new supported methods.
Please note that the behavior may vary depending on the version of pandas you are using (behavior was changed in pandas 1.3.0).

A more detailed description with examples can be found in the official pandas API reference:

- [Styler.hide_index](https://pandas.pydata.org/docs/dev/reference/api/pandas.io.formats.style.Styler.hide_index.html)
- [Styler.hide_columns](https://pandas.pydata.org/docs/dev/reference/api/pandas.io.formats.style.Styler.hide_columns.html)


### Fixed
#### Broken `Styler.highlight_min` and `Styler.highlight_max`
The behavior of `Styler.highlight_min` and `Styler.highlight_max` has been changed as part of a bug fix in pandas.
Therefore, the plugin could no longer patch these methods because it wasn't able to detect these two built-in styles.

### Other
- upgrade dependency `jsoup` to 1.14.2

## v0.4.0
- `Released`: 2021-07-27
- `Supported pandas Versions`: 1.1.x - 1.3.x
- `Min Required IntelliJ Version`: 2020.3

### Added
#### Initial Support For pandas 1.3
All already by the plugin supported `Styler` methods are now also usable with pandas 1.3.
- `Styler.apply`
- `Styler.applymap`
- `Styler.background_gradient`
- `Styler.format`
- `Styler.highlight_max`
- `Styler.highlight_min`
- `Styler.highlight_null`
- `Styler.set_properties`

Some of them got additional parameters, which are also supported.

And the following methods, added in pandas 1.3, are also on board:
- `Styler.highlight_between`
- `Styler.highlight_quantile`
- `Styler.text_gradient`

#### Revised Column Header For Multi-Index DataFrames
Revised column header parsing and rendering to improve handling of multi-index `DataFrames`.

Index names of multi-index `DataFrames` are now included in the header tooltip. Before it was not possible to see this information.

![x](./docs/images/changelog/0.4.x/header_tooltips.gif)

Code of the multi-index `DataFrame` example:
```python
import pandas as pd
import numpy as np

np.random.seed(123456)

# hierarchical indices and columns
df = pd.DataFrame(
  data=np.round(np.random.randn(4, 6), 1),
  index=pd.MultiIndex.from_product([[2013, 2014], [1, 2]], names=['year', 'visit']),
  columns=pd.MultiIndex.from_product([['Bob', 'Guido', 'Sue'], ['HR', 'Temp']], names=['subject', 'type'])
)

styler = df.style

breakpoint()
```

### Changed

#### Reduced Size Of Generated HTML
The size of the html file generated in Python has been reduced. 
Unused content is now excluded, which can reduce the size of the generated html file. 
The reduction depends on the used styling.

#### Beta Status Hint Removed
In the last two versions of the plugin, a lot of the code was rewritten to provide better testing capabilities. 
Almost all tests previously performed manually are now automated.

### Other
- plugin logo added
- compatibility with IntelliJ 2021.2
- upgrade dependency `jsoup` to 1.14.1

## v0.3.1-b.1
- `Released`: 2021-06-20
- `Supported pandas Versions`: 1.1.x - 1.2.x
- `Min Required IntelliJ Version`: 2020.3 (previous plugin version was: 2019.2)

During the last months I started to rewrite large parts of the plugin to increase the maintainability and to build the foundation for faster implementation of new features.

This is a screenshot of the branch which contains all the changes for this version:

![x](./docs/images/changelog/0.3.1-b.1/changes-vcs_version-0.3.1.png)

Most of the changed files, 4743 of them, are generated test resources which are used by the new added unit tests.

### Added
#### Multi-Index DataFrames
`DataFrames` with multi-index column labels are now supported. Multi-index labels are separated by a `/` when displayed.
### Changed
#### Reduced Initial Loading Time
Column labels are now evaluated on demand, and not all at once. This allows to view `DataFrames` with many columns.

#### Fetching Data From DataFrames
The applied styles of a `DataFrame` are now always patched. 
In the previous version there were two ways of fetching and patching, depending on the size of the `DataFrame`.

>The rule was:
>* `DataFrame` with \> 500 rows -> styles were patched and data was fetched in chunks
>* `DataFrame` with <= 500 rows -> data was fetched in one step without patching styles

This can lead to different results, especially in the previous version of the plugin. 
It can also be irritating for users, if some `DataFrames` seem to show the expected result and sometimes (> 500 rows) not. 
To have a constant behavior, styles are always patched before data is fetched.

## v0.3-b.1
- `Released`: 2021-02-07
- `Supported pandas Versions`: 1.1.x - 1.2.x
- `Min Required IntelliJ Version`: 2019.2

Initial release of the plugin.
