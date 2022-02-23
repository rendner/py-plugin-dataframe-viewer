# Version 0.6.0

February, 2022

### Notable Changes
* [pandas 1.4] initial support for pandas 1.4
* [plugin] performance improvement (background_gradient)
* compatibility with IntelliJ 2022
* changed release cycle

#### Bug fixes
* terminate ExecuterService, used for data fetching, on window close 

### Supported pandas Versions
* 1.1.x
* 1.2.x
* 1.3.x (tested with 1.3.5)
* 1.4.x (new in this version, tested with pandas 1.4.1)

### Min Required IntelliJ Version
* 2020.3

## What's New

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

#### Performance Improvement (background_gradient)
Pandas builtin [Styler::background_gradient](https://pandas.pydata.org/docs/reference/api/pandas.io.formats.style.Styler.background_gradient.html)
generates a background color for each cell of the DataFrame.

To reduce the time needed to display the data including colors, the number of elements which are fetched from the underlying `DataFrame` in one step was reduced by half from 60x20 (`rows`x`cols`) to 30x20.
There are also some minor improvements, such as fixing multiple parsing of data.

The changes apply for all supported builtin style methods and not only for `Styler::background_gradient`.
However, the difference is hardly noticeable with the other supported builtin methods.

#### Changed Release Cycle
In the past, a new version of the plugin had to be released for each new IntelliJ minor-version to ensure that there were no breaking API changes.
So far I could not find any plugin issue related to a new IntelliJ version.
As of this release, plugin compatibility will be configured to work until the next IntelliJ major release (next will be 2023).
In case of an incompatibility problem I will release a new version.

