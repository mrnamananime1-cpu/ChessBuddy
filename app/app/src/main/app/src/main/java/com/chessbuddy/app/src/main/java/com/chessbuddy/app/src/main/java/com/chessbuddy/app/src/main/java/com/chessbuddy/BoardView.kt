package com.chessbuddy

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.PieceType
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.Square
import com.github.bhlangonijr.chesslib.move.Move

class BoardView(ctx: Context) : View(ctx) {

    var board: Board = Board()
    var selected: Square? = null
    var targets: Set<Square> = emptySet()
    var lastMove: Move? = null
    var onSquareTap: ((Square) -> Unit)? = null

    private val darkSq = Color.parseColor("#769656")
    private val lightSq = Color.parseColor("#EEEED2")
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    private fun glyph(t: PieceType): String = when (t) {
        PieceType.KING -> "\u265A"
        PieceType.QUEEN -> "\u265B"
        PieceType.ROOK -> "\u265C"
        PieceType.BISHOP -> "\u265D"
        PieceType.KNIGHT -> "\u265E"
        PieceType.PAWN -> "\u265F"
        else -> ""
    } + "\uFE0E" // force text style, not emoji

    override fun onMeasure(w: Int, h: Int) {
        val s = MeasureSpec.getSize(w)
        setMeasuredDimension(s, s)
    }

    override fun onDraw(c: Canvas) {
        val s = width / 8f
        text.textSize = s * 0.8f
        val baseOffset = -(text.descent() + text.ascent()) / 2f

        for (r in 0..7) for (f in 0..7) {
            val sq = Square.squareAt(r * 8 + f)
            val x = f * s
            val y = (7 - r) * s

            fill.style = Paint.Style.FILL
            fill.color = if ((r + f) % 2 == 0) darkSq else lightSq
            c.drawRect(x, y, x + s, y + s, fill)

            val lm = lastMove
            if (lm != null && (sq == lm.from || sq == lm.to)) {
                fill.color = Color.argb(90, 255, 235, 59)
                c.drawRect(x, y, x + s, y + s, fill)
            }
            if (sq == selected) {
                fill.color = Color.argb(140, 255, 235, 59)
                c.drawRect(x, y, x + s, y + s, fill)
            }

            val p = board.getPiece(sq)
            if (p != Piece.NONE) {
                val g = glyph(p.pieceType)
                val cy = y + s / 2f + baseOffset
                if (p.pieceSide == Side.WHITE) {
                    text.style = Paint.Style.STROKE
                    text.strokeWidth = s * 0.05f
                    text.color = Color.BLACK
                    c.drawText(g, x + s / 2f, cy, text)
                    text.style = Paint.Style.FILL
                    text.color = Color.WHITE
                    c.drawText(g, x + s / 2f, cy, text)
                } else {
                    text.style = Paint.Style.FILL
                    text.color = Color.BLACK
                    c.drawText(g, x + s / 2f, cy, text)
                }
            }

            if (sq in targets) {
                fill.color = Color.argb(110, 0, 0, 0)
                c.drawCircle(x + s / 2f, y + s / 2f, s * 0.14f, fill)
            }
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action == MotionEvent.ACTION_DOWN) return true
        if (e.action == MotionEvent.ACTION_UP) {
            val s = width / 8f
            val f = (e.x / s).toInt()
            val r = 7 - (e.y / s).toInt()
            if (f in 0..7 && r in 0..7) onSquareTap?.invoke(Square.squareAt(r * 8 + f))
            return true
        }
        return super.onTouchEvent(e)
    }
}
