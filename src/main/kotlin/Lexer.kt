val D = "\$"

enum class TK {
    ERR, EOF, CHAR,
    XID, XNAT, XNUM,
    UNIT, ARROW, ATBRACK,
    ACTIVE, AWAIT, AWAKE, BCAST, BREAK, CALL, CATCH, ELSE, FUNC, IF, IN, INPUT,
    LOOP, NATIVE, NEW, OUTPUT, RETURN, SET, SPAWN, TASK, TASKS, THROW, TYPE, VAR,
}

val key2tk: HashMap<String, TK> = hashMapOf (
    "active" to TK.ACTIVE,
    "await"  to TK.AWAIT,
    "awake"  to TK.AWAKE,
    "bcast"  to TK.BCAST,
    "break"  to TK.BREAK,
    "call"   to TK.CALL,
    "catch"  to TK.CATCH,
    "else"   to TK.ELSE,
    "func"   to TK.FUNC,
    "if"     to TK.IF,
    "in"     to TK.IN,
    "input"  to TK.INPUT,
    "loop"   to TK.LOOP,
    "native" to TK.NATIVE,
    "new"    to TK.NEW,
    "output" to TK.OUTPUT,
    "return" to TK.RETURN,
    "set"    to TK.SET,
    "spawn"  to TK.SPAWN,
    "task"   to TK.TASK,
    "tasks"  to TK.TASKS,
    "throw"  to TK.THROW,
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
    data class Id  (val enu_: TK, val lin_: Int, val col_: Int, val id: String):  Tk(enu_,lin_,col_)
    data class Nat (val enu_: TK, val lin_: Int, val col_: Int, val chr: Char?, val src: String): Tk(enu_,lin_,col_)
    data class Num (val enu_: TK, val lin_: Int, val col_: Int, val num: Int):    Tk(enu_,lin_,col_)
    data class Up  (val enu_: TK, val lin_: Int, val col_: Int, val up:  Int):    Tk(enu_,lin_,col_)
}

fun Tk.astype (): Tk.Id {
    val id = this as Tk.Id
    All_assert_tk(this, id.id[0].isUpperCase()) { "invalid type identifier" }
    return id
}
fun Tk.asvar (): Tk.Id {
    val id = this as Tk.Id
    All_assert_tk(this, id.id[0].isLowerCase()) { "invalid variable identifier" }
    return id
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
        else -> TODO(this.toString())
    }
}

object Lexer {
    fun blanks() {
        while (true) {
            val (c1, x1) = all.read()
            when (x1) {
                '\n', ' ' -> {
                }                // ignore line/space
                '-' -> {
                    val (c2, x2) = all.read()
                    if (x2 == '-') {            // ignore comments
                        while (true) {
                            val (c3, x3) = all.read()
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

    fun token() {
        val LIN = all.lin
        val COL = all.col

        var (c1, x1) = all.read()

        when {
            (c1 == -1) -> all.tk1 = Tk.Sym(TK.EOF, LIN, COL, "")
            (x1 in listOf(')', '{', '}', '[', ']', '<', '>', ';', ':', '=', ',', '\\', '/', '.', '!', '?')) -> {
                all.tk1 = Tk.Chr(TK.CHAR, LIN, COL, x1)
            }
            (x1 == '(') -> {
                val (c2, x2) = all.read()
                if (x2 == ')') {
                    all.tk1 = Tk.Sym(TK.UNIT, LIN, COL, "()")
                } else {
                    all.tk1 = Tk.Chr(TK.CHAR, LIN, COL, x1)
                    all.unread(c2)
                }
            }
            (x1 == '-') -> {
                val (_, x2) = all.read()
                if (x2 == '>') {
                    all.tk1 = Tk.Sym(TK.ARROW, LIN, COL, "->")
                } else {
                    all.tk1 = Tk.Err(TK.ERR, LIN, COL, "" + x1 + x2)
                }
            }
            (x1 == '@') -> {
                all.read().let { c1 = it.first; x1 = it.second }
                if (x1 == '[') {
                    all.tk1 = Tk.Sym(TK.ATBRACK, LIN, COL, "@[")
                } else {
                    all.unread(c1)
                    all.tk1 = Tk.Chr(TK.CHAR, LIN, COL, '@')
                }
            }
            (x1 == '_') -> {
                var (c2, x2) = all.read()
                var pay = ""

                var open: Char? = null
                var close: Char? = null
                var open_close = 0
                if (x2 == '(' || x2 == '{') {
                    open = x2
                    close = if (x2 == '(') ')' else '}'
                    open_close += 1
                    all.read().let { c2 = it.first; x2 = it.second }
                }

                while ((close != null || x2.isLetterOrDigit() || x2 == '_')) {
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
                    all.read().let { c2 = it.first; x2 = it.second }
                }
                if (close == null) {
                    all.unread(c2)
                }
                all.tk1 = Tk.Nat(TK.XNAT, LIN, COL, open, pay)
            }
            x1.isDigit() -> {
                var pay = ""
                do {
                    pay += x1
                    all.read().let { c1 = it.first; x1 = it.second }
                } while (x1.isDigit())
                all.unread(c1)
                all.tk1 = Tk.Num(TK.XNUM, LIN, COL, pay.toInt())
            }
            x1.isLetter() -> {
                var pay = ""
                do {
                    pay += x1
                    all.read().let { c1 = it.first; x1 = it.second }
                } while (x1.isLetterOrDigit() || x1 == '_')
                all.unread(c1)

                all.tk1 = key2tk[pay].let {
                    if (it != null) {
                        Tk.Key(it, LIN, COL, pay)
                    } else {
                        Tk.Id(TK.XID, LIN, COL, pay)
                    }
                }
            }
            else -> {
                all.tk1 = Tk.Err(TK.ERR, LIN, COL, x1.toString())
            }
        }
    }

    fun lex() {
        all.tk0 = all.tk1
        blanks()
        token()
        blanks()
    }
}
