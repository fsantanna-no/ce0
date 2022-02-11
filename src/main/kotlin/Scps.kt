// Triple<lvl,par,depth>
data class Scope (var scp1: Tk.Id, var scp2: Triple<Int,String?,Int?>?)

fun Any.localBlock (): String {
    return (this.ups_first { it is Stmt.Block } as Stmt.Block?).let {
        if (it == null) "GLOBAL" else it.scp1!!.id
    }
}

fun Stmt.setScp1s () {
    fun fx (up: Any, scp: Scope) {
        scp.scp1 = if (scp.scp1.id != "LOCAL") scp.scp1 else {
            Tk.Id(TK.XID, scp.scp1.lin, scp.scp1.col, up.localBlock())
        }
    }
    this.visit(null, null, null, ::fx)
}

fun Stmt.setScp2s () {
    fun fx (up: Any, scp: Scope) {
        val lvl = up.ups_tolist().filter { it is Expr.Func }.count() // level of function nesting
        scp.scp2 = when (scp.scp1.id) { // 2xExpr.Func, otherwise no level between outer/arg/body
            "GLOBAL" -> Triple(0, null, 0)
            else -> {
                val blk = up.env(scp.scp1.id)
                if (blk != null) {
                    // @A, @x, ...
                    val one = if (blk is Stmt.Block) 1 else 0
                    val umn = if (scp.scp1.id=="arg")    1 else 0   // "arg" is in between Func-arg-Block
                    Triple(lvl, null, one - umn + blk.ups_tolist().let { it.count{it is Stmt.Block} + 2*it.count{it is Expr.Func} })
                } else {
                    Triple(lvl, scp.scp1.id, null)
                }
            }
        }
    }
    this.visit(null, null, null, ::fx)
}
