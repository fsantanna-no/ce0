fun Type.Union.expand (): Array<Type> {
    fun aux (cur: Type, up: Int): Type {
        return when (cur) {
            is Type.Rec   -> if (up == cur.tk_.up) this else { assert(up>cur.tk_.up) ; cur }
            is Type.Tuple -> Type.Tuple(cur.tk_, cur.vec.map { aux(it,up) }.toTypedArray()) .up(AUX.ups[cur]!!)
            is Type.Union -> Type.Union(cur.tk_, cur.isrec, cur.vec.map { aux(it,up+1) }.toTypedArray()) .up(AUX.ups[cur]!!)
            is Type.Ptr   -> Type.Ptr(cur.tk_, cur.scope, aux(cur.pln,up)) .up(AUX.ups[cur]!!)
            is Type.Func  -> Type.Func(cur.tk_, cur.clo, cur.scps, aux(cur.inp,up), aux(cur.out,up)) .up(AUX.ups[cur]!!)
            else -> cur
        }
    }
    return this.vec.map { aux(it, 1) }.toTypedArray()
}

fun Tk.Scope.scope (up: Any): Scope {
    val lvl = up.ups_tolist().filter { it is Expr.Func }.count() // level of function nesting
    return when (this.lbl) {
        "var"    -> Scope(lvl, null, up.ups_tolist().let { it.count { it is Stmt.Block } })
        "global" -> Scope(lvl, null, 0)
        "local"  -> Scope(lvl, null, up.ups_tolist().let { it.count { it is Stmt.Block } })
        else -> {
            val blk = up.ups_first { it is Stmt.Block && it.scope!=null && it.scope.lbl==this.lbl }
            if (blk != null) {
                Scope(lvl, null, 1 + blk.ups_tolist().let { it.count { it is Stmt.Block } })
            } else {    // false = relative to function block
                Scope(lvl, this.lbl, (this.num ?: 0))
            }
        }
    }
}

fun Type.scope (): Scope {
    return when {
        this is Type.Ptr -> this.scope.scope(this)
        (this is Type.Func) && (this.clo!=null) -> this.clo.scope(this)    // body holds pointers in clo
        else -> {
            val lvl = this.ups_tolist().filter { it is Expr.Func }.count()
            Scope(lvl, null, this.ups_tolist().let { it.count { it is Stmt.Block } })
            Scope(0, null, 0)
        }
    }
}
