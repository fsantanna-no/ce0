sealed class Type (val tk: Tk) {
    data class Any   (val tk_: Tk.Chr): Type(tk_)
    data class Unit  (val tk_: Tk.Sym): Type(tk_)
    data class Nat   (val tk_: Tk.Str): Type(tk_)
    data class User  (val tk_: Tk.Str): Type(tk_)
    data class Tuple (val tk_: Tk.Chr, val vec: Array<Type>): Type(tk_)
    data class Func  (val tk_: Tk.Sym, val inp: Type, val out: Type): Type(tk_)
    data class Ptr   (val tk_: Tk.Chr, val tp: Type): Type(tk_)
}

sealed class Expr (val tk: Tk) {
    data class Unk   (val tk_: Tk.Chr): Expr(tk_)
    data class Unit  (val tk_: Tk.Sym): Expr(tk_)
    data class Int   (val tk_: Tk.Num): Expr(tk_)
    data class Var   (val tk_: Tk.Str): Expr(tk_)
    data class Nat   (val tk_: Tk.Str): Expr(tk_)
    data class Tuple (val tk_: Tk.Chr, val vec: Array<Expr>): Expr(tk_)
    data class Cons  (val tk_: Tk.Chr, val sup: Tk.Str, val sub: Tk.Str, val arg: Expr): Expr(tk_)
    data class Dnref (val tk_: Tk, val e: Expr): Expr(tk_)
    data class Upref (val tk_: Tk.Chr, val e: Expr): Expr(tk_)
    data class Index (val tk_: Tk.Num, val e: Expr): Expr(tk_)
    data class Pred  (val tk_: Tk.Str, val e: Expr): Expr(tk_)
    data class Disc  (val tk_: Tk.Str, val e: Expr): Expr(tk_)
    data class Call  (val tk_: Tk.Key, val f: Expr, val arg: Expr): Expr(tk_)
}

sealed class Stmt (val tk: Tk) {
    data class Pass  (val tk_: Tk) : Stmt(tk_)
    data class Var   (val tk_: Tk.Str, val outer: Boolean, val type: Type, val init: Expr) : Stmt(tk_)
    data class Set   (val tk_: Tk.Chr, val dst: Expr, val src: Expr) : Stmt(tk_)
    data class User  (val tk_: Tk.Str, val isrec: Boolean, val subs: Array<Pair<Tk.Str,Type>>) : Stmt(tk_)
    data class Nat   (val tk_: Tk.Str) : Stmt(tk_)
    data class Call  (val tk_: Tk.Key, val call: Expr.Call) : Stmt(tk_)
    data class Seq   (val tk_: Tk, val s1: Stmt, val s2: Stmt) : Stmt(tk_)
    data class If    (val tk_: Tk.Key, val tst: Expr, val true_: Block, val false_: Block) : Stmt(tk_)
    data class Func  (val tk_: Tk.Str, val type: Type.Func, val block: Block?) : Stmt(tk_)
    data class Ret   (val tk_: Tk.Key, val e: Expr) : Stmt(tk_)
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
                assert(all.accept_err(TK.CHAR,',')) { all.err }
                val tps = arrayListOf(tp)
                while (true) {
                    val tp2 = parser_type(all)
                    tps.add(tp2)
                    if (!all.accept(TK.CHAR,',')) {
                        break
                    }
                }
                assert(all.accept_err(TK.CHAR,')')) {
                    all.err
                }
                Type.Tuple(tk0, tps.toTypedArray())
            }
            else -> {
                all.err_expected("type")
                error(all.err)
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
            all.accept(TK.XNUM) -> Expr.Int(all.tk0 as Tk.Num)
            all.accept(TK.XVAR) -> Expr.Var(all.tk0 as Tk.Str)
            all.accept(TK.XNAT) -> Expr.Nat(all.tk0 as Tk.Str)
            all.accept(TK.CHAR,'?') -> Expr.Unk(all.tk0 as Tk.Chr)
            all.accept(TK.XUSER) -> {
                val sup = all.tk0 as Tk.Str
                assert(all.accept_err(TK.CHAR,'.')) {
                    all.err
                }
                val tk0 = all.tk0 as Tk.Chr
                assert(all.accept_err(TK.XUSER)) {
                    all.err
                }
                val sub = all.tk0 as Tk.Str
                try {
                    val arg = parser_expr(all, false)
                    Expr.Cons(tk0, sup, sub, arg)
                } catch (e: Throwable) {
                    assert(!all.consumed(sub)) {
                        all.err
                    }
                    Expr.Cons(tk0, sup, sub, Expr.Unit(Tk.Sym(TK.UNIT,all.tk1.lin,all.tk1.col,"()")))
                }
            }
            all.accept(TK.CHAR,'\\') -> {
                val tk0 = all.tk0 as Tk.Chr
                val e = parser_expr(all,false)
                assert(e is Expr.Nat || e is Expr.Var || e is Expr.Index) {
                    all.err_tk(all.tk0, "unexpected operand to `\\´")
                    all.err
                }
                Expr.Upref(tk0,e)
            }
            all.accept(TK.CHAR,'(') -> { // Expr.Tuple
                val tk0 = all.tk0 as Tk.Chr
                val e = parser_expr(all,false)
                if (all.accept(TK.CHAR,')')) {
                    return e
                }
                assert(all.accept_err(TK.CHAR,',')) {
                    all.err
                }
                val es = arrayListOf(e!!)
                while (true) {
                    val e2 = parser_expr(all,false)
                    es.add(e2)
                    if (!all.accept(TK.CHAR,',')) {
                        break
                    }
                }
                assert(all.accept_err(TK.CHAR,')')) {
                    all.err
                }
                return Expr.Tuple(tk0, es.toTypedArray())
            }
            else -> { all.err_expected("expression") ; error(all.err) }
        }
    }

    fun dnref (tk: Tk.Chr?, e: Expr): Expr {
        if (tk == null) {
            return e
        }
        assert(e is Expr.Var || e is Expr.Nat || e is Expr.Upref || e is Expr.Index || e is Expr.Call || e is Expr.Disc) {
            all.err_tk(e.tk, "unexpected operand to `\\´")
            all.err
        }
        return Expr.Dnref(tk, e)
    }
    fun upref (tk: Tk.Chr?, e: Expr): Expr {
        if (tk == null) {
            return e
        }
        assert (e is Expr.Var || e is Expr.Index || e is Expr.Disc) {
            all.err_tk(e.tk, "unexpected operand to `\\´")
            all.err
        }
        return Expr.Upref(tk, e)
    }

    fun dots (): Pair<Expr,Tk.Chr?> {
        var ret = one()

        var tk_slash = if (all.accept(TK.CHAR,'\\')) all.tk0 as Tk.Chr else null

        while (all.accept(TK.CHAR,'.')) {
            ret = dnref(tk_slash, ret!!)
            tk_slash = null

            ret = when {
                all.accept(TK.XNUM)  -> Expr.Index(all.tk0 as Tk.Num, ret!!)
                all.accept(TK.XUSER) -> {
                    val tk = all.tk0 as Tk.Str
                    when {
                        all.accept(TK.CHAR, '?') -> Expr.Pred(tk, ret!!)
                        all.accept(TK.CHAR, '!') -> Expr.Disc(tk, ret!!)
                        else -> {
                            all.err_expected("`?´ or `!´")
                            error(all.err)
                        }
                    }
                }
                else -> {
                    all.err_expected("index or subtype")
                    error(all.err)
                }
            }
        }

        return Pair(ret!!,tk_slash)
    }
    fun call (ispre: Boolean): Expr {
        val tk_pre = all.tk0
        val ret = dots()
        var (e1,tk_slash) = ret

        val tk_bef = all.tk0
        var e2 = try {
            parser_expr(all, false)
        } catch (e: Throwable) {
            assert(!all.consumed(tk_bef)) {
                all.err // failed parser_expr and consumed input: error)
            }
            if (!ispre) {
                return dnref(tk_slash, e1)
            }
            val x = dnref(tk_slash,e1)
            tk_slash = null
            e1 = x
            Expr.Unit(Tk.Sym(TK.UNIT,all.tk1.lin,all.tk1.col,"()")) // call f -> call f ()
        }
        e2 = upref(tk_slash, e2)

        assert(e1 is Expr.Var || (e1 is Expr.Nat && (!ispre || tk_pre.enu==TK.CALL))) {
            all.err_tk(e1.tk, "expected function")
            all.err
        }
        val tk_pre2 = if (ispre) tk_pre as Tk.Key else Tk.Key(TK.CALL,tk_pre.lin,tk_pre.col,"call")
        if (ispre && tk_pre2.enu==TK.OUT) {
            assert(e1 is Expr.Var)
            e1 = Expr.Var (
                Tk.Str(TK.XVAR,e1.tk.lin,e1.tk.col,"output_"+(e1.tk as Tk.Str).str)
            )
        }
        return Expr.Call(tk_pre2, e1, e2!!)
    }

    val ispre = (canpre && (all.accept(TK.CALL) || all.accept(TK.OUT)))
    return call(ispre)
}

fun parser_stmt (all: All): Stmt {
    fun parser_block (): Stmt.Block {
        assert(all.accept_err(TK.CHAR,'{')) {
            all.err
        }
        val tk0 = all.tk0 as Tk.Chr
        val ret = parser_stmts(all, Pair(TK.CHAR,'}'))
        assert(all.accept_err(TK.CHAR,'}')) {
            all.err
        }
        return Stmt.Block(tk0, ret)
    }
    when {
        all.accept(TK.VAR)   -> {
            assert(all.accept_err(TK.XVAR)) {
                all.err
            }
            val tk_id = all.tk0 as Tk.Str
            assert(all.accept_err(TK.CHAR,':')) {
                all.err
            }
            val outer = all.accept(TK.CHAR, '^')
            val tp = parser_type(all)
            if (outer && tp !is Type.Ptr) {
                all.err_tk(tp.tk, "expected pointer type")
                error(all.err)
            }
            assert(all.accept_err(TK.CHAR,'=')) {
                all.err
            }
            val e = parser_expr(all, true)
            return Stmt.Var(tk_id, outer, tp, e)
        }
        all.accept(TK.SET)   -> {
            val dst = parser_expr(all, false)
            assert(all.accept_err(TK.CHAR,'=')) {
                all.err
            }
            val tk0 = all.tk0 as Tk.Chr
            val src = parser_expr(all, true)
            return Stmt.Set(tk0, dst, src)
        }
        all.accept(TK.NAT)   -> {
            assert(all.accept_err(TK.XNAT)) {
                all.err
            }
            return Stmt.Nat(all.tk0 as Tk.Str)
        }
        all.accept(TK.TYPE)  -> {
            val ispre = all.accept(TK.APRE)
            val isrec = all.accept(TK.AREC)
            assert(all.accept_err(TK.XUSER)) {
                all.err
            }
            val tk_id = all.tk0 as Tk.Str

            if (ispre) {
                return Stmt.User(tk_id,isrec, emptyArray())
            }

            assert(all.accept_err(TK.CHAR,'{')) {
                all.err
            }

            fun parser_sub (): Pair<Tk.Str,Type> {
                assert(all.accept_err(TK.XUSER)) {
                    all.err
                }
                val tk = all.tk0 as Tk.Str
                assert(all.accept_err(TK.CHAR,':')) {
                    all.err
                }
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

            assert(all.accept_err(TK.CHAR,'}')) {
                all.err
            }

            return Stmt.User(tk_id,isrec,subs.toTypedArray())
        }
        all.check(TK.CALL) || all.check(TK.OUT) -> {
            val tk0 = all.tk1 as Tk.Key
            val e = parser_expr(all, true)
            return Stmt.Call(tk0, e as Expr.Call)
        }
        all.accept(TK.IF)    -> {
            val tk0 = all.tk0 as Tk.Key
            val tst = parser_expr(all, false)
            val true_ = parser_block()
            if (!all.accept(TK.ELSE)) {
                return Stmt.If(tk0, tst, true_, Stmt.Block(Tk.Chr(TK.CHAR,all.tk1.lin,all.tk1.col,'{'),Stmt.Pass(all.tk0)))
            }
            val false_ = parser_block()
            return Stmt.If(tk0, tst, true_, false_)
        }
        all.accept(TK.FUNC)  -> {
            assert(all.accept_err(TK.XVAR)) {
                all.err
            }
            val tk_id = all.tk0 as Tk.Str
            assert(all.accept_err(TK.CHAR,':')) {
                all.err
            }
            val tp = parser_type(all)
            assert(tp is Type.Func) {
                all.err_tk(all.tk0, "expected function type")
                all.err
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
                        Expr.Nat(Tk.Str(TK.XNAT,lin,col,"_arg_"))
                    ),
                    Stmt.Seq(block.tk,
                        Stmt.Var (
                            Tk.Str(TK.XVAR,lin,col,"_ret_"),
                            false,
                            tp.out,
                            Expr.Unk(Tk.Chr(TK.CHAR,lin,col,'?'))
                        ),
                        block,
                    )
                )
            )
            return Stmt.Func(tk_id, tp, xblock)
        }
        all.accept(TK.RET)   -> {
            val tk0 = all.tk0 as Tk.Key
            var e = try {
                parser_expr(all, false)
            } catch (e: Throwable) {
                assert(!all.consumed(tk0)) {
                    all.err
                }
                Expr.Unit(Tk.Sym(TK.UNIT,all.tk1.lin,all.tk1.col,"()"))
            }
            return Stmt.Seq (tk0,
                Stmt.Set (
                    Tk.Chr(TK.CHAR,tk0.lin,tk0.col,'='),
                    Expr.Var(Tk.Str(TK.XVAR,tk0.lin,tk0.col,"_ret_")),
                    e
                ),
                Stmt.Ret(tk0, e)
            )
        }
        all.accept(TK.LOOP)  -> {
            val tk0 = all.tk0 as Tk.Key
            val block = parser_block()
            return Stmt.Loop(tk0, block)
        }
        all.accept(TK.BREAK) -> return Stmt.Break(all.tk0 as Tk.Key)
        all.check(TK.CHAR,'{') -> return parser_block()
        else -> { all.err_expected("statement") ; error(all.err) }
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
                all.err
            }
            assert(all.check(opt.first, opt.second)) {
                all.err
            }
            break
        }
    }
    return ret
}