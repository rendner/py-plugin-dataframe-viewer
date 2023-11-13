# Plugin Quick Overview
The plugin extracts information from a styled pandas `DataFrame` by using the PyCharm debugger and displays the data in a JTable (swing component).

## How Does It Work
The plugin uses the PyCharm debugger to evaluate values, as you can do during debugging.
Since it takes more than one line of code to do what it does, it uses the PyCharm debugger to inject the required code.
The code is only injected when you try to view a pandas `DataFrame` or `Styler` variable from the debugger.

## Used Programming Languages
### Kotlin
The interaction with the PyCharm debugger and visualisation of the data is written in Kotlin.

### Python 
All code which interacts with pandas is written directly in Python to have:

- code completion during development
- shorter round trips between writing and testing of code
- easier debugging of code
- better testability