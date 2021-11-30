package cms.rendner.intellij.dataframe.viewer.core.html.css

data class Specificity(val a: Int = 0, val b: Int = 0, val c: Int = 0) : Comparable<Specificity> {
    // ascending: low to high
    override fun compareTo(other: Specificity): Int {
        val a = a - other.a
        if (a == 0) {
            val b = b - other.b
            if (b == 0) {
                return this.c - other.c
            }
            return b
        }
        return a
    }
}