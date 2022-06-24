# Version 0.7.0

June 2022

### Notable Changes
* [plugin] validation of styling functions

### Other changes
* [plugin] upgrade dependency `jsoup` to 1.15.1
* [plugin] fix small table cell height
* [plugin] fix deprecated notification api warning

#### Bug fixes
-

### Supported pandas Versions
* 1.1.x
* 1.2.x
* 1.3.x (tested with 1.3.5)
* 1.4.x (tested with pandas 1.4.3)

### Min Required IntelliJ Version
* 2020.3

## What's New

#### Validation of styling functions
Since the plugin generates the rendered output of a `DataFrame` chunk-wise, custom styling functions have to handle chunks correctly.
You can read more about it here: [The Problem](./../../../../README.md#the-problem) 

Ensuring that custom functions, registered via `Styler.apply` or `Styler.applymap`, work properly can be time-consuming and in some cases very cumbersome. 
The plugin now offers the possibility to validate styling functions automatically in the background.

The feature is currently in experimental status. Please let me know if anything is not working, feedback of any kind is welcome.

Curious? [Here we go](./../../../../docs/VALIDATING_STYLE_FUNCTIONS.md)