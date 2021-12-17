sealed class Expr (val tk: Tk) {
    data class Unit  (val tk_: Tk.Sym): Expr(tk_)
    data class Var   (val tk_: Tk.Str): Expr(tk_)
    data class Nat   (val tk_: Tk.Str): Expr(tk_)
    data class TCons (val tk_: Tk.Chr, val arg: Array<Expr>): Expr(tk_)
    data class UCons (val tk_: Tk.Num, val type: Type, val arg: Expr): Expr(tk_)
    data class TDisc (val tk_: Tk.Num, val tup: Expr): Expr(tk_)
    data class UDisc (val tk_: Tk.Num, val uni: Expr): Expr(tk_)
    data class UPred (val tk_: Tk.Num, val uni: Expr): Expr(tk_)
    data class New   (val tk_: Tk.Key, val scope: Tk.Scope, val arg: Expr.UCons): Expr(tk_)
    data class Dnref (val tk_: Tk,     val ptr: Expr): Expr(tk_)
    data class Upref (val tk_: Tk.Chr, val pln: Expr): Expr(tk_)
    data class Call  (val tk_: Tk.Key, val scope: Tk.Scope?, val f: Expr, val arg: Expr): Expr(tk_)
    data class Func  (val tk_: Tk.Key, val type: Type.Func, val block: Stmt.Block) : Expr(tk_)
}

sealed class Attr (val tk: Tk) {
    data class Var   (val tk_: Tk.Str): Attr(tk_)
    data class Nat   (val tk_: Tk.Str): Attr(tk_)
    data class Dnref (val tk_: Tk, val ptr: Attr): Attr(tk_)
    data class TDisc (val tk_: Tk.Num, val tup: Attr): Attr(tk_)
    data class UDisc (val tk_: Tk.Num, val uni: Attr): Attr(tk_)
}

fun Attr.toExpr (): Expr {
    return when (this) {
        is Attr.Var   -> Expr.Var(this.tk_)
        is Attr.Nat   -> Expr.Nat(this.tk_)
        is Attr.Dnref -> Expr.Dnref(this.tk_,this.ptr.toExpr())
        is Attr.TDisc -> Expr.TDisc(this.tk_,this.tup.toExpr())
        is Attr.UDisc -> Expr.UDisc(this.tk_,this.uni.toExpr())
    }
}

fun Expr.flatten (): List<Expr> {
    return when (this) {
        is Expr.Unit, is Expr.Var, is Expr.Nat, is Expr.Func -> listOf(this)
        is Expr.TDisc -> this.tup.flatten() + this
        is Expr.UDisc -> this.uni.flatten() + this
        is Expr.UPred -> this.uni.flatten() + this
        is Expr.New   -> this.arg.flatten() + this
        is Expr.Dnref -> this.ptr.flatten() + this
        is Expr.Upref -> this.pln.flatten() + this
        else -> { println(this) ; TODO() }
    }
}
