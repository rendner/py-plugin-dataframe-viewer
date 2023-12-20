# Sorting
*Since Version: 0.8.0*

The values of a `DataFrame` can be sorted by clicking on the table headers of the columns or by using keyboard shortcuts.

Using keyboard shortcuts, the desired sort order can be set or deleted directly without switching between different sort states.
See the section `Table Sorting` in [KEYBOARD_SHORTCUTS.md](./KEYBOARD_SHORTCUTS.md).

## Single Column Sort
To sort the data by a single column click on the table header of this column.

You can switch between the following 3 sorting states by clicking on the column header: `ascending`, `descending` and `unsorted` (initial state).

## Multi Column Sort
To sort by multiple columns hold the <kbd>Shift</kbd>-key when clicking on the column header to sort.
The plugin supports up to 9 columns.

In multi-sort, you can only switch between the following 2 sort states by clicking on the column header: `ascending` and `descending`.

To clear a multi-sort, click on a column - without holding the <kbd>Shift</kbd>-key to start a new single-sort.
Then click on the column header several times until the `unsorted` state is reached.
