# == copy after here ==
class TableStructure:
    def __init__(self,
                 rows_count: int,
                 columns_count: int,
                 visible_columns_count: int,
                 row_levels_count: int,
                 column_levels_count: int,
                 hide_row_header: bool):
        self.rows_count = rows_count
        self.columns_count = columns_count
        self.visible_rows_count = rows_count
        self.visible_columns_count = visible_columns_count
        self.row_levels_count = row_levels_count
        self.column_levels_count = column_levels_count
        self.hide_row_header = hide_row_header
        self.hide_column_header = False
