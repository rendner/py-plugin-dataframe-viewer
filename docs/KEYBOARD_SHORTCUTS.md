## Keyboard Shortcuts

### Table Column Width
Requires a focused table cell to use these actions.

| Shortcut             | Action                                                              |
|----------------------|---------------------------------------------------------------------|
| `.`                  | Set/unset fixedWidth marker for focused column[^1].                 |
| `Alt` + `.`          | Invert fixedWidth markers for all columns[^1].                      |
| `Alt` + `Ctrl` + `.` | Remove all fixedWidth markers.                                      |
| `+`                  | Expand focused column width by 10px, but not larger than 350px.     |
| `Alt` + `+`          | Set focused column width to 350px (large width).                    |
| `-`                  | Shrink focused column width by 10px[^2], but not smaller than 65px. |
| `Alt`+ `-`           | Set focused column width to 65px[^2] (small width).                 |

[^1]: Last column can't be set to a fixed width.

[^2]: Shrinking an unfixed column can have no effect if shrinking would reduce the width below the required column width or the width of the table viewport.

### Table Sorting
Requires a focused table cell to use these actions.

| Shortcut    | Action                                                                                 |
|-------------|----------------------------------------------------------------------------------------|
| `A`         | Sort focused column ascending. Resets all previous sorted columns.                     |
| `D`         | Sort focused column descending. Resets all previous sorted columns.                    |
| `C`         | Reset all sorted columns.                                                              |
| `Alt` + `A` | Sort focused column ascending. Previous sorted columns are not cleared (multi-sort).   |
| `Alt` + `D` | Sort focused column descending. Previous sorted columns are not cleared (multi-sort).  |
| `Alt` + `C` | Clear sorting of focused column. Previous sorted columns are not cleared (multi-sort). |


### Table Scrolling
The following keys allow navigation within the table.

| Shortcut               | Action                                                                                                   |
|------------------------|----------------------------------------------------------------------------------------------------------|
| `Up Arrow`             | Move focus one row up, stop at first row.                                                                |
| `Down Arrow`           | Move focus one row down, stop at last row.                                                               |
| `Left Arrow`           | Move focus one cell left, stop at first cell of current row.                                             |
| `Right Arrow`          | Move focus one cell right, stop at last cell of current row.                                             |
| `Page Down`            | Move focus one page down, stop at last row.                                                              |
| `Page Up`              | Move focus one page up, stop at first row.                                                               |
| `Tab`                  | Move focus one cell right, continue at the first cell of next row. If last row, continue in first row.   |
| `Shift` + `Tab`        | Move focus one cell left, continue at the last cell of previous row. If first row, continue in last row. |
| `Home`/`Pos1`          | Move focus to the leftmost cell in the row.                                                              |
| `End`                  | Move focus to the rightmost cell in the row.                                                             |
| `Ctrl` + `Home`/`Pos1` | Move focus to first row.                                                                                 |
| `Ctrl` + `End`         | Move focus to last row.                                                                                  |
| `Ctrl` + `Page Down`   | Move focus one page right, stop at last cell.                                                            |
| `Ctrl` + `Page Up`     | Move focus one page left, stop at first cell.                                                            |

### Window Actions
The following keys allow to interact with the dialog window.
>**Note:** These shortcuts depend on the used keymap and IntelliJ version.
>
> Please check your current [keymap](https://www.jetbrains.com/help/idea/settings-keymap.html) and search for `Active Tool Window Resize`.

| Shortcut                         | Action                     |
|----------------------------------|----------------------------|
| `Esc`                            | Close dialog.              |
| `Shift` + `Ctrl` + `Up Arrow`    | Decrease height of dialog. |
| `Shift` + `Ctrl` + `Down Arrow`  | Increase height of dialog. |
| `Shift` + `Ctrl` + `Left Arrow`  | Decrease width of dialog.  |
| `Shift` + `Ctrl` + `Right Arrow` | Increase width of dialog.  |
