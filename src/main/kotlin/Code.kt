fun Type.toce (): String {
    return when (this) {
        is Type.Unit  -> "Unit"
        is Type.Tuple -> "TUPLE__" + this.vec.map { it.toce() }.joinToString("__")
        else -> error("TODO")
    }
}

fun Type.toc (): String {
    return when (this) {
        is Type.Tuple -> this.toce()
        else -> error("TODO")
    }
}

fun code_expr (e: Expr): String {
    val tp = e.totype()
    return when (e) {
        is Expr.Unit  -> ""
        is Expr.Var   -> (e.tk.pay as TK_Str).v
        is Expr.Nat   -> (e.tk.pay as TK_Str).v
        is Expr.Tuple -> "((${tp.toc()}) { })"
        else -> error("TODO")
    }
}