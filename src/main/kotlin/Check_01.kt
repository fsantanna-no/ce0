fun Tk.Id.check (up: Any) {
    val ok = when {
        (this.id == "GLOBAL") -> true
        (this.id == "LOCAL")  -> true
        (up.ups_first { it is Type.Func } != null) -> true  // (@i1 -> ...)
        up.env(this.id).let {                              // { @aaa ... @aaa }
            /*
            println(this.id)
            println(up)
            println(">>>")
            println(up.env_all())
            println("<<<")
            println(it)
            */
            it is Stmt.Block && this.id==it.xscp1!!.id  ||
            it is Stmt.Var   && this.id==it.tk_.id.toUpperCase()
        } -> true
        (up.ups_first {                                     // [@i1, ...] { @i1 }
            it is Stmt.Typedef && (it.xscp1s.any { it.id==this.id })
         || it is Expr.Func    && (it.type.xscp1s.second?.any { it.id==this.id } ?: false)
        } != null) -> true
        else -> false
    }
    All_assert_tk(this, ok) {
        "undeclared scope \"${this.id}\""
    }
}

// need to check UNull/UCons on check_01 (Ce0) and check_02 (Ce1, b/c no type at check_01)

fun Expr.UNull.check () {
    All_assert_tk(this.xtype!!.tk, this.xtype.let { it is Type.Pointer && it.pln.noalias() is Type.Union }) { "invalid type : expected pointer to union"}
}

fun Expr.UCons.check () {
    All_assert_tk(this.xtype!!.tk, this.xtype.let { it.noalias() is Type.Union }) { "invalid type : expected union"}
    val uni = this.xtype.noalias() as Type.Union
    val ok = (uni.vec.size >= this.tk_.num)
    All_assert_tk(this.tk, ok) {
        "invalid union constructor : out of bounds"
    }
}

fun check_01_before_tps (s: Stmt) {
    fun ft (tp: Type) {
        when (tp) {
            is Type.Alias -> {
                val def = tp.env(tp.tk_.id)
                All_assert_tk(tp.tk, def is Stmt.Typedef) {
                    "undeclared type \"${tp.tk_.id}\""
                }
                val s1 = (def as Stmt.Typedef).xscp1s.size
                val s2 = tp.xscp1s.size
                All_assert_tk(tp.tk, s1 == s2) {
                    "invalid type : scope mismatch : expecting $s1, have $s2 argument(s)"
                }
            }
            is Type.Rec -> {
                val str = "^".repeat(tp.tk_.up)
                All_assert_tk(tp.tk, tp.wup is Type.Pointer) {
                    "invalid `$str´ : expected pointer type"    // must be pointer b/c ups is a vector of void*
                }
                val unions = tp.ups_tolist().count { it is Type.Union }
                All_assert_tk(tp.tk, unions >= tp.tk_.up) {
                    "invalid `$str´ : missing enclosing recursive type"
                }

            }
            is Type.Pointer -> tp.xscp1?.check(tp)
            is Type.Func -> {
                tp.xscp1s.first?.check(tp)
                val ptrs = (tp.inp.flattenLeft() + tp.out.flattenLeft()).filter { it is Type.Pointer } as List<Type.Pointer>
                val ok1 = ptrs.all {
                    val ptr = it.xscp1!!
                    when {
                        (ptr.id == "GLOBAL") -> true
                        //(ptr.id == "LOCAL")  -> true
                        (
                            tp.xscp1s.first.let  { it!=null && ptr.id==it.id } || // {@a} ...@a
                            tp.xscp1s.second?.any { ptr.id==it.id } ?: false      // (@i1 -> ...@i1...)
                        ) -> true
                        (tp.ups_first {                     // { @aaa \n ...@aaa... }
                            it is Stmt.Block && it.xscp1.let { it!=null && it.id==ptr.id }
                        } != null) -> true
                        else -> false
                    }
                }
                // all pointers must be listed either in "func.clo" or "func.scps"
                All_assert_tk(tp.tk, ok1) {
                    "invalid function type : missing scope argument"
                }
            }
        }
    }
    fun fe (e: Expr) {
        when (e) {
            is Expr.Var -> {
                All_assert_tk(e.tk, e.env(e.tk_.id) != null) {
                    "undeclared variable \"${e.tk_.id}\""
                }
            }
            is Expr.Upref -> {
                var track = false   // start tracking count if crosses UDisc
                var count = 1       // must remain positive after track (no uprefs)
                for (ee in e.flattenRight()) {
                    count = when (ee) {
                        is Expr.UDisc -> { track=true ; 1 }
                        is Expr.Dnref -> count+1
                        is Expr.Upref -> count-1
                        else -> count
                    }
                }
                All_assert_tk(e.tk, !track || count>0) {
                    "invalid operand to `/´ : union discriminator"
                }
            }
            is Expr.UNull -> {
                if (e.xtype != null) e.check()
            }
            is Expr.UCons -> {
                if (e.xtype != null) e.check()
            }
            is Expr.Func -> {
                val outers = e.ups_tolist().filter { it is Expr.Func } as List<Expr.Func>
                for (f in outers) {
                    val err = f.type.xscp1s.second?.find { tk2 -> e.type.xscp1s.second!!.any { tk1 -> tk1.id==tk2.id } }
                    All_assert_tk(e.tk, err==null) {
                        "invalid scope : \"${err!!.id}\" is already declared (ln ${err!!.lin})"
                    }
                }
                e.ups.forEach {
                    All_assert_tk(e.tk, e.env(it.id) != null) {
                        "undeclared variable \"${it.id}\""
                    }
                }
            }

            is Expr.New  -> e.xscp1?.check(e)
            is Expr.Call -> {
                e.xscp1s.second.let { it?.check(e) }
                e.xscp1s.first?.forEach { it.check(e) }
            }
        }
    }
    fun fs (s: Stmt) {
        fun Any.toTk (): Tk {
            return when (this) {
                is Type         -> this.tk
                is Stmt.Var     -> this.tk
                is Stmt.Block   -> this.tk
                is Stmt.Typedef -> this.tk
                else -> error("bug found")
            }
        }

        when (s) {
            is Stmt.Var -> {
                val dcl = s.env(s.tk_.id)
                All_assert_tk(s.tk, dcl == null) {
                    "invalid declaration : \"${s.tk_.id}\" is already declared (ln ${dcl!!.toTk().lin})"
                }
            }
            is Stmt.Return -> {
                val ok = s.ups_first { it is Expr.Func } != null
                All_assert_tk(s.tk, ok) {
                    "invalid return : no enclosing function"
                }
            }
            is Stmt.Block -> {
                s.xscp1?.let {
                    val dcl = s.env(it.id)
                    All_assert_tk(it, dcl == null) {
                        "invalid scope : \"${it.id}\" is already declared (ln ${dcl!!.toTk().lin})"
                    }
                }
                if (s.iscatch) {
                    All_assert_tk(s.tk, s.ups_first { it is Expr.Func } != null) {
                        "invalid `catch` : requires enclosing task"
                    }
                }
            }
        }
    }
    s.visit(false, ::fs, ::fe, ::ft)
}
