# Plugin Quick Overview
The plugin extracts HTML from a styled pandas `DataFrame` by using the PyCharm debugger and displays the parsed HTML in a JTable (swing component).

## How Does It Work
The plugin uses the PyCharm debugger to evaluate values, as you can do during debugging.
Since it takes more than one line of code to do what it does, it uses the PyCharm debugger to inject the required code.
The code is only injected when you try to view a pandas `Styler` variable from the debugger.

Whenever the plugin is used to visualize a pandas `Styler` variable which belongs to a `DataFrame`, it uses the 
PyCharm debugger in the background to fetch the content of the styled `DataFrame`.

## Used Programming Languages
The plugin is written in Kotlin and some parts are written in Python.

### Kotlin
The interaction with the PyCharm debugger is written in Kotlin. 
Also, the parsing of the HTML output.

### Python 
All code which interacts with pandas is written directly in Python to have:

- code completion
- shorter round trips between writing and testing of code
- easier debugging of code
- better testability

Writing all parts in Kotlin would make this project unmaintainable. 

My first initial beta release of the plugin, which had fewer features at the time, was fully written in Kotlin.
And the whole code was assembled from single strings to a very long unreadable one-liner. 
Since I could only execute single-line strings with the debugger at that time.
Fortunately I discovered a little later that you can also execute more complex constructs with the debugger.