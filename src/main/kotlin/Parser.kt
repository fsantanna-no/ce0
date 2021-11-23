sealed class Type (val tk: Tk) {
    data class Any   (val tk_: Tk.Chr): Type(tk_)
    data class Unit  (val tk_: Tk.Sym): Type(tk_)
    data class Nat   (val tk_: Tk.Str): Type(tk_)
    data class Tuple (val tk_: Tk.Chr, val vec: Array<Type>): Type(tk_)
    data class Union (val tk_: Tk.Chr, val isrec: Boolean, val ishold: Boolean, val isnull: Boolean, val vec: Array<Type>): Type(tk_)
    data class UCons (val tk_: Tk.Num, val arg: Type): Type(tk_)
    data class Func  (val tk_: Tk.Sym, val inp: Type, val out: Type): Type(tk_)
    data class Ptr   (val tk_: Tk.Chr, val scope: Char?, val pln: Type): Type(tk_)
    data class Rec   (val tk_: Tk.Up): Type(tk_)
}

fun Type.tostr (): String {
    return when (this) {
        is Type.Any   -> "?"
        is Type.Unit  -> "()"
        is Type.Nat   -> this.tk_.str
        is Type.Rec   -> "^".repeat(this.tk_.up)
        is Type.Ptr   -> "\\" + this.pln.tostr()
        is Type.Tuple -> "[" + this.vec.map { it.tostr() }.joinToString(",") + "]"
        is Type.Union -> "<" + (if (this.isnull) "? " else "") + this.vec.map { it.tostr() }.joinToString(",") + ">"
        is Type.UCons -> "<." + this.tk_.num + " " + this.arg.tostr() + ">"
        is Type.Func  -> this.inp.tostr() + " -> " + this.out.tostr()
        else -> error("$this")
    }
}

fun Type_Unit (tk: Tk): Type.Unit {
    return Type.Unit(Tk.Sym(TK.UNIT, tk.lin, tk.col, "()"))
}

fun Type_Any (tk: Tk): Type.Any {
    return Type.Any(Tk.Chr(TK.CHAR,tk.lin,tk.col,'?'))
}

fun Type_Nat (tk: Tk, str: String): Type.Nat {
    return Type.Nat(Tk.Str(TK.XNAT,tk.lin,tk.col,str))
}

fun Type.keepAnyNat (other: ()->Type): Type {
    return when (this) {
        is Type.Any, is Type.Nat -> this
        else -> other()
    }
}

fun Type.isnullptr (): Boolean {
    return this is Type.Union && this.isnull && this.vec.size==1 && this.vec[0] is Type.Ptr
}

// TODO: use it to detect recursive unions that do not require tags b/c of single subtype+null pointer
// (e.g., lists). Remove field/tests from the struct.
fun Type.isnullexrec (): Boolean {
    return this is Type.Union && this.exactlyRec() && this.isnull && this.vec.size==1
}

fun Type.expand (): Array<Type> {
    fun aux (cur: Type, up: Int): Type {
        return when (cur) {
            is Type.Rec   -> if (up == cur.tk_.up) this else { assert(up>cur.tk_.up) ; cur }
            is Type.Tuple -> Type.Tuple(cur.tk_, cur.vec.map { aux(it,up+1) }.toTypedArray())
            is Type.Union -> Type.Union(cur.tk_, cur.isrec, cur.ishold, cur.isnull, cur.vec.map { aux(it,up+1) }.toTypedArray())
            is Type.Ptr   -> Type.Ptr(cur.tk_, cur.scope, aux(cur.pln,up))
            is Type.Func  -> Type.Func(cur.tk_, aux(cur.inp,up), aux(cur.out,up))
            is Type.UCons -> error("bug found")
            else -> cur
        }
    }
    return when (this) {
        is Type.Union -> this.vec.map { aux(it, 1) }.toTypedArray()
        is Type.Tuple -> this.vec.map { aux(it, 1) }.toTypedArray()
        else -> error("bug found")
    }
}

sealed class Expr (val tk: Tk) {
    data class Unit  (val tk_: Tk.Sym): Expr(tk_)
    data class Var   (val tk_: Tk.Str): Expr(tk_)
    data class Nat   (val tk_: Tk.Str): Expr(tk_)
    data class TCons (val tk_: Tk.Chr, val arg: Array<Expr>): Expr(tk_)
    data class UCons (val tk_: Tk.Num, val arg: Expr): Expr(tk_)
    data class TDisc (val tk_: Tk.Num, val tup: Expr): Expr(tk_)
    data class UDisc (val tk_: Tk.Num, val uni: Expr): Expr(tk_)
    data class UPred (val tk_: Tk.Num, val uni: Expr): Expr(tk_)
    data class New   (val tk_: Tk.Key, val arg: Expr): Expr(tk_)
    data class Dnref (val tk_: Tk,     val ptr: Expr): Expr(tk_)
    data class Upref (val tk_: Tk.Chr, val pln: Expr): Expr(tk_)
    data class Call  (val tk_: Tk.Key, val f: Expr, val arg: Expr): Expr(tk_)
    data class Func  (val tk_: Tk.Key, val type: Type.Func, val block: Stmt.Block) : Expr(tk_)
}

sealed class Attr (val tk: Tk) {
    data class Var   (val tk_: Tk.Str): Attr(tk_)
    data class Nat   (val tk_: Tk.Str): Attr(tk_)
    data class Dnref (val tk_: Tk, val ptr: Attr): Attr(tk_)
    data class TDisc (val tk_: Tk.Num, val tup: Attr): Attr(tk_)
    data class UDisc (val tk_: Tk.Num, val uni: Attr): Attr(tk_)
}

private
fun Attr.toExpr (): Expr {
    return when (this) {
        is Attr.Var   -> Expr.Var(this.tk_)
        is Attr.Nat   -> Expr.Nat(this.tk_)
        is Attr.Dnref -> Expr.Dnref(this.tk_,this.ptr.toExpr())
        is Attr.TDisc -> Expr.TDisc(this.tk_,this.tup.toExpr())
        is Attr.UDisc -> Expr.UDisc(this.tk_,this.uni.toExpr())
    }
}

sealed class Stmt (val tk: Tk) {
    data class Pass  (val tk_: Tk) : Stmt(tk_)
    data class Var   (val tk_: Tk.Str, val type: Type) : Stmt(tk_)
    data class Set   (val tk_: Tk.Chr, val dst: Expr, val src: Expr) : Stmt(tk_)
    data class Nat   (val tk_: Tk.Str) : Stmt(tk_)
    data class Call  (val tk_: Tk.Key, val call: Expr.Call) : Stmt(tk_)
    data class Seq   (val tk_: Tk, val s1: Stmt, val s2: Stmt) : Stmt(tk_)
    data class If    (val tk_: Tk.Key, val tst: Expr, val true_: Block, val false_: Block) : Stmt(tk_)
    data class Ret   (val tk_: Tk.Key) : Stmt(tk_)
    data class Loop  (val tk_: Tk.Key, val block: Block) : Stmt(tk_)
    data class Break (val tk_: Tk.Key) : Stmt(tk_)
    data class Block (val tk_: Tk.Chr, val scope: Char?, val body: Stmt) : Stmt(tk_)
}

fun parser_type (all: All): Type {
    fun one (): Type { // Unit, Nat, User, Cons
        return when {
            all.accept(TK.UNIT) -> Type.Unit(all.tk0 as Tk.Sym)
            all.accept(TK.XNAT) -> Type.Nat(all.tk0 as Tk.Str)
            all.accept(TK.XUP)  -> Type.Rec(all.tk0 as Tk.Up)
            all.accept(TK.CHAR,'\\') -> {
                val tk0 = all.tk0 as Tk.Chr
                val pln = one()
                val scope = if (!all.accept(TK.XSCOPE)) null else {
                    (all.tk0 as Tk.Scope).scope
                }
                Type.Ptr(tk0, scope, pln)
            }
            all.accept(TK.CHAR,'(') -> {
                val tp = parser_type(all)
                all.accept_err(TK.CHAR,')')
                return tp
            }
            all.accept(TK.CHAR,'[') || all.accept(TK.CHAR,'<') -> {
                val tk0 = all.tk0 as Tk.Chr
                val isnullable = (tk0.chr=='<' && all.accept(TK.CHAR,'?'))
                val tp = parser_type(all)
                val tps = arrayListOf(tp)
                while (true) {
                    if (!all.accept(TK.CHAR,',')) {
                        break
                    }
                    val tp2 = parser_type(all)
                    tps.add(tp2)
                }
                if (tk0.chr == '[') {
                    all.accept_err(TK.CHAR, ']')
                    Type.Tuple(tk0, tps.toTypedArray())
                } else {
                    all.accept_err(TK.CHAR, '>')
                    fun f (tp: Type, n: Int): Boolean {
                        return when (tp) {
                            is Type.Rec   -> return n <= tp.tk_.up
                            is Type.Tuple -> tp.vec.any { f(it, n+1) }
                            is Type.Union -> tp.vec.any { f(it, n+1) }
                            else -> false
                        }
                    }
                    fun g (tp: Type, n: Int): Boolean {
                        return when (tp) {
                            is Type.Ptr   -> return (tp.pln is Type.Rec) && (n == tp.pln.tk_.up)
                            is Type.Tuple -> tp.vec.any { g(it, n+1) }
                            is Type.Union -> tp.vec.any { g(it, n+1) }
                            else -> false
                        }
                    }
                    val vec    = tps.toTypedArray()
                    val isrec  = vec.any { f(it, 1) }
                    val ishold = vec.any { g(it, 1) }
                    All_assert_tk(tk0,!isnullable || isrec || (vec.size==1 && vec[0] is Type.Ptr)) {
                        "invalid type declaration : unexpected `?´"
                    }
                    All_assert_tk(tk0,!ishold || isrec) {
                        "invalid type declaration : unexpected recursive pointer"
                    }
                    Type.Union(tk0, isrec, ishold, isnullable, vec)
                }
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

fun parser_expr (all: All, canpre: Boolean): Expr {
    fun one (): Expr {
        return when {
            all.accept(TK.UNIT) -> Expr.Unit(all.tk0 as Tk.Sym)
            all.accept(TK.XVAR) -> Expr.Var(all.tk0 as Tk.Str)
            all.accept(TK.XNAT) -> Expr.Nat(all.tk0 as Tk.Str)
            all.accept(TK.CHAR,'\\') -> {
                val tk0 = all.tk0 as Tk.Chr
                val e = parser_expr(all,false)
                all.assert_tk(all.tk0, e is Expr.Nat || e is Expr.Var || e is Expr.TDisc || e is Expr.UDisc || e is Expr.Dnref || e is Expr.Upref) {
                    "unexpected operand to `\\´"
                }
                Expr.Upref(tk0,e)
            }
            all.accept(TK.CHAR,'/') -> {
                val tk0 = all.tk0 as Tk.Chr
                val e = parser_expr(all,false)
                all.assert_tk(all.tk0, e is Expr.Nat || e is Expr.Var || e is Expr.TDisc || e is Expr.UDisc || e is Expr.Dnref || e is Expr.Upref || e is Expr.Call) {
                    "unexpected operand to `/´"
                }
                Expr.Dnref(tk0,e)
            }
            all.accept(TK.CHAR,'(') -> {
                val e = parser_expr(all, false)
                all.accept_err(TK.CHAR,')')
                e
            }
            all.accept(TK.CHAR,'[') -> {
                val tk0 = all.tk0 as Tk.Chr
                val e = parser_expr(all, false)
                val es = arrayListOf(e)
                while (true) {
                    if (!all.accept(TK.CHAR,',')) {
                        break
                    }
                    val e2 = parser_expr(all, false)
                    es.add(e2)
                }
                all.accept_err(TK.CHAR, ']')
                Expr.TCons(tk0, es.toTypedArray())
            }
            all.accept(TK.CHAR,'<') -> {
                all.accept_err(TK.CHAR,'.')
                all.accept_err(TK.XNUM)
                val tk0 = all.tk0 as Tk.Num
                val cons = try {
                    parser_expr(all, false)
                } catch (e: Throwable) {
                    assert(!all.consumed(tk0)) {
                        e.message!!
                    }
                    Expr.Unit(Tk.Sym(TK.UNIT, all.tk1.lin, all.tk1.col, "()"))
                }
                all.accept_err(TK.CHAR, '>')
                Expr.UCons(tk0, cons)
            }
            all.accept(TK.NEW) -> {
                Expr.New(all.tk0 as Tk.Key, parser_expr(all,false))
            }
            all.accept(TK.FUNC) -> {
                val tk0 = all.tk0 as Tk.Key
                //all.accept_err(TK.CHAR,'(')
                val tp = parser_type(all)
                all.assert_tk(all.tk0, tp is Type.Func) {
                    "expected function type"
                }
                //all.accept_err(TK.CHAR,')')
                val block = parser_block(all)

                val lin = block.tk.lin
                val col = block.tk.col
                val xblock = Stmt.Block(block.tk_, null,
                    Stmt.Seq(block.tk,
                        Stmt.Var (
                            Tk.Str(TK.XVAR,lin,col,"arg"),
                            (tp as Type.Func).inp
                        ),
                        Stmt.Seq(block.tk,
                            Stmt.Set (
                                Tk.Chr(TK.XVAR,lin,col,'='),
                                Expr.Var(Tk.Str(TK.XVAR,lin,col,"arg")),
                                Expr.Nat(Tk.Str(TK.XNAT,lin,col,"_arg_"))
                            ),
                            Stmt.Seq(block.tk,
                                Stmt.Var (
                                    Tk.Str(TK.XVAR,lin,col,"_ret_"),
                                    tp.out,
                                ),
                                block,
                            )
                        )
                    )
                )
                Expr.Func(tk0, tp, xblock)
            }
            else -> {
                all.err_expected("expression")
                error("unreachable")
            }
        }
    }

    fun call (ispre: Boolean): Expr {
        val tk_pre = all.tk0

        // one!1.2?1
        var e1 = one()
        while (all.accept(TK.CHAR, '.') || all.accept(TK.CHAR, '!') || all.accept(TK.CHAR, '?')) {
            val chr = all.tk0 as Tk.Chr
            all.accept_err(TK.XNUM)
            val num = all.tk0 as Tk.Num
                e1 = when {
            (chr.chr == '?') -> Expr.UPred(num, e1)
                (chr.chr == '!') -> Expr.UDisc(num, e1)
                (chr.chr == '.') -> Expr.TDisc(num, e1)
                else -> error("impossible case")
            }
        }

        val tk_bef = all.tk0
        val e2 = try {
            parser_expr(all, false)
        } catch (e: Throwable) {
            //throw e
            assert(!all.consumed(tk_bef)) {
                e.message!! // failed parser_expr and consumed input: error)
            }
            if (!ispre) {
                return e1
            }
            // call f -> call f ()
            Expr.Unit(Tk.Sym(TK.UNIT,all.tk1.lin,all.tk1.col,"()"))
        }

        //all.assert_tk(e1.tk, e1 is Expr.Var || (e1 is Expr.Nat && (!ispre || tk_pre.enu==TK.CALL))) {
        //    "expected function"
        //}
        val tk_pre2 = if (ispre) tk_pre as Tk.Key else Tk.Key(TK.CALL,tk_pre.lin,tk_pre.col,"call")
        if (ispre && tk_pre2.enu==TK.OUTPUT) {
            all.assert_tk(e1.tk, e1 is Expr.Var) { "invalid `output` : expected identifier" }
            e1 = Expr.Var (
                Tk.Str(TK.XVAR,e1.tk.lin,e1.tk.col,"output_"+(e1.tk as Tk.Str).str)
            )
        }
        return Expr.Call(tk_pre2, e1, e2)
    }

    val ispre = (canpre && (all.accept(TK.CALL) || all.accept(TK.OUTPUT)))
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
                all.assert_tk(all.tk0, e is Attr.Nat || e is Attr.Var || e is Attr.TDisc || e is Attr.UDisc || e is Attr.Dnref) {
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
    while (all.accept(TK.CHAR, '.') || all.accept(TK.CHAR, '!')) {
        val chr = all.tk0 as Tk.Chr
        all.accept_err(TK.XNUM)
        val num = all.tk0 as Tk.Num
        e1 = when {
            (chr.chr == '!') -> Attr.UDisc(num, e1)
            (chr.chr == '.') -> Attr.TDisc(num, e1)
            else -> error("impossible case")
        }
    }
    return e1
}

fun parser_block (all: All): Stmt.Block {
    all.accept_err(TK.CHAR,'{')
    val tk0 = all.tk0 as Tk.Chr
    val scope = if (all.accept(TK.XSCOPE)) (all.tk0 as Tk.Scope).scope else null
    val ret = parser_stmts(all, Pair(TK.CHAR,'}'))
    all.accept_err(TK.CHAR,'}')
    return Stmt.Block(tk0, scope, ret)
}

fun parser_stmt (all: All): Stmt {
    return when {
        all.accept(TK.VAR) -> {
            all.accept_err(TK.XVAR)
            val tk_id = all.tk0 as Tk.Str
            all.accept_err(TK.CHAR,':')
            val tp = parser_type(all)
            Stmt.Var(tk_id, tp)
        }
        all.accept(TK.SET) -> {
            val dst = parser_attr(all)
            all.accept_err(TK.CHAR,'=')
            val tk0 = all.tk0 as Tk.Chr
            val src = parser_expr(all, true)
            Stmt.Set(tk0, dst.toExpr(), src)
        }
        all.accept(TK.NATIVE) -> {
            all.accept_err(TK.XNAT)
            Stmt.Nat(all.tk0 as Tk.Str)
        }
        all.check(TK.CALL) || all.check(TK.OUTPUT) -> {
            val tk0 = all.tk1 as Tk.Key
            val e = parser_expr(all, true)
            Stmt.Call(tk0, e as Expr.Call)
        }
        all.accept(TK.IF) -> {
            val tk0 = all.tk0 as Tk.Key
            val tst = parser_expr(all, false)
            val true_ = parser_block(all)
            if (!all.accept(TK.ELSE)) {
                return Stmt.If(tk0, tst, true_, Stmt.Block(Tk.Chr(TK.CHAR,all.tk1.lin,all.tk1.col,'{'),null,Stmt.Pass(all.tk0)))
            }
            val false_ = parser_block(all)
            Stmt.If(tk0, tst, true_, false_)
        }
        all.accept(TK.RETURN) -> {
            val tk0 = all.tk0 as Tk.Key
            val e = try {
                parser_expr(all, false)
            } catch (e: Throwable) {
                assert(!all.consumed(tk0)) {
                    e.message!!
                }
                Expr.Unit(Tk.Sym(TK.UNIT,all.tk1.lin,all.tk1.col,"()"))
            }
            Stmt.Seq (tk0,
                Stmt.Set (
                    Tk.Chr(TK.CHAR,tk0.lin,tk0.col,'='),
                    Expr.Var(Tk.Str(TK.XVAR,tk0.lin,tk0.col,"_ret_")),
                    e
                ),
                Stmt.Ret(tk0)
            )
        }
        all.accept(TK.LOOP) -> {
            val tk0 = all.tk0 as Tk.Key
            val block = parser_block(all)
            Stmt.Loop(tk0, block)
        }
        all.accept(TK.BREAK) -> return Stmt.Break(all.tk0 as Tk.Key)
        all.check(TK.CHAR,'{') -> return parser_block(all)
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
            //throw e
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
