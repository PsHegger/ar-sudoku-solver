package io.github.pshegger.sudokusolver

import io.github.pshegger.sudokusolver.rules.NormalSudoku
import io.github.pshegger.sudokusolver.rules.Rule

class Sudoku(val cells: List<Int?>, private val rules: List<Rule> = listOf(NormalSudoku)) {

    init {
        if (cells.size != 81) {
            throw IllegalArgumentException("Only 9x9 Sudokus are supported")
        }
    }

    fun solve(): List<Sudoku> {
        val emptyCells = mutableListOf<Pair<Int, List<Int>>>()
        for (i in cells.indices) {
            if (cells[i] == null) {
                val possible = validNumbers(i / 9, i % 9)
                if (possible.isEmpty()) {
                    return emptyList()
                }
                if (possible.size == 1) {
                    return copyWithValue(i, possible[0]).solve()
                }
                emptyCells.add(Pair(i, possible))
            }
        }
        if (emptyCells.isEmpty()) {
            return listOf(this)
        }
        return emptyCells[0].let { (cell, possible) ->
            possible.flatMap { n -> copyWithValue(cell, n).solve() }
        }
    }

    private fun isNumberPossible(row: Int, column: Int, n: Int) =
        rules.all { it.isNumberPossible(this, row, column, n) }

    private fun validNumbers(row: Int, column: Int) = (1..9).filter { isNumberPossible(row, column, it) }

    private fun copyWithValue(cell: Int, n: Int) = Sudoku(
        cells.take(cell) + n + cells.drop(cell + 1),
        rules
    )

    operator fun get(row: Int, column: Int) = cells[row * 9 + column]
}
