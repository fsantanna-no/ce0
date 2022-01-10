fun Type.setUp (up: Any): Type {
    this.up = up
    return this
}

fun Any.getUp (): Any? {
    return when (this) {
        is Type -> this.up
        is Expr -> this.up
        is Stmt -> this.up
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
