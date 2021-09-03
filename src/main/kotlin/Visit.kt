private val X = mutableSetOf<String>()

typealias Env = List<Stmt>

private
fun Type.visit (ft: ((Type)->Unit)?) {
    this.toce().let {
        if (X.contains(it)) {
            return
        }
        X.add(it)
    }
    when (this) {
        is Type.Tuple -> this.vec.forEach { it.visit(ft) }
        is Type.Union -> this.expand().vec.forEach { it.visit(ft) }
        is Type.UCons -> this.arg.visit(ft)
        is Type.Func  -> { this.inp.visit(ft) ; this.out.visit(ft) }
        is Type.Ptr   -> this.pln.visit(ft)
    }
    if (ft != null) {
        ft(this)
    }
}

private
fun Expr.visit (env: Env, fs: ((Env,Stmt)->Unit)?, fx: ((Env,XExpr)->Unit)?, fe: ((Env,Expr)->Unit)?, ft: ((Type)->Unit)?) {
    when (this) {
        is Expr.Unk, is Expr.Unit, is Expr.Var, is Expr.Nat -> {}
        is Expr.TCons -> this.arg.forEach { it.visit(env,fs,fx,fe,ft) }
        is Expr.UCons  -> this.arg.visit(env,fs,fx,fe,ft)
        is Expr.Dnref -> this.ptr.visit(env,fs,fx,fe,ft)
        is Expr.Upref -> this.pln.visit(env,fs,fx,fe,ft)
        is Expr.TDisc -> this.tup.visit(env,fs,fx,fe,ft)
        is Expr.UDisc -> this.uni.visit(env,fs,fx,fe,ft)
        is Expr.UPred -> this.uni.visit(env,fs,fx,fe,ft)
        is Expr.Call  -> { this.f.visit(env,fs,fx,fe,ft) ; this.arg.visit(env,fs,fx,fe,ft) }
        is Expr.Func  -> { this.type.visit(ft) ; this.block.visit(env,fs,fx,fe,ft) }
    }
    if (fe != null) {
        fe(env,this)
    }
}

private
fun XExpr.visit (env: Env, fs: ((Env,Stmt)->Unit)?, fx: ((Env,XExpr)->Unit)?, fe: ((Env,Expr)->Unit)?, ft: ((Type)->Unit)?) {
    this.e.visit(env, fs, fx, fe, ft)
    if (fx != null) {
        fx(env, this)
    }
}

fun Stmt.visit (old: Env, fs: ((Env,Stmt)->Unit)?, fx: ((Env, XExpr)->Unit)?, fe: ((Env,Expr)->Unit)?, ft: ((Type)->Unit)?): Env {
    X.clear()
    return this.visit_(old, fs, fx, fe, ft)
}

private
fun Stmt.visit_ (old: Env, fs: ((Env,Stmt)->Unit)?, fx: ((Env, XExpr)->Unit)?, fe: ((Env,Expr)->Unit)?, ft: ((Type)->Unit)?): Env {
    val new = when (this) {
        is Stmt.Pass, is Stmt.Nat, is Stmt.Break -> old
        is Stmt.Var   -> { this.type.visit(ft) ; this.src.visit(old,fs,fx,fe,ft) ; listOf(this)+old }
        is Stmt.Set   -> { this.dst.toExpr().visit(old,fs,fx,fe,ft) ; this.src.visit(old,fs,fx,fe,ft) ; old }
        is Stmt.Call  -> { this.call.visit(old,fs,fx,fe,ft) ; old }
        is Stmt.Seq   -> { val e1=this.s1.visit(old,fs,fx,fe,ft) ; val e2=this.s2.visit(e1,fs,fx,fe,ft) ; e2}
        is Stmt.If    -> { this.tst.visit(old,fs,fx,fe,ft) ; this.true_.visit(old,fs,fx,fe,ft) ; this.false_.visit(old,fs,fx,fe,ft) ; old }
        is Stmt.Ret   -> { this.e.visit(old,fs,fx,fe,ft) ; old }
        is Stmt.Loop  -> { this.block.visit(old,fs,fx,fe,ft) ; old }
        is Stmt.Block -> { this.body.visit(listOf(this)+old,fs,fx,fe,ft) ; old }
    }
    if (fs != null) {
        fs(old, this)
    }
    return new
}
