fun Scope.local2Block (up: Any) {
    this.scp1 = if (this.scp1.id == "LOCAL") {
        val id = (up.ups_first { it is Stmt.Block } as Stmt.Block?).let {
            if (it == null) "GLOBAL" else it.scp!!.scp1.id
        }
        Tk.Id(TK.XID, this.scp1.lin, this.scp1.col, id)
    } else {
        this.scp1
    }
}

fun Stmt.setScp1s () {
    fun ft (tp: Type) {
        when (tp) {
            is Type.Alias -> {
                tp.scps!!.map { it.local2Block(tp) }.toTypedArray()
            }
            is Type.Pointer -> {
                tp.scp!!.local2Block(tp)
            }
            is Type.Func -> {
                tp.scps.first?.local2Block(tp)
                tp.scps.second!!.forEach { it.local2Block(tp) }
            }
        }
    }

    fun fe (e: Expr) {
        when (e) {
            is Expr.New -> {
                e.scp?.local2Block(e)
            }
            is Expr.Call -> {
                e.scps.first?.forEach { it.local2Block(e) }
                e.scps.second?.local2Block(e)
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
    this.visit(::fs, ::fe, ::ft, null)
}
