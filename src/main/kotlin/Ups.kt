val UPS = mutableMapOf<Any,Any>()

fun ups (s: Stmt) {
    UPS.clear()
    s.ups(null)
}

fun Any.ups_first (f: (Any)->Boolean): Any? {
    return when {
        f(this) -> this
        (UPS[this] == null) -> null
        else -> UPS[this]!!.ups_first(f)
    }
}

fun Any.ups_env (id: String): Stmt.Var? {
    return this.ups_first { it is Stmt.Var && it.tk_.str==id } as Stmt.Var?
}

private
fun ups_add (v: Any, up: Any) {
    assert(UPS[v] == null)
    UPS[v] = up
}

private
fun Type.ups (up: Any) {
    ups_add(this, up)
    when (this) {
        is Type.Tuple -> this.vec.forEach { it.ups(this) }
        is Type.Union -> this.vec.forEach { it.ups(this) }
        is Type.UCons -> this.arg.ups(this)
        is Type.Func  -> { this.inp.ups(this) ; this.out.ups(this) }
        is Type.Ptr   -> this.pln.ups(this)
    }
}

private
fun XExpr.ups (up: Any) {
    ups_add(this, up)
    if (this is XExpr.Replace) {
        this.new.ups(this)
    }
    this.e.ups(this)
}

private
fun Expr.ups (up: Any) {
    ups_add(this, up)
    when (this) {
        is Expr.TCons -> this.arg.forEach { it.ups(this) }
        is Expr.UCons  -> this.arg.ups(this)
        is Expr.Dnref -> this.ptr.ups(this)
        is Expr.Upref -> this.pln.ups(this)
        is Expr.TDisc -> this.tup.ups(this)
        is Expr.UDisc -> this.uni.ups(this)
        is Expr.UPred -> this.uni.ups(this)
        is Expr.Call  -> { this.f.ups(this) ; this.arg.ups(this) }
        is Expr.Func  -> { /*this.type.ups(this)*/ ; this.block.ups(this) }
    }
}

private
fun Attr.ups (up: Any) {
    ups_add(this, up)
    when (this) {
        is Attr.Dnref -> this.ptr.ups(this)
        is Attr.TDisc -> this.tup.ups(this)
        is Attr.UDisc -> this.uni.ups(this)
    }
}

private
fun Stmt.ups (up: Any?) {
    if (up != null) {
        ups_add(this, up)
    }
    when (this) {
        is Stmt.Var   -> { /*this.type.ups(this)*/ ; this.src.ups(this) }
        is Stmt.Set   -> { this.dst.ups(this) ; this.src.ups(this) }
        is Stmt.Call  -> { this.call.ups(this) }
        is Stmt.Seq   -> { this.s1.ups(this) ; this.s2.ups(this) }
        is Stmt.If    -> { this.tst.ups(this) ; this.true_.ups(this) ; this.false_.ups(this) }
        is Stmt.Loop  -> { this.block.ups(this) }
        is Stmt.Block -> { this.body.ups(this) }
    }
}
