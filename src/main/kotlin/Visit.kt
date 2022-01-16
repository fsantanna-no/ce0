// TODO: remove this hack completely

private val XPDS = mutableSetOf<String>()

fun Type.visit (xpd: Boolean, ft: ((Type)->Unit)?) {
    XPDS.clear()
    return this.visit_(xpd, ft)
}

fun Expr.visit (xpd: Boolean, fs: ((Stmt)->Unit)?, fe: ((Expr)->Unit)?, ft: ((Type)->Unit)?) {
    XPDS.clear()
    return this.visit_(xpd, fs, fe, ft)
}

fun Stmt.visit (xpd: Boolean, fs: ((Stmt)->Unit)?, fe: ((Expr)->Unit)?, ft: ((Type)->Unit)?) {
    XPDS.clear()
    return this.visit_(xpd, fs, fe, ft)
}

///////////////////////////////////////////////////////////////////////////////

private
fun Type.visit_ (xpd: Boolean, ft: ((Type)->Unit)?) {
    if (xpd) {
        val ce = this.toce()
        if (XPDS.contains(ce)) {
            return
        }
        XPDS.add(ce)
    }
    when (this) {
        is Type.Tuple -> this.vec.forEach { it.visit_(xpd,ft) }
        is Type.Union -> (if (xpd) this.expand() else this.vec).forEach { it.visit_(xpd,ft) }
        is Type.Func  -> { this.inp.visit_(xpd,ft) ; this.out.visit_(xpd,ft) }
        is Type.Ptr   -> this.pln.visit_(xpd,ft)
    }
    if (ft != null) {
        ft(this)
    }
}

private
fun Expr.visit_ (xpd: Boolean, fs: ((Stmt)->Unit)?, fe: ((Expr)->Unit)?, ft: ((Type)->Unit)?) {
    this.wtype?.visit_(xpd,ft)
    when (this) {
        is Expr.TCons -> this.arg.forEach { it.visit_(xpd,fs,fe,ft) }
        is Expr.UCons -> { this.xtype?.visit_(xpd,ft) ; this.arg.visit_(xpd,fs,fe,ft) }
        is Expr.UNull -> this.xtype?.visit_(xpd,ft)
        is Expr.New   -> this.arg.visit_(xpd,fs,fe,ft)
        is Expr.Dnref -> this.ptr.visit_(xpd,fs,fe,ft)
        is Expr.Upref -> this.pln.visit_(xpd,fs,fe,ft)
        is Expr.TDisc -> this.tup.visit_(xpd,fs,fe,ft)
        is Expr.UDisc -> this.uni.visit_(xpd,fs,fe,ft)
        is Expr.UPred -> this.uni.visit_(xpd,fs,fe,ft)
        is Expr.Call  -> { this.f.visit_(xpd,fs,fe,ft) ; this.arg.visit_(xpd,fs,fe,ft) }
        is Expr.Out   -> this.arg.visit_(xpd,fs,fe,ft)
        is Expr.Func  -> { this.type.visit_(xpd,ft) ; this.block.visit(xpd,fs,fe,ft) }
    }
    if (fe != null) {
        fe(this)
    }
}

private
fun Stmt.visit_ (xpd: Boolean, fs: ((Stmt)->Unit)?, fe: ((Expr)->Unit)?, ft: ((Type)->Unit)?) {
    when (this) {
        is Stmt.Var   -> this.xtype?.visit_(xpd,ft)
        is Stmt.Set   -> { this.dst.visit_(xpd,fs,fe,ft) ; this.src.visit_(xpd,fs,fe,ft) }
        is Stmt.SExpr -> this.e.visit_(xpd,fs,fe,ft)
        is Stmt.Seq   -> { this.s1.visit(xpd,fs,fe,ft) ; this.s2.visit(xpd,fs,fe,ft) }
        is Stmt.If    -> { this.tst.visit_(xpd,fs,fe,ft) ; this.true_.visit(xpd,fs,fe,ft) ; this.false_.visit(xpd,fs,fe,ft) }
        //is Stmt.Ret   -> this.e.visit(old,fs,fx,fe,ft)
        is Stmt.Loop  -> { this.block.visit(xpd,fs,fe,ft) }
        is Stmt.Block -> { this.body.visit(xpd,fs,fe,ft) }
    }
    if (fs != null) {
        fs(this)
    }
}
