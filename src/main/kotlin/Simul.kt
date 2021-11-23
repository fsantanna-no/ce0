interface IState {
    fun copy (): IState
}

fun nxt (
    st: IState,
    fs: ((Stmt, IState) -> Unit)?,
    fe: ((Expr, IState) -> Unit)?,
    nxts: List<Any>
) {
    if (nxts.isNotEmpty()) {
        val xxx = nxts.first()
        xxx.simul(st, fs, fe, nxts.drop(1))
    }
}

private
var STACK = ArrayDeque<Pair<Any,List<Any>>>()

fun stack_rem (f: (Any)->Boolean): List<Any> {
    while (true) {
        val s = STACK.removeFirst()!!
        if (f(s.first)) {
            return s.second
        }
    }
}

fun Any.simul (
    st: IState,
    fs: ((Stmt, IState) -> Unit)?,
    fe: ((Expr, IState) -> Unit)?,
    nxts: List<Any>
) {
    when (this) {
        is Stmt  -> this.simul(st, fs, fe, nxts)
        is Expr  -> this.simul(st, fs, fe, nxts)
        else -> error("impossible case")
    }
}

fun Expr.simul (
    st: IState,
    fs: ((Stmt, IState) -> Unit)?,
    fe: ((Expr, IState) -> Unit)?,
    nxts: List<Any>
) {
    if (fe != null) {
        fe(this,st)
    }
    when (this) {
        is Expr.TCons -> nxt(st, fs, fe, this.arg.toList()+nxts)
        is Expr.UCons -> this.arg.simul(st,fs,fe,nxts)
        is Expr.Dnref -> this.ptr.simul(st,fs,fe,nxts)
        is Expr.Upref -> this.pln.simul(st,fs,fe,nxts)
        is Expr.TDisc -> this.tup.simul(st,fs,fe,nxts)
        is Expr.UDisc -> this.uni.simul(st,fs,fe,nxts)
        is Expr.UPred -> this.uni.simul(st,fs,fe,nxts)
        //is Expr.Func  -> this.block.simul(st,fs,fx,fe)
        is Expr.Call  -> this.arg.simul(st,fs,fe,nxts)
        else -> nxt(st, fs, fe, nxts)
    }
}

fun Stmt.simul (
    st: IState,
    fs: ((Stmt, IState) -> Unit)?,
    fe: ((Expr, IState) -> Unit)?,
    nxts: List<Any>
) {
    if (fs != null) {
        fs(this,st)
    }

    when (this) {
        is Stmt.Set   -> this.src.simul(st, fs, fe, listOf(this.dst)+nxts)
        is Stmt.Call  -> this.call.simul(st, fs, fe, nxts)
        is Stmt.Break -> nxt(st, fs, fe, stack_rem { it is Stmt.Loop })
        is Stmt.Ret   -> {} //nxt(st, fs, fx, fe, stack_rem { it is Expr.Call })
        is Stmt.Block -> this.body.simul(st, fs, fe, nxts)
        is Stmt.Seq   -> this.s1.simul(st, fs, fe, listOf(this.s2)+nxts)
        is Stmt.Loop  -> {
            STACK.addFirst(Pair(this,nxts))
            this.block.simul(st, fs, fe, emptyList())
        }
        is Stmt.If -> {
            val s = ArrayDeque(STACK)
            this.tst.simul(st.copy(), fs, fe, listOf(this.true_)+nxts)
            STACK = s
            this.tst.simul(st.copy(), fs, fe, listOf(this.false_)+nxts)
        }
        else -> nxt(st, fs, fe, nxts)
    }
}
