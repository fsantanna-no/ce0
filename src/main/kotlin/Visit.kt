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

fun Expr.visit (fe: ((Expr)->Unit)?) {
    when (this) {
        is Expr.Unk, is Expr.Unit, is Expr.Var, is Expr.Nat -> {}
        is Expr.Tuple -> this.vec.forEach { it.e.visit(fe) }
        is Expr.Varia -> this.e.e.visit(fe)
        is Expr.Dnref -> this.e.visit(fe)
        is Expr.Upref -> this.e.visit(fe)
        is Expr.Index -> this.e.visit(fe)
        is Expr.Call  -> { this.f.visit(fe) ; this.e.e.visit(fe) }
    }
    if (fe != null) {
        fe(this)
    }
}

fun Stmt.visit (fs: ((Stmt)->Unit)?, fe: ((Expr)->Unit)?, ft: ((Type)->Unit)?) {
    when (this) {
        is Stmt.Pass, is Stmt.Nat, is Stmt.Break -> {}
        is Stmt.Var   -> { this.type.visit(ft) ; this.init.e.visit(fe) }
        is Stmt.Set   -> { this.dst.toExpr().visit(fe) ; this.src.e.visit(fe) }
        is Stmt.Call  -> this.call.visit(fe)
        is Stmt.Seq   -> { this.s1.visit(fs,fe,ft) ; this.s2.visit(fs,fe,ft) }
        is Stmt.If    -> { this.tst.visit(fe) ; this.true_.visit(fs,fe,ft) ; this.false_.visit(fs,fe,ft) }
        is Stmt.Func  -> { this.type.visit(ft) ; if (this.block!=null) { this.block.visit(fs,fe,ft) } }
        is Stmt.Ret   -> this.e.e.visit(fe)
        is Stmt.Loop  -> this.block.visit(fs,fe,ft)
        is Stmt.Block -> this.body.visit(fs,fe,ft)
    }
    if (fs != null) {
        fs(this)
    }
}
