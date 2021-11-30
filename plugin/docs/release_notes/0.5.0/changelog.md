# Version 0.5.0

September, 2021

### Notable Changes

* [pandas 1.3] fix broken `Styler.highlight_min` and `Styler.highlight_max` (was broken for pandas >= 1.3.2)
* [pandas 1.3] add support for `Styler.hide_columns` and `Styler.hide_index`
* [pandas 1.2] add support for `Styler.hide_columns` and `Styler.hide_index`
* [pandas 1.1] add support for `Styler.hide_columns` and `Styler.hide_index`
* [plugin] upgrade dependency `jsoup` to 1.14.2

#### Bug fixes
* The behavior of `Styler.highlight_min` and `Styler.highlight_max` has been changed as part of a bug fix, and also some non-public code.
  Therefore, the plugin could no longer recognize these two built-in styles.

### Supported pandas Versions
* 1.1.x
* 1.2.x
* 1.3.x (was tested with 1.3.3)

### Min Required IntelliJ Version
* 2020.3

## What's New

#### Support For `Styler.hide_columns` And `Styler.hide_index`
It's now possible to hide specific rows and columns from a `DataFrame` by using these new supported methods.
Please note that the behavior may vary depending on the version of pandas you are using (behavior was changed in pandas 1.3.0).

A more detailed description with examples can be found in the official pandas API reference:

 - [Styler.hide_index](https://pandas.pydata.org/docs/dev/reference/api/pandas.io.formats.style.Styler.hide_index.html)
 - [Styler.hide_columns](https://pandas.pydata.org/docs/dev/reference/api/pandas.io.formats.style.Styler.hide_columns.html)


