# Version 0.3.1-b.1

June, 2020


During the last months I started to rewrite large parts of the plugin to increase the maintainability and to build the foundation for faster implementation of new features.

This is a screenshot of the branch which contains all the changes for this version:

![x](./images/changes-vcs_version-0.3.1.png)

Most of the changed files, 4743 of them, are generated test resources which are used by the new added unit tests.

No big features in this release.

### Supported Pandas Versions
* 1.1.x
* 1.2.x

### Min Required IntelliJ Version
* 2020.3 (previous plugin version was: 2019.2)

## What's New

#### Multi Index DataFrames
`DataFrames` with multi index column labels are now supported. Multi index labels are separated by a `\ ` when displayed.

#### Reduced Initial Loading Time
Column labels are now evaluated on demand, and not all at once. This allows to view `DataFrames` with many columns.

#### Fetching Data From DataFrames
The applied styles of a `DataFrame` are now always patched, before fetching data from a `DataFrame`, no matter how many rows or columns a `DataFrame` has. 

> This is a breaking change, but it depends on your custom stylers.
 
In the previous version there were two ways of fetching and patching, depending on the size of the `DataFrame`.

The rule was:
    
* `DataFrame` has \> 500 rows -> styles were patched and data was fetched in chunks
* `DataFrame` has <= 500 rows -> data was fetched in one step without patching styles

This can lead to different results, especially in the previous version of the plugin. It can also be irritating for users, if some `DataFrames` seem to show the expected result and sometimes (> 500 rows) not. To have a constant behavior, styles are always patched before data is fetched.

> Please check the linked description [here](../../README.md#handle-chunks-in-custom-styles), to validate if you have to adjust your custom styles.
