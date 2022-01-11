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

fun Any.ups_first (f: (Any)->Boolean): Any? {
    val up = this.getUp()
    return when {
        (up == null) -> null
        f(up) -> up
        else -> up.ups_first(f)
    }
}

//////////////////////////////////////////////////////////////////////////////

fun Type.setUps (up: Any) {
    this.wup = up
    when (this) {
        is Type.Tuple -> this.vec.forEach { it.setUps(this) }
        is Type.Union -> this.vec.forEach { it.setUps(this) }
        is Type.Func  -> { this.inp.setUps(this) ; this.out.setUps(this) }
        is Type.Ptr   -> this.pln.setUps(this)
    }
}

//private
fun Expr.setUps (up: Any) {
    this.wup = up
    when (this) {
        is Expr.Nat -> this.xtype?.setUps(this)
        is Expr.Inp -> this.xtype?.setUps(this)
        is Expr.TCons -> this.arg.forEach { it.setUps(this) }
        is Expr.UCons -> { this.xtype?.setUps(this) ; this.arg.setUps(this) }
        is Expr.New   -> this.arg.setUps(this)
        is Expr.Dnref -> this.ptr.setUps(this)
        is Expr.Upref -> this.pln.setUps(this)
        is Expr.TDisc -> this.tup.setUps(this)
        is Expr.UDisc -> this.uni.setUps(this)
        is Expr.UPred -> this.uni.setUps(this)
        is Expr.Out   -> this.arg.setUps(this)
        is Expr.Call  -> {
            this.f.setUps(this)
            this.arg.setUps(this)
        }
        is Expr.Func  -> {
            this.type.setUps(this)
            this.block.setUps(this)
        }
    }
}

fun Stmt.setUps (up: Any?) {
    this.wup = up
    when (this) {
        is Stmt.Var -> this.xtype?.setUps(this)
        is Stmt.Set -> {
            this.dst.setUps(this)
            this.src.setUps(this)
        }
        is Stmt.SExpr -> this.e.setUps(this)
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
        is Stmt.Block -> this.body.setUps(this)
    }
}