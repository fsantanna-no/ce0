// TODO: remove this hack completely
var XPD = false
private val XPDS = mutableSetOf<String>()

private
fun Type.visit (ft: ((Type)->Unit)?) {
    if (XPD) {
        val ce = this.toce()
        if (XPDS.contains(ce)) {
            return
        }
        XPDS.add(ce)
    }
    when (this) {
        is Type.Tuple -> this.vec.forEach { it.visit(ft) }
        is Type.Union -> (if (XPD) this.expand() else this.vec).forEach { it.visit(ft) }
        is Type.UCons -> this.arg.visit(ft)
        is Type.Func  -> { this.inp.visit(ft) ; this.out.visit(ft) }
        is Type.Ptr   -> this.pln.visit(ft)
    }
    if (ft != null) {
        ft(this)
    }
}

fun Expr.visit (fs: ((Stmt)->Unit)?, fe: ((Expr)->Unit)?, ft: ((Type)->Unit)?) {
    when (this) {
        is Expr.TCons -> this.arg.forEach { it.visit(fs,fe,ft) }
        is Expr.UCons -> this.arg.visit(fs,fe,ft)
        is Expr.New   -> this.arg.visit(fs,fe,ft)
        is Expr.Dnref -> this.ptr.visit(fs,fe,ft)
        is Expr.Upref -> this.pln.visit(fs,fe,ft)
        is Expr.TDisc -> this.tup.visit(fs,fe,ft)
        is Expr.UDisc -> this.uni.visit(fs,fe,ft)
        is Expr.UPred -> this.uni.visit(fs,fe,ft)
        is Expr.Call  -> { this.f.visit(fs,fe,ft) ; this.arg.visit(fs,fe,ft) }
        is Expr.Func  -> { this.type.visit(ft) ; this.block.visit(fs,fe,ft) }
    }
    if (fe != null) {
        fe(this)
    }
}

fun Stmt.visit (fs: ((Stmt)->Unit)?, fe: ((Expr)->Unit)?, ft: ((Type)->Unit)?) {
    XPDS.clear()
    return this.visit_(fs, fe, ft)
}

private
fun Stmt.visit_ (fs: ((Stmt)->Unit)?, fe: ((Expr)->Unit)?, ft: ((Type)->Unit)?) {
    when (this) {
        is Stmt.Var   -> this.type.visit(ft)
        is Stmt.Set   -> { this.dst.visit(fs,fe,ft) ; this.src.visit(fs,fe,ft) }
        is Stmt.Call  -> this.call.visit(fs,fe,ft)
        is Stmt.Seq   -> { this.s1.visit(fs,fe,ft) ; this.s2.visit(fs,fe,ft) }
        is Stmt.If    -> { this.tst.visit(fs,fe,ft) ; this.true_.visit(fs,fe,ft) ; this.false_.visit(fs,fe,ft) }
        //is Stmt.Ret   -> this.e.visit(old,fs,fx,fe,ft)
        is Stmt.Loop  -> { this.block.visit(fs,fe,ft) }
        is Stmt.Block -> { this.body.visit(fs,fe,ft) }
    }
    if (fs != null) {
        fs(this)
    }
}
