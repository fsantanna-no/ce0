enum class TK {
    ERR, EOF, CHAR,
    XVAR, XNAT, XNUM, XUP, XSCOPE,
    UNIT, ARROW,
    BREAK, CALL, ELSE, FUNC,
    IF, INPUT, LOOP, NATIVE, NEW, OUTPUT, RETURN, SET, VAR,
}

val key2tk: HashMap<String, TK> = hashMapOf (
    "break"  to TK.BREAK,
    "call"   to TK.CALL,
    "else"   to TK.ELSE,
    "func"   to TK.FUNC,
    "if"     to TK.IF,
    "input"  to TK.INPUT,
    "loop"   to TK.LOOP,
    "native" to TK.NATIVE,
    "new"    to TK.NEW,
    "output" to TK.OUTPUT,
    "return" to TK.RETURN,
    "set"    to TK.SET,
    "var"    to TK.VAR,
)

sealed class Tk (
    val enu: TK,
    val lin: Int,
    val col: Int,
) {
    data class Err (val enu_: TK, val lin_: Int, val col_: Int, val err: String): Tk(enu_,lin_,col_)
    data class Sym (val enu_: TK, val lin_: Int, val col_: Int, val sym: String): Tk(enu_,lin_,col_)
    data class Chr (val enu_: TK, val lin_: Int, val col_: Int, val chr: Char):   Tk(enu_,lin_,col_)
    data class Key (val enu_: TK, val lin_: Int, val col_: Int, val key: String): Tk(enu_,lin_,col_)
    data class Str (val enu_: TK, val lin_: Int, val col_: Int, val str: String): Tk(enu_,lin_,col_)
    data class Num (val enu_: TK, val lin_: Int, val col_: Int, val num: Int):    Tk(enu_,lin_,col_)
    data class Up  (val enu_: TK, val lin_: Int, val col_: Int, val up:  Int):    Tk(enu_,lin_,col_)
    data class Scope (val enu_: TK, val lin_: Int, val col_: Int, val lbl: String, val num: Int?):Tk(enu_,lin_,col_)
}

fun TK.toErr (chr: Char?): String {
    return when (this) {
        TK.EOF    -> "end of file"
        TK.CHAR   -> "`" + chr!! + "´"
        TK.XNAT   -> "`_´"
        TK.XVAR   -> "variable identifier"
        TK.XNUM   -> "number"
        TK.ARROW  -> "`->´"
        TK.XSCOPE -> "`@´"
        else -> TODO(this.toString())
    }
}

fun blanks (all: All) {
    while (true) {
        val (c1,x1) = all.read()
        when (x1) {
            '\n', ' ' -> { }                // ignore line/space
            '-' -> {
                val (c2,x2) = all.read()
                if (x2 == '-') {            // ignore comments
                    while (true) {
                        val (c3,x3) = all.read()
                        if (c3 == -1) {     // EOF stops comment
                            break
                        }
                        if (x3 == '\n') {   // LN stops comment
                            all.unread(c3)
                            break
                        }
                    }
                } else {
                    all.unread(c2)
                    all.unread(c1)
                    return
                }
            }
            else -> {
                all.unread(c1)
                return
            }
        }
    }
}

fun token (all: All) {
    val LIN = all.lin
    val COL = all.col

    var (c1,x1) = all.read()

    fun lowers (): String {
        var pay = ""
        do {
            pay += x1
            all.read().let { c1=it.first ; x1=it.second }
        } while (x1.isLetterOrDigit() || x1=='_')
        all.unread(c1)
        return pay
    }

    when {
        (c1 == -1) -> all.tk1 = Tk.Sym(TK.EOF, LIN, COL, "")
        (x1 in arrayOf(')', '{', '}', '[', ']', '<' , '>' , ';' , ':' , '=' , ',' , '\\', '/' , '.', '!' , '?')) -> {
            all.tk1 = Tk.Chr(TK.CHAR, LIN, COL, x1)
        }
        (x1 == '^') -> {
            var n = 0
            while (x1 == '^') {
                n++
                all.read().let { c1=it.first ; x1=it.second }
            }
            all.unread(c1)
            all.tk1 = Tk.Up(TK.XUP, LIN, COL, n)
        }
        (x1 == '@') -> {
            var lbl = ""
            all.read().let { c1=it.first ; x1=it.second }
            while (x1.isLowerCase()) {
                lbl += x1
                all.read().let { c1=it.first ; x1=it.second }
            }

            var num: Int? = null
            if (x1 == '_') {
                var num_ = ""
                all.read().let { c1=it.first ; x1=it.second }
                if (!x1.isDigit()) {
                    all.unread(c1)
                    all.tk1 = Tk.Err(TK.ERR, LIN, COL, "@${lbl}_$x1")
                    return
                }
                while (x1.isDigit()) {
                    num_ += x1
                    all.read().let { c1=it.first ; x1=it.second }
                }
                num = num_.toInt()
            }

            all.unread(c1)
            all.tk1 = Tk.Scope(TK.XSCOPE, LIN, COL, lbl, num)
        }
        (x1 == '(') -> {
            val (c2,x2) = all.read()
            if (x2 == ')') {
                all.tk1 = Tk.Sym(TK.UNIT, LIN, COL, "()")
            } else {
                all.tk1 = Tk.Chr(TK.CHAR, LIN, COL, x1)
                all.unread(c2)
            }
        }
        (x1 == '-') -> {
            val (_,x2) = all.read()
            if (x2 == '>') {
                all.tk1 = Tk.Sym(TK.ARROW, LIN, COL, "->")
            } else {
                all.tk1 = Tk.Err(TK.ERR, LIN, COL, ""+x1+x2)
            }
        }
        (x1 == '_') -> {
            var (c2,x2) = all.read()
            var pay = ""

            var open:  Char? = null
            var close: Char? = null
            var open_close = 0
            if (x2=='(' || x2=='{') {
                open  = x2
                close = if (x2=='(') ')' else '}'
                open_close += 1
                all.read().let { c2=it.first ; x2=it.second }
            }

            while ((close!=null || x2.isLetterOrDigit() || x2=='_')) {
                if (c2 == -1) {
                    all.tk1 = Tk.Err(TK.ERR, LIN, COL, "unterminated token")
                    return
                }
                if (x2 == open) {
                    open_close += 1
                } else if (x2 == close) {
                    open_close -= 1
                    if (open_close == 0) {
                        break
                    }
                }
                pay += x2
                all.read().let { c2=it.first ; x2=it.second }
            }
            if (close == null) {
                all.unread(c2)
            }
            all.tk1 = Tk.Str(TK.XNAT, LIN, COL, pay)
        }
        x1.isDigit() -> {
            var pay = ""
            do {
                pay += x1
                all.read().let { c1=it.first ; x1=it.second }
            } while (x1.isDigit())
            all.unread(c1)
            all.tk1 = Tk.Num(TK.XNUM, LIN, COL, pay.toInt())
        }
        x1.isLowerCase() -> {
            var pay = lowers()
            all.tk1 = key2tk[pay].let {
                if (it != null) Tk.Key(it, LIN, COL, pay) else Tk.Str(TK.XVAR, LIN, COL, pay)
            }
        }
        else -> {
            all.tk1 = Tk.Err(TK.ERR, LIN, COL, x1.toString())
        }
    }
}

fun lexer (all: All) {
    all.tk0 = all.tk1
    blanks(all)
    token(all)
    blanks(all)
}
