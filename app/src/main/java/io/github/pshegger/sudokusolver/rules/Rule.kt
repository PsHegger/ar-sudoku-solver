package io.github.pshegger.sudokusolver.rules

import io.github.pshegger.sudokusolver.Sudoku

interface Rule {
    fun isNumberPossible(sudoku: Sudoku, row: Int, column: Int, n: Int): Boolean
}
