# Sorting
The values of the styled `DataFrame` can be sorted by clicking on the table headers of the columns or by using keyboard shortcuts.

The sorting is done by a call to the pandas builtin method [DataFrame.sort_values](https://pandas.pydata.org/docs/reference/api/pandas.DataFrame.sort_values.html).
Currently, only the columns to sort and the sort direction are forwarded.

>Hint: Using keyboard shortcuts, the desired sort order can be set or deleted directly without switching between different sort states.
>
> See the section `TableSorting` in [KEYBOARD_SHORTCUTS.md](./KEYBOARD_SHORTCUTS.md).


>Note: The sorting of the styled `DataFrame` is done, by the plugin, before applying the styling.
>
>Therefore, it can produce unexpected results in combination with pandas [Styler.format](https://pandas.pydata.org/docs/reference/api/pandas.io.formats.style.Styler.format.html).

## Single Column Sort
To sort the data by a single column click on the table header of this column.

You can switch between the following 3 sorting states by clicking on the column header: `ascending`, `descending` and `unsorted` (initial state).

## Multi Column Sort
To sort by multiple columns hold the `Shift`-key when clicking on the column header to sort.
The plugin supports up to 9 columns.

In multi-sort, you can only switch between the following 2 sort states by clicking on the column header: `ascending` and `descending`.

To clear a multi-sort, click on a column - without holding the `Shift`-key to start a new single-sort.
Then click on the column header several times until the `unsorted` state is reached.
