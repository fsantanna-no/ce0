enum class TK {
    ERR, EOF,
    CHAR, XVAR,
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
//    data class Num (val v: Int)

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

    val c1 = all.inp.read()
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
                if (c2 == '>') {
                    all.tk1.enu = TK.ARROW
                }
            }
            else -> {
                if (x1.isLetter()) {
                    // OK
                } else {
                    all.tk1.enu = TK.ERR
                    all.tk1.pay = TK_Chr(x1)
                    return
                }
                var pay = ""
                while (x1.isLetterOrDigit() || x1=='_') {
                    pay += x1
                    x1 = all.inp.read().toChar()
                    all.col += 1
                }
                all.tk1.pay = TK_Str(pay)

                val key = key2tk[pay]
                all.tk1.enu = key ?: TK.XVAR
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