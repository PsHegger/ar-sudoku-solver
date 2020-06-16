package io.github.pshegger.sudokusolver.rules

import io.github.pshegger.sudokusolver.Sudoku

object RowUniqueness : Rule {
    override fun isNumberPossible(sudoku: Sudoku, row: Int, column: Int, n: Int) =
        (0 until 9).none { sudoku[row, it] == n }
}
