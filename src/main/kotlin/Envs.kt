// s = Stmt.Var (var), Type (arg/ret), Block (@xxx)

fun Any.getEnv (): Any? {
    return when (this) {
        is Type -> this.wenv
        is Expr -> this.wenv
        is Stmt -> this.wenv
        else -> error("bug found")
    }
}

fun Type.setEnvs (env: Any?) {
    this.wenv = env
    when (this) {
        is Type.Unit, is Type.Nat, is Type.Rec, is Type.Alias -> {}
        is Type.Tuple -> this.vec.forEach { it.setEnvs(this) }
        is Type.Union -> this.vec.forEach { it.setEnvs(this) }
        is Type.Func  -> { this.inp.setEnvs(this) ; this.pub?.setEnvs(this) ; this.out.setEnvs(this) }
        is Type.Spawn   -> this.tsk.setEnvs(this)
        is Type.Spawns  -> this.tsk.setEnvs(this)
        is Type.Pointer   -> this.pln.setEnvs(this)
        else -> TODO(this.toString()) // do not remove this line b/c we may add new cases
    }
}

fun Any.toType (): Type {
    return when (this) {
        is Type         -> this
        is Stmt.Var     -> this.xtype!!
        is Stmt.Typedef -> this.type
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

fun Any.env (id: String, upval: Boolean=false): Any? {
    //print(">>> ") ; println(id)
    return this.env_first {
        //println(it)
        it is Stmt.Typedef && it.tk_.id.toLowerCase()==id.toLowerCase() ||
        it is Stmt.Var     && it.tk_.id.toLowerCase()==id.toLowerCase() ||
        it is Stmt.Block   && it.xscp1?.id?.toUpperCase()==id.toUpperCase() ||
        //it is Expr.Func  && (id=="arg" || id=="ret" || id=="evt")
        it is Expr.Func  && (id=="arg" || id=="pub" || id=="ret" || id=="evt" || (upval && it.ups.any { it.id==id }))
    }.let {
        if (it is Expr.Func) {
            when (id) {
                "arg" -> it.type.inp
                "pub" -> it.type.pub!!
                "ret" -> it.type.out
                "evt" -> Type.Nat(Tk.Nat(TK.XNAT, it.tk.lin, it.tk.col, null, "int")).clone(it,it.tk.lin,it.tk.col)
                //else  -> error("bug found")
                else  -> (it.env(id) as Stmt.Var).xtype!!.clone(it,it.tk.lin,it.tk.col)
            }
        } else {
            it
        }
    }
}

//////////////////////////////////////////////////////////////////////////////

private
fun Expr.setEnvs (env: Any?) {
    this.wenv = env
    fun ft (tp: Type) {
        tp.wenv = env
    }
    when (this) {
        is Expr.Var   -> {}
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
        is Expr.Pub   -> this.tsk.setEnvs(env)
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
    fun ft (tp: Type) { // recursive typedef
        tp.wenv = if (this is Stmt.Typedef) this else env
    }
    return when (this) {
        is Stmt.Nop, is Stmt.Native, is Stmt.Return, is Stmt.Break, is Stmt.Throw -> env
        is Stmt.Var    -> { this.xtype?.visit(false, ::ft) ; this }
        is Stmt.Set    -> { this.dst.setEnvs(env) ; this.src.setEnvs(env) ; env }
        is Stmt.SCall  -> { this.e.setEnvs(env) ; env }
        is Stmt.SSpawn -> { this.dst.setEnvs(env) ; this.call.setEnvs(env) ; env }
        is Stmt.DSpawn -> { this.dst.setEnvs(this) ; this.call.setEnvs(this) ; env }
        is Stmt.Await  -> { this.e.setEnvs(env) ; env }
        is Stmt.Bcast  -> { this.e.setEnvs(env) ; env }
        is Stmt.Input  -> { this.dst?.setEnvs(env) ; this.arg.setEnvs(env) ; this.xtype?.visit(false, ::ft) ; env }
        is Stmt.Output -> { this.arg.setEnvs(env) ; env }
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
        is Stmt.DLoop -> { this.i.setEnvs(env) ; this.tsks.setEnvs(env) ; this.block.setEnvs(env) ; env }
        is Stmt.Block -> {
            this.body.setEnvs(this) // also include blocks w/o labels b/c of inference
            env
        }
        is Stmt.Typedef -> { this.type.visit(false, ::ft) ; this }
        else -> TODO(this.toString()) // do not remove this line b/c we may add new cases
    }
}
