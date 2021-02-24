fun Expr.visit (fe: ((Expr)->Boolean)?): Boolean {
    return (fe==null || fe(this)) && when (this) {
        is Expr.Unit, is Expr.Int, is Expr.Var, is Expr.Nat, is Expr.Empty -> true
        is Expr.Tuple -> this.vec.all { it.visit(fe) }
        is Expr.Cons  -> this.pos.visit(fe)
        is Expr.Dnref -> this.pre.visit(fe)
        is Expr.Upref -> this.pos.visit(fe)
        is Expr.Index -> this.pre.visit(fe)
        is Expr.Pred  -> this.pre.visit(fe)
        is Expr.Disc  -> this.pre.visit(fe)
        is Expr.Call  -> this.pre.visit(fe) && this.pos.visit(fe)
    }
}

fun Stmt.visit (fs: ((Stmt)->Boolean)?, fe: ((Expr)->Boolean)?): Boolean {
    return (fs==null || fs(this)) && when (this) {
        is Stmt.Pass, is Stmt.User, is Stmt.Nat, is Stmt.Break -> true
        is Stmt.Var   -> this.init.visit(fe)
        is Stmt.Set   -> this.dst.visit(fe) && this.src.visit(fe)
        is Stmt.Call  -> this.call.visit(fe)
        is Stmt.Seq   -> this.s1.visit(fs,fe) && this.s2.visit(fs,fe)
        is Stmt.If    -> this.tst.visit(fe)  && this.true_.visit(fs,fe) && this.false_.visit(fs,fe)
        is Stmt.Func  -> this.block==null || this.block.visit(fs,fe)
        is Stmt.Ret   -> this.e.visit(fe)
        is Stmt.Loop  -> this.block.visit(fs,fe)
        is Stmt.Block -> this.body.visit(fs,fe)
    }
}
