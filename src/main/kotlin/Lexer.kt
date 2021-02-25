enum class TK {
    ERR, EOF, CHAR,
    XVAR, XUSER, XNAT, XNUM, XEMPTY,
    UNIT, ARROW,
    BREAK, CALL, ELSE, FUNC, IF, LOOP, NAT, OUT, RET, SET, TYPE, VAR
}

val key2tk: HashMap<String, TK> = hashMapOf (
    "break"  to TK.BREAK,
    "call"   to TK.CALL,
    "else"   to TK.ELSE,
    "func"   to TK.FUNC,
    "if"     to TK.IF,
    "loop"   to TK.LOOP,
    "native" to TK.NAT,
    "output" to TK.OUT,
    "return" to TK.RET,
    "set"    to TK.SET,
    "type"   to TK.TYPE,
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
}

fun TK.toErr (chr: Char?): String {
    return when (this) {
        TK.EOF   -> "end of file"
        TK.CHAR  -> "`" + chr!! + "´"
        TK.XNAT  -> "`_´"
        TK.XVAR  -> "variable identifier"
        TK.XUSER -> "type identifier"
        else -> { println(this) ; error("TODO") }
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
    if (c1 == -1) {
        all.tk1 = Tk.Sym(TK.EOF, LIN, COL, "")
    } else {
        when (x1) {
            '{' , '}' , ')' , ';' , ':' , '=' , ',' , '.' , '\\' , '!' , '?' -> {
                all.tk1 = Tk.Chr(TK.CHAR, LIN, COL, x1)
            }
            '(' -> {
                val (c2,x2) = all.read()
                if (x2 == ')') {
                    all.tk1 = Tk.Sym(TK.UNIT, LIN, COL, "()")
                } else {
                    all.tk1 = Tk.Chr(TK.CHAR, LIN, COL, x1)
                    all.unread(c2)
                }
            }
            '-' -> {
                val (_,x2) = all.read()
                when {
                    (x2 == '>')  -> all.tk1 = Tk.Sym(TK.ARROW, LIN, COL, "->")
                    x2.isDigit() -> {
                        var pay = ""+x1
                        var c3 = 0
                        var x3 = x2
                        while (x3.isLetterOrDigit()) {
                            pay += x3
                            if (x3.isDigit()) {
                                all.read().let { c3=it.first ; x3=it.second }
                            } else {
                                all.tk1 = Tk.Err(TK.ERR, LIN, COL, pay)
                                return
                            }
                        }
                        all.unread(c3)
                        all.tk1 = Tk.Num(TK.XNUM, LIN, COL, pay.toInt())
                    }
                    else -> all.tk1 = Tk.Err(TK.ERR, LIN, COL, ""+x1+x2)
                }
            }
            '_' -> {
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

                while (close!=null || x2.isLetterOrDigit() || x2=='_') {
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
            else -> {
                var pay = ""

                val isdollar = (x1 == '$')
                if (isdollar) {
                    all.read().let { c1=it.first ; x1=it.second }
                }

                if (!isdollar && x1.isDigit()) {
                    while (x1.isLetterOrDigit()) {
                        pay += x1
                        if (x1.isDigit()) {
                            all.read().let { c1=it.first ; x1=it.second }
                        } else {
                            all.tk1 = Tk.Err(TK.ERR, LIN, COL, pay)
                            return
                        }
                    }
                    all.unread(c1)
                    all.tk1 = Tk.Num(TK.XNUM, LIN, COL, pay.toInt())
                } else if (x1.isUpperCase() || (x1.isLowerCase() && !isdollar)) {
                    while (x1.isLetterOrDigit() || x1=='_') {
                        pay += x1
                        all.read().let { c1=it.first ; x1=it.second }
                    }
                    all.unread(c1)
                    val key = key2tk[pay]
                    all.tk1 = when {
                        (key != null) -> { assert(pay[0].isLowerCase()); Tk.Key(key, LIN, COL, pay) }
                        isdollar -> Tk.Str(TK.XEMPTY, LIN, COL, pay)
                        pay[0].isLowerCase() -> Tk.Str(TK.XVAR, LIN, COL, pay)
                        pay[0].isUpperCase() -> Tk.Str(TK.XUSER, LIN, COL, pay)
                        else -> error("impossible case")
                    }
                } else {
                    all.tk1 = Tk.Err(TK.ERR, LIN, COL, x1.toString())
                    return
                }
            }
        }
    }
}

fun lexer (all: All) {
    all.tk0 = all.tk1
    blanks(all)
    token(all)
    blanks(all)
}