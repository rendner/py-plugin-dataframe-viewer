# Settings
Settings persistently store states that control the behavior and appearance of the plugin.
Changes to the settings are not applied to `Styled DataFrame Viewer` dialogs that are already open.

To adjust the settings of the plugin, open **Preferences** of your Jetbrains IDE for macOS or **File | Settings** for Windows and Linux.
Alternatively, press <kbd>Ctrl</kbd> + <kbd>Alt</kbd> + <kbd>S</kbd>.

In the settings dialog select **Tools > Styled DataFrame Viewer**

> The plugin settings are global and apply to all existing projects of the current IntelliJ IDEA version.

![settings](images/settings/settings.png)
## Table
### Show Column DType In Header
Default: `on`

Displays the data type in the column header of the table.

## Filter input
### Use editor from internal IntelliJ API
Default: `on`

The internal editor keeps a history of entered expressions (per project).
And may have some additional features. 
In case the internal editor is not available, a simplified version is used instead.

### Code completion provided by plugin
Default: `on`

#### [pandas] Column Names
When you invoke code completion, the completion popup contains also the column names of the DataFrame.

### Runtime code completion (Python Console)
Default: `on`

The filter input for the Python Console uses runtime code completion.

Runtime code completion may lead to certain side effects, such as unintentional code execution without an explicit user input.

## Data fetching

### Validate Pandas Style Functions
Default: `off`

Allows to automatically validate used style functions whenever data is fetched from the running Python process.
> For more information read: [Validating Style Functions](PANDAS_VALIDATING_STYLE_FUNCTIONS.md)
