fun Tk.Scp1.check (up: Any) {
    val ok = when {
        (this.lbl == "GLOBAL") -> true
        (this.lbl == "LOCAL")  -> true
        (up.ups_first { it is Type.Func } != null) -> true  // (@i1 -> ...)
        up.env(this.lbl).let {                              // { @aaa ... @aaa }
            /*
            println(this.lbl)
            println(up)
            println(">>>")
            println(up.env_all())
            println("<<<")
            println(it)
            */
            it is Stmt.Block && this.lbl==it.xscp1!!.lbl && this.num==it.xscp1!!.num ||
            it is Stmt.Var   && this.lbl==it.tk_.str.toUpperCase() && this.num==null
        } -> true
        (up.ups_first {                                     // [@i1, ...] { @i1 }
            it is Expr.Func && (it.type.xscp1s.second?.any { it.lbl==this.lbl && it.num==this.num } ?: false)
        } != null) -> true
        else -> false
    }
    All_assert_tk(this, ok) {
        "undeclared scope \"@$lbl${this.num?:""}\""
    }
}

fun Expr.UCons.check () {
    val uni = this.xtype as Type.Union
    val ok = (uni.vec.size >= this.tk_.num)
    All_assert_tk(this.tk, ok) {
        "invalid constructor : out of bounds"
    }
}

fun check_01_before_tps (s: Stmt) {
    fun ft (tp: Type) {
        when (tp) {
            is Type.Rec -> {
                val str = "^".repeat(tp.tk_.up)
                All_assert_tk(tp.tk, tp.wup is Type.Ptr) {
                    "invalid `$str´ : expected pointer type"    // must be pointer b/c ups is a vector of void*
                }
                val unions = tp.ups_tolist().count { it is Type.Union }
                All_assert_tk(tp.tk, unions >= tp.tk_.up) {
                    "invalid `$str´ : missing enclosing recursive type"
                }

            }
            is Type.Ptr -> tp.xscp1?.check(tp)
            is Type.Func -> {
                tp.xscp1s.first?.check(tp)
                val ptrs  = (tp.inp.flattenLeft() + tp.out.flattenLeft()).filter { it is Type.Ptr } as List<Type.Ptr>
                val ok1 = ptrs.all {
                    val ptr = it.xscp1!!
                    when {
                        (ptr.lbl == "GLOBAL") -> true
                        //(ptr.lbl == "LOCAL")  -> true
                        (
                            tp.xscp1s.first.let  { it!=null && ptr.lbl==it.lbl && ptr.num==it.num } || // {@a} ...@a
                            tp.xscp1s.second?.any { ptr.lbl==it.lbl && ptr.num==it.num } ?: false      // (@i1 -> ...@i1...)
                        ) -> true
                        (tp.ups_first {                     // { @aaa \n ...@aaa... }
                            it is Stmt.Block && it.xscp1.let { it!=null && it.lbl==ptr.lbl && it.num==ptr.num }
                        } != null) -> true
                        else -> false
                    }
                }
                // all pointers must be listed either in "func.clo" or "func.scps"
                All_assert_tk(tp.tk, ok1) {
                    "invalid function type : missing pool argument"
                }
                val ok2 = (tp.xscp1s.second == null) || (tp.xscp1s.second!!
                    .groupBy { it.lbl }             // { "a"=[...], "b"=[...]
                    .all {
                        it.value                    // [...]
                            .map { it.num!! }       // [1,2,3,1,...]
                            .toSet()                // [1,2,3,...]
                            .let { (it.minOrNull() == 1) && (it.maxOrNull() == it.size) }
                    })
                All_assert_tk(tp.tk, ok2) {
                    "invalid function type : pool arguments are not continuous"
                }
            }
        }
    }
    fun fe (e: Expr) {
        when (e) {
            is Expr.Var -> {
                All_assert_tk(e.tk, e.env() != null) {
                    "undeclared variable \"${e.tk_.str}\""
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
            is Expr.UCons -> if (e.xtype != null) e.check()
            is Expr.Func -> {
                val outers = e.ups_tolist().filter { it is Expr.Func } as List<Expr.Func>
                for (f in outers) {
                    val err = f.type.xscp1s.second?.find { tk2 -> e.type.xscp1s.second!!.any { tk1 -> tk1.lbl==tk2.lbl } }
                    All_assert_tk(e.tk, err==null) {
                        "invalid pool : \"@${err!!.lbl}\" is already declared (ln ${err!!.lin})"
                    }
                }
                e.ups.forEach {
                    All_assert_tk(e.tk, e.env(it.str) != null) {
                        "undeclared variable \"${it.str}\""
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
                is Type -> this.tk
                is Stmt.Var -> this.tk
                is Stmt.Block -> this.tk
                else -> error("bug found")
            }
        }

        when (s) {
            is Stmt.Var -> {
                val dcl = s.env(s.tk_.str)
                All_assert_tk(s.tk, dcl == null) {
                    "invalid declaration : \"${s.tk_.str}\" is already declared (ln ${dcl!!.toTk().lin})"
                }
            }
            is Stmt.Ret -> {
                val ok = s.ups_first { it is Expr.Func } != null
                All_assert_tk(s.tk, ok) {
                    "invalid return : no enclosing function"
                }
            }
            is Stmt.Block -> s.xscp1?.let {
                All_assert_tk(it, it.num == null) {
                    "invalid pool : unexpected `_${it.num}´ depth"
                }
                val dcl = s.env(it.lbl)
                All_assert_tk(it, dcl == null) {
                    "invalid pool : \"@${it.lbl}\" is already declared (ln ${dcl!!.toTk().lin})"
                }
            }
        }
    }
    s.visit(false, ::fs, ::fe, ::ft)
}
