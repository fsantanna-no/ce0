import java.io.PushbackReader

data class All(
    val inp: PushbackReader,
    var tk0: Tk,
    var tk1: Tk,
    var lin: Int = 1,
    var col: Int = 1,
    var err: String = "",
)

fun All_new (inp: PushbackReader): All {
    return All(inp, Tk(TK.ERR,null,1,1), Tk(TK.ERR,null,1,1))
}

fun All.read (): Pair<Int,Char> {
    val i = this.inp.read().let { if (it == 65535) -1 else it }  // TODO: 65535??
    val c = i.toChar()
    when {
        (c == '\n') -> {
            this.lin += 1
            this.col = 1
        }
        (i != -1) -> {
            this.col += 1
        }
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

fun All.accept (enu: TK, chr: Char? = null): Boolean {
    val ret = this.check(enu, chr)
    if (ret) {
        lexer(this)
    }
    return ret
}

fun All.accept_err (enu: TK, chr: Char? = null): Boolean {
    val ret = this.accept(enu,chr)
    if (!ret) {
        this.err_expected(enu.toErr(chr))
    }
    return ret
}

fun All.check (enu: TK, chr: Char? = null): Boolean {
    return when {
        (this.tk1.enu != enu) -> false
        (chr == null)         -> true
        else -> (this.tk1.pay as TK_Chr).v == chr
    }
}

fun All.consumed (tk: Tk): Boolean {
    return (tk.lin!=this.tk0.lin || tk.col!=this.tk0.col)
}

fun All.err_expected (str: String) {
    this.err = "(ln ${this.tk1.lin}, col ${this.tk1.col}): expected $str : have ${this.tk1.toPay()}"
}

fun All.err_tk (tk: Tk, str: String) {
    this.err = "(ln ${tk.lin}, col ${tk.col}): $str"
}