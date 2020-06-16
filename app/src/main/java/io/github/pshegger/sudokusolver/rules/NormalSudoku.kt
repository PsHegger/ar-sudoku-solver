package io.github.pshegger.sudokusolver.rules

import io.github.pshegger.sudokusolver.Sudoku

object NormalSudoku : Rule {

    private val rules = listOf(
        ColumnUniqueness, RowUniqueness, BoxUniqueness
    )

    override fun isNumberPossible(sudoku: Sudoku, row: Int, column: Int, n: Int): Boolean =
        rules.all { it.isNumberPossible(sudoku, row, column, n) }
}
