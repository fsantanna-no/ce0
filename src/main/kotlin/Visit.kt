private val X = mutableSetOf<String>()
val VISIT = ArrayDeque<Stmt>()

private
fun Type.visit (ft: ((Type)->Unit)?) {
    val ce = this.toce()
    if (X.contains(ce)) {
        return
    }
    X.add(ce)
    when (this) {
        is Type.Tuple -> this.expand().forEach { it.visit(ft) }
        is Type.Union -> this.expand().forEach { it.visit(ft) }
        is Type.UCons -> this.arg.visit(ft)
        is Type.Func  -> { this.inp.visit(ft) ; this.out.visit(ft) }
        is Type.Ptr   -> this.pln.visit(ft)
    }
    if (ft != null) {
        ft(this)
    }
}

fun Expr.visit (fs: ((Stmt)->Unit)?, fx: ((XExpr)->Unit)?, fe: ((Expr)->Unit)?, ft: ((Type)->Unit)?) {
    when (this) {
        is Expr.TCons -> this.arg.forEach { it.visit(fs,fx,fe,ft) }
        is Expr.UCons -> this.arg.visit(fs,fx,fe,ft)
        is Expr.Dnref -> this.ptr.visit(fs,fx,fe,ft)
        is Expr.Upref -> this.pln.visit(fs,fx,fe,ft)
        is Expr.TDisc -> this.tup.visit(fs,fx,fe,ft)
        is Expr.UDisc -> this.uni.visit(fs,fx,fe,ft)
        is Expr.UPred -> this.uni.visit(fs,fx,fe,ft)
        is Expr.Call  -> { this.f.visit(fs,fx,fe,ft) ; this.arg.visit(fs,fx,fe,ft) }
        is Expr.Func  -> { this.type.visit(ft) ; this.block.visit(fs,fx,fe,ft) }
    }
    if (fe != null) {
        fe(this)
    }
}

private
fun XExpr.visit (fs: ((Stmt)->Unit)?, fx: ((XExpr)->Unit)?, fe: ((Expr)->Unit)?, ft: ((Type)->Unit)?) {
    if (this is XExpr.Replace) {
        this.new.visit(fs, fx, fe, ft)
    }
    this.e.visit(fs, fx, fe, ft)
    if (fx != null) {
        fx(this)
    }
}

fun Stmt.visit (fs: ((Stmt)->Unit)?, fx: ((XExpr)->Unit)?, fe: ((Expr)->Unit)?, ft: ((Type)->Unit)?) {
    X.clear()
    return this.visit_(fs, fx, fe, ft)
}

private
fun Stmt.visit_ (fs: ((Stmt)->Unit)?, fx: ((XExpr)->Unit)?, fe: ((Expr)->Unit)?, ft: ((Type)->Unit)?) {
    when (this) {
        is Stmt.Var   -> { this.type.visit(ft) ; this.src.visit(fs,fx,fe,ft)  }
        is Stmt.Set   -> { this.dst.visit(fs,fx,fe,ft) ; this.src.visit(fs,fx,fe,ft) }
        is Stmt.Call  -> this.call.visit(fs,fx,fe,ft)
        is Stmt.Seq   -> { this.s1.visit(fs,fx,fe,ft) ; this.s2.visit(fs,fx,fe,ft) }
        is Stmt.If    -> { this.tst.visit(fs,fx,fe,ft) ; this.true_.visit(fs,fx,fe,ft) ; this.false_.visit(fs,fx,fe,ft) }
        //is Stmt.Ret   -> this.e.visit(old,fs,fx,fe,ft)
        is Stmt.Loop  -> { this.block.visit(fs,fx,fe,ft) }
        is Stmt.Block -> { this.body.visit(fs,fx,fe,ft) }
    }
    if (fs != null) {
        fs(this)
    }
}
