package io.github.pshegger.sudokusolver

import android.annotation.SuppressLint
import android.graphics.*
import android.widget.ImageView
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.core.content.ContextCompat
import androidx.core.text.trimmedLength
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import io.github.pshegger.sudokusolver.utils.ImageUtils
import java.io.File
import java.io.FileOutputStream
import kotlin.math.pow

class SudokuAnalyzer(private val overlay: ImageView) : ImageAnalysis.Analyzer {

    private val recognizer = TextRecognition.getClient()
    private val paint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        textSize = 32f
        color = ContextCompat.getColor(overlay.context, R.color.colorPrimaryDark)
        textAlign = Paint.Align.CENTER
    }

    private var solution: Sudoku? = null

    @SuppressLint("UnsafeExperimentalUsageError")
    override fun analyze(image: ImageProxy) {
        image.image?.let { mediaImage ->
            val img = InputImage.fromMediaImage(mediaImage, image.imageInfo.rotationDegrees)
            img.analyze { text ->
                overlay.setImageBitmap(generateOverlay(image.width, image.height, text))
                image.close()
            }
        } ?: image.close()
    }

    fun addSolution(file: File, onSuccess: (File) -> Unit) {
        val originalBmp = ImageUtils.decodeBitmap(file)
        InputImage.fromBitmap(originalBmp, 0).analyze { text ->
            val newBmp = Bitmap.createBitmap(originalBmp.width, originalBmp.height, originalBmp.config)
            val canvas = Canvas(newBmp)
            canvas.drawBitmap(originalBmp, Matrix(), null)
            canvas.drawBitmap(generateOverlay(newBmp.width, newBmp.height, text), Matrix(), null)

            FileOutputStream(file).use { stream ->
                newBmp.compress(Bitmap.CompressFormat.JPEG, 100, stream)
            }

            onSuccess(file)
        }
    }

    fun reset() {
        solution = null
    }

    private fun InputImage.analyze(onSuccess: (Text) -> Unit) {
        recognizer.process(this)
            .addOnSuccessListener { onSuccess(it) }
    }

    private fun generateOverlay(width: Int, height: Int, text: Text): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val fields = mutableListOf<Field>()

        val cellSize = bmp.width / 9f
        (0..8).forEach { row ->
            (0..8).forEach { col ->
                val cx = col * cellSize + cellSize / 2
                val cy = row * cellSize + cellSize / 2
                fields.add(Field(cx, cy, 0))
            }
        }

        text.textBlocks.forEach { block ->
            block.lines.forEach { line ->
                line.boundingBox?.let { box ->
                    val fieldWidth = box.width().toFloat() / line.text.trimmedLength()
                    line.text.trim().forEachIndexed { index, c ->
                        val n = "$c".toIntOrNull()
                        if (n != null) {
                            val cx = box.left + index * fieldWidth + fieldWidth / 2
                            val cy = box.top + (box.bottom - box.top) / 2f
                            fields.indices.minBy { i ->
                                val cell = fields[i]
                                (cx - cell.centerX).pow(2) + (cy - cell.centerY).pow(2)
                            }?.let { nearestIndex ->
                                fields[nearestIndex] = Field(cx, cy, n)
                            }
                        }
                    }
                }
            }
        }

        val bounds = calculateBounds(bmp.width, bmp.height, fields) ?: return bmp
        fields.updateEmptyPositions(bounds)

        paint.style = Paint.Style.STROKE
        canvas.drawRect(bounds, paint)

        val cells = fields.map { if (it.value != 0) it.value else null }
        val solution = solution ?: let {
            if (cells.count { it != null } < 17) return@let null
            val sudoku = Sudoku(cells)
            val solutions = sudoku.solve()
            solutions.firstOrNull()
        }
        ?: return bmp
        this.solution = solution

        val cellHeight = bounds.height() / 9
        val fontHeight = cellHeight * 0.67f
        paint.apply {
            textSize = fontHeight
            style = Paint.Style.FILL
        }
        fields.forEachIndexed { index, field ->
            if (field.value == 0) {
                canvas.drawText(
                    "${solution.cells[index]}",
                    field.centerX,
                    field.centerY + fontHeight / 2,
                    paint
                )
            }
        }

        return bmp
    }

    private fun MutableList<Field>.updateEmptyPositions(bounds: RectF) {
        val cellWidth = bounds.width() / 9
        val cellHeight = bounds.height() / 9
        indices.forEach { i ->
            val field = get(i)
            if (field.value == 0) {
                val row = i / 9
                val col = i % 9
                val cx = bounds.left + col * cellWidth + cellWidth / 2
                val cy = bounds.top + row * cellHeight + cellHeight / 2
                set(i, field.copy(centerX = cx, centerY = cy))
            }
        }
    }

    private fun calculateBounds(width: Int, height: Int, fields: List<Field>): RectF? {
        val cellXs = (0..8).map { col ->
            (0..8).mapNotNull { row ->
                val field = fields[row * 9 + col]
                if (field.value != 0) field.centerX else null
            }.average().toFloat()
        }
        val firstColX = cellXs.indexOfFirst { it != 0f && !it.isNaN() }
        val lastColX = cellXs.indexOfLast { it != 0f && !it.isNaN() }
        if (firstColX < 0 || lastColX < 0) return null
        val cellWidth = (cellXs[lastColX] - cellXs[firstColX]) / (lastColX - firstColX)
        val marginLeft = cellXs[firstColX] - firstColX * cellWidth - cellWidth / 2
        val marginRight = width - cellXs[lastColX] - ((8 - lastColX) * cellWidth) - cellWidth / 2

        val cellYs = (0..8).map { row ->
            (0..8).mapNotNull { col ->
                val field = fields[row * 9 + col]
                if (field.value != 0) field.centerY else null
            }.average().toFloat()
        }
        val firstRowY = cellYs.indexOfFirst { it != 0f && !it.isNaN() }
        val lastRowY = cellYs.indexOfLast { it != 0f && !it.isNaN() }
        if (firstRowY < 0 || lastRowY < 0) return null
        val cellHeight = (cellYs[lastRowY] - cellYs[firstRowY]) / (lastRowY - firstRowY)
        val marginTop = cellYs[firstRowY] - firstRowY * cellHeight - cellHeight / 2
        val marginBottom =
            height - cellYs[lastRowY] - ((8 - lastRowY) * cellHeight) - cellHeight / 2

        return RectF(marginLeft, marginTop, width - marginRight, height - marginBottom)
    }

    private data class Field(val centerX: Float, val centerY: Float, val value: Int)
}
