# Validating Style Functions
*Since Version: 0.7.0*

As mentioned in [PANDAS_DATAFRAME.md](./PANDAS_DATAFRAME.md#styling-a-dataframe) you have to take some extra care to
ensure that custom styling functions behave correctly when applied via `Styler.apply`. 

The problem is, how can you recognize that displayed styled values are not correct?

One could do the following (pseudo algorithm):

- create all styled values for the `DataFrame` by using your styling function
- create all styled values for all disjunctive chunks (a chunk is a smaller 2d area of a `DataFrame`)
- compose the styled values of the chunks so that they form the same 2d shape as the `DataFrame`
- compare the composite styled values of the chunks with the styled values of the `DataFrame`

This is a lot of work, especially for a large `DataFrame`. 
And this might have to be done not only for one `DataFrame`, but for many ones to be sure that it works (depends on the complexity of the styling function).

## Support From The Plugin
The plugin provides an automatic validation for pandas `DataFrames` by using a similar algorithm as described above.

Whenever a chunk is fetched from a `DataFrame`, the plugin can validate that the used style functions return a stable styling for different sizes of chunks.
A short video to demo the feature, taken from an earlier PoC, can be found here: [Validation Demo](https://twitter.com/rendner/status/1530298351698296833?s=20&t=6wXXchcZvLfHJK5ZndpNFA)


### Things To Keep In Mind:

- Evaluation and validation of a chunk takes place on the Python side.

- The number of styling functions used (less is better) to style a `DataFrame` can greatly affect the duration of validation.
  To find faulty styling functions, the plugin applies the described algorithm to each styling function.

- Problems reported by the plugin indicate that a styling function generates different outputs for different sizes of chunks.
  However, this does not mean that you can always see such a problem in the current displayed values.
  Because, the internal validation uses smaller chunks to speed up the validation.

## Configure Validation
Validation is **disabled** by default.

> The configuration is described in [SETTINGS.md](SETTINGS.md)

## Notifications
Whenever an incompatible styling function is detected a balloon notification is displayed.
These notifications are also gathered in the *Event Log* tool window of IntelliJ and can be reviewed later.

![notification_invalid_styling_function](images/notification_invalid_styling_function.png)

The notification provides a `Show Report` and a `Copy To Clipboard` action.

### Action: Show Report
Opens a small info dialog in which the warnings/errors found are listed.

All styling functions which raised an exception during the validation process are reported as *errors*.

All styling functions that produce different output for different sizes of chunks are reported as *incompatible*.

The following information is provided to identify the function that may be faulty:

| property        | description                                                                                          |
|-----------------|------------------------------------------------------------------------------------------------------|
| reason          | A short description why a styling function is reported.                                              |
| index           | The index of the styling function in `Styler._todo`.                                                 |
| func-name       | The name of the styling function.                                                                    |
| func-qname      | The qualified name of the styling function. Only if it differs from the `func-name`.                 |
| pandasBuiltin   | True, if it is a pandas builtin styling function. Only for pandas builtin styling functions.         |
| isSupported     | True, if the styling function is supported by the plugin. Only for pandas builtin styling functions. |
| arg-chunkParent | True, if the styling function already uses the `chunk_parent` parameter.                             |
| arg-axis        | The used axis of the styling function.                                                               |

### Action: Copy To Clipboard
Copies a simplified report string into the clipboard.

This can be used to create a GitHub-Issue.
However, the information it contains alone is not sufficient for this purpose.
It would be very helpful to provide also a minimal code example.
