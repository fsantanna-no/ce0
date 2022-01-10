// s = Stmt.Var (var), Type (arg/ret), Block (@xxx)
data class Env (val s: Any, val prv: Env?)

val ENV = mutableMapOf<Any,Env>()

//////////////////////////////////////////////////////////////////////////////

fun Any.toType (): Type {
    return when (this) {
        is Type     -> this
        is Stmt.Var -> this.type
        else -> error("bug found")
    }
}

fun Any.env_first (f: (Any)->Boolean): Any? {
    fun aux (env: Env?): Any? {
        return when {
            (env == null) -> null
            f(env.s) -> env.s
            else -> aux(env.prv)
        }
    }
    return aux (ENV[this])
}

fun Any.env (id: String): Any? {
    return this.env_first {
        it is Stmt.Var   && it.tk_.str==id ||
        it is Stmt.Block && it.scope!!.lbl==id ||
        it is Expr.Func  && (id=="arg" || id=="ret")
    }.let {
        if (it is Expr.Func) {
            if (id=="arg") it.type_.inp else it.type_.out
    } else {
            it
        }
    }
}

fun Expr.Var.env (): Any? {
    return (this as Any).env(this.tk_.str)
}

private
fun env_add (v: Any, env: Env?) {
    if (env == null) return
    assert(ENV[v] == null)
    ENV[v] = env
}

//////////////////////////////////////////////////////////////////////////////

private
fun Expr.setEnvs (env: Env?) {
    env_add(this, env)
    when (this) {
        is Expr.TCons -> this.arg.forEachIndexed { _,e -> e.setEnvs(env) }
        is Expr.UCons -> this.arg.setEnvs(env)
        is Expr.New   -> this.arg.setEnvs(env)
        is Expr.Dnref -> this.ptr.setEnvs(env)
        is Expr.Upref -> this.pln.setEnvs(env)
        is Expr.TDisc -> this.tup.setEnvs(env)
        is Expr.UDisc -> this.uni.setEnvs(env)
        is Expr.UPred -> this.uni.setEnvs(env)
        is Expr.Out   -> this.arg.setEnvs(env)
        is Expr.Call  -> {
            this.f.setEnvs(env)
            this.arg.setEnvs(env)
        }
        is Expr.Func  -> this.block.setEnvs(Env(this,env))
    }
}

fun Stmt.setEnvs (env: Env?): Env? {
    env_add(this, env)
    return when (this) {
        is Stmt.Nop, is Stmt.Nat, is Stmt.Ret, is Stmt.Break -> env
        is Stmt.Var -> Env(this,env)
        is Stmt.Set -> {
            this.dst.setEnvs(env)
            this.src.setEnvs(env)
            env
        }
        is Stmt.SExpr -> { this.e.setEnvs(env) ; env }
        is Stmt.Seq -> {
            val e1 = this.s1.setEnvs(env)
            val e2 = this.s2.setEnvs(e1)
            e2
        }
        is Stmt.If -> {
            this.tst.setEnvs(env)
            this.true_.setEnvs(env)
            this.false_.setEnvs(env)
            env
        }
        is Stmt.Loop  -> { this.block.setEnvs(env) ; env }
        is Stmt.Block -> {
            val env_ = if (this.scope == null) env else Env(this,env)
            this.body.setEnvs(env_)
            env
        }
    }
}
