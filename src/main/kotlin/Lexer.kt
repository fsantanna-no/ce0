enum class TK {
    ERR, EOF, CHAR, ARROW
}

sealed class TK_Val
data class TK_Chr (val v: Char): TK_Val()
//    data class Str (val v: String)
//    data class Num (val v: Int)

data class Tk (
    var enu: TK,
    var pay: TK_Val?,
    var lin: Int,
    var col: Int
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
    all.col += 1
    if (c1 == -1) {
        all.tk1.enu = TK.EOF
    } else {
        when (c1.toChar()) {
            '{' , '}' , ')' , ';' , ':' , '=' , ',' , '.' , '\\' , '!' , '?' -> {
                all.tk1.enu = TK.CHAR
                all.tk1.pay = TK_Chr(c1.toChar())
            }
            '-' -> {
                val c2 = all.inp.read().toChar()
                if (c2 == '>') {
                    all.tk1.enu = TK.ARROW
                }
            }
            else -> assert(false) { "TODO" }
        }
    }
}

fun lexer (all: All) {
    all.tk0 = all.tk1
    blanks(all)
    token(all)
    blanks(all)
}