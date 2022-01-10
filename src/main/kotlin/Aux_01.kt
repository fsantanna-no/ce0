data class Env (val s: Stmt.Var, val prv: Env?)

object AUX {
    val env = mutableMapOf<Any,Env>()
}

fun aux_clear () {
    AUX.env.clear()
}

//////////////////////////////////////////////////////////////////////////////

fun Type.setUp (up: Any): Type {
    this.up = up
    return this
}

fun Any.getUp (): Any? {
    return when (this) {
        is Type -> this.up
        is Expr -> this.up
        is Stmt -> this.up
        else    -> error("bug found")
    }
}

fun Any.ups_tolist(): List<Any> {
    val up = this.getUp()
    return when {
        (up == null) -> emptyList()
        else -> up.let { listOf(it) + it.ups_tolist() }
    }
}

fun Any.ups_first (f: (Any)->Boolean): Any? {
    val up = this.getUp()
    return when {
        (up == null) -> null
        f(up) -> up
        else -> up.ups_first(f)
    }
}

//////////////////////////////////////////////////////////////////////////////

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

private
fun env_add (v: Any, env: Env?) {
    if (env == null) return
    assert(AUX.env[v] == null)
    AUX.env[v] = env
}

//////////////////////////////////////////////////////////////////////////////

//private
fun Expr.aux_envs (env: Env?) {
    env_add(this, env)
    when (this) {
        is Expr.TCons -> this.arg.forEachIndexed { _,e -> e.aux_envs(env) }
        is Expr.UCons -> this.arg.aux_envs(env)
        is Expr.New   -> this.arg.aux_envs(env)
        is Expr.Dnref -> this.ptr.aux_envs(env)
        is Expr.Upref -> this.pln.aux_envs(env)
        is Expr.TDisc -> this.tup.aux_envs(env)
        is Expr.UDisc -> this.uni.aux_envs(env)
        is Expr.UPred -> this.uni.aux_envs(env)
        is Expr.Out   -> this.arg.aux_envs(env)
        is Expr.Call  -> {
            this.f.aux_envs(env)
            this.arg.aux_envs(env)
        }
        is Expr.Func  -> this.block.aux_envs(this, env)
    }
}

fun Stmt.aux_envs (up: Any?, env: Env?): Env? {
    env_add(this, env)
    return when (this) {
        is Stmt.Nop, is Stmt.Nat, is Stmt.Ret, is Stmt.Break -> env
        is Stmt.Var -> Env(this,env)
        is Stmt.Set -> {
            this.dst.aux_envs(env)
            this.src.aux_envs(env)
            env
        }
        is Stmt.SExpr -> { this.e.aux_envs(env) ; env }
        is Stmt.Seq -> {
            val e1 = this.s1.aux_envs(this, env)
            val e2 = this.s2.aux_envs(this, e1)
            e2
        }
        is Stmt.If -> {
            this.tst.aux_envs(env)
            this.true_.aux_envs(this, env)
            this.false_.aux_envs(this, env)
            env
        }
        is Stmt.Loop  -> { this.block.aux_envs(this,env) ; env }
        is Stmt.Block -> { this.body.aux_envs(this,env) ; env }
    }
}
