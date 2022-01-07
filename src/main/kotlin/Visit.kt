// TODO: remove this hack completely
var XPD = false
private val XPDS = mutableSetOf<String>()

fun Type.visit (ft: ((Type)->Unit)?) {
    XPDS.clear()
    return this.visit_(ft)
}

fun Expr.visit (fs: ((Stmt)->Unit)?, fe: ((Expr)->Unit)?, ft: ((Type)->Unit)?) {
    XPDS.clear()
    return this.visit_(fs, fe, ft)
}

fun Stmt.visit (fs: ((Stmt)->Unit)?, fe: ((Expr)->Unit)?, ft: ((Type)->Unit)?) {
    XPDS.clear()
    return this.visit_(fs, fe, ft)
}

///////////////////////////////////////////////////////////////////////////////

private
fun Type.visit_ (ft: ((Type)->Unit)?) {
    if (XPD) {
        val ce = this.toce()
        if (XPDS.contains(ce)) {
            return
        }
        XPDS.add(ce)
    }
    when (this) {
        is Type.Tuple -> this.vec.forEach { it.visit_(ft) }
        is Type.Union -> (if (XPD) this.expand() else this.vec).forEach { it.visit_(ft) }
        is Type.Func  -> { this.inp.visit_(ft) ; this.out.visit_(ft) }
        is Type.Ptr   -> this.pln.visit_(ft)
    }
    if (ft != null) {
        ft(this)
    }
}

private
fun Expr.visit_ (fs: ((Stmt)->Unit)?, fe: ((Expr)->Unit)?, ft: ((Type)->Unit)?) {
    when (this) {
        is Expr.TCons -> this.arg.forEach { it.visit_(fs,fe,ft) }
        is Expr.UCons -> { this.type!!.visit_(ft) ; this.arg.visit_(fs,fe,ft) }
        is Expr.New   -> this.arg.visit_(fs,fe,ft)
        is Expr.Dnref -> this.ptr.visit_(fs,fe,ft)
        is Expr.Upref -> this.pln.visit_(fs,fe,ft)
        is Expr.TDisc -> this.tup.visit_(fs,fe,ft)
        is Expr.UDisc -> this.uni.visit_(fs,fe,ft)
        is Expr.UPred -> this.uni.visit_(fs,fe,ft)
        is Expr.Call  -> { this.f.visit_(fs,fe,ft) ; this.arg.visit_(fs,fe,ft) }
        is Expr.Out   -> this.arg.visit_(fs,fe,ft)
        is Expr.Func  -> { this.type.visit_(ft) ; this.block.visit(fs,fe,ft) }
    }
    if (fe != null) {
        fe(this)
    }
}

private
fun Stmt.visit_ (fs: ((Stmt)->Unit)?, fe: ((Expr)->Unit)?, ft: ((Type)->Unit)?) {
    when (this) {
        is Stmt.Var   -> if (this.type != null) this.type.visit_(ft)
        is Stmt.Set   -> { this.dst.visit_(fs,fe,ft) ; this.src.visit_(fs,fe,ft) }
        is Stmt.SExpr -> this.expr.visit_(fs,fe,ft)
        is Stmt.Seq   -> { this.s1.visit(fs,fe,ft) ; this.s2.visit(fs,fe,ft) }
        is Stmt.If    -> { this.tst.visit_(fs,fe,ft) ; this.true_.visit(fs,fe,ft) ; this.false_.visit(fs,fe,ft) }
        //is Stmt.Ret   -> this.e.visit(old,fs,fx,fe,ft)
        is Stmt.Loop  -> { this.block.visit(fs,fe,ft) }
        is Stmt.Block -> { this.body.visit(fs,fe,ft) }
    }
    if (fs != null) {
        fs(this)
    }
}
