package io.github.pshegger.sudokusolver.rules

import io.github.pshegger.sudokusolver.Sudoku

object BoxUniqueness : Rule {

    private val cellsInBox: Map<Pair<Int, Int>, List<Pair<Int, Int>>> =
        (0..8).flatMap { row -> (0..8).map { col -> Pair(row, col) } }.associateWith { (row, column) ->
            val rowStart = 3 * (row / 3)
            val columnStart = 3 * (column / 3)
            (rowStart..rowStart + 2).flatMap { r -> (columnStart..columnStart + 2).map { c -> Pair(r, c) } }
        }

    override fun isNumberPossible(sudoku: Sudoku, row: Int, column: Int, n: Int): Boolean =
        cellsInBox[Pair(row, column)]?.none { sudoku[it.first, it.second] == n } ?: error("Unknown coordinate")
}
