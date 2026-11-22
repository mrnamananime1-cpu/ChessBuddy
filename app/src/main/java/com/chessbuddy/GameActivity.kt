package com.chessbuddy

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.PieceType
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.Square
import com.github.bhlangonijr.chesslib.move.Move
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class GameActivity : Activity() {

    private val startFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
    private val elos = listOf(1320, 1500, 1700, 2000, 2400)

    private lateinit var engine: StockfishEngine
    private lateinit var boardView: BoardView
    private lateinit var status: TextView
    private lateinit var feedback: TextView
    private lateinit var linesView: TextView
    private lateinit var eloBtn: Button

    private val board = Board()
    private var eloIdx = 1
    private var busy = true
    private var hintLevel = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        engine = StockfishEngine(this)

        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad / 2, pad, pad / 2, pad)
        }

        status = TextView(this).apply { textSize = 18f; setPadding(pad / 2, 0, 0, pad / 2); text = "Starting engine…" }
        boardView = BoardView(this).apply {
            board = this@GameActivity.board
            onSquareTap = { handleTap(it) }
        }
        feedback = TextView(this).apply { textSize = 17f; setPadding(pad / 2, pad, 0, 0) }
        linesView = TextView(this).apply { textSize = 15f; setPadding(pad / 2, pad / 2, 0, pad / 2) }

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun btn(label: String, click: () -> Unit) = Button(this).apply {
            text = label
            setOnClickListener { click() }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        eloBtn = btn("Bot: ${elos[eloIdx]}") {
            eloIdx = (eloIdx + 1) % elos.size
            eloBtn.text = "Bot: ${elos[eloIdx]}"
        }
        row.addView(btn("New game") { newGame() })
        row.addView(eloBtn)
        row.addView(btn("💡 Hint") { hint() })

        root.addView(status)
        root.addView(boardView)
        root.addView(feedback)
        root.addView(linesView)
        root.addView(row)
        setContentView(ScrollView(this).apply { addView(root) })

        Thread {
            try {
                engine.start()
                runOnUiThread {
                    busy = false
                    status.text = "Your move (White)"
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Engine failed: ${e.message}" }
            }
        }.start()
    }

    override fun onDestroy() {
        engine.stop()
        super.onDestroy()
    }

    // ---------- helpers ----------

    private fun uci(m: Move): String {
        val promo = if (m.promotion != Piece.NONE) pieceLetter(m.promotion.pieceType).lowercase() else ""
        return m.from.value().lowercase() + m.to.value().lowercase() + promo
    }

    private fun pieceLetter(t: PieceType): String = when (t) {
        PieceType.KNIGHT -> "N"
        PieceType.BISHOP -> "B"
        PieceType.ROOK -> "R"
        PieceType.QUEEN -> "Q"
        PieceType.KING -> "K"
        else -> ""
    }

    private fun describe(b: Board, u: String): String {
        val from = Square.fromValue(u.substring(0, 2).uppercase())
        val to = Square.fromValue(u.substring(2, 4).uppercase())
        val letter = pieceLetter(b.getPiece(from).pieceType)
        val sep = if (b.getPiece(to) != Piece.NONE) "x" else "→"
        return "$letter${u.substring(0, 2)}$sep${u.substring(2, 4)}"
    }

    private fun fmt(cp: Int): String =
        if (abs(cp) >= 90000) (if (cp > 0) "M" else "-M") + (100000 - abs(cp))
        else String.format("%+.1f", cp / 100.0)

    private fun clamp(cp: Int) = max(-1000, min(1000, cp))

    private fun resultText(): String? = when {
        board.isMated -> if (board.sideToMove == Side.WHITE) "Checkmate. Bot wins 😤" else "Checkmate. You win! 🏆"
        board.isStaleMate -> "Stalemate"
        board.isDraw -> "Draw"
        else -> null
    }

    private fun label(best: String, played: String, loss: Int): String = when {
        played == best -> "Best ✅"
        loss <= 25 -> "Good 👍"
        loss <= 80 -> "Inaccuracy ?!"
        loss <= 200 -> "Mistake ❌"
        else -> "Blunder 💀"
    }

    private fun clearSelection() {
        boardView.selected = null
        boardView.targets = emptySet()
    }

    // ---------- game flow ----------

    private fun newGame() {
        if (busy && status.text.startsWith("Starting")) return
        board.loadFromFen(startFen)
        boardView.lastMove = null
        clearSelection()
        boardView.invalidate()
        feedback.text = ""
        linesView.text = ""
        hintLevel = 0
        busy = false
        status.text = "Your move (White)"
    }

    private fun handleTap(sq: Square) {
        if (busy || resultText() != null || board.sideToMove != Side.WHITE) return
        val legal = board.legalMoves()
        val sel = boardView.selected
        if (sel != null) {
            val mv = legal.firstOrNull {
                it.from == sel && it.to == sq &&
                    (it.promotion == Piece.NONE || it.promotion.pieceType == PieceType.QUEEN)
            }
            if (mv != null) {
                playUser(mv)
                return
            }
        }
        val p = board.getPiece(sq)
        if (p != Piece.NONE && p.pieceSide == Side.WHITE) {
            boardView.selected = sq
            boardView.targets = legal.filter { it.from == sq }.map { it.to }.toSet()
        } else {
            clearSelection()
        }
        boardView.invalidate()
    }

    private fun playUser(mv: Move) {
        busy = true
        hintLevel = 0
        val fenBefore = board.fen
        val played = uci(mv)
        board.doMove(mv)
        boardView.lastMove = mv
        clearSelection()
        boardView.invalidate()
        val fenAfter = board.fen
        val over = resultText()
        linesView.text = ""
        status.text = "Checking your move…"

        Thread {
            try {
                val before = engine.analyse(fenBefore, 500, 1, null)
                val bestCp = before.lines.firstOrNull()?.cp ?: 0
                                val playerCp = when {
                    over != null -> if (over.contains("You win")) 1000 else 0
                    else -> -(engine.analyse(fenAfter, 500, 1, null).lines.firstOrNull()?.cp ?: 0)
                }
                val loss = max(0, clamp(bestCp) - clamp(playerCp))
                val lbl = label(before.best, played, loss)
                val bestDesc = if (before.best.length >= 4 && before.best != played)
                    "\nBest was ${describe(Board().apply { loadFromFen(fenBefore) }, before.best)}" else ""
                runOnUiThread {
                    feedback.text = "Your move: $lbl$bestDesc"
                }

                if (over != null) {
                    runOnUiThread { status.text = over; busy = false }
                    return@Thread
                }

                runOnUiThread { status.text = "Bot is thinking…" }
                val bot = engine.analyse(fenAfter, 800, 1, elos[eloIdx])
                runOnUiThread {
                    val m = board.legalMoves().firstOrNull { uci(it) == bot.best }
                    if (m != null) {
                        board.doMove(m)
                        boardView.lastMove = m
                        boardView.invalidate()
                    }
                    val end = resultText()
                    status.text = end ?: "Your move (White)"
                    busy = false
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Engine error: ${e.message}"; busy = false }
            }
        }.start()
    }

    private fun hint() {
        if (busy || resultText() != null || board.sideToMove != Side.WHITE) return
        busy = true
        hintLevel++
        val level = hintLevel
        val fen = board.fen
        status.text = "Analysing…"
        Thread {
            try {
                val r = engine.analyse(fen, 1000, 3, null)
                runOnUiThread {
                    val top = r.lines.firstOrNull()?.pv?.firstOrNull()
                    if (top != null) {
                        if (level == 1) {
                            val from = Square.fromValue(top.substring(0, 2).uppercase())
                            val name = board.getPiece(from).pieceType.name.lowercase()
                            linesView.text = "💡 Hint 1: the best move uses your $name.\nTap Hint again to see the top 3 lines."
                        } else {
                            linesView.text = r.lines.joinToString("\n") {
                                "${it.rank}. ${describe(board, it.pv[0])}   (${fmt(it.cp)})"
                            }
                        }
                    }
                    status.text = "Your move (White)"
                    busy = false
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Engine error: ${e.message}"; busy = false }
            }
        }.start()
    }
}
