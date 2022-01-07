fun Expr.flatten (): List<Expr> {
    return when (this) {
        is Expr.Unit, is Expr.Var, is Expr.Nat, is Expr.Inp, is Expr.Func -> listOf(this)
        is Expr.TCons -> this.arg.map { it.flatten() }.flatten() + this
        is Expr.Call  -> this.f.flatten() + this.arg.flatten() + this
        is Expr.Out   -> this.arg.flatten() + this
        is Expr.TDisc -> this.tup.flatten() + this
        is Expr.UDisc -> this.uni.flatten() + this
        is Expr.UPred -> this.uni.flatten() + this
        is Expr.New   -> this.arg.flatten() + this
        is Expr.Dnref -> this.ptr.flatten() + this
        is Expr.Upref -> this.pln.flatten() + this
        is Expr.UCons -> TODO(this.toString())
    }
}

fun Attr.toExpr (): Expr {
    return when (this) {
        is Attr.Var   -> Expr.Var(this.tk_)
        is Attr.Nat   -> Expr.Nat(this.tk_, null)
        is Attr.Dnref -> Expr.Dnref(this.tk_,this.ptr.toExpr())
        is Attr.TDisc -> Expr.TDisc(this.tk_,this.tup.toExpr())
        is Attr.UDisc -> Expr.UDisc(this.tk_,this.uni.toExpr())
    }
}
