fun Type.map (f: ((Type)->Type)): Type {
    return when (this) {
        is Type.Any, is Type.Unit, is Type.Nat, is Type.Rec -> f(this)
        is Type.Ptr  -> f(Type.Ptr(this.tk_, f(this.tp)))
        is Type.Cons -> f(Type.Cons(this.tk_, this.vec.map(f).toTypedArray()))
        is Type.Func -> f(Type.Func(this.tk_, f(this.inp), f(this.out)))
        is Type.Varia -> error("TODO")
    }
}

fun Type.visit (ft: ((Type)->Unit)?) {
    when (this) {
        is Type.Any, is Type.Unit, is Type.Nat, is Type.Rec -> true
        is Type.Ptr  -> this.tp.visit(ft)
        is Type.Cons -> this.vec.forEach { it.visit(ft) }
        is Type.Func -> { this.inp.visit(ft) ; this.out.visit(ft) }
        is Type.Varia -> error("TODO")
    }
    if (ft != null) {
        ft(this)
    }
}

fun Expr.visit (env: Env, fe: ((Env,Expr)->Unit)?) {
    when (this) {
        is Expr.Unk, is Expr.Unit, is Expr.Var, is Expr.Nat -> {}
        is Expr.Tuple -> this.vec.forEach { it.e.visit(env,fe) }
        is Expr.Varia -> this.e.e.visit(env,fe)
        is Expr.Dnref -> this.e.visit(env,fe)
        is Expr.Upref -> this.e.visit(env,fe)
        is Expr.Index -> this.e.visit(env,fe)
        is Expr.Call  -> { this.f.visit(env,fe) ; this.e.e.visit(env,fe) }
    }
    if (fe != null) {
        fe(env,this)
    }
}

typealias Env = List<Stmt>

fun Stmt.visit (old: Env, fs: ((Env,Stmt)->Unit)?, fe: ((Env,Expr)->Unit)?, ft: ((Type)->Unit)?): Env {
    val new = when (this) {
        is Stmt.Pass, is Stmt.Nat, is Stmt.Break -> emptyList()
        is Stmt.Var   -> { this.type.visit(ft) ; this.init.e.visit(old,fe) ; listOf(this)+old }
        is Stmt.Set   -> { this.dst.toExpr().visit(old,fe) ; this.src.e.visit(old,fe) ; old }
        is Stmt.Call  -> { this.call.visit(old,fe) ; old }
        is Stmt.Seq   -> { val e1=this.s1.visit(old,fs,fe,ft) ; val e2=this.s2.visit(e1,fs,fe,ft) ; e2}
        is Stmt.If    -> { this.tst.visit(old,fe) ; this.true_.visit(old,fs,fe,ft) ; this.false_.visit(old,fs,fe,ft) ; old }
        is Stmt.Func  -> { this.type.visit(ft) ; if (this.block!=null) { this.block.visit(old,fs,fe,ft) } ; listOf(this)+old }
        is Stmt.Ret   -> { this.e.e.visit(old,fe) ; old }
        is Stmt.Loop  -> { this.block.visit(old,fs,fe,ft) ; old }
        is Stmt.Block -> { this.body.visit(listOf(this)+old,fs,fe,ft) ; old }
    }
    if (fs != null) {
        fs(old, this)
    }
    return new
}
