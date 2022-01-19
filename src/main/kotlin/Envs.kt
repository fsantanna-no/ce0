// s = Stmt.Var (var), Type (arg/ret), Block (@xxx)

fun Any.getEnv (): Any? {
    return when (this) {
        is Type -> this.wenv
        is Expr -> this.wenv
        is Stmt -> this.wenv
        else -> error("bug found")
    }
}

fun Any.toType (): Type {
    return when (this) {
        is Type     -> this
        is Stmt.Var -> this.xtype!!
        else -> error("bug found")
    }
}

fun Any.env_all (): List<Any> {
    return this.getEnv()?.let { listOf(it) + it.env_all() } ?: emptyList()
}

fun Any.env_first (f: (Any)->Boolean): Any? {
    fun aux (env: Any?): Any? {
        return when {
            (env == null) -> null
            f(env) -> env
            else -> aux(env.getEnv())
        }
    }
    return aux (this.getEnv())
}

fun Any.env (id: String): Any? {
    //print(">>> ") ; println(id)
    return this.env_first {
        //println(it)
        it is Stmt.Var   && it.tk_.str.toLowerCase()==id.toLowerCase() ||
        it is Stmt.Block && it.xscp1?.lbl?.toUpperCase()==id.toUpperCase() ||
        it is Expr.Func  && (id=="arg" || id=="ret")
    }.let {
        if (it is Expr.Func) {
            if (id=="arg") it.type.inp else it.type.out
    } else {
            it
        }
    }
}

fun Expr.Var.env (): Any? {
    return (this as Any).env(this.tk_.str)
}

//////////////////////////////////////////////////////////////////////////////

private
fun Expr.setEnvs (env: Any?) {
    this.wenv = env
    fun ft (tp: Type) {
        tp.wenv = env
    }
    when (this) {
        is Expr.Var -> {}
        is Expr.Unit  -> this.wtype?.visit(false, ::ft)
        is Expr.Nat   -> this.xtype?.visit(false, ::ft)
        is Expr.TCons -> this.arg.forEachIndexed { _,e -> e.setEnvs(env) }
        is Expr.UCons -> {
            this.xtype?.visit(false, ::ft)
            this.arg.setEnvs(env)
        }
        is Expr.UNull -> this.xtype?.visit(false, ::ft)
        is Expr.New   -> this.arg.setEnvs(env)
        is Expr.Dnref -> this.ptr.setEnvs(env)
        is Expr.Upref -> this.pln.setEnvs(env)
        is Expr.TDisc -> this.tup.setEnvs(env)
        is Expr.UDisc -> this.uni.setEnvs(env)
        is Expr.UPred -> this.uni.setEnvs(env)
        is Expr.Call  -> {
            this.f.setEnvs(env)
            this.arg.setEnvs(env)
        }
        is Expr.Func  -> {
            this.type?.visit(false, ::ft)
            this.block.setEnvs(this)
        }
        else -> TODO(this.toString()) // do not remove this line b/c we may add new cases
    }
}

fun Stmt.setEnvs (env: Any?): Any? {
    this.wenv = env
    fun ft (tp: Type) {
        tp.wenv = env
    }
    return when (this) {
        is Stmt.Nop, is Stmt.Nat, is Stmt.Ret, is Stmt.Break, is Stmt.Await -> env
        is Stmt.Var -> {
            this.xtype?.visit(false, ::ft)
            this
        }
        is Stmt.SSet -> {
            this.dst.setEnvs(env)
            this.src.setEnvs(env)
            env
        }
        is Stmt.ESet -> {
            this.dst.setEnvs(env)
            this.src.setEnvs(env)
            env
        }
        is Stmt.SCall -> { this.e.setEnvs(env) ; env }
        is Stmt.Spawn -> { this.e.setEnvs(env) ; env }
        is Stmt.Awake -> { this.e.setEnvs(env) ; env }
        is Stmt.Inp   -> { this.arg.setEnvs(env) ; this.xtype?.visit(false, ::ft) ; env }
        is Stmt.Out   -> { this.arg.setEnvs(env) ; env }
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
            this.body.setEnvs(this) // also include blocks w/o labels b/c of inference
            env
        }
        else -> TODO(this.toString()) // do not remove this line b/c we may add new cases
    }
}
