import java.io.File
import java.io.PushbackReader
import java.io.StringReader

val D = "\$"

sealed class Tk (
    val enu: TK,
    val lin: Int,
    val col: Int,
) {
    data class Err (val enu_: TK, val lin_: Int, val col_: Int, val err: String): Tk(enu_,lin_,col_)
    data class Sym (val enu_: TK, val lin_: Int, val col_: Int, val sym: String): Tk(enu_,lin_,col_)
    data class Chr (val enu_: TK, val lin_: Int, val col_: Int, val chr: Char):   Tk(enu_,lin_,col_)
    data class Key (val enu_: TK, val lin_: Int, val col_: Int, val key: String): Tk(enu_,lin_,col_)
    data class Id  (val enu_: TK, val lin_: Int, val col_: Int, val id: String):  Tk(enu_,lin_,col_)
    data class Nat (val enu_: TK, val lin_: Int, val col_: Int, val chr: Char?, val src: String): Tk(enu_,lin_,col_)
    data class Num (val enu_: TK, val lin_: Int, val col_: Int, val num: Int):    Tk(enu_,lin_,col_)
    data class Clk (val enu_: TK, val lin_: Int, val col_: Int, val ms: Int):     Tk(enu_,lin_,col_)
}

fun Tk.astype (): Tk.Id {
    val id = this as Tk.Id
    All_assert_tk(this, this.istype()) { "invalid type identifier" }
    return id
}
fun Tk.istype (): Boolean {
    return (this is Tk.Id) && this.id.istype()
}
fun String.istype (): Boolean {
    return this.length>1 && this[0].isUpperCase() && this.any { it.isLowerCase() }
}

fun Tk.asvar (): Tk.Id {
    val id = this as Tk.Id
    All_assert_tk(this, this.isvar()) { "invalid variable identifier" }
    return id
}
fun Tk.isvar (): Boolean {
    return (this is Tk.Id) && this.id.isvar()
}
fun String.isvar (): Boolean {
    return this.length>0 && this[0].isLowerCase()
}

fun Tk.asscope (): Tk.Id {
    val id = this as Tk.Id
    All_assert_tk(this, id.isscopecst() || id.isscopepar()) { "invalid scope identifier" }
    return id
}
fun Tk.isscopepar (): Boolean {
    val id = this as Tk.Id
    return id.id.none { it.isUpperCase() }
}
fun Tk.asscopepar (): Tk.Id {
    All_assert_tk(this, this.isscopepar()) { "invalid scope parameter identifier" }
    return this as Tk.Id
}
fun Tk.isscopecst (): Boolean {
    val id = this as Tk.Id
    return id.id.none { it.isLowerCase() }
}
fun Tk.asscopecst (): Tk.Id {
    All_assert_tk(this, this.isscopecst()) { "invalid scope constant identifier" }
    return this as Tk.Id
}

fun Tk.Nat.toce (): String {
    val (op, cl) = when (this.chr) {
        '{' -> Pair("{", "}")
        '(' -> Pair("(", ")")
        else -> Pair("", "")
    }
    return "_" + op + this.src + cl
}

fun TK.toErr (chr: Char?): String {
    return when (this) {
        TK.EOF     -> "end of file"
        TK.CHAR    -> "`" + chr!! + "´"
        TK.XNAT    -> "`_´"
        TK.XID     -> "identifier"
        TK.XNUM    -> "number"
        TK.ARROW   -> "`->´"
        TK.ATBRACK -> "`@[´"
        TK.ELSE    -> "`else`"
        TK.IN      -> "`in`"
        TK.INPUT   -> "`input`"
        TK.TASK    -> "`task`"
        else -> TODO(this.toString())
    }
}

object Lexer {
    fun blanks() {
        while (true) {
            val (c1, x1) = alls.first().read()
            when (x1) {
                '\n', ' ' -> {
                }                // ignore line/space
                '-' -> {
                    val (c2, x2) = alls.first().read()
                    if (x2 == '-') {            // ignore comments
                        while (true) {
                            val (c3, x3) = alls.first().read()
                            if (c3 == -1) {     // EOF stops comment
                                break
                            }
                            if (x3 == '\n') {   // LN stops comment
                                alls.first().unread(c3)
                                break
                            }
                        }
                    } else {
                        alls.first().unread(c2)
                        alls.first().unread(c1)
                        return
                    }
                }
                else -> {
                    alls.first().unread(c1)
                    return
                }
            }
        }
    }

    fun lincol (): Boolean {
        var (c1,x1) = alls.first().read()
        if (x1 != '^') {
            alls.first().unread(c1)
            return false
        }
        x1 = alls.first().read().second
        when (x1) {
            '[' -> {
                x1 = alls.first().read().second
                if (x1 == ']') {
                    alls.first().stack.removeFirst()
                } else if (x1.isDigit()) {
                    fun digits (): Int {
                        assert(x1.isDigit())
                        var pay = ""
                        do {
                            pay += x1
                            alls.first().read().let { c1 = it.first; x1 = it.second }
                        } while (x1.isDigit())
                        alls.first().unread(c1)
                        return pay.toInt()
                    }

                    val lin = digits()
                    x1 = alls.first().read().second
                    if (x1 != ',')  TODO()
                    x1 = alls.first().read().second
                    if (!x1.isDigit()) TODO()
                    val col = digits()
                    x1 = alls.first().read().second
                    if (x1 != ']') TODO()
                    alls.first().stack.addFirst(Pair(lin, col))
                }
            }
            '"' -> {
                var file = ""
                val (lin,col) = alls.first().let { Pair(it.lin,it.col) }
                while (true) {
                    x1 = alls.first().read().second
                    if (x1 == '"') {
                        break
                    }
                    file += x1
                }
                assert(x1 == '"')
                val f = File(file)
                All_assert_tk(alls.first().let{Tk.Err(TK.ERR,lin,col,"")}, f.exists()) {
                    "file not found : $file"
                }
                alls.addFirst(All(file, PushbackReader(StringReader(f.readText()), 2)))
            }
            else -> TODO()
        }
        return true
    }

    fun token () {
        val LIN = alls.first().lin
        val COL = alls.first().col

        fun lin (): Int {
            return if (alls.first().stack.isEmpty()) LIN else alls.first().stack.first().first
        }
        fun col (): Int {
            return if (alls.first().stack.isEmpty()) COL else alls.first().stack.first().second
        }

        var (c1, x1) = alls.first().read()

        when {
            (c1 == -1) -> alls.first().tk1 = Tk.Sym(TK.EOF, lin(), col(), "")
            (x1 in listOf(')', '{', '}', '[', ']', '<', '>', ';', '=', ',', '\\', '/', '.', '!', '?')) -> {
                alls.first().tk1 = Tk.Chr(TK.CHAR, lin(), col(), x1)
            }
            (x1 == ':') -> {
                val (c2, x2) = alls.first().read()
                if (x2=='-' || x2=='+') {
                    alls.first().tk1 = Tk.Sym(TK.XAS, lin(), col(), ""+x1+x2)

                } else {
                    alls.first().tk1 = Tk.Chr(TK.CHAR, lin(), col(), ':')
                    alls.first().unread(c2)
                }
            }
            (x1 == '(') -> {
                val (c2, x2) = alls.first().read()
                if (x2 == ')') {
                    alls.first().tk1 = Tk.Sym(TK.UNIT, lin(), col(), "()")
                } else {
                    alls.first().tk1 = Tk.Chr(TK.CHAR, lin(), col(), x1)
                    alls.first().unread(c2)
                }
            }
            (x1 == '-') -> {
                val (_, x2) = alls.first().read()
                if (x2 == '>') {
                    alls.first().tk1 = Tk.Sym(TK.ARROW, lin(), col(), "->")
                } else {
                    alls.first().tk1 = Tk.Err(TK.ERR, lin(), col(), "" + x1 + x2)
                }
            }
            (x1 == '@') -> {
                alls.first().read().let { c1 = it.first; x1 = it.second }
                if (x1 == '[') {
                    alls.first().tk1 = Tk.Sym(TK.ATBRACK, lin(), col(), "@[")
                } else {
                    alls.first().unread(c1)
                    alls.first().tk1 = Tk.Chr(TK.CHAR, lin(), col(), '@')
                }
            }
            (x1 == '_') -> {
                var (c2, x2) = alls.first().read()
                var pay = ""

                var open: Char? = null
                var close: Char? = null
                var open_close = 0
                if (x2 == '(' || x2 == '{') {
                    open = x2
                    close = if (x2 == '(') ')' else '}'
                    open_close += 1
                    alls.first().read().let { c2 = it.first; x2 = it.second }
                }

                while ((close != null || x2.isLetterOrDigit() || x2 == '_')) {
                    if (c2 == -1) {
                        alls.first().tk1 = Tk.Err(TK.ERR, lin(), col(), "unterminated token")
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
                    alls.first().read().let { c2 = it.first; x2 = it.second }
                }
                if (close == null) {
                    alls.first().unread(c2)
                }
                alls.first().tk1 = Tk.Nat(TK.XNAT, lin(), col(), open, pay)
            }
            x1.isDigit() -> {
                fun digits (): Int {
                    assert(x1.isDigit())
                    var pay = ""
                    do {
                        pay += x1
                        alls.first().read().let { c1 = it.first; x1 = it.second }
                    } while (x1.isDigit())
                    alls.first().unread(c1)
                    return pay.toInt()
                }

                var num = digits()
                if (!x1.isLetter()) {
                    alls.first().tk1 = Tk.Num(TK.XNUM, lin(), col(), num)
                } else {
                    fun letters(): String {
                        assert(x1.isLetter())
                        var pay = ""
                        do {
                            pay += x1
                            alls.first().read().let { c1 = it.first; x1 = it.second }
                        } while (x1.isLetter())
                        alls.first().unread(c1)
                        return pay
                    }

                    var ms = 0
                    while (true) {
                        alls.first().read().let { c1 = it.first; x1 = it.second }
                        if (!x1.isLetter()) {
                            alls.first().unread(c1)
                            alls.first().tk1 = Tk.Err(TK.ERR, lin(), col(), "invalid time constant")
                            break
                        }
                        val unit = letters()
                        ms += when (unit) {
                            "ms" -> num
                            "s" -> num * 1000
                            "min" -> num * 1000 * 60
                            "h" -> num * 1000 * 60 * 60
                            else -> {
                                alls.first().tk1 = Tk.Err(TK.ERR, lin(), col(), "invalid time constant")
                                break
                            }
                        }
                        alls.first().read().let { c1 = it.first; x1 = it.second }
                        if (!x1.isDigit()) {
                            alls.first().unread(c1)
                            alls.first().tk1 = Tk.Clk(TK.XCLK, lin(), col(), ms)
                            break
                        }
                        num = digits()
                    }
                }
            }
            x1.isLetter() -> {
                var pay = ""
                do {
                    pay += x1
                    alls.first().read().let { c1 = it.first; x1 = it.second }
                } while (x1.isLetterOrDigit() || x1 == '_')
                alls.first().unread(c1)

                alls.first().tk1 = key2tk[pay].let {
                    if (it != null) {
                        Tk.Key(it, lin(), col(), pay)
                    } else {
                        Tk.Id(TK.XID, lin(), col(), pay)
                    }
                }
            }
            else -> {
                alls.first().tk1 = Tk.Err(TK.ERR, lin(), col(), x1.toString())
            }
        }
    }

    fun lex () {
        alls.first().tk0 = alls.first().tk1
        blanks(); while (lincol()) { blanks() }
        token()
        while (alls.first().tk1.enu == TK.EOF) {
            if (alls.size == 1) {
                break
            } else {
                assert(alls.size > 1)
                alls.removeFirst()
            }
            blanks(); while (lincol()) { blanks() }
            token()
        }
        blanks()
    }
}
