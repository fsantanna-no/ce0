enum class TK {
    ERR, EOF, CHAR,
    XVAR, XUSER, XNAT, XNUM, XEMPTY,
    ARROW,
    VAR, ELSE, TYPE
}

val key2tk: HashMap<String, TK> = hashMapOf (
    "var"  to TK.VAR,
    "else" to TK.ELSE,
    "type" to TK.TYPE,
)

sealed class TK_Val
data class TK_Chr (val v: Char):   TK_Val()
data class TK_Str (val v: String): TK_Val()
data class TK_Num (val v: Int):    TK_Val()

data class Tk (
    var enu: TK,
    var pay: TK_Val?,
    var lin: Int,
    var col: Int,
)

fun blanks (all: All) {
    while (true) {
        val c1 = all.inp.read()
        when (c1.toChar()) {
            '\n' -> {   // ignore new lines
                all.lin += 1
                all.col = 1
            }
            ' ' -> {    // ignore new spaces
                all.col += 1
            }
            '-' -> {
                val c2 = all.inp.read()
                if (c2.toChar() == '-') {            // ignore comments
                    all.col += 1
                    all.col += 1
                    while (true) {
                        val c3 = all.inp.read()
                        if (c3 == -1) {              // EOF stops comment
                            break
                        }
                        if (c3.toChar() == '\n') {   // LN stops comment
                            all.inp.unread(c3)
                            break
                        }
                        all.col += 1
                    }
                } else {
                    all.inp.unread(c2)
                    all.inp.unread(c1)
                    return
                }
            }
            else -> {
                all.inp.unread(c1)
                return
            }
        }
    }
}

fun token (all: All) {
    all.tk1.lin = all.lin
    all.tk1.col = all.col

    var c1 = all.inp.read()
    var x1 = c1.toChar()
    all.col += 1
    if (c1 == -1) {
        all.tk1.enu = TK.EOF
    } else {
        when (x1) {
            '{' , '}' , ')' , ';' , ':' , '=' , ',' , '.' , '\\' , '!' , '?' -> {
                all.tk1.enu = TK.CHAR
                all.tk1.pay = TK_Chr(x1)
            }
            '-' -> {
                val c2 = all.inp.read().toChar()
                all.col += 1
                if (c2 == '>') {
                    all.tk1.enu = TK.ARROW
                } else {
                    assert(false) { "TODO" }
                }
            }
            '_' -> {
                var c2 = all.inp.read()
                var x2 = c2.toChar()
                var pay = ""

                var open:  Char? = null
                var close: Char? = null
                var open_close = 0
                if (x2=='(' || x2=='{') {
                    open  = x2
                    close = if (x2=='(') ')' else '}'
                    open_close += 1
                    c2 = all.inp.read()
                    x2 = c2.toChar()
                    all.col += 1
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
                    if (x2 == '\n') {
                        all.lin += 1
                        all.col = 0
                    }
                    c2 = all.inp.read()
                    x2 = c2.toChar()
                    all.col += 1
                }
                if (close == null) {
                    all.inp.unread(c2)
                    all.col -= 1
                }
                all.tk1.pay = TK_Str(pay)
                all.tk1.enu = TK.XNAT
            }
            else -> {
                var pay = ""

                val isdollar = (x1 == '$')
                if (isdollar) {
                    c1 = all.inp.read()
                    x1 = c1.toChar()
                    all.col += 1
                }

                if (!isdollar && x1.isDigit()) {
                    while (x1.isLetterOrDigit()) {
                        if (x1.isDigit()) {
                            pay += x1
                            c1 = all.inp.read()
                            x1 = c1.toChar()
                            all.col += 1
                        } else {
                            all.tk1.enu = TK.ERR
                            all.tk1.pay = TK_Str(pay)
                            return
                        }
                    }
                    all.inp.unread(c1)
                    all.col -= 1
                    all.tk1.enu = TK.XNUM
                    all.tk1.pay = TK_Num(pay.toInt())
                } else if (x1.isUpperCase() || (x1.isLowerCase() && !isdollar)) {
                    while (x1.isLetterOrDigit() || x1=='_') {
                        pay += x1
                        c1 = all.inp.read()
                        x1 = c1.toChar()
                        all.col += 1
                    }
                    all.inp.unread(c1)
                    all.col -= 1
                    all.tk1.pay = TK_Str(pay)
                    val key = key2tk[pay]
                    all.tk1.enu = when {
                        key != null -> { assert(pay[0].isLowerCase()); key }
                        isdollar -> TK.XEMPTY
                        pay[0].isLowerCase() -> TK.XVAR
                        pay[0].isUpperCase() -> TK.XUSER
                        else -> { assert(false) { "impossible case" }; TK.ERR }
                    }
                } else {
                    all.tk1.enu = TK.ERR
                    all.tk1.pay = TK_Chr(x1)
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