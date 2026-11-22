package com.chessbuddy

import android.content.Context
import java.io.BufferedReader
import java.io.BufferedWriter

/** One analysed line. cp is from the side-to-move's view. Mates are +/-(100000 - movesToMate). */
class EngineLine(val rank: Int, val cp: Int, val pv: List<String>)

class EngineResult(val lines: List<EngineLine>, val best: String)

class StockfishEngine(private val ctx: Context) {

    private var proc: Process? = null
    private var out: BufferedWriter? = null
    private var inp: BufferedReader? = null

    fun start() {
        val path = ctx.applicationInfo.nativeLibraryDir + "/libstockfish.so"
        val p = ProcessBuilder(path).redirectErrorStream(true).start()
        proc = p
        out = p.outputStream.bufferedWriter()
        inp = p.inputStream.bufferedReader()
        send("uci")
        waitFor("uciok")
        send("setoption name Threads value 4")
        send("setoption name Hash value 128")
        send("isready")
        waitFor("readyok")
    }

    private fun send(cmd: String) {
        out?.apply {
            write(cmd)
            newLine()
            flush()
        }
    }

    private fun readLine(): String =
        inp?.readLine() ?: throw IllegalStateException("Engine closed")

    private fun waitFor(token: String) {
        while (true) {
            if (readLine().startsWith(token)) return
        }
    }

    /** limitElo = null means full strength. Call from a background thread. */
    @Synchronized
    fun analyse(fen: String, moveTimeMs: Int, multiPv: Int, limitElo: Int?): EngineResult {
        send("setoption name MultiPV value $multiPv")
        if (limitElo != null) {
            send("setoption name UCI_LimitStrength value true")
            send("setoption name UCI_Elo value $limitElo")
        } else {
            send("setoption name UCI_LimitStrength value false")
        }
        send("position fen $fen")
        send("go movetime $moveTimeMs")

        val map = sortedMapOf<Int, EngineLine>()
        var best = ""
        while (true) {
            val line = readLine()
            if (line.startsWith("bestmove")) {
                best = line.split(" ").getOrElse(1) { "" }
                break
            }
            if (!line.startsWith("info")) continue
            val t = line.split(" ")
            val si = t.indexOf("score")
            val pi = t.indexOf("pv")
            if (si < 0 || pi < 0) continue
            val mi = t.indexOf("multipv")
            val rank = if (mi >= 0) t[mi + 1].toInt() else 1
            val v = t[si + 2].toInt()
            val cp = if (t[si + 1] == "cp") v else if (v > 0) 100000 - v else -100000 - v
            map[rank] = EngineLine(rank, cp, t.drop(pi + 1))
        }
        return EngineResult(map.values.toList(), best)
    }

    fun stop() {
        try {
            send("quit")
        } catch (_: Exception) {
        }
        proc?.destroy()
    }
}
