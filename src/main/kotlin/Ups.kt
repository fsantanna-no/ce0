fun Any.getUp (): Any? {
    return when (this) {
        is Type -> this.wup
        is Expr -> this.wup
        is Stmt -> this.wup
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

fun Any.ups_first (me: Boolean=false, f: (Any)->Boolean): Any? {
    val up = if (me) this else this.getUp()
    return when {
        (up == null) -> null
        f(up) -> up
        else -> up.ups_first(false,f)
    }
}

//////////////////////////////////////////////////////////////////////////////

fun Type.setUps (up: Any) {
    this.wup = up
    when (this) {
        is Type.Unit, is Type.Nat, is Type.Alias -> {}
        is Type.Tuple -> this.vec.forEach { it.setUps(this) }
        is Type.Union -> this.vec.forEach { it.setUps(this) }
        is Type.Func  -> { this.inp.setUps(this) ; this.pub?.setUps(this) ; this.out.setUps(this) }
        is Type.Spawn   -> this.tsk.setUps(this)
        is Type.Spawns  -> this.tsk.setUps(this)
        is Type.Pointer   -> this.pln.setUps(this)
        else -> TODO(this.toString()) // do not remove this line b/c we may add new cases
    }
}

//private
fun Expr.setUps (up: Any) {
    this.wup = up
    when (this) {
        is Expr.Unit, is Expr.Var -> {}
        is Expr.Nat   -> this.xtype?.setUps(this)
        is Expr.TCons -> this.arg.forEach { it.setUps(this) }
        is Expr.UCons -> { this.xtype?.setUps(this) ; this.arg.setUps(this) }
        is Expr.UNull -> this.xtype?.setUps(this)
        is Expr.New   -> this.arg.setUps(this)
        is Expr.Dnref -> this.ptr.setUps(this)
        is Expr.Upref -> this.pln.setUps(this)
        is Expr.TDisc -> this.tup.setUps(this)
        is Expr.Pub   -> this.tsk.setUps(this)
        is Expr.UDisc -> this.uni.setUps(this)
        is Expr.UPred -> this.uni.setUps(this)
        is Expr.Call  -> {
            this.f.setUps(this)
            this.arg.setUps(this)
        }
        is Expr.Func  -> {
            this.type.setUps(this)
            this.block.setUps(this)
        }
        else -> TODO(this.toString()) // do not remove this line b/c we may add new cases
    }
}

fun Stmt.setUps (up: Any?) {
    this.wup = up
    when (this) {
        is Stmt.Nop, is Stmt.Native, is Stmt.Break, is Stmt.Return, is Stmt.Throw -> {}
        is Stmt.Var -> this.xtype?.setUps(this)
        is Stmt.Set -> {
            this.dst.setUps(this)
            this.src.setUps(this)
        }
        is Stmt.SCall -> this.e.setUps(this)
        is Stmt.SSpawn -> { this.dst.setUps(this) ; this.call.setUps(this) }
        is Stmt.DSpawn -> { this.dst.setUps(this) ; this.call.setUps(this) }
        is Stmt.Await -> this.e.setUps(this)
        is Stmt.Emit -> this.e.setUps(this)
        is Stmt.Input   -> { this.arg.setUps(this) ; this.xtype?.setUps(this) }
        is Stmt.Output   -> this.arg.setUps(this)
        is Stmt.Seq -> {
            this.s1.setUps(this)
            this.s2.setUps(this)
        }
        is Stmt.If -> {
            this.tst.setUps(this)
            this.true_.setUps(this)
            this.false_.setUps(this)
        }
        is Stmt.Loop  -> this.block.setUps(this)
        is Stmt.DLoop -> { this.i.setUps(this) ; this.tsks.setUps(this) ; this.block.setUps(this) }
        is Stmt.Block -> this.body.setUps(this)
        is Stmt.Typedef -> this.type.setUps(this)
        else -> TODO(this.toString()) // do not remove this line b/c we may add new cases
    }
}
