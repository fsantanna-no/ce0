import java.io.PushbackReader
import java.io.StringReader

data class All(
    val inp: PushbackReader,
    var tk0: Tk,
    var tk1: Tk,
    var lin: Int = 1,
    var col: Int = 1,
    var err: String = "",
)

fun All_inp2c (inp: String): Pair<Boolean,String> {
    val all = All_new(PushbackReader(StringReader(inp), 2))
    lexer(all)
    var s = parser_stmts(all, Pair(TK.EOF,null))
    //println(s)
    if (s == null) {
        return Pair(false, all.err)
    }
    s = env_prelude(s)
    val (_,err1) = env_PRV_set(s, null)
    if (err1 != null) {
        return Pair(false, err1)
    }
    //println(env_PRV)
    //println(s)
    val err2 = check_dcls(s)
    if (err2 != null) {
        return Pair(false, err2)
    }
    val err3 = check_types(s)
    if (err3 != null) {
        return Pair(false, err3)
    }
    return Pair(true, s.code())
}

fun All_new (inp: PushbackReader): All {
    return All(inp, Tk.Err(TK.ERR,1,1,""), Tk.Err(TK.ERR,1,1,""))
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
        else -> (this.tk1 as Tk.Chr).chr == chr
    }
}

fun All.consumed (tk: Tk): Boolean {
    return (tk.lin!=this.tk0.lin || tk.col!=this.tk0.col)
}

fun All.err_expected (str: String) {
    fun Tk.toPay (): String {
        return when {
            (this.enu == TK.EOF) -> "end of file"
            (this is Tk.Err)      -> '"' + this.err + '"'
            (this is Tk.Chr)     -> "`" + this.chr + "´"
            (this is Tk.Sym)     -> '`' + this.sym + '´'
            (this is Tk.Str)     -> '"' + this.str + '"'
            (this is Tk.Key)     -> this.key
            else -> { println(this); error("TODO") }
        }
    }
    this.err = "(ln ${this.tk1.lin}, col ${this.tk1.col}): expected $str : have ${this.tk1.toPay()}"
}

fun All_err_tk (tk: Tk, str: String): String {
    return "(ln ${tk.lin}, col ${tk.col}): $str"
}

fun All.err_tk (tk: Tk, str: String) {
    this.err = All_err_tk(tk,str)
}