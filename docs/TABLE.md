## Table

### Columns

By default, the width of the table columns is automatically expanded to fully display the content of a column.
The min width is `65px` and the max width is `350px` in auto expand mode.

>Hint: Using keyboard shortcuts, the column width can be set or cleared directly.
>
> See the section `Table Column Width` in [KEYBOARD_SHORTCUTS.md](./KEYBOARD_SHORTCUTS.md).

The table offers the possibility to adjust the column width individually.
Drag the boundary line on the right side of the column header until the column has the desired width.
The column width can be set larger than the maximum size that applies in the automatic expansion mode.
After releasing the mouse, the column will be marked as fixed (indicated by the marker above the column table header).

Marked columns keep their width even if the content would need more space or the width of the table is changed.

The last column cannot be given a fixed width.
This column is used as a buffer, when resizing the table or the columns of the table, to which the remaining width is applied.
For example, one column has been resized and the delta of the resized width must be distributed to other columns, depending on the resize mode.
If all other columns would have a fixed width, the delta can't be distributed to any other column.
This could also lead to an empty space after the last column if the sum of the width of all columns is smaller than the width of the table viewport.

### Number of Rows/Columns Info
Below the table a small label with the number of rows and columns is displayed.

The info label will also include the number of visible rows and columns in case some data:
- was marked as hidden via [Styler.hide](https://pandas.pydata.org/docs/reference/api/pandas.io.formats.style.Styler.hide.html)
- was filtered out, by a filter query provided to the plugin


### Data Loading
The data is loaded only when needed.
Each time you scroll through the table, the data for the currently visible area is loaded, if it does not already exist.
