data class Env (val s: Stmt.Var, val prv: Env?)

object AUX {
    val ups = mutableMapOf<Any,Any>()
    val env = mutableMapOf<Any,Env>()
    val tps = mutableMapOf<Expr,Type>()
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

fun Any.env_first (f: (Any)->Boolean): Any? {
    fun aux (env: Env?): Stmt? {
        return when {
            (env == null) -> null
            f(env.s) -> env.s
            else -> aux(env.prv)
        }
    }
    return aux (AUX.env[this])
}

fun Any.env (id: String): Pair<Stmt.Var?,Type?> {   // TODO: Either
    return when (id) {
        "arg" -> this.ups_first { it is Expr.Func }.let { it as Expr.Func? }.let { if (it == null) Pair(null,null) else Pair(null,it.type_.inp) }
        "ret" -> this.ups_first { it is Expr.Func }.let { it as Expr.Func? }.let { if (it == null) Pair(null,null) else Pair(null,it.type_.out) }
        else  -> this.env_first { it is Stmt.Var && it.tk_.str==id }.let { it as Stmt.Var? }.let { if (it == null) Pair(null,null) else Pair(it,it.type) }
    }
}

fun Expr.Var.env (): Pair<Stmt.Var?,Type?> {   // TODO: Either
    return (this as Any).env(this.tk_.str)
}

//private
fun Type.up (up: Any): Type {
    ups_add(this, up)
    return this
}

//////////////////////////////////////////////////////////////////////////////

//private
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
}

//private
fun Type.aux_upsenvs (up: Any) {
    ups_add(this, up)

    when (this) {
        is Type.Tuple -> this.vec.forEach { it.aux_upsenvs(this) }
        is Type.Union -> this.vec.forEach { it.aux_upsenvs(this) }
        is Type.Func  -> { this.inp.aux_upsenvs(this) ; this.out.aux_upsenvs(this) }
        is Type.Ptr   -> this.pln.aux_upsenvs(this)
    }
}

//private
fun Expr.aux_upsenvs (up: Any, env: Env?) {
    ups_add(this, up)
    env_add(this, env)
    when (this) {
        is Expr.TCons -> this.arg.forEachIndexed { _,e -> e.aux_upsenvs(this, env) }
        is Expr.UCons -> { this.type?.aux_upsenvs(this) ; this.arg.aux_upsenvs(this, env) }
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
            this.type_.aux_upsenvs(this)
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
            this.type?.aux_upsenvs(this)
            Env(this,env)
        }
        is Stmt.Set -> {
            this.dst.aux_upsenvs(this, env)
            this.src.aux_upsenvs(this, env)
            env
        }
        is Stmt.SExpr -> { this.e.aux_upsenvs(this, env) ; env }
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
