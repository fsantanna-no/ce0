fun Expr.totype (): Type {
    return when (this) {
        is Expr.Unit  -> Type.Unit(this.tk_)
        is Expr.Var   -> error("TODO")
        is Expr.Nat   -> Type.Nat(this.tk_)
        is Expr.Tuple -> Type.Tuple(this.tk_, this.vec.map{it.totype()}.toTypedArray())
        is Expr.Index -> error("TODO")
        else -> error("TODO")
    }
}