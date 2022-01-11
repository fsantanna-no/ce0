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
        is Stmt.Var -> this.xtype
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
        it is Stmt.Var   && it.tk_.str==id ||
        it is Stmt.Block && it.scp1!!.lbl==id ||
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
        is Expr.Unit  -> this.wtype?.visit(false, ::ft)
        is Expr.Nat   -> this.xtype?.visit(false, ::ft)
        is Expr.Inp   -> this.xtype?.visit(false, ::ft)
        is Expr.TCons -> this.arg.forEachIndexed { _,e -> e.setEnvs(env) }
        is Expr.UCons -> {
            this.xtype?.visit(false, ::ft)
            this.arg.setEnvs(env)
        }
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
        is Expr.Func  -> {
            this.type?.visit(false, ::ft)
            this.block.setEnvs(this)
        }
    }
}

fun Stmt.setEnvs (env: Any?): Any? {
    this.wenv = env
    fun ft (tp: Type) {
        tp.wenv = env
    }
    return when (this) {
        is Stmt.Nop, is Stmt.Nat, is Stmt.Ret, is Stmt.Break -> env
        is Stmt.Var -> {
            this.xtype?.visit(false, ::ft)
            this
        }
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
            val env_ = if (this.scp1 == null) env else this
            this.body.setEnvs(env_)
            env
        }
    }
}
