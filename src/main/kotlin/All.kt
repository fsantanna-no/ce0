import java.io.PushbackReader

data class All(
    val inp: PushbackReader,
    var tk0: Tk,
    var tk1: Tk,
    var lin: Int = 1,
    var col: Int = 1,
)

fun All_new (inp: PushbackReader): All {
    return All(inp, Tk(TK.ERR,null,1,1), Tk(TK.ERR,null,1,1))
}

fun All.read (): Pair<Int,Char> {
    val i = this.inp.read()
    val c = i.toChar()
    if (c == '\n') {
        this.lin += 1
        this.col = 1
    } else if (i != -1) {
        this.col += 1
    }
    return Pair(i,c)
}

fun All.unread (i: Int) {
    this.inp.unread(i)
    if (i != -1) {
        this.col -= 1
    }
    if (i.toChar() == '\n') {
        this.lin -= 1
        //this.col = ?
    }
}