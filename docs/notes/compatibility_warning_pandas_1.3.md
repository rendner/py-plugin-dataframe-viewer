# Parts of the plugin will not longer work starting with upcoming [pandas version 1.3](https://github.com/pandas-dev/pandas/milestone/80)

I had no time to test the plugin with this upcoming version yet.
What I can already say clearly is that with this version the following builtin styles are no longer work:

* `highlight_max`
* `highlight_min`

## Reason
Some builtin style methods like `highlight_max` and `highlight_min` are patched by the plugin when the plugin uses chunks to process a `DataFrame`.
Otherwise, these style methods would result in a wrong result. A very detailed example why they have to be patched can be found under [Handle Chunks In Your Python Code](https://github.com/rendner/py-plugin-dataframe-viewer#handle-chunks-in-your-python-code)
which also uses a custom `highlight_max` function to illustrate the problem.

In pandas the `highlight_max` and `highlight_min` method use an internal method `_highlight_extrema`. To detect a `highlight_max` or `highlight_min` call,
the plugin checks all style functions of the `Styler` for a function object named `_highlight_extrema` which belongs to the class `Styler`. If it finds such a function, the call is replaced by a patched version.

This check will not work anymore. There was a change in the Pandas repo which simplified the existing builtin methods of the `Styler`: [CLN: Styler simplify existing builtin methods](https://github.com/pandas-dev/pandas/pull/39797)
which removed the internal method `_highlight_extrema`.

Since the plugin can no longer find this method, it will treat the named builtin methods as user-defined style functions.
Which in case of `highlight_max` and `highlight_min` leads to wrong highlighted values, because the min/max value is taken from the single chunks and not from the whole `Series` or `DataFrame`.