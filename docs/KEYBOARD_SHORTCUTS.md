## Keyboard Shortcuts

### Table Column Width
Requires a focused table cell to use these actions.

| Shortcut                                        | Action                                                              |
|-------------------------------------------------|---------------------------------------------------------------------|
| <kbd>.</kbd>                                    | Set/unset fixedWidth marker for focused column[^1].                 |
| <kbd>Alt</kbd> + <kbd>.</kbd>                   | Invert fixedWidth markers for all columns[^1].                      |
| <kbd>Alt</kbd> + <kbd>Ctrl</kbd> + <kbd>.</kbd> | Remove all fixedWidth markers.                                      |
| <kbd>+</kbd>                                    | Expand focused column width by 10px, but not larger than 350px.     |
| <kbd>Alt</kbd> + <kbd>+</kbd>                   | Set focused column width to 350px (large width).                    |
| <kbd>-</kbd>                                    | Shrink focused column width by 10px[^2], but not smaller than 65px. |
| <kbd>Alt</kbd> + <kbd>-</kbd>                   | Set focused column width to 65px[^2] (small width).                 |

[^1]: Last column can't be set to a fixed width.

[^2]: Shrinking an unfixed column can have no effect if shrinking would reduce the width below the required column width or the width of the table viewport.

### Table Sorting
Requires a focused table cell to use these actions.

| Shortcut                       | Action                                                                                 |
|--------------------------------|----------------------------------------------------------------------------------------|
| <kbd>A</kbd>                   | Sort focused column ascending. Resets all previous sorted columns.                     |
| <kbd>D</kbd>                   | Sort focused column descending. Resets all previous sorted columns.                    |
| <kbd>C</kbd>                   | Reset all sorted columns.                                                              |
| <kbd>Alt</kbd> + <kbd>A</kbd>  | Sort focused column ascending. Previous sorted columns are not cleared (multi-sort).   |
| <kbd>Alt</kbd> + <kbd>D</kbd>  | Sort focused column descending. Previous sorted columns are not cleared (multi-sort).  |
| <kbd>Alt</kbd> + <kbd>C</kbd>  | Clear sorting of focused column. Previous sorted columns are not cleared (multi-sort). |


### Table Scrolling
The following keys allow navigation within the table.

| Shortcut                                          | Action                                                                                                   |
|---------------------------------------------------|----------------------------------------------------------------------------------------------------------|
| <kbd>&uarr;</kbd>                                 | Move focus one row up, stop at first row.                                                                |
| <kbd>&darr;</kbd>                                 | Move focus one row down, stop at last row.                                                               |
| <kbd>&larr;</kbd>                                 | Move focus one cell left, stop at first cell of current row.                                             |
| <kbd>&rarr;</kbd>                                 | Move focus one cell right, stop at last cell of current row.                                             |
| <kbd>Page &darr;</kbd>                            | Move focus one page down, stop at last row.                                                              |
| <kbd>Page &uarr;</kbd>                            | Move focus one page up, stop at first row.                                                               |
| <kbd>Tab</kbd>                                    | Move focus one cell right, continue at the first cell of next row. If last row, continue in first row.   |
| <kbd>Shift</kbd> + <kbd>Tab</kbd>                 | Move focus one cell left, continue at the last cell of previous row. If first row, continue in last row. |
| <kbd>Home</kbd>/<kbd>Pos1</kbd>                   | Move focus to the leftmost cell in the row.                                                              |
| <kbd>End</kbd>                                    | Move focus to the rightmost cell in the row.                                                             |
| <kbd>Ctrl</kbd> + <kbd>Home</kbd>/<kbd>Pos1</kbd> | Move focus to first row.                                                                                 |
| <kbd>Ctrl</kbd> + <kbd>End</kbd>                  | Move focus to last row.                                                                                  |
| <kbd>Ctrl</kbd> + <kbd>Page &darr;</kbd>          | Move focus one page right, stop at last cell.                                                            |
| <kbd>Ctrl</kbd> + <kbd>Page &uarr;</kbd>          | Move focus one page left, stop at first cell.                                                            |

### Window Actions
The following keys allow to interact with the dialog window.
>**Note:** These shortcuts depend on the used keymap and IntelliJ version.
>
> Please check your current [keymap](https://www.jetbrains.com/help/idea/settings-keymap.html) and search for `Active Tool Window Resize`.

| Shortcut                                               | Action                     |
|--------------------------------------------------------|----------------------------|
| <kbd>Esc</kbd>                                         | Close dialog.              |
| <kbd>Shift</kbd> + <kbd>Ctrl</kbd> + <kbd>&uarr;</kbd> | Decrease height of dialog. |
| <kbd>Shift</kbd> + <kbd>Ctrl</kbd> + <kbd>&darr;</kbd> | Increase height of dialog. |
| <kbd>Shift</kbd> + <kbd>Ctrl</kbd> + <kbd>&larr;</kbd> | Decrease width of dialog.  |
| <kbd>Shift</kbd> + <kbd>Ctrl</kbd> + <kbd>&rarr;</kbd> | Increase width of dialog.  |
