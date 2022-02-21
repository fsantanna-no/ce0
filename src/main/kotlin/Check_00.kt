// need to check UNull/UCons on check_01 (Ce0) and check_02 (Ce1, b/c no type at check_01)

fun Expr.UNull.check () {
    val ok = this.xtype.let {
        it is Type.Pointer && it.pln.noalias() is Type.Union ||
                // <.0>:/List @[LOCAL]  ---  type List  @[s] = </List  @[s] @s>
                it is Type.Alias   && it.noalias().let { it is Type.Pointer && it.pln is Type.Union }
        // <.0>:PList @[LOCAL]  ---  type PList @[s] = /<PList @[s]> @s
    }
    All_assert_tk(this.xtype!!.tk, ok) { "invalid type : expected pointer to union"}
}

fun Expr.UCons.check () {
    val tp = this.xtype!!.noalias()     // .noalias() b/c of Ce1
    All_assert_tk(this.xtype!!.tk, tp is Type.Union) { "invalid type : expected union" }
    val uni = tp as Type.Union
    val ok = (uni.vec.size >= this.tk_.num)
    All_assert_tk(this.tk, ok) {
        "invalid union constructor : out of bounds"
    }
}

fun check_00_after_envs (s: Stmt) {
    fun ft (tp: Type) {
        when (tp) {
            is Type.Alias -> {
                val def = tp.env(tp.tk_.id,true)
                All_assert_tk(tp.tk, def is Stmt.Typedef) {
                    "undeclared type \"${tp.tk_.id}\""
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
                s.scp1?.let {
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
    s.visit(::fs, ::fe, ::ft, null)
}
