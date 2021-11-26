val UPS = mutableMapOf<Any,Any>()
val ENV = mutableMapOf<Any,Env>()
val TPS = mutableMapOf<Expr,Type>()
val XPS = mutableMapOf<Expr,Type>()  // needed b/c of TCons/UCons

data class Env (val s: Stmt.Var, val prv: Env?)

fun aux (s: Stmt) {
    UPS.clear()
    ENV.clear()
    TPS.clear()
    XPS.clear()
    s.aux(null, null)
}

private
fun ups_add (v: Any, up: Any?) {
    if (up == null) return
    //assert(UPS[v] == null)    // fails b/c of expands
    UPS[v] = up
}

private
fun env_add (v: Any, env: Env?) {
    if (env == null) return
    assert(ENV[v] == null)
    ENV[v] = env
}

private
fun xps_add (e: Expr, tp: Type) {
    //assert(XPS[e] == null)    // fails b/c of expands
    XPS[e] = tp
}

//////////////////////////////////////////////////////////////////////////////

fun Any.ups_tolist (): List<Any> {
    return when {
        (UPS[this] == null) -> emptyList()
        else -> UPS[this]!!.let { listOf(it) + it.ups_tolist() }
    }
}

fun Any.ups_first (f: (Any)->Boolean): Any? {
    val up = UPS[this]
    return when {
        (up == null) -> null
        f(up) -> up
        else -> up.ups_first(f)
    }
}

//////////////////////////////////////////////////////////////////////////////

fun Any.env_first (f: (Stmt)->Boolean): Stmt? {
    fun aux (env: Env?): Stmt? {
        return when {
            (env == null) -> null
            f(env.s) -> env.s
            else -> aux(env.prv)
        }
    }
    return aux (ENV[this])
}

fun Any.env_all (f: (Stmt)->Boolean): Set<Stmt> {
    fun aux (env: Env?): Set<Stmt> {
        return when {
            (env == null) -> emptySet()
            f(env.s) -> setOf(env.s) + aux(env.prv)
            else -> aux(env.prv)
        }
    }
    return aux (ENV[this])
}

fun Any.env (id: String): Stmt.Var? {
    return this.env_first { it is Stmt.Var && it.tk_.str==id } as Stmt.Var?
}

fun Expr.Var.env (): Stmt.Var? {
    return this.env_first { it is Stmt.Var && it.tk_.str==this.tk_.str } as Stmt.Var?
}

fun Type.Ptr.scopeDepth (): Int? {
    return when (this.scope) {
        null -> this.ups_tolist().count { it is Stmt.Block }
        "@global" -> 0
        else -> {
            val num = this.scope.drop(1).toIntOrNull()
            if (num == null) {
                val blk = this.ups_first { it is Stmt.Block && it.scope == this.scope }
                return if (blk == null) null else {
                    1 + blk.ups_tolist().count { it is Stmt.Block }
                }
            } else {
                num
            }
        }
    }
}

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

fun Type.up (up: Any): Type {
    ups_add(this, up)
    return this
}

fun Expr.tps_add () {
    assert(TPS[this] == null)
    TPS[this] = try {
        when (this) {
            is Expr.Unit  -> Type.Unit(this.tk_).up(this)
            is Expr.Nat   -> Type.Nat(this.tk_).up(this)
            is Expr.Upref -> TPS[this.pln]!!.let { Type.Ptr(this.tk_,null,it).up(it) }
            is Expr.Dnref -> (TPS[this.ptr] as Type.Ptr).pln
            is Expr.TCons -> Type.Tuple(this.tk_, this.arg.map{TPS[it]!!}.toTypedArray()).up(this)
            is Expr.UCons -> Type.UCons(this.tk_, TPS[this.arg]!!).up(this)
            is Expr.New   -> TPS[this.arg]!!
            is Expr.Call  -> {
                TPS[this.f].let {
                    when (it) {
                        is Type.Func -> it.out
                        is Type.Nat  -> it //Type.Nat(it.tk_).ups(this)
                        else -> error("impossible case")
                    }
                }
            }
            is Expr.Func  -> this.type
            is Expr.UPred -> Type.Nat(Tk.Str(TK.XNAT, this.tk.lin, this.tk.col, "int")).up(this)
            is Expr.TDisc -> (TPS[this.tup] as Type.Tuple).vec[this.tk_.num-1].up(this)
            is Expr.UDisc -> (TPS[this.uni] as Type.Union).let {
                if (this.tk_.num == 0) {
                    assert(it.isrec()) { "bug found" }
                    Type_Unit(this.tk).up(this)
                } else {
                    it.expand()[this.tk_.num - 1].up(this)
                }
            }
            is Expr.Var -> this.env()!!.type
        }
    } catch (e: Exception) {
        Type_Any(this.tk)
    }
}

private
fun Type.aux (up: Any) {
    ups_add(this, up)
    when (this) {
        is Type.Tuple -> this.vec.forEach { it.aux(this) }
        is Type.Union -> this.vec.forEach { it.aux(this) }
        is Type.UCons -> this.arg.aux(this)
        is Type.Func  -> { this.inp.aux(this) ; this.out.aux(this) }
        is Type.Ptr   -> this.pln.aux(this)
    }
}

private
fun Expr.aux (up: Any, env: Env?, xp: Type) {
    ups_add(this, up)
    env_add(this, env)
    xps_add(this, xp)
    when (this) {
        is Expr.TCons -> {
            if (xp !is Type.Tuple) {
                this.arg.forEachIndexed { _, e ->
                    e.aux(this, env, Type_Any(this.tk))
                }
            } else {
                this.arg.forEachIndexed { i, e ->
                    e.aux(this, env, if (xp.vec.size>i) xp.vec[i] else Type_Any(this.tk))
                }
            }
        }
        is Expr.UCons -> {
            val sub = when {
                (xp !is Type.Union) -> Type_Any(this.tk)
                (this.tk_.num == 0) -> Type_Unit(this.tk)
                (xp.vec.size < this.tk_.num) -> Type_Any(this.tk)
                else -> xp.expand()[this.tk_.num - 1]
            }
            this.arg.aux(this, env, sub)
        }
        is Expr.New -> this.arg.aux(this, env, xp)
        is Expr.Dnref -> {
            if (xp is Type.Ptr) {
                this.ptr.aux(this, env, Type_Any(this.tk))
            } else {
                this.ptr.aux(this, env, xp.keepAnyNat { Type.Ptr(Tk.Chr(TK.CHAR, this.tk.lin, this.tk.col, '\\'), null, xp) })
            }
        }
        is Expr.Upref -> {
            if (xp !is Type.Ptr) {
                this.pln.aux(this, env, Type_Any(this.tk))
            } else {
                this.pln.aux(this, env, xp.keepAnyNat { xp.pln })
            }
        }
        is Expr.TDisc -> this.tup.aux(this, env, Type_Any(this.tk))
        is Expr.UDisc -> this.uni.aux(this, env, Type_Any(this.tk))
        is Expr.UPred -> this.uni.aux(this, env, Type_Any(this.tk))
        is Expr.Call  -> {
            this.f.aux(this, env, Type_Any(this.tk))
            val xp2 = TPS[this.f]!!.let { it.keepAnyNat{it} }
            this.arg.aux(this, env, if (xp2 is Type.Func) xp2.keepAnyNat{ xp2.inp } else Type_Any(this.tk))
        }
        is Expr.Func  -> {
            this.type.aux(this)
            this.block.aux(this, env)
        }
    }
    this.tps_add()
}

private
fun Stmt.aux (up: Any?, env: Env?): Env? {
    ups_add(this, up)
    env_add(this, env)
    return when (this) {
        is Stmt.Var -> {
            this.type.aux(this)
            Env(this,env)
        }
        is Stmt.Set -> {
            this.dst.aux(this, env, Type_Any(this.tk))
            this.src.aux(this, env, TPS[this.dst]!!)
            env
        }
        is Stmt.Call -> { this.call.aux(this, env, Type_Any(this.tk)) ; env }
        is Stmt.Seq -> {
            val e1 = this.s1.aux(this, env)
            val e2 = this.s2.aux(this, e1)
            e2
        }
        is Stmt.If -> {
            this.tst.aux(this,env,Type_Nat(this.tk,"int"))
            this.true_.aux(this, env)
            this.false_.aux(this, env)
            env
        }
        is Stmt.Loop  -> { this.block.aux(this,env) ; env }
        is Stmt.Block -> { this.body.aux(this,env) ; env }
        else -> env
    }
}
