import java.lang.Exception

val env_PRV: MutableMap<Any,Stmt> = mutableMapOf()

fun env_prelude (s: Stmt): Stmt {
    val int = Stmt.User(Tk.Str(TK.XUSER,1,1,"Int"), false, emptyArray())
    val stdo = Stmt.Func (
        Tk.Str(TK.XVAR,1,1,"output_std"),
        Type.Func (
            Tk.Sym(TK.ARROW, 1, 1, "->"),
            Type.Unit(Tk.Sym(TK.UNIT,1,1,"()")),
            Type.Unit(Tk.Sym(TK.UNIT,1,1,"()"))
        ),
        null
    )
    return Stmt.Seq(int.tk, int, Stmt.Seq(stdo.tk, stdo, s))
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
        if (prv!=null && (e is Expr.Var || e is Expr.Cons || e is Expr.Pred || e is Expr.Disc)) {
            env_PRV[e] = prv
        }
        return true
    }
    fun ft (tp: Type): Boolean {
        if (prv!=null && tp is Type.User) {
            env_PRV[tp] = prv
        }
        return true
    }
    if (prv!=null && (s is Stmt.Var || s is Stmt.User || s is Stmt.Func)) {
        env_PRV[s] = prv
    }
    return when (s) {
        is Stmt.Pass, is Stmt.Nat, is Stmt.Break -> prv
        is Stmt.Var   -> { s.type.visit(::ft) ; s.init.visit(::fe) ; s }
        is Stmt.User  -> { s.subs.forEach { it.second.visit(::ft) } ; s }
        is Stmt.Set   -> { s.dst.visit(::fe) ; s.src.visit(::fe) ; prv }
        is Stmt.Call  -> { s.call.visit(::fe) ; prv }
        is Stmt.Seq   -> { val prv2=env_PRV_set(s.s1,prv) ; env_PRV_set(s.s2, prv2) }
        is Stmt.If    -> { s.tst.visit(::fe) ; env_PRV_set(s.true_,prv) ; env_PRV_set(s.false_,prv) ; prv }
        is Stmt.Func  -> { if (s.block != null) { s.type.visit(::ft) ; env_PRV_set(s.block,prv) } ; s }
        is Stmt.Ret   -> { s.e.visit(::fe) ; prv }
        is Stmt.Loop  -> { env_PRV_set(s.block,prv) ; prv }
        is Stmt.Block -> { env_PRV_set(s.body,prv) ; prv }
    }
}

fun Any.env_dump () {
    println(this)
    val env = env_PRV[this]
    if (env != null) {
        env.env_dump()
    }
}

fun Any.id2stmt (id: String): Stmt? {
    //println("$id: $this")
    return when {
        (this is Stmt.Var  && this.tk_.str==id) -> this
        (this is Stmt.Func && this.tk_.str==id) -> this
        (this is Stmt.User && this.tk_.str==id) -> this
        (env_PRV[this] == null) -> null
        else -> env_PRV[this]!!.id2stmt(id)
    }
}

fun Any.supsub2type (sup: String, sub: String): Type? {
    try {
        val user = this.id2stmt(sup) as Stmt.User
        return user.subs.first { (id,_) -> id.str==sub }.second
    } catch (_: Exception) {
        return null
    }
}

fun Expr.totype (): Type {
    return when (this) {
        is Expr.Unit  -> Type.Unit(this.tk_)
        is Expr.Int   -> Type.User(Tk.Str(TK.XUSER,this.tk.lin,this.tk.col,"Int"))
        is Expr.Nat   -> Type.Nat(this.tk_)
        is Expr.Var   -> {
            val s = this.id2stmt(this.tk_.str)!!
            when (s) {
                is Stmt.Var -> s.type
                is Stmt.Func -> s.type
                else -> error("bug found")
            }
        }
        is Expr.Tuple -> Type.Tuple(this.tk_, this.vec.map{it.totype()}.toTypedArray())
        is Expr.Call  -> if (this.f is Expr.Nat) Type.Nat(this.f.tk_) else (this.f.totype() as Type.Func).out
        is Expr.Index -> (this.e.totype() as Type.Tuple).vec[this.tk_.num-1]
        is Expr.Cons  -> Type.User(Tk.Str(TK.XUSER,this.tk.lin,this.tk.col, this.sup.str))
        is Expr.Disc  -> this.supsub2type((this.e.totype() as Type.User).tk_.str, this.tk_.str)!!
        else -> { println(this) ; error("TODO") }
    }
}

fun check_dcls (s: Stmt): String? {
    var ret: String? = null
    fun ft (tp: Type): Boolean {
        return when (tp) {
            is Type.User -> {
                if (tp.id2stmt(tp.tk_.str) == null) {
                    ret = All_err_tk(tp.tk, "undeclared type \"${tp.tk_.str}\"")
                    return false
                }
                return true
            }
            else -> true

        }
    }
    fun fe (e: Expr): Boolean {
        return when (e) {
            is Expr.Var -> {
                if (e.id2stmt(e.tk_.str) == null) {
                    ret = All_err_tk(e.tk, "undeclared variable \"${e.tk_.str}\"")
                    false
                } else {
                    true
                }
            }
            is Expr.Cons -> {
                val sup = (e.totype() as Type.User).tk_.str
                when {
                    (e.id2stmt(sup) == null) -> {
                        ret = All_err_tk(e.tk, "undeclared type \"$sup\"")
                        false
                    }
                    (e.supsub2type(sup,e.sub.str) == null) -> {
                        ret = All_err_tk(e.tk, "undeclared subcase \"${e.sub.str}\"")
                        false
                    }
                    else -> true
                }
            }
            else -> true
        }
    }
    s.visit(null,::fe,::ft)
    return ret
}