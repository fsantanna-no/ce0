open class Parser
{
    open fun type (): Type {
        return when {
            alls.first().accept(TK.XID) -> {
                val tk0 = alls.first().tk0.astype()
                val scps = if (alls.first().accept(TK.ATBRACK)) {
                    val ret = this.scp1s { it.asscope() }
                    alls.first().accept_err(TK.CHAR, ']')
                    ret
                } else {
                    emptyList()
                }
                Type.Alias(tk0, false, scps.map { Scope(it,null) })
            }
            alls.first().accept(TK.CHAR, '/') -> {
                val tk0 = alls.first().tk0 as Tk.Chr
                val pln = this.type()
                val scp = if (alls.first().accept(TK.CHAR, '@')) {
                    alls.first().accept_err(TK.XID)
                    alls.first().tk0.asscope()
                } else {
                    Tk.Id(TK.XID, alls.first().tk0.lin, alls.first().tk0.col, "LOCAL")
                }
                Type.Pointer(tk0, Scope(scp,null), pln)
            }
            alls.first().accept(TK.FUNC) || alls.first().accept(TK.TASK) -> {
                val tk0 = alls.first().tk0 as Tk.Key

                val (scps, ctrs) = if (alls.first().check(TK.ATBRACK)) {
                    val (x, y) = this.scopepars()
                    alls.first().accept_err(TK.ARROW)
                    Pair(x, y)
                } else {
                    Pair(emptyList(), emptyList())
                }

                val inp = this.type()

                val pub = if (tk0.enu != TK.FUNC) {
                    alls.first().accept_err(TK.ARROW)
                    this.type()
                } else null

                alls.first().accept_err(TK.ARROW)
                val out = this.type() // right associative

                Type.Func(tk0,
                    Triple (
                        Scope(Tk.Id(TK.XID, tk0.lin, tk0.col, "LOCAL"),null),
                        scps.map { Scope(it,null) },
                        ctrs
                    ),
                    inp, pub, out)
            }
            alls.first().accept(TK.UNIT) -> Type.Unit(alls.first().tk0 as Tk.Sym)
            alls.first().accept(TK.XNAT) -> Type.Nat(alls.first().tk0 as Tk.Nat)
            alls.first().accept(TK.CHAR, '(') -> {
                val tp = this.type()
                alls.first().accept_err(TK.CHAR, ')')
                tp
            }
            alls.first().accept(TK.CHAR, '[') || alls.first().accept(TK.CHAR, '<') -> {
                val tk0 = alls.first().tk0 as Tk.Chr
                val tp = this.type()
                val tps = arrayListOf(tp)
                while (true) {
                    if (!alls.first().accept(TK.CHAR, ',')) {
                        break
                    }
                    val tp2 = this.type()
                    tps.add(tp2)
                }
                if (tk0.chr == '[') {
                    alls.first().accept_err(TK.CHAR, ']')
                    Type.Tuple(tk0, tps)
                } else {
                    alls.first().accept_err(TK.CHAR, '>')
                    val vec = tps
                    Type.Union(tk0, vec)
                }
            }
            alls.first().accept(TK.ACTIVE) -> {
                val tk0 = alls.first().tk0 as Tk.Key
                val (isdyn,len) = if (!alls.first().accept(TK.CHAR,'{')) Pair(false,null) else {
                    val len = if (alls.first().accept(TK.XNUM)) alls.first().tk0 as Tk.Num else null
                    alls.first().accept_err(TK.CHAR, '}')
                    Pair(true, len)
                }
                //alls.first().check_err(TK.TASK)
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
                alls.first().err_expected("type")
                error("unreachable")
            }
        }
    }

    open fun expr_one (): Expr {
        return when {
            alls.first().accept(TK.XNAT) -> {
                val tk0 = alls.first().tk0 as Tk.Nat
                alls.first().accept_err(TK.CHAR, ':')
                val tp = this.type()
                Expr.Nat(tk0, tp)
            }
            alls.first().accept(TK.CHAR, '<') -> {
                alls.first().accept_err(TK.CHAR, '.')
                alls.first().accept_err(TK.XNUM)
                val tk0 = alls.first().tk0 as Tk.Num
                val cons = if (tk0.num == 0) null else this.expr()
                alls.first().accept_err(TK.CHAR, '>')
                alls.first().accept_err(TK.CHAR, ':')
                val tp = this.type()
                when (tk0.num) {
                    0 -> alls.first().assert_tk(tp.tk,tp is Type.Pointer && tp.pln is Type.Alias) {
                        "invalid type : expected pointer to alias type"
                    }
                    else -> alls.first().assert_tk(tp.tk,tp is Type.Union) {
                        "invalid type : expected union type"
                    }
                }
                if (tk0.num == 0) {
                    Expr.UNull(tk0, tp as Type.Pointer)
                } else {
                    Expr.UCons(tk0, tp as Type.Union, cons!!)
                }
            }
            alls.first().accept(TK.NEW) -> {
                val tk0 = alls.first().tk0
                val e = this.expr()
                alls.first().assert_tk(tk0,e is Expr.As && e.e is Expr.UCons && e.e.tk_.num!=0) {
                    "invalid `new` : expected alias constructor"
                }

                val scp = if (alls.first().accept(TK.CHAR, ':')) {
                    alls.first().accept_err(TK.CHAR, '@')
                    alls.first().accept_err(TK.XID)
                    alls.first().tk0.asscope()
                } else {
                    Tk.Id(TK.XID, alls.first().tk0.lin, alls.first().tk0.col, "LOCAL")
                }
                Expr.New(tk0 as Tk.Key, Scope(scp,null), e as Expr.As)
            }

            alls.first().accept(TK.UNIT) -> Expr.Unit(alls.first().tk0 as Tk.Sym)
            alls.first().tk1.isvar() && alls.first().accept(TK.XID) -> Expr.Var(alls.first().tk0 as Tk.Id)
            alls.first().accept(TK.CHAR, '/') -> {
                val tk0 = alls.first().tk0 as Tk.Chr
                val e = this.expr()
                alls.first().assert_tk(
                    alls.first().tk0,
                    e is Expr.Nat || e is Expr.Var || e is Expr.TDisc || e is Expr.Dnref || e is Expr.Upref
                ) {
                    "unexpected operand to `/´"
                }
                Expr.Upref(tk0, e)
            }
            alls.first().accept(TK.CHAR, '(') -> {
                val e = this.expr()
                alls.first().accept_err(TK.CHAR, ')')
                e
            }
            alls.first().accept(TK.CHAR, '[') -> {
                val tk0 = alls.first().tk0 as Tk.Chr
                val e = this.expr()
                val es = arrayListOf(e)
                while (true) {
                    if (!alls.first().accept(TK.CHAR, ',')) {
                        break
                    }
                    val e2 = this.expr()
                    es.add(e2)
                }
                alls.first().accept_err(TK.CHAR, ']')
                Expr.TCons(tk0, es)
            }
            alls.first().check(TK.TASK) || alls.first().check(TK.FUNC) -> {
                val tk = alls.first().tk1 as Tk.Key
                val tp = this.type() as Type.Func
                val block = this.block()
                Expr.Func(tk, tp, block)
            }
            else -> {
                alls.first().err_expected("expression")
                error("unreachable")
            }
        }
    }

    open fun expr (): Expr {
        var e = this.expr_dots()

        // call
        if (alls.first().checkExpr() || alls.first().check(TK.ATBRACK)) {
            val iscps = if (alls.first().accept(TK.ATBRACK)) {
                val ret = this.scp1s { it.asscope() }
                alls.first().accept_err(TK.CHAR, ']')
                ret
            } else {
                emptyList()
            }
            val arg = this.expr()
            val oscp = if (!alls.first().accept(TK.CHAR, ':')) null else {
                alls.first().accept_err(TK.CHAR, '@')
                alls.first().accept_err(TK.XID)
                alls.first().tk0.asscope()
            }
            e = Expr.Call(e.tk, e, arg, Pair (
                iscps.map { Scope(it,null) },
                if (oscp == null) null else Scope(oscp,null)
            ))
        }
        return e
    }

    fun set_tail (tk0: Tk.Chr, dst: Expr): Stmt {
        return when {
            alls.first().accept(TK.INPUT) -> {
                val tk = alls.first().tk0 as Tk.Key
                alls.first().accept_err(TK.XID)
                val lib = (alls.first().tk0 as Tk.Id)
                val arg = this.expr()
                alls.first().accept_err(TK.CHAR, ':')
                val tp = this.type()
                Stmt.Input(tk, tp, dst, lib, arg)
            }
            alls.first().check(TK.SPAWN) -> {
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

    open fun stmt (): Stmt {
        return when {
            alls.first().accept(TK.VAR) -> {
                alls.first().accept_err(TK.XID)
                val tk_id = alls.first().tk0 as Tk.Id
                alls.first().accept_err(TK.CHAR, ':')
                val tp = this.type()
                Stmt.Var(tk_id, tp, null)
            }
            alls.first().accept(TK.SET) -> {
                val dst = this.attr().toExpr()
                alls.first().accept_err(TK.CHAR, '=')
                val tk0 = alls.first().tk0 as Tk.Chr
                this.set_tail(tk0, dst)
            }
            alls.first().accept(TK.INPUT) -> {
                val tk = alls.first().tk0 as Tk.Key
                alls.first().accept_err(TK.XID)
                val lib = (alls.first().tk0 as Tk.Id)
                val arg = this.expr()
                alls.first().accept_err(TK.CHAR, ':')
                val tp = this.type()
                Stmt.Input(tk, tp, null, lib, arg)
            }
            alls.first().accept(TK.IF) -> {
                val tk0 = alls.first().tk0 as Tk.Key
                val tst = this.expr()
                val true_ = this.block()
                alls.first().accept_err(TK.ELSE)
                val false_ = this.block()
                Stmt.If(tk0, tst, true_, false_)
            }
            alls.first().accept(TK.RETURN) -> Stmt.Return(alls.first().tk0 as Tk.Key)
            alls.first().accept(TK.TYPE) -> {
                alls.first().accept_err(TK.XID)
                val id = alls.first().tk0.astype()
                val scp1s = if (alls.first().check(TK.ATBRACK)) {
                    this.scopepars()
                } else {
                    Pair(emptyList(), emptyList())
                }
                alls.first().accept_err(TK.CHAR, '=')
                val tp = this.type()
                Stmt.Typedef(id, scp1s, tp)
            }
            alls.first().accept(TK.NATIVE) -> {
                val istype = alls.first().accept(TK.TYPE)
                alls.first().accept_err(TK.XNAT)
                Stmt.Native(alls.first().tk0 as Tk.Nat, istype)
            }
            alls.first().accept(TK.CALL) -> {
                val tk0 = alls.first().tk0 as Tk.Key
                val e = this.expr()
                All_assert_tk(tk0, e is Expr.Call) { "expected call expression" }
                Stmt.SCall(tk0, e as Expr.Call)
            }
            alls.first().accept(TK.SPAWN) -> {
                val tk0 = alls.first().tk0 as Tk.Key
                val e = this.expr()
                All_assert_tk(tk0, e is Expr.Call) { "expected call expression" }
                if (alls.first().accept(TK.IN)) {
                    val tsks = this.expr()
                    Stmt.DSpawn(tk0, tsks, e as Expr.Call)
                } else {
                    Stmt.SSpawn(tk0, null, e as Expr.Call)
                }
            }
            alls.first().accept(TK.PAUSE) -> {
                val tk0 = alls.first().tk0 as Tk.Key
                val e = this.expr()
                Stmt.Pause(tk0, e, true)
            }
            alls.first().accept(TK.RESUME) -> {
                val tk0 = alls.first().tk0 as Tk.Key
                val e = this.expr()
                Stmt.Pause(tk0, e, false)
            }
            alls.first().accept(TK.AWAIT) -> {
                val tk0 = alls.first().tk0 as Tk.Key
                val e = this.expr()
                Stmt.Await(tk0, e)
            }
            alls.first().accept(TK.EMIT) -> {
                val tk0 = alls.first().tk0 as Tk.Key
                val tgt = if (alls.first().accept(TK.CHAR, '@')) {
                    alls.first().accept_err(TK.XID)
                    Scope(alls.first().tk0.asscope(),null)
                } else {
                    this.expr_dots()
                }
                val e = this.expr()
                Stmt.Emit(tk0, tgt, e)
            }
            alls.first().accept(TK.THROW) -> {
                Stmt.Throw(alls.first().tk0 as Tk.Key)
            }
            alls.first().accept(TK.LOOP) -> {
                val tk0 = alls.first().tk0 as Tk.Key
                if (alls.first().check(TK.CHAR, '{')) {
                    val block = this.block()
                    // add additional block to break out w/ goto and cleanup
                    Stmt.Block(block.tk_, false, null,
                        Stmt.Loop(tk0, block))
                } else {
                    val i = this.expr()
                    All_assert_tk(alls.first().tk0, i is Expr.Var) {
                        "expected variable expression"
                    }
                    alls.first().accept_err(TK.IN)
                    val tsks = this.expr()
                    val block = this.block()
                    // add additional block to break out w/ goto and cleanup
                    Stmt.Block(block.tk_, false, null,
                        Stmt.DLoop(tk0, i as Expr.Var, tsks, block))
                }
            }
            alls.first().accept(TK.BREAK) -> Stmt.Break(alls.first().tk0 as Tk.Key)
            alls.first().accept(TK.CATCH) || alls.first().check(TK.CHAR, '{') -> this.block()
            alls.first().accept(TK.OUTPUT) -> {
                val tk = alls.first().tk0 as Tk.Key
                alls.first().accept_err(TK.XID)
                val lib = (alls.first().tk0 as Tk.Id)
                val arg = this.expr()
                Stmt.Output(tk, lib, arg)
            }
            else -> {
                alls.first().err_expected("statement")
                error("unreachable")
            }
        }
    }

    fun scp1s (f: (Tk) -> Tk.Id): List<Tk.Id> {
        val scps = mutableListOf<Tk.Id>()
        while (alls.first().accept(TK.XID)) {
            scps.add(f(alls.first().tk0))
            if (!alls.first().accept(TK.CHAR, ',')) {
                break
            }
        }
        return scps
    }

    fun scopepars (): Pair<List<Tk.Id>, List<Pair<String, String>>> {
        alls.first().accept_err(TK.ATBRACK)
        val scps = this.scp1s { it.asscopepar() }
        val ctrs = mutableListOf<Pair<String, String>>()
        if (alls.first().accept(TK.CHAR, ':')) {
            while (alls.first().accept(TK.XID)) {
                val id1 = alls.first().tk0.asscopepar().id
                alls.first().accept_err(TK.CHAR, '>')
                alls.first().accept_err(TK.XID)
                val id2 = alls.first().tk0.asscopepar().id
                ctrs.add(Pair(id1, id2))
                if (!alls.first().accept(TK.CHAR, ',')) {
                    break
                }
            }
        }
        alls.first().accept_err(TK.CHAR, ']')
        return Pair(scps, ctrs)
    }

    fun expr_as (e: Expr): Expr {
        return if (!alls.first().accept(TK.XAS)) e else {
            val tk0 = alls.first().tk0 as Tk.Sym
            val type = this.type()
            All_assert_tk(alls.first().tk0, type is Type.Alias) {
                "expected alias type"
            }
            Expr.As(tk0, e, type as Type.Alias)
        }
    }

    fun expr_dots (): Expr {
        var e = this.expr_as(this.expr_one())

        // one!1\.2?1
        while (alls.first().accept(TK.CHAR, '\\') || alls.first().accept(TK.CHAR, '.') || alls.first().accept(TK.CHAR, '!') || alls.first().accept(
                TK.CHAR,
                '?'
            )
        ) {
            val chr = alls.first().tk0 as Tk.Chr
            e = if (chr.chr == '\\') {
                alls.first().assert_tk(
                    alls.first().tk0,
                    e is Expr.Nat || e is Expr.Var || e is Expr.TDisc || e is Expr.UDisc || e is Expr.Dnref || e is Expr.Upref || e is Expr.Call
                ) {
                    "unexpected operand to `\\´"
                }
                Expr.Dnref(chr, e)
            } else {
                val ok = when {
                    (chr.chr != '.') -> false
                    alls.first().accept(TK.XID) -> {
                        val tk = alls.first().tk0 as Tk.Id
                        alls.first().assert_tk(tk, tk.id=="pub" || tk.id=="ret" || tk.id=="state") {
                            "unexpected \"${tk.id}\""
                        }
                        true
                    }
                    else -> false
                }
                if (!ok) {
                    alls.first().accept_err(TK.XNUM)
                }
                val num = if (ok) null else (alls.first().tk0 as Tk.Num)
                alls.first().assert_tk(alls.first().tk0, e !is Expr.TCons && e !is Expr.UCons && e !is Expr.UNull) {
                    "invalid discriminator : unexpected constructor"
                }
                if (chr.chr == '?' || chr.chr == '!') {
                    All_assert_tk(alls.first().tk0, num!!.num != 0 || e is Expr.Dnref) {
                        "invalid discriminator : union cannot be <.0>"
                    }
                }
                when {
                    (chr.chr == '?') -> Expr.UPred(num!!, e)
                    (chr.chr == '!') -> Expr.UDisc(num!!, e)
                    (chr.chr == '.') -> {
                        if (alls.first().tk0.enu == TK.XID) {
                            Expr.Field(alls.first().tk0 as Tk.Id, e)
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
        return if (!alls.first().accept(TK.XAS)) e else {
            val tk0 = alls.first().tk0 as Tk.Sym
            val type = this.type()
            All_assert_tk(alls.first().tk0, type is Type.Alias) {
                "expected alias type"
            }
            Attr.As(tk0, e, type as Type.Alias)
        }
    }

    fun attr (): Attr {
        var e = when {
            alls.first().accept(TK.XID) -> Attr.Var(alls.first().tk0 as Tk.Id)
            alls.first().accept(TK.XNAT) -> {
                alls.first().accept_err(TK.CHAR, ':')
                val tp = this.type()
                Attr.Nat(alls.first().tk0 as Tk.Nat, tp)
            }
            alls.first().accept(TK.CHAR, '\\') -> {
                val tk0 = alls.first().tk0 as Tk.Chr
                val e = this.attr()
                alls.first().assert_tk(
                    alls.first().tk0,
                    e is Attr.Nat || e is Attr.Var || e is Attr.TDisc || e is Attr.UDisc || e is Attr.Dnref
                ) {
                    "unexpected operand to `\\´"
                }
                Attr.Dnref(tk0, e)
            }
            alls.first().accept(TK.CHAR, '(') -> {
                val e = this.attr()
                alls.first().accept_err(TK.CHAR, ')')
                e
            }
            else -> {
                alls.first().err_expected("expression")
                error("unreachable")
            }
        }

        e = this.attr_as(e)

        // one.1!\.2.1?
        while (alls.first().accept(TK.CHAR, '\\') || alls.first().accept(TK.CHAR, '.') || alls.first().accept(TK.CHAR, '!')) {
            val chr = alls.first().tk0 as Tk.Chr
            e = if (chr.chr == '\\') {
                alls.first().assert_tk(
                    alls.first().tk0,
                    e is Attr.Nat || e is Attr.Var || e is Attr.TDisc || e is Attr.UDisc || e is Attr.Dnref
                ) {
                    "unexpected operand to `\\´"
                }
                Attr.Dnref(chr, e)
            } else {
                val ok = when {
                    (chr.chr != '.') -> false
                    alls.first().accept(TK.XID) -> {
                        val tk = alls.first().tk0 as Tk.Id
                        alls.first().assert_tk(tk, tk.id == "pub") {
                            "unexpected \"${tk.id}\""
                        }
                        true
                    }
                    else -> false
                }
                if (!ok) {
                    alls.first().accept_err(TK.XNUM)
                }
                val num = if (ok) null else (alls.first().tk0 as Tk.Num)
                when {
                    (chr.chr == '!') -> Attr.UDisc(num!!, e)
                    (chr.chr == '.') -> {
                        if (alls.first().tk0.enu == TK.XID) {
                            Attr.Field(alls.first().tk0 as Tk.Id, e)
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
        val iscatch = (alls.first().tk0.enu == TK.CATCH)
        alls.first().accept_err(TK.CHAR, '{')
        val tk0 = alls.first().tk0 as Tk.Chr
        val scp1 = if (!alls.first().accept(TK.CHAR, '@')) null else {
            alls.first().accept_err(TK.XID)
            alls.first().tk0.asscopecst()
        }
        val ss = this.stmts()
        alls.first().accept_err(TK.CHAR, '}')
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

        var ret: Stmt = Stmt.Nop(alls.first().tk0)
        while (true) {
            alls.first().accept(TK.CHAR, ';')
            val isend = alls.first().check(TK.CHAR, '}') || alls.first().check(TK.EOF)
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
