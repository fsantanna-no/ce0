open class Parser
{
    open fun type (): Type {
        return when {
            all.accept(TK.XID) -> {
                val tk0 = all.tk0.astype()
                val scps = if (all.accept(TK.ATBRACK)) {
                    val ret = this.scp1s { it.asscope() }
                    all.accept_err(TK.CHAR, ']')
                    ret
                } else {
                    emptyList()
                }
                Type.Alias(tk0, false, scps.map { Scope(it,null) })
            }
            all.accept(TK.CHAR, '/') -> {
                val tk0 = all.tk0 as Tk.Chr
                val pln = this.type()
                val scp = if (all.accept(TK.CHAR, '@')) {
                    all.accept_err(TK.XID)
                    all.tk0.asscope()
                } else {
                    Tk.Id(TK.XID, all.tk0.lin, all.tk0.col, "LOCAL")
                }
                Type.Pointer(tk0, Scope(scp,null), pln)
            }
            all.accept(TK.FUNC) || all.accept(TK.TASK) -> {
                val tk0 = all.tk0 as Tk.Key

                val (scps, ctrs) = if (all.check(TK.ATBRACK)) {
                    val (x, y) = this.scopepars()
                    all.accept_err(TK.ARROW)
                    Pair(x, y)
                } else {
                    Pair(emptyList(), emptyList())
                }

                val inp = this.type()

                val pub = if (tk0.enu != TK.FUNC) {
                    all.accept_err(TK.ARROW)
                    this.type()
                } else null

                all.accept_err(TK.ARROW)
                val out = this.type() // right associative

                Type.Func(tk0,
                    Triple (
                        Scope(Tk.Id(TK.XID, tk0.lin, tk0.col, "LOCAL"),null),
                        scps.map { Scope(it,null) },
                        ctrs
                    ),
                    inp, pub, out)
            }
            all.accept(TK.UNIT) -> Type.Unit(all.tk0 as Tk.Sym)
            all.accept(TK.XNAT) -> Type.Nat(all.tk0 as Tk.Nat)
            all.accept(TK.CHAR, '(') -> {
                val tp = this.type()
                all.accept_err(TK.CHAR, ')')
                tp
            }
            all.accept(TK.CHAR, '[') || all.accept(TK.CHAR, '<') -> {
                val tk0 = all.tk0 as Tk.Chr
                val tp = this.type()
                val tps = arrayListOf(tp)
                while (true) {
                    if (!all.accept(TK.CHAR, ',')) {
                        break
                    }
                    val tp2 = this.type()
                    tps.add(tp2)
                }
                if (tk0.chr == '[') {
                    all.accept_err(TK.CHAR, ']')
                    Type.Tuple(tk0, tps)
                } else {
                    all.accept_err(TK.CHAR, '>')
                    val vec = tps
                    Type.Union(tk0, vec)
                }
            }
            all.accept(TK.ACTIVE) -> {
                val tk0 = all.tk0 as Tk.Key
                val (isdyn,len) = if (!all.accept(TK.CHAR,'{')) Pair(false,null) else {
                    val len = if (all.accept(TK.XNUM)) all.tk0 as Tk.Num else null
                    all.accept_err(TK.CHAR, '}')
                    Pair(true, len)
                }
                //all.check_err(TK.TASK)
                val task = this.type()
                //assert(task is Type.Func && task.tk.enu == TK.TASK)
                All_assert_tk(tk0, task is Type.Alias || task is Type.Func && task.tk.enu==TK.TASK) {
                    "invalid type : expected task type"
                }
                if (isdyn) {
                    Type.Actives(tk0, len, task)
                } else {
                    Type.Active(tk0, task)
                }
            }
            else -> {
                all.err_expected("type")
                error("unreachable")
            }
        }
    }

    open fun expr_one (): Expr {
        return when {
            all.accept(TK.XNAT) -> {
                val tk0 = all.tk0 as Tk.Nat
                all.accept_err(TK.CHAR, ':')
                val tp = this.type()
                Expr.Nat(tk0, tp)
            }
            all.accept(TK.CHAR, '<') -> {
                all.accept_err(TK.CHAR, '.')
                all.accept_err(TK.XNUM)
                val tk0 = all.tk0 as Tk.Num
                val cons = if (tk0.num == 0) null else this.expr()
                all.accept_err(TK.CHAR, '>')
                all.accept_err(TK.CHAR, ':')
                val tp = this.type()
                when (tk0.num) {
                    0 -> all.assert_tk(tp.tk,tp is Type.Pointer && tp.pln is Type.Alias) {
                        "invalid type : expected pointer to alias type"
                    }
                    else -> all.assert_tk(tp.tk,tp is Type.Union) {
                        "invalid type : expected union type"
                    }
                }
                if (tk0.num == 0) {
                    Expr.UNull(tk0, tp as Type.Pointer)
                } else {
                    Expr.UCons(tk0, tp as Type.Union, cons!!)
                }
            }
            all.accept(TK.NEW) -> {
                val tk0 = all.tk0
                val e = this.expr()
                all.assert_tk(tk0,e is Expr.As && e.e is Expr.UCons && e.e.tk_.num!=0) {
                    "invalid `new` : expected alias constructor"
                }

                val scp = if (all.accept(TK.CHAR, ':')) {
                    all.accept_err(TK.CHAR, '@')
                    all.accept_err(TK.XID)
                    all.tk0.asscope()
                } else {
                    Tk.Id(TK.XID, all.tk0.lin, all.tk0.col, "LOCAL")
                }
                Expr.New(tk0 as Tk.Key, Scope(scp,null), e as Expr.As)
            }

            all.accept(TK.UNIT) -> Expr.Unit(all.tk0 as Tk.Sym)
            all.tk1.isvar() && all.accept(TK.XID) -> Expr.Var(all.tk0 as Tk.Id)
            all.accept(TK.CHAR, '/') -> {
                val tk0 = all.tk0 as Tk.Chr
                val e = this.expr()
                all.assert_tk(
                    all.tk0,
                    e is Expr.Nat || e is Expr.Var || e is Expr.TDisc || e is Expr.Dnref || e is Expr.Upref
                ) {
                    "unexpected operand to `/´"
                }
                Expr.Upref(tk0, e)
            }
            all.accept(TK.CHAR, '(') -> {
                val e = this.expr()
                all.accept_err(TK.CHAR, ')')
                e
            }
            all.accept(TK.CHAR, '[') -> {
                val tk0 = all.tk0 as Tk.Chr
                val e = this.expr()
                val es = arrayListOf(e)
                while (true) {
                    if (!all.accept(TK.CHAR, ',')) {
                        break
                    }
                    val e2 = this.expr()
                    es.add(e2)
                }
                all.accept_err(TK.CHAR, ']')
                Expr.TCons(tk0, es)
            }
            all.check(TK.TASK) || all.check(TK.FUNC) -> {
                val tk = all.tk1 as Tk.Key
                val tp = this.type() as Type.Func
                val block = this.block()
                Expr.Func(tk, tp, block)
            }
            else -> {
                all.err_expected("expression")
                error("unreachable")
            }
        }
    }

    open fun expr (): Expr {
        var e = this.expr_dots()

        // call
        if (all.checkExpr() || all.check(TK.ATBRACK)) {
            val iscps = if (all.accept(TK.ATBRACK)) {
                val ret = this.scp1s { it.asscope() }
                all.accept_err(TK.CHAR, ']')
                ret
            } else {
                emptyList()
            }
            val arg = this.expr()
            val oscp = if (!all.accept(TK.CHAR, ':')) null else {
                all.accept_err(TK.CHAR, '@')
                all.accept_err(TK.XID)
                all.tk0.asscope()
            }
            e = Expr.Call(e.tk, e, arg, Pair (
                iscps.map { Scope(it,null) },
                if (oscp == null) null else Scope(oscp,null)
            ))
        }
        return e
    }

    open fun stmt (): Stmt {
        return when {
            all.accept(TK.VAR) -> {
                all.accept_err(TK.XID)
                val tk_id = all.tk0 as Tk.Id
                all.accept_err(TK.CHAR, ':')
                val tp = this.type()
                Stmt.Var(tk_id, tp)
            }
            all.accept(TK.SET) -> {
                val dst = this.attr().toExpr()
                all.accept_err(TK.CHAR, '=')
                val tk0 = all.tk0 as Tk.Chr
                when {
                    all.check(TK.INPUT) -> {
                        all.accept(TK.INPUT)
                        val tk = all.tk0 as Tk.Key
                        all.accept_err(TK.XID)
                        val lib = (all.tk0 as Tk.Id)
                        val arg = this.expr()
                        all.accept_err(TK.CHAR, ':')
                        val tp = this.type()
                        Stmt.Input(tk, tp, dst, lib, arg)
                    }
                    all.check(TK.SPAWN) -> {
                        val s = this.stmt()
                        All_assert_tk(s.tk, s is Stmt.SSpawn) { "unexpected dynamic `spawn`" }
                        val ss = s as Stmt.SSpawn
                        Stmt.SSpawn(ss.tk_, dst, ss.call)
                    }
                    else -> {
                        val src = this.expr()
                        Stmt.Set(tk0, dst, src)
                    }
                }
            }
            all.accept(TK.INPUT) -> {
                val tk = all.tk0 as Tk.Key
                all.accept_err(TK.XID)
                val lib = (all.tk0 as Tk.Id)
                val arg = this.expr()
                all.accept_err(TK.CHAR, ':')
                val tp = this.type()
                Stmt.Input(tk, tp, null, lib, arg)
            }
            all.accept(TK.IF) -> {
                val tk0 = all.tk0 as Tk.Key
                val tst = this.expr()
                val true_ = this.block()
                all.accept_err(TK.ELSE)
                val false_ = this.block()
                Stmt.If(tk0, tst, true_, false_)
            }
            all.accept(TK.RETURN) -> Stmt.Return(all.tk0 as Tk.Key)
            all.accept(TK.TYPE) -> {
                all.accept_err(TK.XID)
                val id = all.tk0.astype()
                val scp1s = if (all.check(TK.ATBRACK)) {
                    this.scopepars()
                } else {
                    Pair(emptyList(), emptyList())
                }
                all.accept_err(TK.CHAR, '=')
                val tp = this.type()
                Stmt.Typedef(id, scp1s, tp)
            }
            all.accept(TK.NATIVE) -> {
                val istype = all.accept(TK.TYPE)
                all.accept_err(TK.XNAT)
                Stmt.Native(all.tk0 as Tk.Nat, istype)
            }
            all.accept(TK.CALL) -> {
                val tk0 = all.tk0 as Tk.Key
                val e = this.expr()
                All_assert_tk(tk0, e is Expr.Call) { "expected call expression" }
                Stmt.SCall(tk0, e as Expr.Call)
            }
            all.accept(TK.SPAWN) -> {
                val tk0 = all.tk0 as Tk.Key
                val e = this.expr()
                All_assert_tk(tk0, e is Expr.Call) { "expected call expression" }
                if (all.accept(TK.IN)) {
                    val tsks = this.expr()
                    Stmt.DSpawn(tk0, tsks, e as Expr.Call)
                } else {
                    Stmt.SSpawn(tk0, null, e as Expr.Call)
                }
            }
            all.accept(TK.PAUSE) -> {
                val tk0 = all.tk0 as Tk.Key
                val e = this.expr()
                Stmt.Pause(tk0, e, true)
            }
            all.accept(TK.RESUME) -> {
                val tk0 = all.tk0 as Tk.Key
                val e = this.expr()
                Stmt.Pause(tk0, e, false)
            }
            all.accept(TK.AWAIT) -> {
                val tk0 = all.tk0 as Tk.Key
                val e = this.expr()
                Stmt.Await(tk0, e)
            }
            all.accept(TK.EMIT) -> {
                val tk0 = all.tk0 as Tk.Key
                val tgt = if (all.accept(TK.CHAR, '@')) {
                    all.accept_err(TK.XID)
                    Scope(all.tk0.asscope(),null)
                } else {
                    this.expr_dots()
                }
                val e = this.expr()
                Stmt.Emit(tk0, tgt, e)
            }
            all.accept(TK.THROW) -> {
                Stmt.Throw(all.tk0 as Tk.Key)
            }
            all.accept(TK.LOOP) -> {
                val tk0 = all.tk0 as Tk.Key
                if (all.check(TK.CHAR, '{')) {
                    val block = this.block()
                    // add additional block to break out w/ goto and cleanup
                    Stmt.Block(block.tk_, false, null,
                        Stmt.Loop(tk0, block))
                } else {
                    val i = this.expr()
                    All_assert_tk(all.tk0, i is Expr.Var) {
                        "expected variable expression"
                    }
                    all.accept_err(TK.IN)
                    val tsks = this.expr()
                    val block = this.block()
                    // add additional block to break out w/ goto and cleanup
                    Stmt.Block(block.tk_, false, null,
                        Stmt.DLoop(tk0, i as Expr.Var, tsks, block))
                }
            }
            all.accept(TK.BREAK) -> Stmt.Break(all.tk0 as Tk.Key)
            all.accept(TK.CATCH) || all.check(TK.CHAR, '{') -> this.block()
            all.accept(TK.OUTPUT) -> {
                val tk = all.tk0 as Tk.Key
                all.accept_err(TK.XID)
                val lib = (all.tk0 as Tk.Id)
                val arg = this.expr()
                Stmt.Output(tk, lib, arg)
            }
            else -> {
                all.err_expected("statement")
                error("unreachable")
            }
        }
    }

    fun scp1s (f: (Tk) -> Tk.Id): List<Tk.Id> {
        val scps = mutableListOf<Tk.Id>()
        while (all.accept(TK.XID)) {
            scps.add(f(all.tk0))
            if (!all.accept(TK.CHAR, ',')) {
                break
            }
        }
        return scps
    }

    fun scopepars (): Pair<List<Tk.Id>, List<Pair<String, String>>> {
        all.accept_err(TK.ATBRACK)
        val scps = this.scp1s { it.asscopepar() }
        val ctrs = mutableListOf<Pair<String, String>>()
        if (all.accept(TK.CHAR, ':')) {
            while (all.accept(TK.XID)) {
                val id1 = all.tk0.asscopepar().id
                all.accept_err(TK.CHAR, '>')
                all.accept_err(TK.XID)
                val id2 = all.tk0.asscopepar().id
                ctrs.add(Pair(id1, id2))
                if (!all.accept(TK.CHAR, ',')) {
                    break
                }
            }
        }
        all.accept_err(TK.CHAR, ']')
        return Pair(scps, ctrs)
    }

    fun expr_as (e: Expr): Expr {
        return if (!all.accept(TK.XAS)) e else {
            val tk0 = all.tk0 as Tk.Sym
            val type = this.type()
            All_assert_tk(all.tk0, type is Type.Alias) {
                "expected alias type"
            }
            Expr.As(tk0, e, type as Type.Alias)
        }
    }

    fun expr_dots (): Expr {
        var e = this.expr_as(this.expr_one())

        // one!1\.2?1
        while (all.accept(TK.CHAR, '\\') || all.accept(TK.CHAR, '.') || all.accept(TK.CHAR, '!') || all.accept(
                TK.CHAR,
                '?'
            )
        ) {
            val chr = all.tk0 as Tk.Chr
            e = if (chr.chr == '\\') {
                all.assert_tk(
                    all.tk0,
                    e is Expr.Nat || e is Expr.Var || e is Expr.TDisc || e is Expr.UDisc || e is Expr.Dnref || e is Expr.Upref || e is Expr.Call
                ) {
                    "unexpected operand to `\\´"
                }
                Expr.Dnref(chr, e)
            } else {
                val ok = when {
                    (chr.chr != '.') -> false
                    all.accept(TK.XID) -> {
                        val tk = all.tk0 as Tk.Id
                        all.assert_tk(tk, tk.id=="pub" || tk.id=="state") {
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
                if (chr.chr == '?' || chr.chr == '!') {
                    All_assert_tk(all.tk0, num!!.num != 0 || e is Expr.Dnref) {
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
            e = this.expr_as(e)
        }
        return e
    }

    fun attr_as (e: Attr): Attr {
        return if (!all.accept(TK.XAS)) e else {
            val tk0 = all.tk0 as Tk.Sym
            val type = this.type()
            All_assert_tk(all.tk0, type is Type.Alias) {
                "expected alias type"
            }
            Attr.As(tk0, e, type as Type.Alias)
        }
    }

    fun attr (): Attr {
        var e = when {
            all.accept(TK.XID) -> Attr.Var(all.tk0 as Tk.Id)
            all.accept(TK.XNAT) -> {
                all.accept_err(TK.CHAR, ':')
                val tp = this.type()
                Attr.Nat(all.tk0 as Tk.Nat, tp)
            }
            all.accept(TK.CHAR, '\\') -> {
                val tk0 = all.tk0 as Tk.Chr
                val e = this.attr()
                all.assert_tk(
                    all.tk0,
                    e is Attr.Nat || e is Attr.Var || e is Attr.TDisc || e is Attr.UDisc || e is Attr.Dnref
                ) {
                    "unexpected operand to `\\´"
                }
                Attr.Dnref(tk0, e)
            }
            all.accept(TK.CHAR, '(') -> {
                val e = this.attr()
                all.accept_err(TK.CHAR, ')')
                e
            }
            else -> {
                all.err_expected("expression")
                error("unreachable")
            }
        }

        e = this.attr_as(e)

        // one.1!\.2.1?
        while (all.accept(TK.CHAR, '\\') || all.accept(TK.CHAR, '.') || all.accept(TK.CHAR, '!')) {
            val chr = all.tk0 as Tk.Chr
            e = if (chr.chr == '\\') {
                all.assert_tk(
                    all.tk0,
                    e is Attr.Nat || e is Attr.Var || e is Attr.TDisc || e is Attr.UDisc || e is Attr.Dnref
                ) {
                    "unexpected operand to `\\´"
                }
                Attr.Dnref(chr, e)
            } else {
                val ok = when {
                    (chr.chr != '.') -> false
                    all.accept(TK.XID) -> {
                        val tk = all.tk0 as Tk.Id
                        all.assert_tk(tk, tk.id == "pub") {
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
                when {
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
            e = this.attr_as(e)
        }
        return e
    }

    fun block (): Stmt.Block {
        val iscatch = (all.tk0.enu == TK.CATCH)
        all.accept_err(TK.CHAR, '{')
        val tk0 = all.tk0 as Tk.Chr
        val scp1 = if (!all.accept(TK.CHAR, '@')) null else {
            all.accept_err(TK.XID)
            all.tk0.asscopecst()
        }
        val ss = this.stmts()
        all.accept_err(TK.CHAR, '}')
        return Stmt.Block(tk0, iscatch, scp1, ss).let {
            it.scp1 = it.scp1 ?: Tk.Id(TK.XID, tk0.lin, tk0.col, "B${it.n}")
            it
        }
    }


    fun stmts (): Stmt {
        fun enseq(s1: Stmt, s2: Stmt): Stmt {
            return when {
                (s1 is Stmt.Nop) -> s2
                (s2 is Stmt.Nop) -> s1
                else -> Stmt.Seq(s1.tk, s1, s2)
            }
        }

        var ret: Stmt = Stmt.Nop(all.tk0)
        while (true) {
            all.accept(TK.CHAR, ';')
            val isend = all.check(TK.CHAR, '}') || all.check(TK.EOF)
            if (!isend) {
                val s = this.stmt()
                ret = enseq(ret, s)
            } else {
                break
            }
        }
        return ret
    }
}
