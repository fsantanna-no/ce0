sealed class Type (val tk: Tk) {
    data class Any   (val tk_: Tk.Chr): Type(tk_)
    data class Unit  (val tk_: Tk.Sym): Type(tk_)
    data class Nat   (val tk_: Tk.Str): Type(tk_)
    data class User  (val tk_: Tk.Str): Type(tk_)
    data class Tuple (val tk_: Tk.Chr, val vec: Array<Type>): Type(tk_)
    data class Func  (val tk_: Tk.Sym, val inp: Type, val out: Type): Type(tk_)
    data class Ptr   (val tk_: Tk.Chr, val tp: Type): Type(tk_)
}

//typealias XEpr = Pair<Tk,Expr>
class XExpr (val x: Tk?, val e: Expr)

sealed class Expr (val tk: Tk) {
    data class Unk   (val tk_: Tk.Chr): Expr(tk_)
    data class Unit  (val tk_: Tk.Sym): Expr(tk_)
    data class Int   (val tk_: Tk.Num): Expr(tk_)
    data class Var   (val tk_: Tk.Str): Expr(tk_)
    data class Nat   (val tk_: Tk.Str): Expr(tk_)
    data class Tuple (val tk_: Tk.Chr, val vec: Array<XExpr>): Expr(tk_)
    data class Cons  (val tk_: Tk.Chr, val sup: Tk.Str, val sub: Tk.Str, val arg: Expr): Expr(tk_)
    data class Dnref (val tk_: Tk, val e: Expr): Expr(tk_)
    data class Upref (val tk_: Tk.Chr, val e: Expr): Expr(tk_)
    data class Index (val tk_: Tk.Num, val e: Expr): Expr(tk_)
    data class Pred  (val tk_: Tk.Str, val e: Expr): Expr(tk_)
    data class Disc  (val tk_: Tk.Str, val e: Expr): Expr(tk_)
    data class Call  (val tk_: Tk.Key, val f: Expr, val arg: XExpr): Expr(tk_)
}

sealed class Attr (val tk: Tk) {
    data class Var   (val tk_: Tk.Str): Attr(tk_)
    data class Nat   (val tk_: Tk.Str): Attr(tk_)
    data class Dnref (val tk_: Tk, val e: Attr): Attr(tk_)
    data class Index (val tk_: Tk.Num, val e: Attr): Attr(tk_)
    data class Disc  (val tk_: Tk.Str, val e: Attr): Attr(tk_)
}

fun Attr.toExpr (): Expr {
    return when (this) {
        is Attr.Var   -> Expr.Var(this.tk_)
        is Attr.Nat   -> Expr.Nat(this.tk_)
        is Attr.Dnref -> Expr.Dnref(this.tk_,this.e.toExpr())
        is Attr.Index -> Expr.Index(this.tk_,this.e.toExpr())
        is Attr.Disc  -> Expr.Disc(this.tk_,this.e.toExpr())
    }
}

sealed class Stmt (val tk: Tk) {
    data class Pass  (val tk_: Tk) : Stmt(tk_)
    data class Var   (val tk_: Tk.Str, val outer: Boolean, val type: Type, val init: XExpr) : Stmt(tk_)
    data class Set   (val tk_: Tk.Chr, val dst: Attr, val src: XExpr) : Stmt(tk_)
    data class User  (val tk_: Tk.Str, val isrec: Boolean, val subs: Array<Pair<Tk.Str,Type>>) : Stmt(tk_)
    data class Nat   (val tk_: Tk.Str) : Stmt(tk_)
    data class Call  (val tk_: Tk.Key, val call: Expr.Call) : Stmt(tk_)
    data class Seq   (val tk_: Tk, val s1: Stmt, val s2: Stmt) : Stmt(tk_)
    data class If    (val tk_: Tk.Key, val tst: Expr, val true_: Block, val false_: Block) : Stmt(tk_)
    data class Func  (val tk_: Tk.Str, val type: Type.Func, val block: Block?) : Stmt(tk_)
    data class Ret   (val tk_: Tk.Key, val e: XExpr) : Stmt(tk_)
    data class Loop  (val tk_: Tk.Key, val block: Block) : Stmt(tk_)
    data class Break (val tk_: Tk.Key) : Stmt(tk_)
    data class Block (val tk_: Tk.Chr, val body: Stmt) : Stmt(tk_)
}

fun parser_type (all: All): Type {
    fun one (): Type { // Unit, Nat, User, Tuple
        return when {
            all.accept(TK.UNIT)  -> Type.Unit(all.tk0 as Tk.Sym)
            all.accept(TK.XNAT)  -> Type.Nat(all.tk0 as Tk.Str)
            all.accept(TK.XUSER) -> Type.User(all.tk0 as Tk.Str)
            all.accept(TK.CHAR,'\\') -> {
                val tk0 = all.tk0 as Tk.Chr
                Type.Ptr(tk0, one())
            }
            all.accept(TK.CHAR,'(') -> { // Type.Tuple
                val tk0 = all.tk0 as Tk.Chr
                val tp = parser_type(all)
                if (all.accept(TK.CHAR,')')) {
                    return tp
                }
                all.accept_err(TK.CHAR,',')
                val tps = arrayListOf(tp)
                while (true) {
                    val tp2 = parser_type(all)
                    tps.add(tp2)
                    if (!all.accept(TK.CHAR,',')) {
                        break
                    }
                }
                all.accept_err(TK.CHAR,')')
                Type.Tuple(tk0, tps.toTypedArray())
            }
            else -> {
                all.err_expected("type")
                error("unreachable")
            }
        }
    }

    // Func: right associative
    val ret = one()
    return when {
        all.accept(TK.ARROW) -> {
            val tk0 = all.tk0 as Tk.Sym
            val oth = parser_type(all) // right associative
            Type.Func(tk0, ret, oth)
        }
        else -> ret
    }
}

fun parser_xexpr (all: All, canpre: Boolean): XExpr {
    val tk = if (all.accept(TK.BORROW) || all.accept(TK.COPY) || all.accept(TK.MOVE)) all.tk0 else null
    val e = parser_expr(all,canpre)
    return XExpr(tk, e)
}

fun parser_expr (all: All, canpre: Boolean): Expr {
    fun one (): Expr {
        return when {
            all.accept(TK.UNIT) -> Expr.Unit(all.tk0 as Tk.Sym)
            all.accept(TK.XNUM) -> Expr.Int(all.tk0 as Tk.Num)
            all.accept(TK.XVAR) -> Expr.Var(all.tk0 as Tk.Str)
            all.accept(TK.XNAT) -> Expr.Nat(all.tk0 as Tk.Str)
            all.accept(TK.CHAR,'?') -> Expr.Unk(all.tk0 as Tk.Chr)
            all.accept(TK.XUSER) -> {
                val sup = all.tk0 as Tk.Str
                all.accept_err(TK.CHAR,'.')
                val tk0 = all.tk0 as Tk.Chr
                all.accept_err(TK.XUSER)
                val sub = all.tk0 as Tk.Str
                try {
                    val arg = parser_expr(all, false)
                    Expr.Cons(tk0, sup, sub, arg)
                } catch (e: Throwable) {
                    assert(!all.consumed(sub)) {
                        e.message!!
                    }
                    Expr.Cons(tk0, sup, sub, Expr.Unit(Tk.Sym(TK.UNIT,all.tk1.lin,all.tk1.col,"()")))
                }
            }
            all.accept(TK.CHAR,'\\') -> {
                val tk0 = all.tk0 as Tk.Chr
                val e = parser_expr(all,false)
                all.assert_tk(all.tk0, e is Expr.Nat || e is Expr.Var || e is Expr.Index || e is Expr.Dnref || e is Expr.Upref || e is Expr.Disc) {
                    "unexpected operand to `\\´"
                }
                Expr.Upref(tk0,e)
            }
            all.accept(TK.CHAR,'/') -> {
                val tk0 = all.tk0 as Tk.Chr
                val e = parser_expr(all,false)
                all.assert_tk(all.tk0, e is Expr.Nat || e is Expr.Var || e is Expr.Index || e is Expr.Dnref || e is Expr.Upref || e is Expr.Disc || e is Expr.Call) {
                    "unexpected operand to `/´"
                }
                Expr.Dnref(tk0,e)
            }
            all.accept(TK.CHAR,'(') -> { // Expr.Tuple
                val tk0 = all.tk0 as Tk.Chr
                val e = parser_xexpr(all, false)
                if (all.accept(TK.CHAR,')')) {
                    if (e.x != null) {
                        all.assert_tk(e.x, false) { "unexpected modifier" }
                    }
                    return e.e
                }
                all.accept_err(TK.CHAR,',')
                val es = arrayListOf(e)
                while (true) {
                    val e2 = parser_xexpr(all, false)
                    es.add(e2)
                    if (!all.accept(TK.CHAR,',')) {
                        break
                    }
                }
                all.accept_err(TK.CHAR,')')
                return Expr.Tuple(tk0, es.toTypedArray())
            }
            else -> {
                all.err_expected("expression")
                error("unreachable")
            }
        }
    }

    fun dots (): Expr {
        var ret = one()
        while (all.accept(TK.CHAR,'.')) {
            ret = when {
                all.accept(TK.XNUM)  -> Expr.Index(all.tk0 as Tk.Num, ret)
                all.accept(TK.XUSER) -> {
                    val tk = all.tk0 as Tk.Str
                    when {
                        all.accept(TK.CHAR, '?') -> Expr.Pred(tk, ret)
                        all.accept(TK.CHAR, '!') -> Expr.Disc(tk, ret)
                        else -> {
                            all.err_expected("`?´ or `!´")
                            error("unreachable")
                        }
                    }
                }
                else -> {
                    all.err_expected("index or subtype")
                    error("unreachable")
                }
            }
        }
        return ret
    }
    fun call (ispre: Boolean): Expr {
        val tk_pre = all.tk0
        var e1 = dots()

        val tk_bef = all.tk0
        val e2 = try {
            parser_xexpr(all, false)
        } catch (e: Throwable) {
            assert(!all.consumed(tk_bef)) {
                e.message!! // failed parser_expr and consumed input: error)
            }
            if (!ispre) {
                return e1
            }
            // call f -> call f ()
            XExpr(null, Expr.Unit(Tk.Sym(TK.UNIT,all.tk1.lin,all.tk1.col,"()")))
        }

        all.assert_tk(e1.tk, e1 is Expr.Var || (e1 is Expr.Nat && (!ispre || tk_pre.enu==TK.CALL))) {
            "expected function"
        }
        val tk_pre2 = if (ispre) tk_pre as Tk.Key else Tk.Key(TK.CALL,tk_pre.lin,tk_pre.col,"call")
        if (ispre && tk_pre2.enu==TK.OUT) {
            assert(e1 is Expr.Var)
            e1 = Expr.Var (
                Tk.Str(TK.XVAR,e1.tk.lin,e1.tk.col,"output_"+(e1.tk as Tk.Str).str)
            )
        }
        return Expr.Call(tk_pre2, e1, e2)
    }

    val ispre = (canpre && (all.accept(TK.CALL) || all.accept(TK.OUT)))
    return call(ispre)
}

fun parser_attr (all: All): Attr {
    fun one (): Attr {
        return when {
            all.accept(TK.XVAR) -> Attr.Var(all.tk0 as Tk.Str)
            all.accept(TK.XNAT) -> Attr.Nat(all.tk0 as Tk.Str)
            all.accept(TK.CHAR,'/') -> {
                val tk0 = all.tk0 as Tk.Chr
                val e = parser_attr(all)
                all.assert_tk(all.tk0, e is Attr.Nat || e is Attr.Var || e is Attr.Index || e is Attr.Dnref || e is Attr.Disc) {
                    "unexpected operand to `/´"
                }
                Attr.Dnref(tk0,e)
            }
            else -> {
                all.err_expected("expression")
                error("unreachable")
            }
        }
    }

    fun dots (): Attr {
        var ret = one()
        while (all.accept(TK.CHAR,'.')) {
            ret = when {
                all.accept(TK.XNUM)  -> Attr.Index(all.tk0 as Tk.Num, ret)
                all.accept(TK.XUSER) -> {
                    val tk = all.tk0 as Tk.Str
                    when {
                        all.accept(TK.CHAR, '!') -> Attr.Disc(tk, ret)
                        else -> {
                            all.err_expected("`!´")
                            error("unreachable")
                        }
                    }
                }
                else -> {
                    all.err_expected("index or subtype")
                    error("unreachable")
                }
            }
        }
        return ret
    }
    return dots()
}

fun parser_stmt (all: All): Stmt {
    fun parser_block (): Stmt.Block {
        all.accept_err(TK.CHAR,'{')
        val tk0 = all.tk0 as Tk.Chr
        val ret = parser_stmts(all, Pair(TK.CHAR,'}'))
        all.accept_err(TK.CHAR,'}')
        return Stmt.Block(tk0, ret)
    }
    return when {
        all.accept(TK.VAR) -> {
            all.accept_err(TK.XVAR)
            val tk_id = all.tk0 as Tk.Str
            all.accept_err(TK.CHAR,':')
            val outer = all.accept(TK.CHAR, '^')
            val tp = parser_type(all)
            all.assert_tk(tp.tk, !outer || tp is Type.Ptr) {
                "expected pointer type"
            }
            all.accept_err(TK.CHAR,'=')
            val e = parser_xexpr(all, true)
            Stmt.Var(tk_id, outer, tp, e)
        }
        all.accept(TK.SET) -> {
            val dst = parser_attr(all)
            all.accept_err(TK.CHAR,'=')
            val tk0 = all.tk0 as Tk.Chr
            val src = parser_xexpr(all, true)
            Stmt.Set(tk0, dst, src)
        }
        all.accept(TK.NAT) -> {
            all.accept_err(TK.XNAT)
            Stmt.Nat(all.tk0 as Tk.Str)
        }
        all.accept(TK.TYPE) -> {
            val ispre = all.accept(TK.APRE)
            val isrec = all.accept(TK.AREC)
            all.accept_err(TK.XUSER)
            val tk_id = all.tk0 as Tk.Str

            if (ispre) {
                return Stmt.User(tk_id,isrec, emptyArray())
            }

            all.accept_err(TK.CHAR,'{')

            fun parser_sub (): Pair<Tk.Str,Type> {
                all.accept_err(TK.XUSER)
                val tk = all.tk0 as Tk.Str
                all.accept_err(TK.CHAR,':')
                val tp = parser_type(all)
                return Pair(tk,tp)
            }

            val sub1 = parser_sub()

            val subs = arrayListOf(sub1)
            while (true) {
                all.accept(TK.CHAR,';')
                try {
                    val subi = parser_sub()
                    subs.add(subi)
                } catch (e: Throwable) {
                    break
                }
            }
            all.accept_err(TK.CHAR,'}')
            Stmt.User(tk_id,isrec,subs.toTypedArray())
        }
        all.check(TK.CALL) || all.check(TK.OUT) -> {
            val tk0 = all.tk1 as Tk.Key
            val e = parser_expr(all, true)
            Stmt.Call(tk0, e as Expr.Call)
        }
        all.accept(TK.IF) -> {
            val tk0 = all.tk0 as Tk.Key
            val tst = parser_expr(all, false)
            val true_ = parser_block()
            if (!all.accept(TK.ELSE)) {
                return Stmt.If(tk0, tst, true_, Stmt.Block(Tk.Chr(TK.CHAR,all.tk1.lin,all.tk1.col,'{'),Stmt.Pass(all.tk0)))
            }
            val false_ = parser_block()
            Stmt.If(tk0, tst, true_, false_)
        }
        all.accept(TK.FUNC) -> {
            all.accept_err(TK.XVAR)
            val tk_id = all.tk0 as Tk.Str
            all.accept_err(TK.CHAR,':')
            val tp = parser_type(all)
            all.assert_tk(all.tk0, tp is Type.Func) {
                "expected function type"
            }
            val block = parser_block()

            val lin = block.tk.lin
            val col = block.tk.col
            val xblock = Stmt.Block(block.tk_,
                Stmt.Seq(block.tk,
                    Stmt.Var (
                        Tk.Str(TK.XVAR,lin,col,"arg"),
                        false,
                        (tp as Type.Func).inp,
                        XExpr(null, Expr.Nat(Tk.Str(TK.XNAT,lin,col,"_arg_")))
                    ),
                    Stmt.Seq(block.tk,
                        Stmt.Var (
                            Tk.Str(TK.XVAR,lin,col,"_ret_"),
                            false,
                            tp.out,
                            XExpr(null, Expr.Unk(Tk.Chr(TK.CHAR,lin,col,'?')))
                        ),
                        block,
                    )
                )
            )
            Stmt.Func(tk_id, tp, xblock)
        }
        all.accept(TK.RET) -> {
            val tk0 = all.tk0 as Tk.Key
            var e = try {
                parser_xexpr(all, false)
            } catch (e: Throwable) {
                assert(!all.consumed(tk0)) {
                    e.message!!
                }
                XExpr(null, Expr.Unit(Tk.Sym(TK.UNIT,all.tk1.lin,all.tk1.col,"()")))
            }
            Stmt.Seq (tk0,
                Stmt.Set (
                    Tk.Chr(TK.CHAR,tk0.lin,tk0.col,'='),
                    Attr.Var(Tk.Str(TK.XVAR,tk0.lin,tk0.col,"_ret_")),
                    e
                ),
                Stmt.Ret(tk0, e)
            )
        }
        all.accept(TK.LOOP) -> {
            val tk0 = all.tk0 as Tk.Key
            val block = parser_block()
            Stmt.Loop(tk0, block)
        }
        all.accept(TK.BREAK) -> return Stmt.Break(all.tk0 as Tk.Key)
        all.check(TK.CHAR,'{') -> return parser_block()
        else -> {
            all.err_expected("statement")
            error("unreachable")
        }
    }
}

fun parser_stmts (all: All, opt: Pair<TK,Char?>): Stmt {
    fun enseq (s1: Stmt, s2: Stmt): Stmt {
        return when {
            (s1 is Stmt.Pass) -> s2
            (s2 is Stmt.Pass) -> s1
            else -> Stmt.Seq(s1.tk, s1, s2)
        }
    }
    var ret: Stmt = Stmt.Pass(all.tk0)
    while (true) {
        all.accept(TK.CHAR, ';')
        val tk_bef = all.tk0
        try {
            val s = parser_stmt(all)
            ret = enseq(ret,s)
        } catch (e: Throwable) {
            assert(!all.consumed(tk_bef)) {
                e.message!!
            }
            assert(all.check(opt.first, opt.second)) {
                e.message!!
            }
            break
        }
    }
    return ret
}