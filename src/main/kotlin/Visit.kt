fun Type.visit (ft: ((Type)->Boolean)?): Boolean {
    return (ft==null || ft(this)) && when (this) {
        is Type.Unit, is Type.Nat, is Type.User -> true
        is Type.Tuple -> this.vec.all { it.visit(ft) }
        is Type.Func  -> this.inp.visit(ft) && this.out.visit(ft)
    }
}

fun Expr.visit (fe: ((Expr)->Boolean)?): Boolean {
    return (fe==null || fe(this)) && when (this) {
        is Expr.Unk, is Expr.Unit, is Expr.Int, is Expr.Var, is Expr.Nat, is Expr.Empty -> true
        is Expr.Tuple -> this.vec.all { it.visit(fe) }
        is Expr.Cons  -> this.arg.visit(fe)
        is Expr.Dnref -> this.e.visit(fe)
        is Expr.Upref -> this.e.visit(fe)
        is Expr.Index -> this.e.visit(fe)
        is Expr.Pred  -> this.e.visit(fe)
        is Expr.Disc  -> this.e.visit(fe)
        is Expr.Call  -> this.f.visit(fe) && this.arg.visit(fe)
    }
}

fun Stmt.visit (fs: ((Stmt)->Boolean)?, fe: ((Expr)->Boolean)?, ft: ((Type)->Boolean)?): Boolean {
    return (fs==null || fs(this)) && when (this) {
        is Stmt.Pass, is Stmt.Nat, is Stmt.Break -> true
        is Stmt.Var   -> this.type.visit(ft) && this.init.visit(fe)
        is Stmt.User  -> this.subs.all { it.second.visit(ft) }
        is Stmt.Set   -> this.dst.visit(fe) && this.src.visit(fe)
        is Stmt.Call  -> this.call.visit(fe)
        is Stmt.Seq   -> this.s1.visit(fs,fe,ft) && this.s2.visit(fs,fe,ft)
        is Stmt.If    -> this.tst.visit(fe)  && this.true_.visit(fs,fe,ft) && this.false_.visit(fs,fe,ft)
        is Stmt.Func  -> this.block==null || (this.type.visit(ft) && this.block.visit(fs,fe,ft))
        is Stmt.Ret   -> this.e.visit(fe)
        is Stmt.Loop  -> this.block.visit(fs,fe,ft)
        is Stmt.Block -> this.body.visit(fs,fe,ft)
    }
}
