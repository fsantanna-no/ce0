fun Expr.visitXP (env: Env, xp: Type, fe: ((Env,Expr,Type)->Unit)?) {
    when (this) {
        is Expr.Unk, is Expr.Unit, is Expr.Var, is Expr.Nat -> {}
        is Expr.Tuple -> {
            val tp = this.toType(env) as Type.Cons
            assert(tp.tk_.chr == '[')
            this.vec.forEachIndexed { i,v -> v.e.visitXP(env,tp.vec[i],fe) }
        }
        is Expr.Varia -> this.e.e.visitXP(env,Type.None(this.tk),fe)
        is Expr.Dnref -> this.e.visitXP(env,Type.None(this.tk),fe)
        is Expr.Upref -> this.e.visitXP(env,Type.None(this.tk),fe)
        is Expr.Index -> this.e.visitXP(env,Type.None(this.tk),fe)
        is Expr.Call  -> { this.f.visitXP(env,Type.None(this.tk),fe) ; this.e.e.visitXP(env,Type.None(this.tk),fe) }
    }
    if (fe != null) {
        assert(this.toType(env).isSupOf(xp)) { "bug found" }
        fe(env,this,xp)
    }
}

fun Stmt.visitXP (old: Env, fs: ((Env,Stmt)->Unit)?, fe: ((Env,Expr,Type)->Unit)?): Env {
    val new = when (this) {
        is Stmt.Pass, is Stmt.Nat, is Stmt.Break -> emptyList()
        is Stmt.Var   -> { this.init.e.visitXP(old,this.type,fe) ; listOf(this)+old }
        is Stmt.Set   -> {
            this.dst.toExpr().visitXP(old,this.src.e.toType(old),fe)
            this.src.e.visitXP(old,this.dst.toExpr().toType(old), fe)
            old
        }
        is Stmt.Call  -> { this.call.visitXP(old,Type.None(this.tk),fe) ; old }
        is Stmt.Seq   -> { val e1=this.s1.visitXP(old,fs,fe) ; val e2=this.s2.visitXP(e1,fs,fe) ; e2}
        is Stmt.If    -> { this.tst.visitXP(old,Type.None(this.tk),fe) ; this.true_.visitXP(old,fs,fe) ; this.false_.visitXP(old,fs,fe) ; old }
        is Stmt.Func  -> { if (this.block!=null) { this.block.visitXP(old,fs,fe) } ; listOf(this)+old }
        is Stmt.Ret   -> { this.e.e.visitXP(old,Type.None(this.tk),fe) ; old }
        is Stmt.Loop  -> { this.block.visitXP(old,fs,fe) ; old }
        is Stmt.Block -> { this.body.visitXP(listOf(this)+old,fs,fe) ; old }
    }
    if (fs != null) {
        fs(old, this)
    }
    return new
}
