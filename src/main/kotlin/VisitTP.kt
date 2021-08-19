fun Type.visitTP (ft: (Type)->Unit) {
    when (this) {
        is Type.Tuple -> this.vec.forEach { it.visitTP(ft) }
        is Type.Union -> this.vec.forEach { it.visitTP(ft) }
        is Type.UCons -> this.arg.visitTP(ft)
        is Type.Func  -> { this.inp.visitTP(ft) ; this.out.visitTP(ft) }
        is Type.Ptr   -> this.pln.visitTP(ft)
    }
    ft(this)
}

fun Stmt.visitTP (ft: (Type)->Unit) {
    when (this) {
        is Stmt.Var   -> this.type.visitTP(ft)
        is Stmt.Seq   -> { this.s1.visitTP(ft) ; this.s2.visitTP(ft) }
        is Stmt.If    -> { this.true_.visitTP(ft) ; this.false_.visitTP(ft) }
        is Stmt.Func  -> { this.type.visitTP(ft) ; if (this.block!=null) { this.block.visitTP(ft) } }
        is Stmt.Loop  -> { this.block.visitTP(ft) }
        is Stmt.Block -> { this.body.visitTP(ft) }
    }
}
