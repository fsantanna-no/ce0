fun Scope.check (up: Any) {
    val ok = when {
        (this.scp1.id == "GLOBAL") -> true
        (this.scp1.id == "LOCAL") -> true
        (up.ups_first { it is Type.Func || it is Stmt.Typedef } != null) -> true  // (@i1 -> ...)
        up.env(this.scp1.id).let {                              // { @aaa ... @aaa }
            it is Stmt.Block && this.scp1.id==it.scp1!!.id  ||
            it is Stmt.Var   && this.scp1.id==it.tk_.id.toUpperCase()
        } -> true
        (up.ups_first {                                     // [@i1, ...] { @i1 }
            it is Stmt.Typedef && (it.xscp1s.first!!.any { it.id==this.scp1.id })
         || it is Expr.Func    && (it.type.xscps.second?.any { it.scp1.id==this.scp1.id } ?: false)
        } != null) -> true
        else -> false
    }
    All_assert_tk(this.scp1, ok) {
        "undeclared scope \"${this.scp1.id}\""
    }
}

fun check_01_before_tps (s: Stmt) {
    fun ft (tp: Type) {
        when (tp) {
            is Type.Pointer -> tp.xscp?.check(tp)
            is Type.Func -> {
                tp.xscps.first?.check(tp)
                val ptrs = (tp.inp.flattenLeft() + tp.out.flattenLeft()).filter { it is Type.Pointer } as List<Type.Pointer>
                val ok = ptrs.all {
                    val ptr = it.xscp!!
                    when {
                        (ptr.scp1.id == "GLOBAL") -> true
                        (
                            tp.xscps.first.let  { it!=null && ptr.scp1.id==it.scp1.id } || // {@a} ...@a
                            tp.xscps.second?.any { ptr.scp1.id==it.scp1.id } ?: false      // (@i1 -> ...@i1...)
                        ) -> true
                        (tp.ups_first {                     // { @aaa \n ...@aaa... }
                            it is Stmt.Block && it.scp1.let { it!=null && it.id==ptr.scp1.id }
                        } != null) -> true
                        else -> false
                    }
                }
                // all pointers must be listed either in "func.clo" or "func.scps"
                All_assert_tk(tp.tk, ok) {
                    "invalid function type : missing scope argument"
                }
            }
        }
    }
    fun fe (e: Expr) {
        when (e) {
            is Expr.Func -> {
                val outers: List<Scope> = e.ups_tolist().let {
                    val es = it.filter { it is Expr.Func }.let { it as List<Expr.Func> }.map { it.type }
                    val ts = it.filter { it is Type.Func }.let { it as List<Type.Func> }
                    (es + ts).map { it.xscps.second ?: emptyList() }.flatten()
                }
                val err = outers.find { out -> e.type.xscps.second!!.any { it.scp1.id==out.scp1.id } }
                All_assert_tk(e.tk, err==null) {
                    "invalid scope : \"${err!!.scp1.id}\" is already declared (ln ${err!!.scp1.lin})"
                }
            }

            is Expr.New  -> e.xscp?.check(e)
            is Expr.Call -> {
                e.xscps.second.let { it?.check(e) }
                e.xscps.first?.forEach { it.check(e) }
            }
        }
    }
    s.visit(null, ::fe, ::ft, null)
}
