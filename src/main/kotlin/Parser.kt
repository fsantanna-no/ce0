fun parser_type (all: All): Type {
    return when {
        all.accept(TK.UNIT) -> Type.Unit(all.tk0 as Tk.Sym)
        all.accept(TK.XNAT) -> Type.Nat(all.tk0 as Tk.Nat)
        all.accept(TK.XUP)  -> Type.Rec(all.tk0 as Tk.Up)
        all.accept(TK.CHAR, '/') -> {
            val tk0 = all.tk0 as Tk.Chr
            val pln = parser_type(all)
            val scp = if (all.accept(TK.XSCOPE)) (all.tk0 as Tk.Scp1) else {
                Tk.Scp1(TK.XSCOPE, all.tk0.lin, all.tk0.col, "local", null)
            }
            Type.Ptr(tk0, scp, null, pln)
        }
        all.accept(TK.CHAR, '(') -> {
            val tp = parser_type(all)
            all.accept_err(TK.CHAR, ')')
            tp
        }
        all.accept(TK.CHAR, '[') || all.accept(TK.CHAR, '<') -> {
            val tk0 = all.tk0 as Tk.Chr
            val tp = parser_type(all)
            val tps = arrayListOf(tp)
            while (true) {
                if (!all.accept(TK.CHAR, ',')) {
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
                fun f(tp: Type, n: Int): Boolean {
                    return when (tp) {
                        is Type.Ptr -> tp.pln.let {
                            f(it, n) || (it is Type.Rec && n == it.tk_.up)
                        }
                        //is Type.Rec   -> return n <= tp.tk_.up
                        is Type.Tuple -> tp.vec.any { f(it, n) }
                        is Type.Union -> tp.vec.any { f(it, n + 1) }
                        else -> false
                    }
                }

                val vec = tps.toTypedArray()
                val isrec = vec.any { f(it, 1) }
                Type.Union(tk0, isrec, vec)
            }
        }
        all.accept(TK.FUNC) -> {
            val tk0 = all.tk0 as Tk.Key
            val par = all.accept(TK.CHAR, '(')
            all.accept_err(TK.CHAR, '{')
            val clo = if (all.accept(TK.XSCOPE)) {
                all.tk0 as Tk.Scp1
            } else {
                null
            }
            all.accept_err(TK.CHAR, '}')
            all.accept_err(TK.ARROW)
            all.accept_err(TK.CHAR, '{')
            val scps = mutableListOf<Tk.Scp1>()
            while (all.accept(TK.XSCOPE)) {
                val tk = all.tk0 as Tk.Scp1
                All_assert_tk(tk, tk.num != null) {
                    "invalid pool : expected `_N´ depth"
                }
                scps.add(tk)
                if (!all.accept(TK.CHAR, ',')) {
                    break
                }
            }
            all.accept_err(TK.CHAR, '}')
            all.accept_err(TK.ARROW)
            val inp = parser_type(all)
            all.accept_err(TK.ARROW)
            val out = parser_type(all) // right associative
            if (par) all.accept_err(TK.CHAR, ')')
            Type.Func(tk0, Pair(clo,scps.toTypedArray()), null, inp, out)
        }
        else -> {
            all.err_expected("type")
            error("unreachable")
        }
    }
}

fun parser_expr (all: All): Expr {
    var e = when {
        all.accept(TK.UNIT) -> Expr.Unit(all.tk0 as Tk.Sym)
        all.accept(TK.XVAR) -> Expr.Var(all.tk0 as Tk.Str)
        all.accept(TK.XNAT) -> {
            val tk0 = all.tk0 as Tk.Nat
            all.accept_err(TK.CHAR, ':')
            val tp = parser_type(all)
            Expr.Nat(tk0, tp)
        }
        all.accept(TK.CHAR, '/') -> {
            val tk0 = all.tk0 as Tk.Chr
            val e = parser_expr(all)
            all.assert_tk(
                all.tk0,
                e is Expr.Nat || e is Expr.Var || e is Expr.TDisc || e is Expr.Dnref || e is Expr.Upref
            ) {
                "unexpected operand to `/´"
            }
            Expr.Upref(tk0, e)
        }
        all.accept(TK.CHAR, '(') -> {
            val e = parser_expr(all)
            all.accept_err(TK.CHAR, ')')
            e
        }
        all.accept(TK.CHAR, '[') -> {
            val tk0 = all.tk0 as Tk.Chr
            val e = parser_expr(all)
            val es = arrayListOf(e)
            while (true) {
                if (!all.accept(TK.CHAR, ',')) {
                    break
                }
                val e2 = parser_expr(all)
                es.add(e2)
            }
            all.accept_err(TK.CHAR, ']')
            Expr.TCons(tk0, es.toTypedArray())
        }
        all.accept(TK.CHAR, '<') -> {
            all.accept_err(TK.CHAR, '.')
            all.accept_err(TK.XNUM)
            val tk0 = all.tk0 as Tk.Num
            val cons = try {
                parser_expr(all)
            } catch (e: Throwable) {
                assert(!all.consumed(tk0)) {
                    e.message!!
                }
                Expr.Unit(Tk.Sym(TK.UNIT, all.tk1.lin, all.tk1.col, "()"))
            }
            all.accept_err(TK.CHAR, '>')
            all.accept_err(TK.CHAR, ':')
            val tp = parser_type(all)
            All_assert_tk(tp.tk, tp is Type.Union || (tp is Type.Ptr && tp.pln is Type.Union)) { "invalid type : expected union"}
            Expr.UCons(tk0, tp, cons)
        }
        all.accept(TK.NEW) -> {
            val tk0 = all.tk0
            val e = parser_expr(all)
            all.assert_tk(tk0, e is Expr.UCons && e.tk_.num != 0) {
                //"invalid `new` : unexpected <.0>"
                "invalid `new` : expected constructor"
            }
            val scp = if (all.accept(TK.CHAR, ':')) {
                all.accept_err(TK.XSCOPE)
                all.tk0 as Tk.Scp1
            } else {
                Tk.Scp1(TK.XSCOPE, all.tk0.lin, all.tk0.col, "local", null)
            }
            Expr.New(tk0 as Tk.Key, scp, e as Expr.UCons)
        }
        all.accept(TK.CALL) -> {
            val tk_pre = all.tk0 as Tk.Key
            val f = parser_expr(all)

            val iscps = mutableListOf<Tk.Scp1>()
            if (all.accept(TK.CHAR, '{')) {
                while (all.accept(TK.XSCOPE)) {
                    val tk = all.tk0 as Tk.Scp1
                    iscps.add(tk)
                    if (!all.accept(TK.CHAR, ',')) {
                        break
                    }
                }
                all.accept_err(TK.CHAR, '}')
            }

            val arg = parser_expr(all)

            val oscp = if (all.accept(TK.CHAR, ':')) {
                all.accept_err(TK.XSCOPE)
                all.tk0 as Tk.Scp1
            } else {
                Tk.Scp1(TK.XSCOPE, all.tk0.lin, all.tk0.col, "local", null)
            }
            Expr.Call(tk_pre, f, arg, Pair(iscps.toTypedArray(),oscp))
        }
        all.accept(TK.INPUT) -> {
            val tk = all.tk0 as Tk.Key
            all.accept_err(TK.XVAR)
            val lib = (all.tk0 as Tk.Str)
            all.accept_err(TK.CHAR, ':')
            val tp = parser_type(all)
            Expr.Inp(tk, tp, lib)
        }
        all.accept(TK.OUTPUT) -> {
            val tk = all.tk0 as Tk.Key
            all.accept_err(TK.XVAR)
            val lib = (all.tk0 as Tk.Str)
            val arg = parser_expr(all)
            Expr.Out(tk, lib, arg)
        }
        all.check(TK.FUNC) -> {
            val tk = all.tk1 as Tk.Key
            val tp = parser_type(all) as Type.Func

            val ups: Array<Tk.Str> = if (!all.accept(TK.CHAR,'[')) emptyArray() else {
                val ret = mutableListOf<Tk.Str>()
                while (all.accept(TK.XVAR)) {
                    ret.add(all.tk0 as Tk.Str)
                    if (!all.accept(TK.CHAR,',')) {
                        break
                    }
                }
                all.accept_err(TK.CHAR,']')
                ret.toTypedArray()
            }

            val block = parser_block(all)
            Expr.Func(tk, tp, ups, block)
        }
        else -> {
            all.err_expected("expression")
            error("unreachable")
        }
    }

    // one!1\.2?1
    while (all.accept(TK.CHAR,'\\') || all.accept(TK.CHAR, '.') || all.accept(TK.CHAR, '!') || all.accept(TK.CHAR, '?')) {
        val chr = all.tk0 as Tk.Chr
        e = if (chr.chr == '\\') {
            all.assert_tk(all.tk0, e is Expr.Nat || e is Expr.Var || e is Expr.TDisc || e is Expr.UDisc || e is Expr.Dnref || e is Expr.Upref || e is Expr.Call) {
                "unexpected operand to `\\´"
            }
            Expr.Dnref(chr, e)
        } else {
            all.accept_err(TK.XNUM)
            val num = all.tk0 as Tk.Num
            all.assert_tk(all.tk0, e !is Expr.TCons && e !is Expr.UCons) {
                "invalid discriminator : unexpected constructor"
            }
            if (chr.chr=='?' || chr.chr=='!') {
                All_assert_tk(all.tk0, num.num!=0 || e is Expr.Dnref) {
                    "invalid discriminator : union cannot be <.0>"
                }
            }
            when {
                (chr.chr == '?') -> Expr.UPred(num, e)
                (chr.chr == '!') -> Expr.UDisc(num, e)
                (chr.chr == '.') -> Expr.TDisc(num, e)
                else -> error("impossible case")
            }
        }
    }

    return e
}

fun parser_attr (all: All): Attr {
    var e = when {
        all.accept(TK.XVAR) -> Attr.Var(all.tk0 as Tk.Str)
        all.accept(TK.XNAT) -> {
            all.accept_err(TK.CHAR, ':')
            val tp = parser_type(all)
            Attr.Nat(all.tk0 as Tk.Nat, tp)
        }
        all.accept(TK.CHAR,'\\') -> {
            val tk0 = all.tk0 as Tk.Chr
            val e = parser_attr(all)
            all.assert_tk(all.tk0, e is Attr.Nat || e is Attr.Var || e is Attr.TDisc || e is Attr.UDisc || e is Attr.Dnref) {
                "unexpected operand to `\\´"
            }
            Attr.Dnref(tk0,e)
        }
        all.accept(TK.CHAR, '(') -> {
            val e = parser_attr(all)
            all.accept_err(TK.CHAR, ')')
            e
        }
        else -> {
            all.err_expected("expression")
            error("unreachable")
        }
    }

    // one.1!\.2.1?
    while (all.accept(TK.CHAR, '\\') || all.accept(TK.CHAR, '.') || all.accept(TK.CHAR, '!')) {
        val chr = all.tk0 as Tk.Chr
        if (chr.chr == '\\') {
            all.assert_tk(
                all.tk0,
                e is Attr.Nat || e is Attr.Var || e is Attr.TDisc || e is Attr.UDisc || e is Attr.Dnref
            ) {
                "unexpected operand to `\\´"
            }
            e = Attr.Dnref(chr, e)
        } else {
            all.accept_err(TK.XNUM)
            val num = all.tk0 as Tk.Num
            e = when {
                (chr.chr == '!') -> Attr.UDisc(num, e)
                (chr.chr == '.') -> Attr.TDisc(num, e)
                else -> error("impossible case")
            }
        }
    }
    return e
}

fun parser_block (all: All): Stmt.Block {
    all.accept_err(TK.CHAR,'{')
    val tk0 = all.tk0 as Tk.Chr
    val scp = all.accept(TK.XSCOPE).let { if (it) all.tk0 as Tk.Scp1 else null }
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
            //val dst = parser_expr(all)
            all.accept_err(TK.CHAR,'=')
            val tk0 = all.tk0 as Tk.Chr
            val src = parser_expr(all)
            Stmt.Set(tk0, dst.toExpr(), src)
        }
        all.accept(TK.NATIVE) -> {
            all.accept_err(TK.XNAT)
            Stmt.Nat(all.tk0 as Tk.Nat)
        }
        all.check(TK.CALL) || all.check(TK.OUTPUT) || all.check(TK.INPUT) -> {
            val tk0 = all.tk1 as Tk.Key
            val e = parser_expr(all)
            Stmt.SExpr(tk0, e)
        }
        all.accept(TK.IF) -> {
            val tk0 = all.tk0 as Tk.Key
            val tst = parser_expr(all)
            val true_ = parser_block(all)
            val false_ = if (all.accept(TK.ELSE)) {
                parser_block(all)
            } else {
                Stmt.Block(Tk.Chr(TK.CHAR,all.tk1.lin,all.tk1.col,'{'),null, Stmt.Nop(all.tk0))
            }
            Stmt.If(tk0, tst, true_, false_)
        }
        all.accept(TK.RETURN) -> Stmt.Ret(all.tk0 as Tk.Key)
        all.accept(TK.LOOP) -> {
            val tk0 = all.tk0 as Tk.Key
            val block = parser_block(all)
            Stmt.Loop(tk0, block)
        }
        all.accept(TK.BREAK) -> Stmt.Break(all.tk0 as Tk.Key)
        all.check(TK.CHAR,'{') -> parser_block(all)
        else -> {
            all.err_expected("statement")
            error("unreachable")
        }
    }
}

fun parser_stmts (all: All, opt: Pair<TK,Char?>): Stmt {
    fun enseq (s1: Stmt, s2: Stmt): Stmt {
        return when {
            (s1 is Stmt.Nop) -> s2
            (s2 is Stmt.Nop) -> s1
            else -> Stmt.Seq(s1.tk, s1, s2)
        }
    }
    var ret: Stmt = Stmt.Nop(all.tk0)
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
