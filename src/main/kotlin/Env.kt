val env_PRV: MutableMap<Any,Stmt> = mutableMapOf()

fun env_prelude (s: Stmt): Stmt {
    val stdo = Stmt.Func (
        Tk.Str(TK.XVAR,1,1,"std"),
        Type.Func (
            Tk.Sym(TK.ARROW, 1, 1, "->"),
            Type.Unit(Tk.Sym(TK.UNIT,1,1,"()")),
            Type.Unit(Tk.Sym(TK.UNIT,1,1,"()"))
        ),
        null
    )
    return Stmt.Seq(stdo.tk, stdo, s)
}

/*
 * Environment of each Stmt/Expr based on backlinks to previous Stmt.Var/Stmt.Func.
 * func f: ...
 * var x: ...
 * var y: ...
 * return f(x,y)    // x+y -> y -> x -> f -> null
 */
fun env_PRV_set (s: Stmt, prv: Stmt?): Stmt? {
    fun fe (e: Expr): Boolean {
        if (prv != null) {
            env_PRV[e] = prv
        }
        return true
    }
    if (prv != null) {
        env_PRV[s] = prv
    }
    return when (s) {
        is Stmt.Pass, is Stmt.User, is Stmt.Nat, is Stmt.Break -> prv
        is Stmt.Var   -> { s.init.visit(::fe) ; s }
        is Stmt.Set   -> { s.dst.visit(::fe) ; s.src.visit(::fe) ; prv }
        is Stmt.Call  -> { s.call.visit(::fe) ; prv }
        is Stmt.Seq   -> { val prv2=env_PRV_set(s.s1,prv) ; env_PRV_set(s.s2, prv2) }
        is Stmt.If    -> { s.tst.visit(::fe) ; env_PRV_set(s.true_,prv) ; env_PRV_set(s.false_,prv) ; prv }
        is Stmt.Func  -> { if (s.block != null) env_PRV_set(s.block,prv) ; s }
        is Stmt.Ret   -> { s.e.visit(::fe) ; prv }
        is Stmt.Loop  -> { env_PRV_set(s.block,prv) ; prv }
        is Stmt.Block -> { env_PRV_set(s.body,prv) ; prv }
    }
}

fun Any.id2type (id: String): Type? {
    //println("$id: $this")
    return when {
        (this is Stmt.Var  && this.tk_.str==id) -> this.type
        (this is Stmt.Func && this.tk_.str==id) -> this.type
        (env_PRV[this] == null) -> null
        else -> env_PRV[this]!!.id2type(id)
    }
}

fun Expr.totype (): Type {
    return when (this) {
        is Expr.Unit  -> Type.Unit(this.tk_)
        is Expr.Var   -> this.id2type(this.tk_.str)!!
        is Expr.Nat   -> Type.Nat(this.tk_)
        is Expr.Tuple -> Type.Tuple(this.tk_, this.vec.map{it.totype()}.toTypedArray())
        is Expr.Index -> error("TODO")
        else -> error("TODO")
    }
}

fun check_vars (s: Stmt): String? {
    var ret: String? = null
    fun fe (e: Expr): Boolean {
        return when (e) {
            is Expr.Var -> {
                if (env_PRV[e]!=null && env_PRV[e]!!.id2type(e.tk_.str) == null) {
                    ret = All_err_tk(e.tk, "undeclared variable \"${e.tk_.str}\"")
                    return false
                }
                return true
            }
            else -> true
        }
    }
    s.visit(null,::fe)
    return ret
}