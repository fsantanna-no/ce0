fun Scope.toScp2 (up: Any) {
    val lvl = up.ups_tolist().filter { it is Expr.Func }.count() // level of function nesting
    this.scp2 = when (this.scp1.id) { // 2xExpr.Func, otherwise no level between outer/arg/body
        "GLOBAL" -> Triple(0, null, 0)
        else -> {
            val blk = up.env(this.scp1.id)
            if (blk != null) {
                // @A, @x, ...
                val one = if (blk is Stmt.Block) 1 else 0
                val umn = if (this.scp1.id=="arg")    1 else 0   // "arg" is in between Func-arg-Block
                Triple(lvl, null, one - umn + blk.ups_tolist().let { it.count{it is Stmt.Block} + 2*it.count{it is Expr.Func} })
            } else {
                Triple(lvl, this.scp1.id, null)
            }
        }
    }
}

fun Stmt.setScp2s () {
    fun ft (tp: Type) {
        when (tp) {
            is Type.Alias -> {
                tp.scps!!.forEach { it.toScp2(tp) }
            }
            is Type.Pointer -> {
                tp.scp!!.toScp2(tp)
            }
            is Type.Func -> {
                tp.scps.first?.toScp2(tp)
                tp.scps.second!!.forEach { it.toScp2(tp) }
            }
        }
    }

    // TODO: both New/Call should have xscp1!!
    fun fe (e: Expr) {
        when (e) {
            is Expr.New -> {
                e.scp?.toScp2(e)
            }
            is Expr.Call -> {
                e.scps.first!!.forEach { it.toScp2(e) }
                e.scps.second?.toScp2(e)
            }
        }
    }

    fun fs (s: Stmt) {
        when (s) {
            is Stmt.Typedef -> {
                //s.xscp2s = s.xscp1s.first.map { it.toScp2(s) }.toTypedArray()
            }
        }
    }
    this.visit(::fs, ::fe, ::ft)
}
