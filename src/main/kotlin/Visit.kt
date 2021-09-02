typealias Env = List<Stmt>

fun Expr.visit (env: Env, fx: ((Env,XExpr)->Unit)?, fe: ((Env,Expr)->Unit)?) {
    when (this) {
        is Expr.Unk, is Expr.Unit, is Expr.Var, is Expr.Nat -> {}
        is Expr.TCons -> this.arg.forEach { it.visit(env,fx,fe) }
        is Expr.UCons  -> this.arg.visit(env,fx,fe)
        is Expr.Dnref -> this.ptr.visit(env,fx,fe)
        is Expr.Upref -> this.pln.visit(env,fx,fe)
        is Expr.TDisc -> this.tup.visit(env,fx,fe)
        is Expr.UDisc -> this.uni.visit(env,fx,fe)
        is Expr.UPred -> this.uni.visit(env,fx,fe)
        is Expr.Call  -> { this.f.visit(env,fx,fe) ; this.arg.visit(env,fx,fe) }
    }
    if (fe != null) {
        fe(env,this)
    }
}

fun XExpr.visit (env: Env, fx: ((Env,XExpr)->Unit)?, fe: ((Env,Expr)->Unit)?) {
    this.e.visit(env, fx, fe)
    if (fx != null) {
        fx(env, this)
    }
}

fun Stmt.visit (old: Env, fs: ((Env,Stmt)->Unit)?, fx: ((Env, XExpr)->Unit)?, fe: ((Env,Expr)->Unit)?): Env {
    val new = when (this) {
        is Stmt.Pass, is Stmt.Nat, is Stmt.Break -> emptyList()
        is Stmt.Var   -> { this.src.visit(old,fx,fe) ; listOf(this)+old }
        is Stmt.Set   -> { this.dst.toExpr().visit(old,fx,fe) ; this.src.visit(old,fx,fe) ; old }
        is Stmt.Call  -> { this.call.visit(old,fx,fe) ; old }
        is Stmt.Seq   -> { val e1=this.s1.visit(old,fs,fx,fe) ; val e2=this.s2.visit(e1,fs,fx,fe) ; e2}
        is Stmt.If    -> { this.tst.visit(old,fx,fe) ; this.true_.visit(old,fs,fx,fe) ; this.false_.visit(old,fs,fx,fe) ; old }
        is Stmt.Func  -> { if (this.block!=null) { this.block.visit(old,fs,fx,fe) } ; listOf(this)+old }
        is Stmt.Ret   -> { this.e.visit(old,fx,fe) ; old }
        is Stmt.Loop  -> { this.block.visit(old,fs,fx,fe) ; old }
        is Stmt.Block -> { this.body.visit(listOf(this)+old,fs,fx,fe) ; old }
    }
    if (fs != null) {
        fs(old, this)
    }
    return new
}
