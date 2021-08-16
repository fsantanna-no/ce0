fun Type.map (f: ((Type)->Type)): Type {
    return when (this) {
        is Type.None, is Type.Any, is Type.Unit, is Type.Nat, is Type.Rec -> f(this)
        is Type.Ptr   -> f(Type.Ptr(this.tk_, f(this.tp)))
        is Type.Tuple -> f(Type.Tuple(this.tk_, this.vec.map(f).toTypedArray()))
        is Type.Union -> f(Type.Union(this.tk_, this.vec.map(f).toTypedArray()))
        is Type.Func  -> f(Type.Func(this.tk_, f(this.inp), f(this.out)))
        is Type.Case  -> TODO()
    }
}

fun Expr.visit (env: Env, fe: ((Env,Expr)->Unit)?) {
    when (this) {
        is Expr.Unk, is Expr.Unit, is Expr.Var, is Expr.Nat -> {}
        is Expr.Tuple -> this.vec.forEach { it.e.visit(env,fe) }
        is Expr.Case  -> this.arg.e.visit(env,fe)
        is Expr.Dnref -> this.sub.visit(env,fe)
        is Expr.Upref -> this.sub.visit(env,fe)
        is Expr.Index -> this.pre.visit(env,fe)
        is Expr.Call  -> { this.f.visit(env,fe) ; this.arg.e.visit(env,fe) }
    }
    if (fe != null) {
        fe(env,this)
    }
}

typealias Env = List<Stmt>

fun Stmt.visit (old: Env, fs: ((Env,Stmt)->Unit)?, fe: ((Env,Expr)->Unit)?): Env {
    val new = when (this) {
        is Stmt.Pass, is Stmt.Nat, is Stmt.Break -> emptyList()
        is Stmt.Var   -> { this.src.e.visit(old,fe) ; listOf(this)+old }
        is Stmt.Set   -> { this.dst.toExpr().visit(old,fe) ; this.src.e.visit(old,fe) ; old }
        is Stmt.Call  -> { this.call.visit(old,fe) ; old }
        is Stmt.Seq   -> { val e1=this.s1.visit(old,fs,fe) ; val e2=this.s2.visit(e1,fs,fe) ; e2}
        is Stmt.If    -> { this.tst.visit(old,fe) ; this.true_.visit(old,fs,fe) ; this.false_.visit(old,fs,fe) ; old }
        is Stmt.Func  -> { if (this.block!=null) { this.block.visit(old,fs,fe) } ; listOf(this)+old }
        is Stmt.Ret   -> { this.e.e.visit(old,fe) ; old }
        is Stmt.Loop  -> { this.block.visit(old,fs,fe) ; old }
        is Stmt.Block -> { this.body.visit(listOf(this)+old,fs,fe) ; old }
    }
    if (fs != null) {
        fs(old, this)
    }
    return new
}
