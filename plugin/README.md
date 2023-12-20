# Plugin Quick Overview
The plugin extracts information from a `DataFrame` by using the PyCharm debugger and displays the data in a JTable (swing component).

## How Does It Work
The plugin uses the PyCharm debugger to evaluate values, as you can do during debugging.
Since it takes more than one line of code to do what it does, it uses the PyCharm debugger to inject the required code.

## Used Programming Languages
### Kotlin
The interaction with the PyCharm debugger and visualisation of the data is written in Kotlin.

### Python 
Code to extract the data from a Python `DataFrame` is written in Python to have:

- code completion during development
- shorter round trips between writing and testing of code
- easier debugging of code
- better testability