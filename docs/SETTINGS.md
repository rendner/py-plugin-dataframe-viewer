# Settings
Settings persistently store states that control the behavior and appearance of the plugin.
Changes to the settings are not applied to `Styled DataFrame Viewer` dialogs that are already open.

To adjust the settings of the plugin, open **Preferences** of your Jetbrains IDE for macOS or **File | Settings** for Windows and Linux.
Alternatively, press <kbd>Ctrl</kbd> + <kbd>Alt</kbd> + <kbd>S</kbd>.

In the settings dialog select **Tools > Styled DataFrame Viewer**

> The plugin settings are global and apply to all existing projects of the current IntelliJ IDEA version.

![settings](images/settings/settings.png)

## Data fetching

### Validate Pandas Style Functions

Allows to automatically validate used style functions whenever data is fetched from the running Python process.
> For more information read: [Validating Style Functions](VALIDATING_STYLE_FUNCTIONS.md)

## Feature switches
Enable/disable experimental or unsupported features.
