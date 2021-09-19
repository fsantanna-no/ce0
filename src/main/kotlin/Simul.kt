interface IState {
    fun copy (): IState
    fun fs (f: Expr): Set<Expr.Func>
}

fun Expr.simul (st: IState, fs: ((Stmt, IState)->Unit)?, fx: ((XExpr, IState)->Unit)?, fe: ((Expr, IState)->Unit)?) {
    when (this) {
        is Expr.TCons -> this.arg.forEach { it.simul(st,fs,fx,fe) }
        is Expr.UCons -> this.arg.simul(st,fs,fx,fe)
        is Expr.Dnref -> this.ptr.simul(st,fs,fx,fe)
        is Expr.Upref -> this.pln.simul(st,fs,fx,fe)
        is Expr.TDisc -> this.tup.simul(st,fs,fx,fe)
        is Expr.UDisc -> this.uni.simul(st,fs,fx,fe)
        is Expr.UPred -> this.uni.simul(st,fs,fx,fe)
        //is Expr.Func  -> this.block.simul(st,fs,fx,fe)
        is Expr.Call  -> {
            this.arg.simul(st, fs, fx, fe)
            st.fs(this.f).forEach {
                it.simul(st.copy(), fs, fx, fe)
            }
        }
    }
    if (fe != null) {
        fe(this,st)
    }
}

private
fun XExpr.simul (st: IState, fs: ((Stmt, IState)->Unit)?, fx: ((XExpr, IState)->Unit)?, fe: ((Expr, IState)->Unit)?) {
    if (this is XExpr.Replace) {
        this.new.simul(st,fs, fx, fe)
    }
    this.e.simul(st,fs, fx, fe)
    if (fx != null) {
        fx(this,st)
    }
}

fun Stmt.simul (st: IState, fs: ((Stmt, IState)->Unit)?, fx: ((XExpr, IState)->Unit)?, fe: ((Expr, IState)->Unit)?) {
    when (this) {
        is Stmt.Var   -> this.src.simul(st,fs,fx,fe)
        is Stmt.Set   -> { this.src.simul(st,fs,fx,fe) ; this.dst.simul(st,fs,fx,fe) }
        is Stmt.Call  -> this.call.simul(st,fs,fx,fe)
        is Stmt.Seq   -> { this.s1.simul(st,fs,fx,fe) ; this.s2.simul(st,fs,fx,fe) }
        //is Stmt.Ret   -> this.e.simul(old,fs,fx,fe)
        is Stmt.Loop  -> { this.block.simul(st,fs,fx,fe) }
        is Stmt.Block -> { this.body.simul(st,fs,fx,fe) }
        is Stmt.If    -> {
            this.tst.simul(st,fs,fx,fe)
            this.true_.simul(st.copy(),fs,fx,fe)
            this.false_.simul(st.copy(),fs,fx,fe)
        }
    }
    if (fs != null) {
        fs(this,st)
    }
}
