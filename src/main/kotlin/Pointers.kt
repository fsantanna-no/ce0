fun Stmt.getDepth (): Int {
    return this.ups_tolist().count { it is Stmt.Block }
}

fun Expr.getDepth (caller: Int, hold: Boolean): Pair<Int,Stmt.Var?> {
    return when (this) {
        is Expr.Unk, is Expr.Unit, is Expr.Nat, is Expr.UPred, is Expr.Func -> Pair(0, null)
        is Expr.Var -> {
            val dcl = this.env()!!
            return if (hold) Pair(dcl.getDepth(), dcl) else Pair(0, null)
        }
        is Expr.Upref -> this.pln.getDepth(caller, hold)
        is Expr.Dnref -> this.ptr.getDepth(caller, hold)
        is Expr.TDisc -> this.tup.getDepth(caller, hold)
        is Expr.UDisc -> this.uni.getDepth(caller, hold)
        is Expr.Call -> this.f.toType().let {
            when (it) {
                is Type.Nat -> Pair(0, null)
                is Type.Func -> if (!it.out.containsPtr()) Pair(0,null) else {
                    // substitute args depth/id by caller depth/id
                    Pair(caller, (this.f as Expr.Var).env())
                }
                else -> error("bug found")
            }
        }
        is Expr.TCons -> this.arg.map { it.e.getDepth(caller,it.e.toType().containsPtr()) }.maxByOrNull { it.first }!!
        is Expr.UCons -> this.arg.e.getDepth(caller, hold)
    }
}

fun check_pointers (S: Stmt) {
    fun fs (s: Stmt) {
        when (s) {
            is Stmt.Var -> {
                val dst_depth = s.getDepth()
                val (src_depth, src_dcl) = s.src.e.getDepth(dst_depth, s.type.containsPtr())
                All_assert_tk(s.tk, dst_depth >= src_depth) {
                    "invalid assignment : cannot hold local pointer \"${src_dcl!!.tk_.str}\" (ln ${src_dcl!!.tk.lin})"
                }
            }
            is Stmt.Set -> {
                val set_depth = s.getDepth()
                val (src_depth, src_dcl) = s.src.e.getDepth(set_depth, s.dst.toType().containsPtr())
                println(s.dst.getDepth(set_depth, s.dst.toType().containsPtr()).first)
                println(src_depth)
                All_assert_tk(s.tk, s.dst.getDepth(set_depth, s.dst.toType().containsPtr()).first >= src_depth) {
                    "invalid assignment : cannot hold local pointer \"${src_dcl!!.tk_.str}\" (ln ${src_dcl!!.tk.lin})"
                }
            }
        }
    }
    S.visit(::fs, null, null, null)
}
