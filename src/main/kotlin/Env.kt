import java.lang.Exception

val env_PRV: MutableMap<Any,Stmt> = mutableMapOf()

fun env_prelude (s: Stmt): Stmt {
    val int = Stmt.User(Tk.Str(TK.XUSER,1,1,"Int"), false, emptyArray())
    val stdo = Stmt.Func (
        Tk.Str(TK.XVAR,1,1,"output_std"),
        Type.Func (
            Tk.Sym(TK.ARROW, 1, 1, "->"),
            Type.Any(Tk.Chr(TK.CHAR,1,1,'?')),
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
fun env_PRV_set (s: Stmt, cur: Stmt?): Stmt? {
    fun fe (e: Expr): Boolean {
        if (cur!=null && (e is Expr.Var || e is Expr.Cons || e is Expr.Pred || e is Expr.Disc)) {
            env_PRV[e] = cur
        }
        return true
    }
    fun ft (tp: Type): Boolean {
        if (cur!=null && tp is Type.User) {
            env_PRV[tp] = cur
        }
        return true
    }
    if (cur!=null && (s is Stmt.Var || s is Stmt.User || s is Stmt.Func)) {
        env_PRV[s] = cur
    }
    return when (s) {
        is Stmt.Pass, is Stmt.Nat, is Stmt.Break -> cur
        is Stmt.Var   -> { s.type.visit(::ft) ; s.init.visit(::fe) ; s }
        is Stmt.User  -> { s.subs.forEach { it.second.visit(::ft) } ; s }
        is Stmt.Set   -> { s.dst.visit(::fe) ; s.src.visit(::fe) ; cur }
        is Stmt.Call  -> { s.call.visit(::fe) ; cur }
        is Stmt.Seq   -> { val prv2=env_PRV_set(s.s1,cur) ; env_PRV_set(s.s2, prv2) }
        is Stmt.If    -> { s.tst.visit(::fe) ; env_PRV_set(s.true_,cur) ; env_PRV_set(s.false_,cur) ; cur }
        is Stmt.Func  -> { if (s.block != null) { s.type.visit(::ft) ; env_PRV_set(s.block,cur) } ; s }
        is Stmt.Ret   -> { s.e.visit(::fe) ; cur }
        is Stmt.Loop  -> { env_PRV_set(s.block,cur) ; cur }
        is Stmt.Block -> { env_PRV_set(s.body,cur) ; cur }
    }
}

fun Any.env_dump () {
    println(this)
    val env = env_PRV[this]
    if (env != null) {
        env.env_dump()
    }
}

fun Any.idToStmt (id: String): Stmt? {
    //println("$id: $this")
    return when {
        (this is Stmt.Var  && this.tk_.str==id) -> this
        (this is Stmt.Func && this.tk_.str==id) -> this
        (this is Stmt.User && this.tk_.str==id) -> this
        (env_PRV[this] == null) -> null
        else -> env_PRV[this]!!.idToStmt(id)
    }
}

fun Any.supSubToType (sup: String, sub: String): Type? {
    try {
        val user = this.idToStmt(sup) as Stmt.User
        return user.subs.first { (id,_) -> id.str==sub }.second
    } catch (_: Exception) {
        return null
    }
}

fun Expr.toType (): Type {
    return when (this) {
        is Expr.Unk   -> Type.Any(this.tk_)
        is Expr.Unit  -> Type.Unit(this.tk_)
        is Expr.Int   -> Type.User(Tk.Str(TK.XUSER,this.tk.lin,this.tk.col,"Int"))
        is Expr.Nat   -> Type.Nat(this.tk_)
        is Expr.Upref -> Type.Ptr(this.tk_, this.e.toType())
        is Expr.Dnref -> (this.e.toType() as Type.Ptr).tp
        is Expr.Var   -> {
            val s = this.idToStmt(this.tk_.str)!!
            when (s) {
                is Stmt.Var -> s.type
                is Stmt.Func -> s.type
                else -> error("bug found")
            }
        }
        is Expr.Tuple -> Type.Tuple(this.tk_, this.vec.map{it.toType()}.toTypedArray())
        is Expr.Call  -> if (this.f is Expr.Nat) Type.Nat(this.f.tk_) else (this.f.toType() as Type.Func).out
        is Expr.Index -> (this.e.toType() as Type.Tuple).vec[this.tk_.num-1]
        is Expr.Cons  -> Type.User(Tk.Str(TK.XUSER,this.tk.lin,this.tk.col, this.sup.str))
        is Expr.Disc  -> this.supSubToType((this.e.toType() as Type.User).tk_.str, this.tk_.str)!!
        is Expr.Pred  -> Type.User(Tk.Str(TK.XUSER,this.tk.lin,this.tk.col, "Bool"))
        is Expr.Empty -> Type.User(this.tk_)
    }
}

fun Stmt.User.isHasRec (): Boolean {
    fun aux (tp: Type): Boolean {
        return when (tp) {
            is Type.Any, is Type.Unit, is Type.Nat, is Type.Ptr -> false
            is Type.Tuple -> tp.vec.any { aux(it) }
            is Type.User  -> (tp.tk_.str.idToStmt(this.tk_.str) as Stmt.User).let { it.isHasRec() }
            else -> false
        }
    }
    return this.subs.any { aux(it.second) }
}

fun check_dcls (s: Stmt): String? {
    var ret: String? = null
    fun ft (tp: Type): Boolean {
        when (tp) {
            is Type.User -> {
                if (tp.idToStmt(tp.tk_.str) == null) {
                    ret = All_err_tk(tp.tk, "undeclared type \"${tp.tk_.str}\"")
                    return false
                }
            }
        }
        return true
    }
    fun fe (e: Expr): Boolean {
        when (e) {
            is Expr.Var -> {
                if (e.idToStmt(e.tk_.str) == null) {
                    ret = All_err_tk(e.tk, "undeclared variable \"${e.tk_.str}\"")
                    return false
                }
            }
            is Expr.Empty -> {
                val sup = (e.toType() as Type.User).tk_.str
                if (e.idToStmt(sup) == null) {
                    ret = All_err_tk(e.tk, "undeclared type \"$sup\"")
                    return false
                }
            }
            is Expr.Cons -> {
                val sup = (e.toType() as Type.User).tk_.str
                when {
                    (e.idToStmt(sup) == null) -> {
                        ret = All_err_tk(e.tk, "undeclared type \"$sup\"")
                        return false
                    }
                    (e.supSubToType(sup,e.sub.str) == null) -> {
                        ret = All_err_tk(e.tk, "undeclared subcase \"${e.sub.str}\"")
                        return false
                    }
                }
            }
            is Expr.Disc -> {
                when {
                    (e.e.toType() !is Type.User) -> {
                        ret = All_err_tk(e.e.tk, "invalid discriminator : expected user type")
                        return false
                    }
                    (e.supSubToType((e.e.toType() as Type.User).tk_.str, e.tk_.str) == null) -> {
                        ret = All_err_tk(e.tk, "undeclared subcase \"${e.tk_.str}\"")
                        return false
                    }
                }
            }
            is Expr.Pred -> {
                when {
                    (e.e.toType() !is Type.User) -> {
                        ret = All_err_tk(e.e.tk, "invalid predicate : expected user type")
                        return false
                    }
                    (e.supSubToType((e.e.toType() as Type.User).tk_.str, e.tk_.str) == null) -> {
                        ret = All_err_tk(e.tk, "undeclared subcase \"${e.tk_.str}\"")
                        return false
                    }
                }
            }
        }
        return true
    }
    fun fs (s: Stmt): Boolean {
        when (s) {
            is Stmt.User -> {
                if (s.isrec != s.isHasRec()) {
                    ret = All_err_tk(s.tk, "invalid type declaration : unexpected `@rec´")
                    return false
                }
            }
        }
        return true
    }
    s.visit(::fs,::fe,::ft)
    return ret
}

fun Type.isSupOf (sub: Type): Boolean {
    return when {
        (this is Type.Any || sub is Type.Any) -> true
        (this is Type.Nat || sub is Type.Nat) -> true
        (this::class != sub::class) -> false
        (this is Type.Ptr && sub is Type.Ptr) -> this.tp.isSupOf(sub.tp)
        (this is Type.Tuple && sub is Type.Tuple) ->
            (this.vec.size==sub.vec.size) && this.vec.zip(sub.vec).all { (x,y) -> x.isSupOf(y) }
        else -> true
    }
}

fun check_types (S: Stmt): String? {
    var ret: String? = null
    fun fe (e: Expr): Boolean {
        when (e) {
            is Expr.Upref -> {
                if (e.e.toType() is Type.Ptr) {
                    ret = All_err_tk(e.e.tk, "invalid `\\` : unexpected pointer type")
                    return false
                }
            }
            is Expr.Dnref -> {
                if (e.e.toType() !is Type.Ptr) {
                    ret = All_err_tk(e.tk, "invalid `\\` : expected pointer type")
                    return false
                }
            }
            is Expr.Call -> {
                val inp = e.f.toType().let { if (it is Type.Func) it.inp else (it as Type.Nat) }
                if (!inp.isSupOf(e.arg.toType())) {
                    ret = All_err_tk(e.f.tk, "invalid call to \"${(e.f as Expr.Var).tk_.str}\" : type mismatch")
                    return false
                }
            }
        }
        return true
    }
    fun fs (s: Stmt): Boolean {
        when (s) {
            is Stmt.Var -> {
                if (!s.type.isSupOf(s.init.toType())) {
                    ret = All_err_tk(s.tk, "invalid assignment to \"${s.tk_.str}\" : type mismatch")
                    return false
                }
            }
            is Stmt.Set -> {
                if (!s.dst.toType().isSupOf(s.src.toType())) {
                    ret = when {
                        (s.dst !is Expr.Var) -> All_err_tk(s.tk, "invalid assignment : type mismatch")
                        (s.dst.tk_.str == "_ret_") -> All_err_tk(s.tk, "invalid return : type mismatch")
                        else -> All_err_tk(s.tk, "invalid assignment to \"${s.dst.tk_.str}\" : type mismatch")
                    }
                    return false
                }
            }
        }
        return true
    }
    S.visit(::fs, ::fe, null)
    return ret
}
