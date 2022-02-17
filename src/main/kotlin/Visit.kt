var EXPR_WTYPE = true

fun Type.visit (ft: ((Type) -> Unit)?, fx: ((Any,Scope) -> Unit)?) {
    when (this) {
        is Type.Unit, is Type.Nat -> {}
        is Type.Tuple   -> this.vec.forEach { it.visit(ft,fx) }
        is Type.Union   -> this.vec.forEach { it.visit(ft,fx) } //(if (xpd) this.expand() else this.vec).forEach { it.visit_(xpd,ft) }
        is Type.Spawn   -> this.tsk.visit(ft,fx)
        is Type.Spawns  -> this.tsk.visit(ft,fx)
        is Type.Pointer -> { if (fx!=null && this.xscp!=null) fx(this,this.xscp!!) ; this.pln.visit(ft,fx) }
        is Type.Alias   -> if (fx!=null && this.xscps!=null) this.xscps!!.forEach { fx(this,it) }
        is Type.Func    -> {
            this.xscps.let {
                if (it.first != null) if (fx!=null) fx(this,it.first!!)
                it.second?.forEach { if (fx!=null) fx(this,it) }
            }
            this.inp.visit(ft,fx) ; this.pub?.visit(ft,fx) ; this.out.visit(ft,fx)
        }
        else -> TODO(this.toString()) // do not remove this line b/c we may add new cases
    }
    if (ft != null) {
        ft(this)
    }
}

fun Expr.visit (fs: ((Stmt) -> Unit)?, fe: ((Expr) -> Unit)?, ft: ((Type) -> Unit)?, fx: ((Any,Scope) -> Unit)?) {
    if (EXPR_WTYPE) {
        this.wtype?.visit(ft,fx)
    }
    when (this) {
        is Expr.Unit, is Expr.Var -> {}
        is Expr.Nat   -> this.xtype?.visit(ft,fx)
        is Expr.TCons -> this.arg.forEach { it.visit(fs, fe, ft, fx) }
        is Expr.UCons -> { this.xtype?.visit(ft, fx) ; this.arg.visit(fs, fe, ft, fx) }
        is Expr.UNull -> this.xtype?.visit(ft, fx)
        is Expr.New   -> { if (fx!=null && this.xscp!=null) fx(this,this.xscp!!) ; this.arg.visit(fs, fe, ft, fx) }
        is Expr.Dnref -> this.ptr.visit(fs, fe, ft, fx)
        is Expr.Upref -> this.pln.visit(fs, fe, ft, fx)
        is Expr.TDisc -> this.tup.visit(fs, fe, ft, fx)
        is Expr.Pub   -> this.tsk.visit(fs, fe, ft, fx)
        is Expr.UDisc -> this.uni.visit(fs, fe, ft, fx)
        is Expr.UPred -> this.uni.visit(fs, fe, ft, fx)
        is Expr.Func  -> { this.xtype?.visit(ft, fx) ; this.block.visit(fs, fe, ft, fx) }
        is Expr.Call  -> {
            this.xscps.let {
                it.first?.forEach { if (fx!=null) fx(this,it) }
                if (it.second != null) if (fx!=null) fx(this,it.second!!)
            }
            this.f.visit(fs, fe, ft, fx) ; this.arg.visit(fs, fe, ft, fx)
        }
        else -> TODO(this.toString()) // do not remove this line b/c we may add new cases
    }
    if (fe != null) {
        fe(this)
    }
}

fun Stmt.visit (fs: ((Stmt) -> Unit)?, fe: ((Expr) -> Unit)?, ft: ((Type) -> Unit)?, fx: ((Any,Scope) -> Unit)?) {
    when (this) {
        is Stmt.Nop, is Stmt.Native, is Stmt.Break, is Stmt.Return, is Stmt.Throw -> {}
        is Stmt.Var     -> this.xtype?.visit(ft, fx)
        is Stmt.Set     -> { this.dst.visit(fs, fe, ft, fx) ; this.src.visit(fs, fe, ft, fx) }
        is Stmt.SCall   -> this.e.visit(fs, fe, ft, fx)
        is Stmt.SSpawn  -> { this.dst?.visit(fs, fe, ft, fx) ; this.call.visit(fs, fe, ft, fx) }
        is Stmt.DSpawn  -> { this.dst.visit(fs, fe, ft, fx) ; this.call.visit(fs, fe, ft, fx) }
        is Stmt.Await   -> this.e.visit(fs, fe, ft, fx)
        is Stmt.Emit   -> { if (fx!=null) fx(this,this.scp) ; this.e.visit(fs, fe, ft, fx) }
        is Stmt.Input   -> { this.xtype?.visit(ft, fx) ; this.dst?.visit(fs, fe, ft, fx) ; this.arg.visit(fs, fe, ft, fx) }
        is Stmt.Output  -> this.arg.visit(fs, fe, ft, fx)
        is Stmt.Seq     -> { this.s1.visit(fs, fe, ft, fx) ; this.s2.visit(fs, fe, ft, fx) }
        is Stmt.If      -> { this.tst.visit(fs, fe, ft, fx) ; this.true_.visit(fs, fe, ft, fx) ; this.false_.visit(fs, fe, ft, fx) }
        is Stmt.Loop    -> { this.block.visit(fs, fe, ft, fx) }
        is Stmt.DLoop   -> { this.i.visit(fs, fe, ft, fx) ; this.tsks.visit(fs, fe, ft, fx) ; this.block.visit(fs, fe, ft, fx) }
        is Stmt.Block   -> this.body.visit(fs, fe, ft, fx)
        is Stmt.Typedef -> this.type.visit(ft, fx)
        else -> TODO(this.toString()) // do not remove this line b/c we may add new cases
    }
    if (fs != null) {
        fs(this)
    }
}
