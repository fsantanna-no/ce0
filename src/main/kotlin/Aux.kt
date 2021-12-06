data class Scope (val level: Int, val isabs: Boolean, val depth: Int)
data class Env (val s: Stmt.Var, val prv: Env?)

object AUX {
    // aux_01
    val ups = mutableMapOf<Any,Any>()
    val env = mutableMapOf<Any,Env>()
    val scp = mutableMapOf<Type.Ptr,Scope>()
    val tps = mutableMapOf<Expr,Type>()
    val xps = mutableMapOf<Expr,Type>()
}

//////////////////////////////////////////////////////////////////////////////

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

fun env_prelude (s: Stmt): Stmt {
    val stdo = Stmt.Var (
        Tk.Str(TK.XVAR,1,1,"output_std"),
        Type.Func (
            Tk.Sym(TK.ARROW, 1, 1, "->"),
            Type.Any(Tk.Chr(TK.CHAR,1,1,'?')),
            Type.Unit(Tk.Sym(TK.UNIT,1,1,"()"))
        )
    )
    return Stmt.Seq(stdo.tk, stdo, s)
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
    AUX.xps.clear()
}

private
fun Type.aux_01_upsenvs (up: Any) {
    ups_add(this, up)

    // Derived scope from pointers above myself:
    // /(/x)@a      --> /x is also @a
    // func /x->()  --> /x is @1 (default)
    fun up (tp: Type.Ptr): String? {
        var cur: Type = tp
        while (true) {
            val nxt = AUX.ups[cur]
            when {
                (cur is Type.Ptr && cur.scope!=null) -> return cur.scope!!
                (nxt == null) -> return null
                (nxt is Type.Func) -> return "@1"
                (nxt is Stmt.Var && nxt.tk_.str=="_ret_") -> return "@1"
                (nxt !is Type) -> return null
                else -> cur = nxt
            }
        }
    }

    when (this) {
        is Type.Tuple -> this.vec.forEach { it.aux_01_upsenvs(this) }
        is Type.Union -> this.vec.forEach { it.aux_01_upsenvs(this) }
        is Type.UCons -> this.arg.aux_01_upsenvs(this)
        is Type.Func  -> { this.inp.aux_01_upsenvs(this) ; this.out.aux_01_upsenvs(this) }
        is Type.Ptr   -> this.pln.aux_01_upsenvs(this)
    }
}

private
fun Expr.aux_01_upsenvs (up: Any, env: Env?) {
    ups_add(this, up)
    env_add(this, env)
    when (this) {
        is Expr.TCons -> this.arg.forEachIndexed { _,e -> e.aux_01_upsenvs(this, env) }
        is Expr.UCons -> this.arg.aux_01_upsenvs(this, env)
        is Expr.New   -> this.arg.aux_01_upsenvs(this, env)
        is Expr.Dnref -> this.ptr.aux_01_upsenvs(this, env)
        is Expr.Upref -> this.pln.aux_01_upsenvs(this, env)
        is Expr.TDisc -> this.tup.aux_01_upsenvs(this, env)
        is Expr.UDisc -> this.uni.aux_01_upsenvs(this, env)
        is Expr.UPred -> this.uni.aux_01_upsenvs(this, env)
        is Expr.Call  -> {
            this.f.aux_01_upsenvs(this, env)
            this.arg.aux_01_upsenvs(this, env)
        }
        is Expr.Func  -> {
            this.type.aux_01_upsenvs(this)
            this.block.aux_01_upsenvs(this, env)
        }
    }
}

fun Stmt.aux_01_upsenvs (up: Any?, env: Env?): Env? {
    ups_add(this, up)
    env_add(this, env)
    return when (this) {
        is Stmt.Pass, is Stmt.Nat, is Stmt.Ret, is Stmt.Break -> env
        is Stmt.Var -> {
            this.type.aux_01_upsenvs(this)
            Env(this,env)
        }
        is Stmt.Set -> {
            this.dst.aux_01_upsenvs(this, env)
            this.src.aux_01_upsenvs(this, env)
            env
        }
        is Stmt.Call -> { this.call.aux_01_upsenvs(this, env) ; env }
        is Stmt.Seq -> {
            val e1 = this.s1.aux_01_upsenvs(this, env)
            val e2 = this.s2.aux_01_upsenvs(this, e1)
            e2
        }
        is Stmt.If -> {
            this.tst.aux_01_upsenvs(this,env)
            this.true_.aux_01_upsenvs(this, env)
            this.false_.aux_01_upsenvs(this, env)
            env
        }
        is Stmt.Loop  -> { this.block.aux_01_upsenvs(this,env) ; env }
        is Stmt.Block -> { this.body.aux_01_upsenvs(this,env) ; env }
    }
}

///////////////////////////////////////////////////////////////////////////////

private
fun Type.up (up: Any): Type {
    ups_add(this, up)
    return this
}

fun Aux_02_tps (s: Stmt) {
    fun fe (e: Expr) {
        AUX.tps[e] = when (e) {
            is Expr.Unit  -> Type.Unit(e.tk_).up(e)
            is Expr.Nat   -> Type.Nat(e.tk_).up(e)
            is Expr.Upref -> AUX.tps[e.pln]!!.let { Type.Ptr(e.tk_, null, it).up(it) }
            is Expr.Dnref -> AUX.tps[e.ptr].let {
                if (it is Type.Nat) it else {
                    All_assert_tk(e.tk, it is Type.Ptr) {
                        "invalid operand to `\\´ : not a pointer"
                    }
                    (it as Type.Ptr).pln
                }
            }
            is Expr.TCons -> Type.Tuple(e.tk_, e.arg.map { AUX.tps[it]!! }.toTypedArray()).up(e)
            is Expr.UCons -> Type.UCons(e.tk_, AUX.tps[e.arg]!!).up(e)
            is Expr.New   -> Type.Ptr(Tk.Chr(TK.CHAR,e.tk.lin,e.tk.col,'/'), null, AUX.tps[e.arg]!!)
            is Expr.Call -> {
                AUX.tps[e.f].let {
                    when (it) {
                        // scope of output is tested in the call through XP
                        // here, just returns the "top" scope to succeed
                        is Type.Func -> it.out.map { if (it !is Type.Ptr) it else Type.Ptr(it.tk_, "@global", it.pln).scp_add(Scope(0,true,0)) }
                        is Type.Nat  -> it //Type.Nat(it.tk_).ups(e)
                        else -> {
                            All_assert_tk(e.f.tk, false) {
                                "invalid call : not a function"
                            }
                            error("impossible case")
                        }
                    }
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
                it.vec[e.tk_.num - 1].up(e)
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
                        Type_Unit(e.tk).up(e)
                    } else {
                        tp.expand()[e.tk_.num - 1].up(e)
                    }
                    is Expr.UPred -> Type.Nat(Tk.Str(TK.XNAT, e.tk.lin, e.tk.col, "int")).up(e)
                    else -> error("bug found")
                }
            }
            is Expr.Var -> e.env()!!.type
        }
    }
    s.visit(null, ::fe, null)
}

///////////////////////////////////////////////////////////////////////////////

// Every single Type.Ptr created after Aux_03_scp must use this function
//  Functions that currently do not use it:
//      - Type.map (not used after AUX.tps)
//      - Type.lincol (not used after parser)
//      - Type.Union.expand (used after Aux_03_scp, problem??)
fun Type.Ptr.scp_add (scope: Scope): Type.Ptr {
    AUX.scp[this] = scope
    return this
}

fun Aux_03_scp () {
    fun ft (tp: Type) {
        if (tp !is Type.Ptr) {
            return
        }

        // Derived scope from pointers above myself:
        // /(/x)@a      --> /x is also @a
        // func /x->()  --> /x is @1 (default)
        fun up (tp: Type.Ptr): String? {
            var cur: Type = tp
            while (true) {
                val nxt = AUX.ups[cur]
                when {
                    (cur is Type.Ptr && cur.scope != null) -> return cur.scope!!
                    (nxt == null) -> return null
                    (nxt is Type.Func) -> return "@1"
                    (nxt is Stmt.Var && nxt.tk_.str == "_ret_") -> return "@1"
                    (nxt !is Type) -> return null
                    else -> cur = nxt
                }
            }
        }

        // Offset of @N (max) of all crossing functions:
        //  func /()->() {              // +1
        //      func [/()@1,/()@2] {    // +2
        //          ...                 // off=3
        fun off (ups: List<Any>): Int {
            return ups
                .filter { it is Expr.Func }
                .map {
                    (it as Expr.Func).type.flatten().filter { it is Type.Ptr }
                        .map { up(it as Type.Ptr) }
                        .filterNotNull()
                        .map { it.toIntOrNull() }
                        .filterNotNull()
                        .maxOrNull() ?: 0
                }
                .sum()
        }

        // Level of function nesting:
        //  func ... {
        //      ...             // lvl=1
        //      func ... {
        //          ...         // lvl=1
        val lvl = tp.level()
        // dropWhile(Type).drop(1) so that prototype skips up func
        //val lvl = this.ups_tolist().dropWhile { it is Type }.drop(1).filter { it is Expr.Func }.count()

        val id = up(tp)
        AUX.scp[tp] = when (id) {
            null -> Scope(lvl, true, tp.ups_tolist().let { off(it) + it.count { it is Stmt.Block } })
            "@global" -> Scope(lvl, true, 0)
            else -> {
                val num = id.drop(1).toIntOrNull()
                if (num == null) {
                    val blk = tp.ups_first { it is Stmt.Block && it.scope == id }!!
                    Scope(lvl, true, 1 + blk.ups_tolist().let { off(it) + it.count { it is Stmt.Block } })
                } else {
                    val n = tp.ups_first { it is Expr.Func }.let { if (it == null) 0 else off(it.ups_tolist()) }
                    Scope(lvl, false, n + num)    // false = relative to function block
                }
            }
        }
    }
    for (tp in AUX.tps.values) {
        tp.visit(::ft)
    }
}

///////////////////////////////////////////////////////////////////////////////

private
fun Expr.aux_04_xps (xp: Type) {
    AUX.xps[this] = xp
    when (this) {
        is Expr.TCons -> {
            assert(xp is Type.Tuple && xp.vec.size==this.arg.size)
            this.arg.forEachIndexed { i,e -> e.aux_04_xps((xp as Type.Tuple).vec[i]) }
        }
        is Expr.UCons -> {
            assert(xp is Type.Union && xp.vec.size>=this.tk_.num || this.tk_.num==0)
            val xp2 = when {
                (this.tk_.num == 0) -> Type_Unit(this.tk)
                else -> (xp as Type.Union).expand()[this.tk_.num - 1]
            }
            this.arg.aux_04_xps(xp2)
        }
        is Expr.New -> this.arg.aux_04_xps((xp as Type.Ptr).pln)
        is Expr.Dnref -> {
            this.ptr.aux_04_xps(xp.keepAnyNat {
                Type.Ptr(Tk.Chr(TK.CHAR, this.tk.lin, this.tk.col, '\\'), null, xp).scp_add(Scope(0,true,0))
            })
        }
        is Expr.Upref -> {
            this.pln.aux_04_xps(xp.keepAnyNat { (xp as Type.Ptr).pln })
        }
        is Expr.TDisc -> this.tup.aux_04_xps(Type_Any(this.tk))
        is Expr.UDisc -> this.uni.aux_04_xps(Type_Any(this.tk))
        is Expr.UPred -> this.uni.aux_04_xps(Type_Any(this.tk))
        is Expr.Call  -> {
            this.f.aux_04_xps(Type_Any(this.tk))
            val xp2 = AUX.tps[this.f]!!.let { it.keepAnyNat{it} }
            val arg = when (xp2) {
                is Type.Func -> xp2.keepAnyNat{ xp2.inp }
                is Type.Nat  -> xp2
                else -> error("impossible case")
            }
            this.arg.aux_04_xps(arg)
        }
        is Expr.Func  -> this.block.aux_04_xps()
    }
}

fun Stmt.aux_04_xps () {
    when (this) {
        is Stmt.Set -> {
            this.dst.aux_04_xps(Type_Any(this.tk))
            this.src.aux_04_xps(AUX.tps[this.dst]!!)
        }
        is Stmt.Call -> this.call.aux_04_xps(Type_Any(this.tk))
        is Stmt.Seq -> {
            this.s1.aux_04_xps()
            this.s2.aux_04_xps()
        }
        is Stmt.If -> {
            this.tst.aux_04_xps(Type_Nat(this.tk,"int"))
            this.true_.aux_04_xps()
            this.false_.aux_04_xps()
        }
        is Stmt.Loop  -> this.block.aux_04_xps()
        is Stmt.Block -> this.body.aux_04_xps()
    }
}
