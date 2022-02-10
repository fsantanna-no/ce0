fun Tk.Id.local2Block (up: Any): Tk.Id {
    return if (this.id == "LOCAL") {
        val id = (up.ups_first { it is Stmt.Block } as Stmt.Block?).let {
            if (it == null) "GLOBAL" else it.scp1!!.id
        }
        Tk.Id(TK.XID, this.lin, this.col, id)
    } else {
        this
    }
}

fun Stmt.setScp1s () {
    fun ft (tp: Type) {
        when (tp) {
            is Type.Alias -> {
                tp.xscp1s = tp.xscp1s!!.map { it.local2Block(tp) }.toTypedArray()
            }
            is Type.Pointer -> {
                tp.xscp1 = tp.xscp1!!.local2Block(tp)
            }
            is Type.Func -> {
                tp.xscp1s = Triple (
                    tp.xscp1s.first?.local2Block(tp),
                    tp.xscp1s.second!!.map { it.local2Block(tp) }.toTypedArray(),
                    tp.xscp1s.third
                )
            }
        }
    }

    fun fe (e: Expr) {
        when (e) {
            is Expr.New -> {
                e.xscp1 = e.xscp1?.local2Block(e)
            }
            is Expr.Call -> {
                e.xscp1s = Pair (
                    e.xscp1s.first?.map { it.local2Block(e) }.toTypedArray(),
                    e.xscp1s.second?.local2Block(e)
                )
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
