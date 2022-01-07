data class Env (val s: Stmt.Var, val prv: Env?)

object AUX {
    val ups = mutableMapOf<Any,Any>()
    val env = mutableMapOf<Any,Env>()
    val tps = mutableMapOf<Expr,Type>()
}

//////////////////////////////////////////////////////////////////////////////

// TODO: use these variations?
/*
fun Any?.ups_tolist2(): List<Any> {
    return when {
        (this == null) -> emptyList()
        else -> { listOf(this) + AUX.ups[this].ups_tolist2() }
    }
}

fun Any?.ups_first2 (f: (Any)->Boolean): Any? {
    return when {
        (this == null) -> null
        f(this) -> this
        else -> AUX.ups[this]?.ups_first(f)
    }
}
 */

fun Any.ups_tolist(): List<Any> {
    return when {
        (AUX.ups[this] == null) -> emptyList()
        else -> AUX.ups[this]!!.let { listOf(it) + it.ups_tolist() }
    }
}

fun Any.ups_first (f: (Any)->Boolean): Any? {
    val up = AUX.ups[this]
    return when {
        (up == null) -> null
        f(up) -> up
        else -> up.ups_first(f)
    }
}

fun Any.env_first (f: (Stmt)->Boolean): Stmt? {
    fun aux (env: Env?): Stmt? {
        return when {
            (env == null) -> null
            f(env.s) -> env.s
            else -> aux(env.prv)
        }
    }
    return aux (AUX.env[this])
}

fun Any.env (id: String): Stmt.Var? {
    return this.env_first { it is Stmt.Var && it.tk_.str==id } as Stmt.Var?
}

fun Expr.Var.env (): Stmt.Var? {
    return this.env_first { it is Stmt.Var && it.tk_.str==this.tk_.str } as Stmt.Var?
}

//////////////////////////////////////////////////////////////////////////////

private
fun ups_add (v: Any, up: Any?) {
    if (up == null) return
    //assert(AUX.ups[v] == null)    // fails b/c of expands
    AUX.ups[v] = up
}

private
fun env_add (v: Any, env: Env?) {
    if (env == null) return
    assert(AUX.env[v] == null)
    AUX.env[v] = env
}

fun aux_clear () {
    AUX.ups.clear()
    AUX.env.clear()
    AUX.tps.clear()
}

private
fun Type.aux_upsenvs (up: Any) {
    ups_add(this, up)

    when (this) {
        is Type.Tuple -> this.vec.forEach { it.aux_upsenvs(this) }
        is Type.Union -> this.vec.forEach { it.aux_upsenvs(this) }
        is Type.Func  -> { this.inp.aux_upsenvs(this) ; this.out.aux_upsenvs(this) }
        is Type.Ptr   -> this.pln.aux_upsenvs(this)
    }
}

private
fun Expr.aux_upsenvs (up: Any, env: Env?) {
    ups_add(this, up)
    env_add(this, env)
    when (this) {
        is Expr.TCons -> this.arg.forEachIndexed { _,e -> e.aux_upsenvs(this, env) }
        is Expr.UCons -> { this.type!!.aux_upsenvs(this) ; this.arg.aux_upsenvs(this, env) }
        is Expr.New   -> this.arg.aux_upsenvs(this, env)
        is Expr.Dnref -> this.ptr.aux_upsenvs(this, env)
        is Expr.Upref -> this.pln.aux_upsenvs(this, env)
        is Expr.TDisc -> this.tup.aux_upsenvs(this, env)
        is Expr.UDisc -> this.uni.aux_upsenvs(this, env)
        is Expr.UPred -> this.uni.aux_upsenvs(this, env)
        is Expr.Out   -> this.arg.aux_upsenvs(this, env)
        is Expr.Call  -> {
            this.f.aux_upsenvs(this, env)
            this.arg.aux_upsenvs(this, env)
        }
        is Expr.Func  -> {
            this.type.aux_upsenvs(this)
            this.block.aux_upsenvs(this, env)
        }
    }
}

fun Stmt.aux_upsenvs (up: Any?, env: Env?): Env? {
    ups_add(this, up)
    env_add(this, env)
    return when (this) {
        is Stmt.Nop, is Stmt.Nat, is Stmt.Ret, is Stmt.Break -> env
        is Stmt.Var -> {
            this.type!!.aux_upsenvs(this)
            Env(this,env)
        }
        is Stmt.Set -> {
            this.dst.aux_upsenvs(this, env)
            this.src.aux_upsenvs(this, env)
            env
        }
        is Stmt.SExpr -> { this.expr.aux_upsenvs(this, env) ; env }
        is Stmt.Seq -> {
            val e1 = this.s1.aux_upsenvs(this, env)
            val e2 = this.s2.aux_upsenvs(this, e1)
            e2
        }
        is Stmt.If -> {
            this.tst.aux_upsenvs(this,env)
            this.true_.aux_upsenvs(this, env)
            this.false_.aux_upsenvs(this, env)
            env
        }
        is Stmt.Loop  -> { this.block.aux_upsenvs(this,env) ; env }
        is Stmt.Block -> { this.body.aux_upsenvs(this,env) ; env }
    }
}

///////////////////////////////////////////////////////////////////////////////

//private
fun Type.up (up: Any): Type {
    ups_add(this, up)
    return this
}

fun Aux_tps (s: Stmt) {
    fun fe (e: Expr) {
        AUX.tps[e] = when (e) {
            is Expr.Unit  -> Type.Unit(e.tk_).up(e)
            is Expr.Nat   -> e.type ?: Type.Nat(e.tk_).up(e)
            is Expr.Upref -> AUX.tps[e.pln]!!.let {
                Type.Ptr(e.tk_, Tk.Scope(TK.XSCOPE,e.tk.lin,e.tk.col,"var",null), it).up(it)
            }
            is Expr.Dnref -> AUX.tps[e.ptr].let {
                if (it is Type.Nat) it else {
                    All_assert_tk(e.tk, it is Type.Ptr) {
                        "invalid operand to `\\´ : not a pointer"
                    }
                    (it as Type.Ptr).pln
                }
            }
            is Expr.TCons -> Type.Tuple(e.tk_, e.arg.map { AUX.tps[it]!! }.toTypedArray()).up(e)
            is Expr.UCons -> e.type!!
            is Expr.New   -> Type.Ptr(Tk.Chr(TK.CHAR,e.tk.lin,e.tk.col,'/'), e.scope!!, AUX.tps[e.arg]!!).up(e)
            is Expr.Inp   -> e.type!!
            is Expr.Out   -> Type.Unit(Tk.Sym(TK.UNIT, e.tk.lin, e.tk.col, "()")).up(e)
            is Expr.Call -> {
                AUX.tps[e.f].let {
                    when (it) {
                        is Type.Nat -> it
                        is Type.Func -> {
                            val MAP = it.scps.map { Pair(it.lbl,it.num) }.zip(e.sinps.map { Pair(it.lbl,it.num) }).toMap()
                            fun f (tk: Tk.Scope): Tk.Scope {
                                return MAP[Pair(tk.lbl,tk.num)].let { if (it == null) tk else
                                    Tk.Scope(TK.XSCOPE, tk.lin, tk.col, it.first, it.second)
                                }
                            }
                            fun map (tp: Type): Type {
                                return when (tp) {
                                    is Type.Ptr   -> Type.Ptr(tp.tk_, f(tp.scope), map(tp.pln))
                                    is Type.Tuple -> Type.Tuple(tp.tk_, tp.vec.map { map(it) }.toTypedArray())
                                    is Type.Union -> Type.Union(tp.tk_, tp.isrec, tp.vec.map { map(it) }.toTypedArray())
                                    is Type.Func  -> Type.Func(tp.tk_, if (tp.clo==null) tp.clo else f(tp.clo), tp.scps.map { f(it) }.toTypedArray(), map(tp.inp), map(tp.out))
                                    else -> tp
                                }
                            }
                            map(it.out)
                        }
                        else -> {
                            All_assert_tk(e.f.tk, false) {
                                "invalid call : not a function"
                            }
                            error("impossible case")
                        }
                    }
                }.lincol(e.tk.lin,e.tk.col).let {
                    it.aux_upsenvs(e)
                    it
                }
            }
            is Expr.Func -> e.type
            is Expr.TDisc -> AUX.tps[e.tup].let {
                All_assert_tk(e.tk, it is Type.Tuple) {
                    "invalid discriminator : type mismatch"
                }
                val (MIN, MAX) = Pair(1, (it as Type.Tuple).vec.size)
                All_assert_tk(e.tk, MIN <= e.tk_.num && e.tk_.num <= MAX) {
                    "invalid discriminator : out of bounds"
                }
                it.vec[e.tk_.num - 1]
            }
            is Expr.UDisc, is Expr.UPred -> {
                val (tk_,uni) = when (e) {
                    is Expr.UPred -> Pair(e.tk_,e.uni)
                    is Expr.UDisc -> Pair(e.tk_,e.uni)
                    else -> error("impossible case")
                }
                val tp = AUX.tps[uni]!!

                All_assert_tk(e.tk, tp is Type.Union) {
                    "invalid discriminator : not an union"
                }
                assert(tk_.num!=0 || tp.isrec()) { "bug found" }

                val (MIN, MAX) = Pair(if (tp.isrec()) 0 else 1, (tp as Type.Union).vec.size)
                All_assert_tk(e.tk, MIN <= tk_.num && tk_.num <= MAX) {
                    "invalid discriminator : out of bounds"
                }

                when (e) {
                    is Expr.UDisc -> if (e.tk_.num == 0) {
                        Type.Unit(Tk.Sym(TK.UNIT, e.tk.lin, e.tk.col, "()")).up(e)
                    } else {
                        tp.expand()[e.tk_.num - 1]
                    }
                    is Expr.UPred -> Type.Nat(Tk.Str(TK.XNAT, e.tk.lin, e.tk.col, "int")).up(e)
                    else -> error("bug found")
                }
            }
            is Expr.Var -> e.env()!!.type!!
        }
    }
    s.visit(null, ::fe, null)
}
