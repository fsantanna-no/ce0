sealed class Type (val tk: Tk) {
    data class Any   (val tk_: Tk): Type(tk_)
    data class Unit  (val tk_: Tk): Type(tk_)
    data class Nat   (val tk_: Tk): Type(tk_)
    data class User  (val tk_: Tk): Type(tk_)
    data class Tuple (val tk_: Tk, val vec: Array<Type>): Type(tk_)
    data class Func  (val tk_: Tk, val inp: Type, val out: Type): Type(tk_)
}

sealed class Expr (val tk: Tk) {
    data class Unit (val tk_: Tk): Expr(tk_)
    data class Var  (val tk_: Tk): Expr(tk_)
    data class Nat  (val tk_: Tk): Expr(tk_)
    data class Tuple (val tk_: Tk, val vec: Array<Expr>): Expr(tk_)
    data class Call (val tk_: Tk, val func: Expr, val arg: Expr): Expr(tk_)
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
    fun one (): Type? { // Unit, Nat, User, Tuple
        return when {
            all.accept(TK.UNIT)  -> Type.Unit(all.tk0)
            all.accept(TK.XNAT)  -> Type.Nat(all.tk0)
            all.accept(TK.XUSER) -> Type.User(all.tk0)
            all.accept(TK.CHAR,'(') -> { // Type.Tuple
                val tp = parser_type(all)
                when {
                    (tp == null)                      -> return null
                    all.accept(TK.CHAR,')')      -> return tp
                    !all.accept_err(TK.CHAR,',') -> return null
                }
                val tps = arrayListOf(tp!!)
                while (true) {
                    val tp2 = parser_type(all)
                    if (tp2 == null) {
                        return null
                    }
                    tps.add(tp2)
                    if (!all.accept(TK.CHAR,',')) {
                        break
                    }
                }
                return Type.Tuple(all.tk0, tps.toTypedArray())
            }
            else -> { all.err_expected("type") ; null }
        }
    }

    // Func: right associative
    val ret = one()
    return when {
        (ret == null) -> null
        all.accept(TK.ARROW) -> {
            val oth = parser_type(all) // right associative
            if (oth==null) null else Type.Func(all.tk0, ret, oth)
        }
        else -> ret
    }
}

fun parser_expr (all: All, canpre: Boolean): Expr? {
    fun one (): Expr? {
        return when {
            all.accept(TK.UNIT) -> Expr.Unit(all.tk0)
            all.accept(TK.XVAR) -> Expr.Var(all.tk0)
            all.accept(TK.XNAT) -> Expr.Nat(all.tk0)
            all.accept(TK.CHAR,'(') -> { // Expr.Tuple
                val e = parser_expr(all,false)
                when {
                    (e == null)                       -> return null
                    all.accept(TK.CHAR,')')      -> return e
                    !all.accept_err(TK.CHAR,',') -> return null
                }
                val es = arrayListOf(e!!)
                while (true) {
                    val e2 = parser_expr(all,false)
                    if (e2 == null) {
                        return null
                    }
                    es.add(e2)
                    if (!all.accept(TK.CHAR,',')) {
                        break
                    }
                }
                return Expr.Tuple(all.tk0, es.toTypedArray())
            }
            else -> { all.err_expected("expression") ; null }
        }
    }

    val ispre = canpre && all.accept(TK.CALL)

    var ret = one()
    if (ret == null) {
        return null
    }

    while (true) {
        val tk = all.tk0
        when {
            all.accept(TK.CHAR,'.') -> {}
            else -> {
                var ok = false
                if (ret is Expr.Nat || ret is Expr.Var) {
                    val e = parser_expr(all, false)
                    if (e != null) {
                        ok = true
                        ret = Expr.Call(tk,ret,e)
                    }
                }
                when {
                    ok -> {}
                    (tk.lin==all.tk0.lin && tk.col==all.tk0.col) -> break
                    else -> return null
                }
            }
        }
    }
    return ret
}