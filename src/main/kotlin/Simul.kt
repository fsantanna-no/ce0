interface IState {
    fun ok ()
    fun copy (): IState
    fun fs (f: Expr): Set<Expr.Func>
}

private
fun Expr._simul (st: IState, fs: ((Stmt, IState)->Unit)?, fx: ((XExpr, IState)->Unit)?, fe: ((Expr, IState)->Unit)?) {
    when (this) {
        is Expr.TCons -> this.arg.forEach { it._simul(st,fs,fx,fe) }
        is Expr.UCons -> this.arg._simul(st,fs,fx,fe)
        is Expr.Dnref -> this.ptr._simul(st,fs,fx,fe)
        is Expr.Upref -> this.pln._simul(st,fs,fx,fe)
        is Expr.TDisc -> this.tup._simul(st,fs,fx,fe)
        is Expr.UDisc -> this.uni._simul(st,fs,fx,fe)
        is Expr.UPred -> this.uni._simul(st,fs,fx,fe)
        //is Expr.Func  -> this.block.simul(st,fs,fx,fe)
        is Expr.Call  -> {
            this.arg._simul(st, fs, fx, fe)
            st.fs(this.f).forEach {
                it._simul(st.copy(), fs, fx, fe)
            }
        }
    }
    if (fe != null) {
        fe(this,st)
    }
}

private
fun XExpr._simul (st: IState, fs: ((Stmt, IState)->Unit)?, fx: ((XExpr, IState)->Unit)?, fe: ((Expr, IState)->Unit)?) {
    if (this is XExpr.Replace) {
        this.new._simul(st,fs, fx, fe)
    }
    this.e._simul(st,fs, fx, fe)
    if (fx != null) {
        fx(this,st)
    }
}

private
fun Stmt._simul (st: IState, fs: ((Stmt, IState)->Unit)?, fx: ((XExpr, IState)->Unit)?, fe: ((Expr, IState)->Unit)?) {
    when (this) {
        is Stmt.Var   -> this.src._simul(st,fs,fx,fe)
        is Stmt.Set   -> { this.src._simul(st,fs,fx,fe) ; this.dst._simul(st,fs,fx,fe) }
        is Stmt.Call  -> this.call._simul(st,fs,fx,fe)
        is Stmt.Seq   -> { this.s1._simul(st,fs,fx,fe) ; this.s2._simul(st,fs,fx,fe) }
        //is Stmt.Ret   -> this.e.simul(old,fs,fx,fe)
        is Stmt.Loop  -> { this.block._simul(st,fs,fx,fe) }
        is Stmt.Block -> { this.body._simul(st,fs,fx,fe) }
        is Stmt.If    -> {
            this.tst._simul(st,fs,fx,fe)
            this.true_._simul(st.copy(),fs,fx,fe)
            this.false_._simul(st.copy(),fs,fx,fe)
        }
    }
    if (fs != null) {
        fs(this,st)
    }
}

fun Stmt.simul (st: IState, fs: ((Stmt, IState)->Unit)?, fx: ((XExpr, IState)->Unit)?, fe: ((Expr, IState)->Unit)?) {
    this._simul(st,fs,fx,fe)
    st.ok()
}