# Filtering
> New in version 0.9.0.

A common operation in data analysis is to filter values based on a condition or multiple conditions. 
Pandas provides a variety of ways to filter a `DataFrame`.

The filter feature in the plugin doesn't use pandas [DataFrame.filter](https://pandas.pydata.org/docs/reference/api/pandas.DataFrame.filter.html).
Because this method doesn't allow to filter a `DataFrame` on its content. Instead, you can provide a Python expression which returns a
`DataFrame`. The intersection of the index and columns of the returned `DataFrame` and the viewed `DataFrame` are displayed.
The returned `DataFrame` defines which values should be displayed, others are filtered out.

> Note: The filtering of the styled `DataFrame` is done after applying the styling but by using the unformatted values of the `DataFrame`.
> Technically it is implemented in another way, but it behaves like this.
> 
> In case you use [Styler.highlight_max](https://pandas.pydata.org/docs/reference/api/pandas.io.formats.style.Styler.highlight_max.html) and filter out
> all max values, none of the displayed values will be highlighted as max value.


## Example
Let's start with a simple example to demonstrate how you can filter your data.
Paste the code below into a new Python file and start the debugger:
```python
import pandas as pd

df = pd.DataFrame({
    'name': ['Jan', 'Regina', 'Ben', 'Finn'],
    'age': [42, 29, 35, 99],
})

breakpoint()
```
Select the variable `df` in the debugger tab.
Right-click on `df` to open the context menu. Select `View as Styled DataFrame`.

This is the result of the code snippet:

![initial_state](images/filtering/initial_state.png)

As initial noted, the filtering doesn't use pandas [DataFrame.filter](https://pandas.pydata.org/docs/reference/api/pandas.DataFrame.filter.html)
but you can use the method in your filter expression.
Or any other expression as long as it returns a `DataFrame`:

![filter_by_dataframe_filter](images/filtering/filter_by_dataframe_filter.png)
![filter_by_logical_operator](images/filtering/filter_by_logical_operator.png)
![filter_by_tilde](images/filtering/filter_by_tilde.png)

## The Table State
Filtering can modify the visual state of the table displayed in the dialog.
Each time a new filter result is displayed, the plugin tries to keep the state of the columns and the 
scroll positions of the previously displayed result.

Table columns which where present before the applied filter will have the same width and sort state.
If you accidentally filter out a column that had a sort status, that status is lost.
Even if you correct the filter to add this column back.

Filtering out columns that have a sorting status leads to an update of the sorting.
This sorting contains only the columns that have a sorting status and were not filtered out.

The scroll position is not reset when updating the table with the new filter result.
So it may happen that previously visible columns, which were not excluded from the filter,
are located at a different position in the table because columns to the left of these columns were filtered out.

## The Filter Input
The filter input is a simplified editor component with syntax highlighting support, code completion and error reporting.

![code_completion_and_errors](images/filtering/code_completion_and_errors.gif)

You can apply your filter by clicking on the `Apply Filter` button or by pressing the <kbd>Enter</kbd>-key.

### Long Filter Queries
Since the filter input is only a single line it can be tedious to enter expressions with long variable names:

![long_query_long](images/filtering/long_query_long.png)

In such a case you can use the special identifier `_df`, which is only available if there is no other variable named `_df`.

![long_query_short](images/filtering/long_query_short.png)

The identifier `_df` is rendered grayed out to indicate that this is the special identifier provided by the plugin.
It is also provided in the code completion of the filter input.
