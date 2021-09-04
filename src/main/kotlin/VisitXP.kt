fun Expr.visitXP (env: Env, fs: ((Env,Stmt)->Unit)?, fx: ((Env, XExpr, Type) -> Unit)?, fe: ((Env, Expr, Type) -> Unit)?, xp: Type) {
    when (this) {
        is Expr.Unk, is Expr.Unit, is Expr.Var, is Expr.Nat -> {}
        is Expr.TCons -> {
            val xp_cons = xp as Type.Tuple
            this.arg.forEachIndexed { i, v -> v.visitXP(env,fs,fx,fe,xp_cons.vec[i]) }
        }
        is Expr.UCons -> {
            assert(xp is Type.Union) { TODO("could be Type.Case?") }
            val xp_cons = xp as Type.Union
            val xp_cons2 = xp_cons.expand()
            val sub = if (this.tk_.num > 0) xp_cons2.vec[this.tk_.num-1] else Type_Unit(this.tk)
            this.arg.visitXP(env,fs,fx,fe,sub)
        }
        is Expr.Dnref -> this.ptr.visitXP(env,fs,fx,fe,xp.keepAnyNat { Type.Ptr(Tk.Chr(TK.CHAR,this.tk.lin,this.tk.col,'\\'),xp) })
        is Expr.Upref -> this.pln.visitXP(env,fs,fx,fe,xp.keepAnyNat{(xp as Type.Ptr).pln})
        is Expr.TDisc -> this.tup.visitXP(env,fs,fx,fe,this.tup.toType(env))
        is Expr.UDisc -> this.uni.visitXP(env,fs,fx,fe,this.uni.toType(env))
        is Expr.UPred -> this.uni.visitXP(env,fs,fx,fe,this.uni.toType(env))
        is Expr.Call  -> {
            val xp2 = this.f.toType(env).let { it.keepAnyNat{it as Type.Func} }
            this.f.visitXP(env,fs,fx,fe,xp2)
            this.arg.visitXP(env,fs,fx,fe,xp2.keepAnyNat{ (xp2 as Type.Func).inp })
        }
        is Expr.Func  -> this.block.visitXP(env,fs,fx,fe)
    }
    if (fe != null) {
        assert(xp.isSupOf(this.toType(env))) { "bug found" }
        fe(env,this,xp)
    }
}

fun XExpr.visitXP (env: Env, fs: ((Env,Stmt)->Unit)?, fx: ((Env, XExpr, Type) -> Unit)?, fe: ((Env, Expr, Type) -> Unit)?, xp: Type) {
    this.e.visitXP(env, fs, fx, fe, xp)
    if (fx != null) {
        fx(env, this, xp)
    }
}

fun Stmt.visitXP (old: Env, fs: ((Env,Stmt)->Unit)?, fx: ((Env, XExpr, Type) -> Unit)?, fe: ((Env,Expr,Type)->Unit)?): Env {
    val new = when (this) {
        is Stmt.Pass, is Stmt.Nat, is Stmt.Break -> old
        is Stmt.Var   -> { this.src.visitXP(listOf(this)+old,fs,fx,fe,this.type) ; listOf(this)+old }
        is Stmt.Set   -> {
            this.dst.toExpr().visitXP(old,fs,fx,fe,Type_Any(this.tk))
            this.src.visitXP(old,fs,fx,fe,this.dst.toExpr().toType(old))
            old
        }
        is Stmt.Call  -> { this.call.visitXP(old,fs,fx,fe,Type_Any(this.tk)) ; old }
        is Stmt.Seq   -> { val e1=this.s1.visitXP(old,fs,fx,fe) ; val e2=this.s2.visitXP(e1,fs,fx,fe) ; e2}
        is Stmt.If    -> { this.tst.visitXP(old,fs,fx,fe,Type_Nat(this.tk,"int")) ; this.true_.visitXP(old,fs,fx,fe) ; this.false_.visitXP(old,fs,fx,fe) ; old }
        is Stmt.Ret   -> { this.e.visitXP(old,fs,fx,fe,(old.idToStmt("_ret_") as Stmt.Var).type) ; old }
        is Stmt.Loop  -> { this.block.visitXP(old,fs,fx,fe) ; old }
        is Stmt.Block -> { this.body.visitXP(listOf(this)+old,fs,fx,fe) ; old }
    }
    if (fs != null) {
        fs(old, this)
    }
    return new
}
