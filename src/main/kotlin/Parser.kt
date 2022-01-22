fun parser_type (all: All): Type {
    return when {
        all.accept(TK.UNIT) -> Type.Unit(all.tk0 as Tk.Sym)
        all.accept(TK.XNAT) -> Type.Nat(all.tk0 as Tk.Nat)
        all.accept(TK.XUP)  -> Type.Rec(all.tk0 as Tk.Up)
        all.accept(TK.CHAR, '/') -> {
            val tk0 = all.tk0 as Tk.Chr
            val pln = parser_type(all)
            all.accept(TK.XSCPCST) || all.accept_err(TK.XSCPVAR)
            Type.Ptr(tk0, all.tk0 as Tk.Scp1, null, pln)
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
        all.accept(TK.FUNC) || all.accept(TK.TASK) -> {
            val tk0 = all.tk0 as Tk.Key
            val clo = if (all.accept(TK.XSCPCST) || (tk0.enu==TK.TASK && all.accept_err(TK.XSCPVAR)) || all.accept(TK.XSCPVAR)) {
                val tk = all.tk0 as Tk.Scp1
                all.accept_err(TK.ARROW)
                tk
            } else {
                null
            }
            all.accept_err(TK.ATBRACK)
            val scps = mutableListOf<Tk.Scp1>()
            while (all.accept(TK.XSCPVAR)) {
                scps.add(all.tk0 as Tk.Scp1)
                if (!all.accept(TK.CHAR, ',')) {
                    break
                }
            }
            all.accept_err(TK.CHAR, ']')
            all.accept_err(TK.ARROW)
            val inp = parser_type(all)
            all.accept_err(TK.ARROW)
            val out = parser_type(all) // right associative
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
            val cons = if (tk0.num == 0) null else parser_expr(all)
            all.accept_err(TK.CHAR, '>')
            all.accept_err(TK.CHAR, ':')
            val tp = parser_type(all)
            if (tk0.num == 0) {
                All_assert_tk(tp.tk, (tp is Type.Ptr && tp.pln is Type.Union)) { "invalid type : expected pointer to union"}
                Expr.UNull(tk0, tp)
            } else {
                All_assert_tk(tp.tk, tp is Type.Union) { "invalid type : expected union"}
                Expr.UCons(tk0, tp, cons!!)
            }
        }
        all.accept(TK.NEW) -> {
            val tk0 = all.tk0
            val e = parser_expr(all)
            all.assert_tk(tk0, e is Expr.UCons && e.tk_.num!=0) {
                "invalid `new` : expected constructor"
            }
            all.accept_err(TK.CHAR, ':')
            all.accept(TK.XSCPCST) || all.accept_err(TK.XSCPVAR)
            Expr.New(tk0 as Tk.Key, all.tk0 as Tk.Scp1, null, e as Expr.UCons)
        }
        all.check(TK.FUNC) || all.check(TK.TASK) -> {
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
            all.assert_tk(all.tk0, e !is Expr.TCons && e !is Expr.UCons && e !is Expr.UNull) {
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

    // call

    if (all.checkExpr() || all.check(TK.ATBRACK)) {
        val iscps = mutableListOf<Tk.Scp1>()
        if (all.accept(TK.ATBRACK)) {
            while (all.accept(TK.XSCPCST) || all.accept(TK.XSCPVAR)) {
                val tk = all.tk0 as Tk.Scp1
                iscps.add(tk)
                if (!all.accept(TK.CHAR, ',')) {
                    break
                }
            }
            all.accept_err(TK.CHAR, ']')
        }
        val arg = parser_expr(all)
        val oscp = if (!all.accept(TK.CHAR, ':')) null else {
            all.accept(TK.XSCPCST) || all.accept_err(TK.XSCPVAR)
            all.tk0 as Tk.Scp1
        }
        e = Expr.Call(e.tk, e, arg, Pair(iscps.toTypedArray(),oscp), null)
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
    val scp = all.accept(TK.XSCPCST).let { if (it) all.tk0 as Tk.Scp1 else null }
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
            if (all.check(TK.INPUT)) {
                val src = parser_stmt(all)
                Stmt.SSet(tk0, dst.toExpr(), src as Stmt.Inp)
            } else {
                val src = parser_expr(all)
                Stmt.ESet(tk0, dst.toExpr(), src)
            }
        }
        all.accept(TK.NATIVE) -> {
            all.accept_err(TK.XNAT)
            Stmt.Nat(all.tk0 as Tk.Nat)
        }
        all.accept(TK.CALL) -> {
            val tk0 = all.tk0 as Tk.Key
            val e = parser_expr(all)
            All_assert_tk(tk0, e is Expr.Call) { "expected call expression" }
            Stmt.SCall(tk0, e as Expr.Call)
        }
        all.accept(TK.SPAWN) -> {
            val tk0 = all.tk0 as Tk.Key
            val e = parser_expr(all)
            All_assert_tk(tk0, e is Expr.Call) { "expected call expression" }
            Stmt.Spawn(tk0, e as Expr.Call)
        }
        all.accept(TK.AWAIT) -> {
            Stmt.Await(all.tk0 as Tk.Key)
        }
        all.accept(TK.AWAKE) -> {
            val tk0 = all.tk0 as Tk.Key
            val e = parser_expr(all)
            All_assert_tk(tk0, e is Expr.Call) { "expected call expression" }
            Stmt.Awake(tk0, e as Expr.Call)
        }
        all.accept(TK.BCAST) -> {
            val tk0 = all.tk0 as Tk.Key
            all.accept(TK.XSCPCST) || all.accept_err(TK.XSCPVAR)
            val scp = all.tk0 as Tk.Scp1
            val e = parser_expr(all)
            Stmt.Bcast(tk0, scp, e)
        }
        all.accept(TK.INPUT) -> {
            val tk = all.tk0 as Tk.Key
            all.accept_err(TK.XVAR)
            val lib = (all.tk0 as Tk.Str)
            val arg = parser_expr(all)
            all.accept_err(TK.CHAR, ':')
            val tp = parser_type(all)
            Stmt.Inp(tk, tp, lib, arg)
        }
        all.accept(TK.OUTPUT) -> {
            val tk = all.tk0 as Tk.Key
            all.accept_err(TK.XVAR)
            val lib = (all.tk0 as Tk.Str)
            val arg = parser_expr(all)
            Stmt.Out(tk, lib, arg)
        }
        all.accept(TK.IF) -> {
            val tk0    = all.tk0 as Tk.Key
            val tst    = parser_expr(all)
            val true_  = parser_block(all)
            all.accept_err(TK.ELSE)
            val false_ = parser_block(all)
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
        val isend = all.check(TK.CHAR,'}') || all.check(TK.EOF)
        if (!isend) {
            val s = parser_stmt(all)
            ret = enseq(ret,s)
        } else {
            break
        }
    }
    return ret
}
