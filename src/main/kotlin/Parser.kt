fun parser_scp1s (all: All, f: (Tk)->Tk.Id): Array<Tk.Id> {
    val scps = mutableListOf<Tk.Id>()
    while (all.accept(TK.XID)) {
        scps.add(f(all.tk0))
        if (!all.accept(TK.CHAR, ',')) {
            break
        }
    }
    return scps.toTypedArray()
}

fun parser_scopepars (all: All): Pair<Array<Tk.Id>,Array<Pair<String,String>>> {
    all.accept_err(TK.ATBRACK)
    val scps = parser_scp1s(all, { it.asscopepar() })
    val ctrs = mutableListOf<Pair<String,String>>()
    if (all.accept(TK.CHAR,':')) {
        while (all.accept(TK.XID)) {
            val id1 = all.tk0.asscopepar().id
            all.accept_err(TK.CHAR,'>')
            all.accept_err(TK.XID)
            val id2 = all.tk0.asscopepar().id
            ctrs.add(Pair(id1,id2))
            if (!all.accept(TK.CHAR, ',')) {
                break
            }
        }
    }
    all.accept_err(TK.CHAR, ']')
    return Pair(scps, ctrs.toTypedArray())
}

fun parser_type (all: All, tasks: Boolean=false): Type {
    return when {
        all.accept(TK.UNIT) -> Type.Unit(all.tk0 as Tk.Sym)
        all.accept(TK.XNAT) -> Type.Nat(all.tk0 as Tk.Nat)
        all.accept(TK.XID)  -> {
            val tk0 = all.tk0.astype()
            val scps = if (all.accept(TK.ATBRACK)) {
                val ret = parser_scp1s(all, { it.asscope() })
                all.accept_err(TK.CHAR, ']')
                ret
            } else {
                emptyArray()
            }
            Type.Alias(tk0, false, scps, null)
        }
        all.accept(TK.CHAR, '/') -> {
            val tk0 = all.tk0 as Tk.Chr
            val pln = parser_type(all)
            val scp = if (all.accept(TK.CHAR,'@')) {
                all.accept_err(TK.XID)
                all.tk0.asscope()
            } else {
                Tk.Id(TK.XID, all.tk0.lin, all.tk0.col, "LOCAL")
            }
            Type.Pointer(tk0, scp, null, pln)
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
                val vec = tps.toTypedArray()
                Type.Union(tk0, vec)
            }
        }
        all.accept(TK.FUNC) || all.accept(TK.TASK) || (tasks && all.accept(TK.TASKS)) -> {
            val tk0 = all.tk0 as Tk.Key
            if (tk0.enu != TK.FUNC) {
                all.check_err(TK.CHAR,'@')
            }
            val clo = if (all.accept(TK.CHAR,'@')) {
                all.accept_err(TK.XID)
                val tk = all.tk0.asscope()
                all.accept_err(TK.ARROW)
                tk
            } else {
                null
            }

            val (scps,ctrs) = if (all.check(TK.ATBRACK)) {
                val (x,y) = parser_scopepars(all)
                all.accept_err(TK.ARROW)
                Pair(x,y)
            } else {
                Pair(emptyArray(), emptyArray())
            }

            val inp = parser_type(all)

            val pub = if (tk0.enu != TK.FUNC) {
                all.accept_err(TK.ARROW)
                parser_type(all)
            } else null

            all.accept_err(TK.ARROW)
            val out = parser_type(all) // right associative

            Type.Func(tk0, Triple(clo,scps,ctrs), null, inp, pub, out)
        }
        all.accept(TK.ACTIVE) -> {
            val tk0 = all.tk0 as Tk.Key
            all.check(TK.TASKS) || all.check_err(TK.TASK)
            val task = parser_type(all, true)
            assert(task is Type.Func && task.tk.enu!=TK.FUNC)
            if (task.tk.enu == TK.TASKS) {
                Type.Spawns(tk0, task as Type.Func)
            } else {
                Type.Spawn(tk0, task as Type.Func)
            }
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
        all.accept(TK.XID) -> Expr.Var(all.tk0 as Tk.Id)
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
                Expr.UNull(tk0, tp)
            } else {
                Expr.UCons(tk0, tp, cons!!)
            }
        }
        all.accept(TK.NEW) -> {
            val tk0 = all.tk0
            val e = parser_expr(all)
            all.assert_tk(tk0, e is Expr.UCons && e.tk_.num!=0) {
                "invalid `new` : expected constructor"
            }

            val scp = if (all.accept(TK.CHAR, ':')) {
                all.accept_err(TK.CHAR,'@')
                all.accept_err(TK.XID)
                all.tk0.asscope()
            } else {
                Tk.Id(TK.XID, all.tk0.lin, all.tk0.col, "LOCAL")
            }
            Expr.New(tk0 as Tk.Key, scp, null, e as Expr.UCons)
        }
        all.check(TK.TASK) || all.check(TK.FUNC) -> {
            val tk = all.tk1 as Tk.Key
            val tp = parser_type(all) as Type.Func

            val ups: Array<Tk.Id> = if (!all.accept(TK.CHAR,'[')) emptyArray() else {
                val ret = mutableListOf<Tk.Id>()
                while (all.accept(TK.XID)) {
                    ret.add(all.tk0 as Tk.Id)
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
            val ok = when {
                (chr.chr != '.') -> false
                all.accept(TK.XID) -> {
                    val tk = all.tk0 as Tk.Id
                    all.assert_tk(tk, tk.id=="pub") {
                        "unexpected \"${tk.id}\""
                    }
                    true
                }
                else -> false
            }
            if (!ok) {
                all.accept_err(TK.XNUM)
            }
            val num = if (ok) null else (all.tk0 as Tk.Num)
            all.assert_tk(all.tk0, e !is Expr.TCons && e !is Expr.UCons && e !is Expr.UNull) {
                "invalid discriminator : unexpected constructor"
            }
            if (chr.chr=='?' || chr.chr=='!') {
                All_assert_tk(all.tk0, num!!.num!=0 || e is Expr.Dnref) {
                    "invalid discriminator : union cannot be <.0>"
                }
            }
            when {
                (chr.chr == '?') -> Expr.UPred(num!!, e)
                (chr.chr == '!') -> Expr.UDisc(num!!, e)
                (chr.chr == '.') -> {
                    if (all.tk0.enu == TK.XID) {
                        Expr.Pub(all.tk0 as Tk.Id, e)
                    } else {
                        Expr.TDisc(num!!, e)
                    }
                }
                else -> error("impossible case")
            }
        }
    }

    // call

    if (all.checkExpr() || all.check(TK.ATBRACK)) {
        val iscps = if (all.accept(TK.ATBRACK)) {
            val ret = parser_scp1s(all, { it.asscopepar() })
            all.accept_err(TK.CHAR, ']')
            ret
        } else {
            emptyArray()
        }
        val arg = parser_expr(all)
        val oscp = if (!all.accept(TK.CHAR, ':')) null else {
            all.accept_err(TK.CHAR,'@')
            all.accept_err(TK.XID)
            all.tk0.asscope()
        }
        e = Expr.Call(e.tk, e, arg, Pair(iscps,oscp), null)
    }

    return e
}

fun parser_attr (all: All): Attr {
    var e = when {
        all.accept(TK.XID) -> Attr.Var(all.tk0 as Tk.Id)
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
            val ok = when {
                (chr.chr != '.') -> false
                all.accept(TK.XID) -> {
                    val tk = all.tk0 as Tk.Id
                    all.assert_tk(tk, tk.id=="pub") {
                        "unexpected \"${tk.id}\""
                    }
                    true
                }
                else -> false
            }
            if (!ok) {
                all.accept_err(TK.XNUM)
            }
            val num = if (ok) null else (all.tk0 as Tk.Num)
            e = when {
                (chr.chr == '!') -> Attr.UDisc(num!!, e)
                (chr.chr == '.') -> {
                    if (all.tk0.enu == TK.XID) {
                        Attr.Pub(all.tk0 as Tk.Id, e)
                    } else {
                        Attr.TDisc(num!!, e)
                    }
                }
                else -> error("impossible case")
            }
        }
    }
    return e
}

fun parser_block (all: All): Stmt.Block {
    val iscatch = (all.tk0.enu == TK.CATCH)
    all.accept_err(TK.CHAR,'{')
    val tk0 = all.tk0 as Tk.Chr
    val scp = if (!all.accept(TK.CHAR,'@')) null else {
        all.accept_err(TK.XID)
        all.tk0.asscopecst()
    }
    val ret = parser_stmts(all)
    all.accept_err(TK.CHAR,'}')
    return Stmt.Block(tk0, iscatch, scp, ret)
}

fun parser_stmt (all: All): Stmt {
    return when {
        all.accept(TK.VAR) -> {
            all.accept_err(TK.XID)
            val tk_id = all.tk0 as Tk.Id
            all.accept_err(TK.CHAR,':')
            val tp = parser_type(all)
            Stmt.Var(tk_id, tp)
        }
        all.accept(TK.SET) -> {
            val dst = parser_attr(all).toExpr()
            all.accept_err(TK.CHAR,'=')
            val tk0 = all.tk0 as Tk.Chr
            when {
                all.check(TK.INPUT) -> {
                    all.accept(TK.INPUT)
                    val tk = all.tk0 as Tk.Key
                    all.accept_err(TK.XID)
                    val lib = (all.tk0 as Tk.Id)
                    val arg = parser_expr(all)
                    all.accept_err(TK.CHAR, ':')
                    val tp = parser_type(all)
                    Stmt.Input(tk, tp, dst, lib, arg)
                }
                all.check(TK.SPAWN) -> {
                    all.accept(TK.SPAWN)
                    val tk = all.tk0 as Tk.Key
                    val e = parser_expr(all)
                    All_assert_tk(tk, e is Expr.Call) { "expected call expression" }
                    Stmt.SSpawn(tk, dst, e as Expr.Call)
                }
                else -> {
                    val src = parser_expr(all)
                    Stmt.Set(tk0, dst, src)
                }
            }
        }
        all.accept(TK.NATIVE) -> {
            val istype = all.accept(TK.TYPE)
            all.accept_err(TK.XNAT)
            Stmt.Native(all.tk0 as Tk.Nat, istype)
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
            all.accept_err(TK.IN)
            val tsks = parser_expr(all)
            Stmt.DSpawn(tk0, tsks, e as Expr.Call)
        }
        all.accept(TK.AWAIT) -> {
            val tk0 = all.tk0 as Tk.Key
            val e = parser_expr(all)
            Stmt.Await(tk0, e)
        }
        all.accept(TK.BCAST) -> {
            val tk0 = all.tk0 as Tk.Key
            val scp = if (all.accept(TK.CHAR, '@')) {
                all.accept_err(TK.XID)
                all.tk0.asscope()
            } else {
                Tk.Id(TK.XID, all.tk0.lin, all.tk0.col, "GLOBAL")
            }
            val e = parser_expr(all)
            Stmt.Bcast(tk0, scp, e)
        }
        all.accept(TK.THROW) -> {
            Stmt.Throw(all.tk0 as Tk.Key)
        }
        all.accept(TK.INPUT) -> {
            val tk = all.tk0 as Tk.Key
            all.accept_err(TK.XID)
            val lib = (all.tk0 as Tk.Id)
            val arg = parser_expr(all)
            all.accept_err(TK.CHAR, ':')
            val tp = parser_type(all)
            Stmt.Input(tk, tp, null, lib, arg)
        }
        all.accept(TK.OUTPUT) -> {
            val tk = all.tk0 as Tk.Key
            all.accept_err(TK.XID)
            val lib = (all.tk0 as Tk.Id)
            val arg = parser_expr(all)
            Stmt.Output(tk, lib, arg)
        }
        all.accept(TK.IF) -> {
            val tk0    = all.tk0 as Tk.Key
            val tst    = parser_expr(all)
            val true_  = parser_block(all)
            all.accept_err(TK.ELSE)
            val false_ = parser_block(all)
            Stmt.If(tk0, tst, true_, false_)
        }
        all.accept(TK.RETURN) -> Stmt.Return(all.tk0 as Tk.Key)
        all.accept(TK.LOOP) -> {
            val tk0 = all.tk0 as Tk.Key
            if (all.check(TK.CHAR, '{')) {
                val block = parser_block(all)
                Stmt.Loop(tk0, block)
            } else {
                val i = parser_expr(all)
                All_assert_tk(all.tk0, i is Expr.Var) { "expected variable expression" }
                all.accept_err(TK.IN)
                val tsks = parser_expr(all)
                val block = parser_block(all)
                Stmt.DLoop(tk0, i as Expr.Var, tsks, block)
            }
        }
        all.accept(TK.BREAK) -> Stmt.Break(all.tk0 as Tk.Key)
        all.accept(TK.CATCH) || all.check(TK.CHAR,'{') -> parser_block(all)
        all.accept(TK.TYPE) -> {
            all.accept_err(TK.XID)
            val id = all.tk0.astype()
            val scps = if (all.check(TK.ATBRACK)) parser_scopepars(all) else Pair(emptyArray(), emptyArray())
            all.accept_err(TK.CHAR,'=')
            val tp = parser_type(all)
            Stmt.Typedef(id, scps, null, tp)
        }
        else -> {
            all.err_expected("statement")
            error("unreachable")
        }
    }
}

fun parser_stmts (all: All): Stmt {
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
