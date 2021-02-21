sealed class Type (val tk: Tk) {
    data class Any   (val tk_: Tk): Type(tk_)
    data class Unit  (val tk_: Tk): Type(tk_)
    data class Nat   (val tk_: Tk): Type(tk_)
    data class User  (val tk_: Tk): Type(tk_)
    data class Tuple (val tk_: Tk, val vec: Array<Type>): Type(tk_)
    data class Func  (val tk_: Tk, val inp: Type, val out: Type): Type(tk_)
}

sealed class Expr (val tk: Tk) {
    data class Unit  (val tk_: Tk): Expr(tk_)
    data class Int   (val tk_: Tk): Expr(tk_)
    data class Var   (val tk_: Tk): Expr(tk_)
    data class Nat   (val tk_: Tk): Expr(tk_)
    data class Empty (val tk_: Tk): Expr(tk_)
    data class Tuple (val tk_: Tk, val vec: Array<Expr>): Expr(tk_)
    data class Cons  (val tk_: Tk, val pos: Expr): Expr(tk_)
    data class Dnref (val tk_: Tk, val pre: Expr): Expr(tk_)
    data class Upref (val tk_: Tk, val pos: Expr): Expr(tk_)
    data class Index (val tk_: Tk, val pre: Expr): Expr(tk_)
    data class Pred  (val tk_: Tk, val pre: Expr): Expr(tk_)
    data class Disc  (val tk_: Tk, val pre: Expr): Expr(tk_)
    data class Call  (val tk_: Tk, val pre: Expr, val pos: Expr): Expr(tk_)
}

sealed class Stmt (val tk: Tk) {
    data class Var(val tk_: Tk, val type: Type, val init: Expr) : Stmt(tk_)
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
            all.accept(TK.UNIT)   -> Expr.Unit(all.tk0)
            all.accept(TK.XNUM)   -> Expr.Int(all.tk0)
            all.accept(TK.XVAR)   -> Expr.Var(all.tk0)
            all.accept(TK.XNAT)   -> Expr.Nat(all.tk0)
            all.accept(TK.XEMPTY) -> Expr.Empty(all.tk0)
            all.accept(TK.XUSER) -> {
                val sub = all.tk0
                val arg = parser_expr(all, false)
                when {
                    (arg != null) -> Expr.Cons(sub, arg)
                    (sub.lin==all.tk0.lin && sub.col==all.tk0.col) -> Expr.Cons(sub, Expr.Unit(all.tk0))
                    else -> null
                }
            }
            all.accept(TK.CHAR,'\\') -> {
                val tk = all.tk0
                val e = parser_expr(all,false)
                when (e) {
                    null -> null
                    is Expr.Nat, is Expr.Var, is Expr.Index -> Expr.Upref(tk,e)
                    else -> { all.err_tk(all.tk0, "unexpected operand to `\\´") ; null }
                }
            }
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

    if (canpre) {
        all.accept(TK.CALL)
    }

    var ret = one()
    if (ret == null) {
        return null
    }

    while (true) {
        val tk_bef = all.tk0
        when {
            // INDEX, DISC, PRED
            all.accept(TK.CHAR,'.') -> {
                when {
                    all.accept(TK.XNUM) -> {
                        ret = Expr.Index(all.tk0, ret!!)
                    }
                    all.accept(TK.XUSER) || all.accept(TK.XEMPTY) -> {
                        val tk = all.tk0
                        when {
                            all.accept(TK.CHAR,'?') -> ret = Expr.Pred(tk,ret!!)
                            all.accept(TK.CHAR,'!') -> ret = Expr.Disc(tk,ret!!)
                            else -> {
                                all.err_expected("`?´ or `!´")
                                return null
                            }
                        }
                    }
                    else -> {
                        all.err_expected("index or subtype")
                        return null
                    }
                }
            }
            // DNREF
            all.accept(TK.CHAR,'\\') -> when (ret) {
                is Expr.Nat, is Expr.Var, is Expr.Upref, is Expr.Dnref, is Expr.Index, is Expr.Call -> ret = Expr.Dnref(all.tk0,ret)
                else -> { all.err_tk(all.tk0, "unexpected operand to `\\´") ; return null }
            }
            // CALL
            else -> {
                var ok = false
                if (ret is Expr.Nat || ret is Expr.Var) {
                    val e = parser_expr(all, false)
                    if (e != null) {
                        ok = true
                        ret = Expr.Call(tk_bef,ret,e)
                    }
                }
                when {
                    ok -> {}
                    (tk_bef.lin==all.tk0.lin && tk_bef.col==all.tk0.col) -> break
                    else -> return null
                }
            }
        }
    }
    return ret
}

fun parser_stmt (all: All): Stmt? {
    when {
        all.accept(TK.VAR) -> {
            if (!all.accept_err(TK.XVAR)) {
                return null
            }
            val tk_id = all.tk0
            if (!all.accept_err(TK.CHAR,':')) {
                return null
            }
            val tp = parser_type(all)
            if (tp == null) {
                return null
            }
            if (!all.accept_err(TK.CHAR,'=')) {
                return null
            }
            val e = parser_expr(all, true)
            if (e == null) {
                return null
            }
            return Stmt.Var(tk_id, tp, e)
        }
        else -> { all.err_expected("statement") ; return null }
    }
}
