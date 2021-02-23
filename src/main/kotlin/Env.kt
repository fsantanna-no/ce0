typealias Env  = HashMap<String,Stmt.Var>
typealias Envs = List<Env>

fun Envs.get (id: String): Stmt.Var? {
    for (env in this) {
        if (env[id] != null) {
            return env[id]
        }
    }
    return null
}

fun Expr.totype (envs: Envs): Type {
    return when (this) {
        is Expr.Unit  -> Type.Unit(this.tk_)
        is Expr.Var   -> envs.get(this.tk_.str)!!.type
        is Expr.Nat   -> Type.Nat(this.tk_)
        is Expr.Tuple -> Type.Tuple(this.tk_, this.vec.map{it.totype(envs)}.toTypedArray())
        is Expr.Index -> error("TODO")
        else -> error("TODO")
    }
}