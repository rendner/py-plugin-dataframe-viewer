# Polars
>Note: polars DataFrame support is experimental.

The plugin allows you to view Python `dicts` and polars `DataFrames`.

**Supported polars Versions:**
* tested with 0.19.15 - 0.20.23

**Features:**
- [sortable](./SORTING.md)
- [keyboard shortcuts to work efficiently](./KEYBOARD_SHORTCUTS.md)

## Data Formatting
The plugin uses the following Polars config values when generating the output of a `DataFrame`:

- [`Config.set_float_precision`](https://docs.pola.rs/py-polars/html/reference/api/polars.Config.set_float_precision.html)
- [`Config.set_fmt_str_lengths`](https://docs.pola.rs/py-polars/html/reference/api/polars.Config.set_fmt_str_lengths.html) (but maximum length of 200 chars (approximately))
- [`Config.set_fmt_table_cell_list_len`](https://docs.pola.rs/py-polars/html/reference/api/polars.Config.set_fmt_table_cell_list_len.html) (but maximum 60 items)
- [`Config.set_thousands_separator`](https://docs.pola.rs/py-polars/html/reference/api/polars.Config.set_thousands_separator.html)
