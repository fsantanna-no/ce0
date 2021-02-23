fun Expr.totype (): Type {
    return when (this) {
        is Expr.Unit  -> Type.Unit(this.tk)
        is Expr.Var   -> Type.Unit(this.tk) //error("TODO")
        is Expr.Nat   -> Type.Nat(this.tk)
        is Expr.Tuple -> Type.Tuple(this.tk, this.vec.map{it.totype()}.toTypedArray())
        else -> error("TODO")
    }
}