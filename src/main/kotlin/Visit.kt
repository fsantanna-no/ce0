fun Type.visit (ft: ((Type) -> Unit)?) {
    when (this) {
        is Type.Unit, is Type.Nat, is Type.Alias -> {}
        is Type.Tuple -> this.vec.forEach { it.visit(ft) }
        is Type.Union -> this.vec.forEach { it.visit(ft) } //(if (xpd) this.expand() else this.vec).forEach { it.visit_(xpd,ft) }
        is Type.Func  -> { this.inp.visit(ft) ; this.pub?.visit(ft) ; this.out.visit(ft) }
        is Type.Spawn   -> this.tsk.visit(ft)
        is Type.Spawns  -> this.tsk.visit(ft)
        is Type.Pointer   -> this.pln.visit(ft)
        else -> TODO(this.toString()) // do not remove this line b/c we may add new cases
    }
    if (ft != null) {
        ft(this)
    }
}

fun Expr.visit (fs: ((Stmt) -> Unit)?, fe: ((Expr) -> Unit)?, ft: ((Type) -> Unit)?) {
    //this.wtype?.visit_(xpd,ft)
    when (this) {
        is Expr.Unit, is Expr.Var -> {}
        is Expr.Nat   -> this.xtype?.visit(ft)
        is Expr.TCons -> this.arg.forEach { it.visit(fs, fe, ft) }
        is Expr.UCons -> { this.xtype?.visit(ft) ; this.arg.visit(fs, fe, ft) }
        is Expr.UNull -> this.xtype?.visit(ft)
        is Expr.New   -> this.arg.visit(fs, fe, ft)
        is Expr.Dnref -> this.ptr.visit(fs, fe, ft)
        is Expr.Upref -> this.pln.visit(fs, fe, ft)
        is Expr.TDisc -> this.tup.visit(fs, fe, ft)
        is Expr.Pub   -> this.tsk.visit(fs, fe, ft)
        is Expr.UDisc -> this.uni.visit(fs, fe, ft)
        is Expr.UPred -> this.uni.visit(fs, fe, ft)
        is Expr.Call  -> { this.f.visit(fs, fe, ft) ; this.arg.visit(fs, fe, ft) }
        is Expr.Func  -> { this.type.visit(ft) ; this.block.visit(fs, fe, ft) }
        else -> TODO(this.toString()) // do not remove this line b/c we may add new cases
    }
    if (fe != null) {
        fe(this)
    }
}

fun Stmt.visit (fs: ((Stmt) -> Unit)?, fe: ((Expr) -> Unit)?, ft: ((Type) -> Unit)?) {
    when (this) {
        is Stmt.Nop, is Stmt.Native, is Stmt.Break, is Stmt.Return, is Stmt.Throw -> {}
        is Stmt.Var    -> this.xtype?.visit(ft)
        is Stmt.Set    -> { this.dst.visit(fs, fe, ft) ; this.src.visit(fs, fe, ft) }
        is Stmt.SCall  -> this.e.visit(fs, fe, ft)
        is Stmt.SSpawn -> { this.dst.visit(fs, fe, ft) ; this.call.visit(fs, fe, ft) }
        is Stmt.DSpawn -> { this.dst.visit(fs, fe, ft) ; this.call.visit(fs, fe, ft) }
        is Stmt.Await  -> this.e.visit(fs, fe, ft)
        is Stmt.Bcast  -> this.e.visit(fs, fe, ft)
        is Stmt.Input  -> { this.xtype?.visit(ft) ; this.dst?.visit(fs, fe, ft) ; this.arg.visit(fs, fe, ft) }
        is Stmt.Output -> this.arg.visit(fs, fe, ft)
        is Stmt.Seq    -> { this.s1.visit(fs, fe, ft) ; this.s2.visit(fs, fe, ft) }
        is Stmt.If     -> { this.tst.visit(fs, fe, ft) ; this.true_.visit(fs, fe, ft) ; this.false_.visit(fs, fe, ft) }
        is Stmt.Loop   -> { this.block.visit(fs, fe, ft) }
        is Stmt.DLoop  -> { this.i.visit(fs, fe, ft) ; this.tsks.visit(fs, fe, ft) ; this.block.visit(fs, fe, ft) }
        is Stmt.Block  -> { this.body.visit(fs, fe, ft) }
        is Stmt.Typedef -> this.type.visit(ft)
        else -> TODO(this.toString()) // do not remove this line b/c we may add new cases
    }
    if (fs != null) {
        fs(this)
    }
}
