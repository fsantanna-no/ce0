data class Scp2 (val lvl: Int, val par: String?, val depth: Int)

fun Type.toScp2 (): Scp2 {
    return when {
        this is Type.Pointer -> this.xscp2!!
        (this is Type.Func) && (this.xscp1s.first!=null) -> this.xscp2s!!.first!! // body holds pointers in clo
        else -> Scp2(0, null, 0)
    }
}

fun Tk.Id.toScp2 (up: Any): Scp2 {
    val lvl = up.ups_tolist().filter { it is Expr.Func }.count() // level of function nesting
    return when (this.id) { // 2xExpr.Func, otherwise no level between outer/arg/body
        "GLOBAL" -> Scp2(0, null, 0)
        "LOCAL"  -> {
            // umn=-2: @LOCAL must be clo annotation, so remove further 2*Expr.Func
            val umn = if (up is Type.Func && up.wup is Expr.Func) -2 else 0
            Scp2(lvl, null, umn + up.ups_tolist().let { it.count{it is Stmt.Block} + 2*it.count{it is Expr.Func} })
        }
        else -> {
            val blk = up.env(this.id)
            /*
            println(this.id)
            println(up)
            println(up.env_all())
             */
            if (blk != null) {
                // @A, @x, ...
                val one = if (blk is Stmt.Block) 1 else 0
                val umn = if (this.id=="arg")   1 else 0   // "arg" is in between Func-arg-Block
                Scp2(lvl, null, one - umn + blk.ups_tolist().let { it.count{it is Stmt.Block} + 2*it.count{it is Expr.Func} })
            } else {
                // @a1, @i2, ...
                println(this)
                println(up.ups_tolist())
                val depth = (up.ups_first(true) {
                    it is Type.Func || it is Expr.Func
                }.let {
                    if (it is Expr) it.wtype!! else it
                } as Type.Func).let {
                    println(it.xscp1s.second)
                    println(this.id)
                    it.xscp1s.second.find { it.first.id==this.id }!!.second
                }
                Scp2(lvl, this.id, depth)
            }
        }
    }
}

fun Stmt.setScp2s () {
    fun ft (tp: Type) {
        when (tp) {
            is Type.Pointer -> {
                tp.xscp2 = tp.xscp1?.toScp2(tp)
            }
            is Type.Func -> {
                tp.xscp2s = Pair(tp.xscp1s.first?.toScp2(tp), tp.xscp1s.second!!.map { it.first.toScp2(tp) }.toTypedArray())
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
