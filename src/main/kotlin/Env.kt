val gUPS: MutableMap<Any,Any> = mutableMapOf()

fun gUPS_set (S: Stmt) {
    fun fs (s: Stmt) {
        when (s) {
            is Stmt.Pass, is Stmt.User, is Stmt.Nat, is Stmt.Break -> {}
            is Stmt.Var   -> gUPS[s.init] = s
            is Stmt.Set   -> { gUPS[s.dst]=s ; gUPS[s.src]=s }
            is Stmt.Call  -> gUPS[s.call] = s
            is Stmt.Seq   -> { gUPS[s.s1]=s ; gUPS[s.s2]=s.s1 }
            is Stmt.If    -> { gUPS[s.tst]=s ; gUPS[s.true_]=s ; gUPS[s.false_]=s }
            is Stmt.Func  -> gUPS[s.block] = s
            is Stmt.Ret   -> gUPS[s.e] = s
            is Stmt.Loop  -> gUPS[s.block] = s
            is Stmt.Block -> gUPS[s.body] = s
        }
    }
    fun fe (e: Expr) {
        when (e) {
            is Expr.Unit, is Expr.Int, is Expr.Var, is Expr.Nat, is Expr.Empty -> {}
            is Expr.Tuple -> e.vec.forEach { gUPS[it]=e }
            is Expr.Cons  -> gUPS[e.pos] = e
            is Expr.Dnref -> gUPS[e.pre] = e
            is Expr.Upref -> gUPS[e.pos] = e
            is Expr.Index -> gUPS[e.pre] = e
            is Expr.Pred  -> gUPS[e.pre] = e
            is Expr.Disc  -> gUPS[e.pre] = e
            is Expr.Call  -> { gUPS[e.pre]=e ; gUPS[e.pos]=e }
        }
    }
    S.visit(::fs,::fe)
}

fun Any.id2var (id: String): Stmt.Var? {
    //println("$id: $this")
    return when {
        (this is Stmt.Var && this.tk_.str==id) -> this
        (gUPS[this] == null) -> null
        else -> gUPS[this]!!.id2var(id)
    }
}

fun Expr.totype (): Type {
    return when (this) {
        is Expr.Unit  -> Type.Unit(this.tk_)
        is Expr.Var   -> this.id2var(this.tk_.str)!!.type
        is Expr.Nat   -> Type.Nat(this.tk_)
        is Expr.Tuple -> Type.Tuple(this.tk_, this.vec.map{it.totype()}.toTypedArray())
        is Expr.Index -> error("TODO")
        else -> error("TODO")
    }
}