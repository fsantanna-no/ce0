sealed class Type (val tk: Tk) {
    data class Any   (val tk_: Tk): Type(tk_)
    data class Unit  (val tk_: Tk): Type(tk_)
    data class Nat   (val tk_: Tk): Type(tk_)
    data class Tuple (val tk_: Tk, val vec: Array<Type>): Type(tk_)
}

fun All.accept (enu: TK, chr: Char? = null): Boolean {
    val ret = when {
        (this.tk1.enu != enu) -> false
        (chr == null)         -> true
        else -> (this.tk1.pay as TK_Chr).v == chr
    }
    if (ret) {
        lexer(this)
    }
    return ret
}

fun All.accept_err (enu: TK, chr: Char? = null): Boolean {
    val ret = this.accept(enu,chr)
    if (!ret) {
        this.err_expected(Tk(enu,if (chr==null) null else TK_Chr(chr),0,0).toPay())
    }
    return ret
}

fun parser_type (all: All): Type? {
    return when {
        all.accept(TK.UNIT) -> Type.Unit(all.tk0)
        all.accept(TK.XNAT) -> Type.Nat(all.tk0)
        all.accept(TK.CHAR,'(') -> {
            val tp = parser_type(all)
            when {
                (tp == null)                 -> return null
                all.accept(TK.CHAR,')') -> return tp
                !all.accept_err(TK.CHAR,',') -> return null
            }
            val tps = arrayOf(tp!!)
            return Type.Tuple(all.tk0, tps)
        }
        else -> { all.err_expected("type") ; null }
    }
}