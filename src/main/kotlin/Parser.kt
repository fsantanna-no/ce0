fun parser_type (all: All): Type {
    fun one (): Type { // Unit, Nat, User, Cons
        return when {
            all.accept(TK.XSCOPE) -> Type.Pool(all.tk0 as Tk.Scope)
            all.accept(TK.UNIT)   -> Type.Unit(all.tk0 as Tk.Sym)
            all.accept(TK.XNAT)   -> Type.Nat(all.tk0 as Tk.Str)
            all.accept(TK.XUP)    -> Type.Rec(all.tk0 as Tk.Up)
            all.accept(TK.CHAR,'/') -> {
                val tk0 = all.tk0 as Tk.Chr
                val pln = one()
                val scp = if (all.accept(TK.XSCOPE)) (all.tk0 as Tk.Scope) else Tk.Scope(TK.XSCOPE,all.tk0.lin,all.tk0.col,"local",null)
                Type.Ptr(tk0, scp, pln)
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
                if (tk0.chr == '[') {
                    all.accept_err(TK.CHAR, ']')
                    Type.Tuple(tk0, tps.toTypedArray())
                } else {
                    all.accept_err(TK.CHAR, '>')
                    fun f (tp: Type, n: Int): Boolean {
                        return when (tp) {
                            is Type.Ptr   -> tp.pln.let {
                                f(it,n) || (it is Type.Rec && n==it.tk_.up)
                            }
                            //is Type.Rec   -> return n <= tp.tk_.up
                            is Type.Tuple -> tp.vec.any { f(it, n) }
                            is Type.Union -> tp.vec.any { f(it, n+1) }
                            else -> false
                        }
                    }
                    val vec   = tps.toTypedArray()
                    val isrec = vec.any { f(it, 1) }
                    Type.Union(tk0, isrec, vec)
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
            val tk = all.tk0 as Tk.Sym
            val oth = parser_type(all) // right associative
            val clo = if (!all.accept(TK.CHAR, '[')) {
                Tk.Scope(TK.XSCOPE, tk.lin, tk.col, "global", null)
            } else {
                all.accept_err(TK.XSCOPE)
                val scope = all.tk0 as Tk.Scope
                all.accept_err(TK.CHAR, ']')
                scope
            }
            Type.Func(tk, clo, ret, oth)
        }
        else -> ret
    }
}

fun parser_expr (all: All, canpre: Boolean): Expr {
    fun one (): Expr {
        return when {
            all.accept(TK.XSCOPE) -> Expr.Pool(all.tk0 as Tk.Scope)
            all.accept(TK.UNIT)   -> Expr.Unit(all.tk0 as Tk.Sym)
            all.accept(TK.XVAR)   -> Expr.Var(all.tk0 as Tk.Str)
            all.accept(TK.XNAT)   -> {
                val tk0 = all.tk0 as Tk.Str
                val tp = if (!all.accept(TK.CHAR, ':')) null else {
                    parser_type(all)
                }
                Expr.Nat(tk0, tp)
            }
            all.accept(TK.CHAR,'/') -> {
                val tk0 = all.tk0 as Tk.Chr
                val e = parser_expr(all,false)
                all.assert_tk(all.tk0, e is Expr.Nat || e is Expr.Var || e is Expr.TDisc || e is Expr.Dnref || e is Expr.Upref) {
                    "unexpected operand to `/´"
                }
                Expr.Upref(tk0, e)
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
                all.accept_err(TK.CHAR, ':')
                val tp = parser_type(all)
                Expr.UCons(tk0, tp, cons)
            }
            all.accept(TK.NEW) -> {
                val tk0 = all.tk0
                val e = parser_expr(all,false)
                all.assert_tk(tk0, e is Expr.UCons && e.tk_.num!=0) {
                    //"invalid `new` : unexpected <.0>"
                    "invalid `new` : expected constructor"
                }
                val scp = if (all.accept(TK.CHAR, ':')) {
                    all.accept_err(TK.XSCOPE)
                    all.tk0 as Tk.Scope
                } else {
                    Tk.Scope(TK.XSCOPE, all.tk0.lin, all.tk0.col, "local", null)
                }
                Expr.New(tk0 as Tk.Key, scp, e as Expr.UCons)
            }
            all.accept(TK.FUNC) -> {
                val tk0 = all.tk0 as Tk.Key

                val tp = parser_type(all)
                all.assert_tk(all.tk0, tp is Type.Func) {
                    "expected function type"
                }
                val tpf = tp as Type.Func

                val block = parser_block(all)
                val lin = block.tk.lin
                val col = block.tk.col
                //println(tpf.inp)

                val xblock = Stmt.Block(block.tk_, null,
                    Stmt.Seq(block.tk,
                        Stmt.Var(Tk.Str(TK.XVAR,lin,col,"_ret_"), tpf.out.lincol(lin,col)),
                        Stmt.Block(block.tk_, null,
                            Stmt.Seq(block.tk,
                                Stmt.Var(Tk.Str(TK.XVAR,lin,col,"arg"), tpf.inp.lincol(lin,col)), //.let{println(it);it}),
                                Stmt.Seq(block.tk,
                                    Stmt.Set(
                                        Tk.Chr(TK.XVAR,lin,col,'='),
                                        Expr.Var(Tk.Str(TK.XVAR,lin,col,"arg")),
                                        Expr.Nat(Tk.Str(TK.XNAT,lin,col,"_arg_"),null)
                                    ),
                                    block
                                )
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

        // one!1\.2?1
        var e1 = one()
        while (all.accept(TK.CHAR,'\\') || all.accept(TK.CHAR, '.') || all.accept(TK.CHAR, '!') || all.accept(TK.CHAR, '?')) {
            val chr = all.tk0 as Tk.Chr
            if (chr.chr == '\\') {
                all.assert_tk(all.tk0, e1 is Expr.Nat || e1 is Expr.Var || e1 is Expr.TDisc || e1 is Expr.UDisc || e1 is Expr.Dnref || e1 is Expr.Upref || e1 is Expr.Call) {
                    "unexpected operand to `\\´"
                }
                e1 = Expr.Dnref(chr, e1)
            } else {
                all.accept_err(TK.XNUM)
                val num = all.tk0 as Tk.Num
                all.assert_tk(all.tk0, e1 !is Expr.TCons && e1 !is Expr.UCons) {
                    "invalid discriminator : unexpected constructor"
                }
                if (chr.chr=='?' || chr.chr=='!') {
                    All_assert_tk(all.tk0, num.num!=0 || e1 is Expr.Dnref) {
                        "invalid discriminator : union cannot be <.0>"
                    }
                }
                e1 = when {
                    (chr.chr == '?') -> Expr.UPred(num, e1)
                    (chr.chr == '!') -> Expr.UDisc(num, e1)
                    (chr.chr == '.') -> Expr.TDisc(num, e1)
                    else -> error("impossible case")
                }
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
        val scp = if (!all.accept(TK.CHAR, ':')) null else {
            all.accept_err(TK.XSCOPE)
            all.tk0 as Tk.Scope
        }
        return Expr.Call(tk_pre2, scp, e1, e2)
    }

    val ispre = (canpre && (all.accept(TK.CALL) || all.accept(TK.OUTPUT)))
    return call(ispre)
}

fun parser_attr (all: All): Attr {
    fun one (): Attr {
        return when {
            all.accept(TK.XVAR) -> Attr.Var(all.tk0 as Tk.Str)
            all.accept(TK.XNAT) -> Attr.Nat(all.tk0 as Tk.Str)
            all.accept(TK.CHAR,'\\') -> {
                val tk0 = all.tk0 as Tk.Chr
                val e = parser_attr(all)
                all.assert_tk(all.tk0, e is Attr.Nat || e is Attr.Var || e is Attr.TDisc || e is Attr.UDisc || e is Attr.Dnref) {
                    "unexpected operand to `\\´"
                }
                Attr.Dnref(tk0,e)
            }
            else -> {
                all.err_expected("expression")
                error("unreachable")
            }
        }
    }

    // one.1!\.2.1?
    var e1 = one()
    while (all.accept(TK.CHAR, '\\') || all.accept(TK.CHAR, '.') || all.accept(TK.CHAR, '!')) {
        val chr = all.tk0 as Tk.Chr
        if (chr.chr == '\\') {
            all.assert_tk(
                all.tk0,
                e1 is Attr.Nat || e1 is Attr.Var || e1 is Attr.TDisc || e1 is Attr.UDisc || e1 is Attr.Dnref
            ) {
                "unexpected operand to `\\´"
            }
            e1 = Attr.Dnref(chr, e1)
        } else {
            all.accept_err(TK.XNUM)
            val num = all.tk0 as Tk.Num
            e1 = when {
                (chr.chr == '!') -> Attr.UDisc(num, e1)
                (chr.chr == '.') -> Attr.TDisc(num, e1)
                else -> error("impossible case")
            }
        }
    }
    return e1
}

fun parser_block (all: All): Stmt.Block {
    all.accept_err(TK.CHAR,'{')
    val tk0 = all.tk0 as Tk.Chr
    val scp = all.accept(TK.XSCOPE).let { if (it) all.tk0 as Tk.Scope else null }
    val ret = parser_stmts(all, Pair(TK.CHAR,'}'))
    all.accept_err(TK.CHAR,'}')
    return Stmt.Block(tk0, scp, ret)
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
