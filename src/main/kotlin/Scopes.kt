data class Scp2 (val lvl: Int, val arg: String?, val depth: Int)

fun Type.toScp2 (): Scp2 {
    return when {
        this is Type.Ptr -> this.xscp2!!
        (this is Type.Func) && (this.xscp1s.first!=null) -> this.xscp2s!!.first!! // body holds pointers in clo
        else -> Scp2(0, null, 0)
    }
}

fun Tk.Scp1.toScp2 (up: Any): Scp2 {
    val lvl = up.ups_tolist().filter { it is Expr.Func }.count() // level of function nesting
    return when (this.lbl) { // (... || it is Expr.Func) b/c of arg/ret, otherwise no block up to outer func
        "global" -> Scp2(lvl, null, 0)
        "local"  -> Scp2(lvl, null, up.ups_tolist().let { it.count { it is Stmt.Block || it is Expr.Func } })
        else -> {
            val blk = up.env(this.lbl)
            /*
            println(this.lbl)
            println(up)
            println(up.env_all())
             */
            if (blk != null) {
                //println(this.lbl)
                val one = if (blk is Stmt.Block) 1 else 0
                Scp2(lvl, null, one + blk.ups_tolist().let { it.count { it is Stmt.Block || it is Expr.Func } })
            } else {    // false = relative to function block
                Scp2(lvl, this.lbl, (this.num ?: 0))
            }
        }
    }
}

fun Stmt.setScopes () {
    fun ft (tp: Type) {
        when (tp) {
            is Type.Ptr -> {
                tp.xscp2 = tp.xscp1.toScp2(tp)
            }
            is Type.Func -> {
                tp.xscp2s = Pair(tp.xscp1s.first?.toScp2(tp), tp.xscp1s.second!!.map { it.toScp2(tp) }.toTypedArray())
            }
        }
    }
    fun fe (e: Expr) {
        when (e) {
            is Expr.New -> {
                e.xscp2 = e.xscp1?.toScp2(e)
            }
            is Expr.Call -> {
                e.xscp2s = Pair(e.xscp1s.first!!.map { it.toScp2(e) }.toTypedArray(), e.xscp1s.second?.toScp2(e))
            }
        }
    }
    this.visit(false, null, ::fe, ::ft)
}
