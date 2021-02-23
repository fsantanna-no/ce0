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

fun Expr.toc (envs: Envs): String {
    return when (this) {
        is Expr.Unit  -> ""
        is Expr.Var   -> this.tk_.str
        is Expr.Nat   -> this.tk_.str
        is Expr.Tuple -> "((${this.totype(envs).toc()}) { })"
        is Expr.Index -> this.pre.toc(envs) + "._" + this.tk_.num
        else -> error("TODO")
    }
}

fun Stmt.toc (envs: Envs): String {
    return when (this) {
        is Stmt.Pass -> ""
        else -> error("TODO")
    }
}