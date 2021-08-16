sealed class Type (val tk: Tk) {
    data class None  (val tk_: Tk):     Type(tk_)
    data class Any   (val tk_: Tk.Chr): Type(tk_)
    data class Unit  (val tk_: Tk.Sym): Type(tk_)
    data class Nat   (val tk_: Tk.Str): Type(tk_)
    data class Cons  (val tk_: Tk.Chr, val vec: Array<Type>): Type(tk_)
    data class Varia (val tk_: Tk.Idx, val tp: Type): Type(tk_)
    data class Func  (val tk_: Tk.Sym, val inp: Type, val out: Type): Type(tk_)
    data class Ptr   (val tk_: Tk.Chr, val tp: Type): Type(tk_)
    data class Rec   (val tk_: Tk.Up): Type(tk_)
}

fun Type_Unit (tk: Tk): Type.Unit {
    return Type.Unit(Tk.Sym(TK.UNIT, tk.lin, tk.col, "()"))
}

fun Type_Any (tk: Tk): Type.Any {
    return Type.Any(Tk.Chr(TK.CHAR,tk.lin,tk.col,'?'))
}

fun Type.keepAnyNat (other: ()->Type): Type {
    return when (this) {
        is Type.Any, is Type.Nat -> this
        else -> other()
    }
}

//typealias XEpr = Pair<Tk,Expr>
data class XExpr (val x: Tk?, val e: Expr)

sealed class Expr (val tk: Tk) {
    data class Unk   (val tk_: Tk.Chr): Expr(tk_)
    data class Unit  (val tk_: Tk.Sym): Expr(tk_)
    data class Var   (val tk_: Tk.Str): Expr(tk_)
    data class Nat   (val tk_: Tk.Str): Expr(tk_)
    data class Tuple (val tk_: Tk.Chr, val vec: Array<XExpr>): Expr(tk_)
    data class Varia (val tk_: Tk.Idx, val arg: XExpr): Expr(tk_)
    data class Index (val tk_: Tk.Idx, val pre: Expr, val op: Tk.Chr?): Expr(tk_)
    data class Dnref (val tk_: Tk, val sub: Expr): Expr(tk_)
    data class Upref (val tk_: Tk.Chr, val sub: Expr): Expr(tk_)
    data class Call  (val tk_: Tk.Key, val f: Expr, val arg: XExpr): Expr(tk_)
}

sealed class Attr (val tk: Tk) {
    data class Var   (val tk_: Tk.Str): Attr(tk_)
    data class Nat   (val tk_: Tk.Str): Attr(tk_)
    data class Dnref (val tk_: Tk, val e: Attr): Attr(tk_)
    data class Index (val tk_: Tk.Idx, val e: Attr, val op: Tk.Chr?): Attr(tk_)
}

fun Attr.toExpr (): Expr {
    return when (this) {
        is Attr.Var   -> Expr.Var(this.tk_)
        is Attr.Nat   -> Expr.Nat(this.tk_)
        is Attr.Dnref -> Expr.Dnref(this.tk_,this.e.toExpr())
        is Attr.Index -> Expr.Index(this.tk_,this.e.toExpr(),this.op)
    }
}

sealed class Stmt (val tk: Tk) {
    data class Pass  (val tk_: Tk) : Stmt(tk_)
    data class Var   (val tk_: Tk.Str, val outer: Boolean, val type: Type, val src: XExpr) : Stmt(tk_)
    data class Set   (val tk_: Tk.Chr, val dst: Attr, val src: XExpr) : Stmt(tk_)
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

fun Stmt.typeVarFunc (): Type {
    return when (this) {
        is Stmt.Var  -> this.type
        is Stmt.Func -> this.type
        else -> error("bug found")
    }
}

fun parser_type (all: All): Type {
    fun one (): Type { // Unit, Nat, User, Cons
        return when {
            all.accept(TK.UNIT) -> Type.Unit(all.tk0 as Tk.Sym)
            all.accept(TK.XNAT) -> Type.Nat(all.tk0 as Tk.Str)
            all.accept(TK.XUP)  -> Type.Rec(all.tk0 as Tk.Up)
            all.accept(TK.CHAR,'\\') -> {
                val tk0 = all.tk0 as Tk.Chr
                Type.Ptr(tk0, one())
            }
            all.accept(TK.CHAR,'(') -> {
                val tp = parser_type(all)
                all.accept_err(TK.CHAR,')')
                return tp
            }
            all.accept(TK.CHAR,'[') || all.accept(TK.CHAR,'<') -> {
                val tk0 = all.tk0 as Tk.Chr
                val tp = parser_type(all)
                val tps = arrayListOf(tp)
                while (true) {
                    if (!all.accept(TK.CHAR,',')) {
                        break
                    }
                    val tp2 = parser_type(all)
                    tps.add(tp2)
                }
                all.accept_err(TK.CHAR, if (tk0.chr=='[') ']' else '>')
                Type.Cons(tk0, tps.toTypedArray())
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
    if (all.accept(TK.CHAR,'(')) {
        val e = parser_xexpr(all, false)
        all.accept_err(TK.CHAR,')')
        return e
    } else {
        val tk = if (all.accept(TK.BORROW) || all.accept(TK.COPY) || all.accept(TK.MOVE) || all.accept(TK.NEW)) all.tk0 else null
        val e = parser_expr(all, canpre)
        return XExpr(tk, e)
    }
}

fun parser_expr (all: All, canpre: Boolean): Expr {
    fun one (): Expr {
        return when {
            all.accept(TK.UNIT) -> Expr.Unit(all.tk0 as Tk.Sym)
            all.accept(TK.XVAR) -> Expr.Var(all.tk0 as Tk.Str)
            all.accept(TK.XNAT) -> Expr.Nat(all.tk0 as Tk.Str)
            all.accept(TK.CHAR,'?') -> Expr.Unk(all.tk0 as Tk.Chr)
            all.accept(TK.CHAR,'\\') -> {
                val tk0 = all.tk0 as Tk.Chr
                val e = parser_expr(all,false)
                all.assert_tk(all.tk0, e is Expr.Nat || e is Expr.Var || e is Expr.Index || e is Expr.Dnref || e is Expr.Upref) {
                    "unexpected operand to `\\´"
                }
                Expr.Upref(tk0,e)
            }
            all.accept(TK.CHAR,'/') -> {
                val tk0 = all.tk0 as Tk.Chr
                val e = parser_expr(all,false)
                all.assert_tk(all.tk0, e is Expr.Nat || e is Expr.Var || e is Expr.Index || e is Expr.Dnref || e is Expr.Upref || e is Expr.Call) {
                    "unexpected operand to `/´"
                }
                Expr.Dnref(tk0,e)
            }
            all.accept(TK.CHAR,'(') -> {
                val e = parser_expr(all, false)
                all.accept_err(TK.CHAR,')')
                e
            }
            all.accept(TK.CHAR,'[') || all.accept(TK.CHAR,'<') -> {
                val tk0 = all.tk0 as Tk.Chr
                val e = parser_xexpr(all, false)
                val es = arrayListOf(e)
                while (true) {
                    if (!all.accept(TK.CHAR,',')) {
                        break
                    }
                    val e2 = parser_xexpr(all, false)
                    es.add(e2)
                }
                all.accept_err(TK.CHAR, if (tk0.chr=='[') ']' else '>')
                Expr.Tuple(tk0, es.toTypedArray())
            }
            all.accept(TK.XIDX) -> {
                val tk0 = all.tk0 as Tk.Idx
                try {
                    val e = parser_xexpr(all, false)
                    Expr.Varia(tk0, e)
                } catch (e: Throwable) {
                    assert(!all.consumed(tk0)) {
                        e.message!!
                    }
                    Expr.Varia(tk0, XExpr(null, Expr.Unit(Tk.Sym(TK.UNIT, all.tk1.lin, all.tk1.col, "()"))))
                }
            }
            else -> {
                all.err_expected("expression")
                error("unreachable")
            }
        }
    }

    fun call (ispre: Boolean): Expr {
        val tk_pre = all.tk0

        // one.1!.2.1?
        var e1 = one()
        while (all.accept(TK.XIDX)) {
            val idx = all.tk0
            val op = all.accept(TK.CHAR, '?') || all.accept(TK.CHAR, '!')
            e1 = Expr.Index(idx as Tk.Idx, e1, if (op) all.tk0 as Tk.Chr else null)
        }

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
                all.assert_tk(all.tk0, e is Attr.Nat || e is Attr.Var || e is Attr.Index || e is Attr.Dnref) {
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

    // one.1!.2.1?
    var e1 = one()
    while (all.accept(TK.XIDX)) {
        val idx = all.tk0
        val op = all.accept(TK.CHAR, '?') || all.accept(TK.CHAR, '!')
        e1 = Attr.Index(idx as Tk.Idx, e1, if (op) all.tk0 as Tk.Chr else null)
    }
    return e1
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
            val outer = all.accept(TK.XUP)
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