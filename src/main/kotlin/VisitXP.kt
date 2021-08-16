fun Expr.visitXP (env: Env, fx: ((Env, XExpr, Type) -> Unit)?, fe: ((Env, Expr, Type) -> Unit)?, xp: Type) {
    when (this) {
        is Expr.Unk, is Expr.Unit, is Expr.Var, is Expr.Nat -> {}
        is Expr.Tuple -> {
            val xp_cons = xp as Type.Cons
            assert(xp_cons.tk_.chr == '[')
            this.vec.forEachIndexed { i,v -> v.visitXP(env,fx,fe,xp_cons.vec[i]) }
        }
        is Expr.Varia -> {
            val xp_cons = xp as Type.Cons
            assert(xp_cons.tk_.chr == '<')
            val xp_cons2 = xp_cons.map { if (it is Type.Rec) xp_cons else it } as Type.Cons
            val sub = if (this.tk_.idx > 0) xp_cons2.vec[this.tk_.idx-1] else Type_Unit(this.tk)
            this.arg.visitXP(env,fx,fe,sub)
        }
        is Expr.Dnref -> this.sub.visitXP(env,fx,fe,xp.keepAnyNat { Type.Ptr(Tk.Chr(TK.CHAR,this.tk.lin,this.tk.col,'\\'),xp) })
        is Expr.Upref -> this.sub.visitXP(env,fx,fe,xp.keepAnyNat{(xp as Type.Ptr).tp})
        is Expr.Index -> this.pre.visitXP(env,fx,fe,this.pre.toType(env))
        is Expr.Call  -> {
            val xp2 = this.f.toType(env).let { it.keepAnyNat{it as Type.Func} }
            this.f.visitXP(env,fx,fe,xp2)
            this.arg.visitXP(env,fx,fe,xp2.keepAnyNat{ (xp2 as Type.Func).inp })
        }
    }
    if (fe != null) {
        assert(xp.isSupOf(this.toType(env))) { "bug found" }
        fe(env,this,xp)
    }
}

fun XExpr.visitXP (env: Env, fx: ((Env, XExpr, Type) -> Unit)?, fe: ((Env, Expr, Type) -> Unit)?, xp: Type) {
    this.e.visitXP(env, fx, fe, xp)
    if (fx != null) {
        fx(env, this, xp)
    }
}

fun Stmt.visitXP (old: Env, fs: ((Env,Stmt)->Unit)?, fx: ((Env, XExpr, Type) -> Unit)?, fe: ((Env,Expr,Type)->Unit)?): Env {
    val new = when (this) {
        is Stmt.Pass, is Stmt.Nat, is Stmt.Break -> emptyList()
        is Stmt.Var   -> { this.src.visitXP(old,fx,fe,this.type) ; listOf(this)+old }
        is Stmt.Set   -> {
            this.dst.toExpr().visitXP(old,fx,fe,Type_Any(this.tk))
            this.src.visitXP(old,fx,fe,this.dst.toExpr().toType(old))
            old
        }
        is Stmt.Call  -> { this.call.visitXP(old,fx,fe,Type_Any(this.tk)) ; old }
        is Stmt.Seq   -> { val e1=this.s1.visitXP(old,fs,fx,fe) ; val e2=this.s2.visitXP(e1,fs,fx,fe) ; e2}
        is Stmt.If    -> { this.tst.visitXP(old,fx,fe,Type.None(this.tk)) ; this.true_.visitXP(old,fs,fx,fe) ; this.false_.visitXP(old,fs,fx,fe) ; old }
        is Stmt.Func  -> { if (this.block!=null) { this.block.visitXP(old,fs,fx,fe) } ; listOf(this)+old }
        is Stmt.Ret   -> { this.e.visitXP(old,fx,fe,(old.idToStmt("_ret_") as Stmt.Var).type) ; old }
        is Stmt.Loop  -> { this.block.visitXP(old,fs,fx,fe) ; old }
        is Stmt.Block -> { this.body.visitXP(listOf(this)+old,fs,fx,fe) ; old }
    }
    if (fs != null) {
        fs(old, this)
    }
    return new
}
