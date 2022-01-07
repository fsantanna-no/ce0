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
        is Attr.Nat   -> Expr.Nat(this.tk_, null)
        is Attr.Dnref -> Expr.Dnref(this.tk_,this.ptr.toExpr())
        is Attr.TDisc -> Expr.TDisc(this.tk_,this.tup.toExpr())
        is Attr.UDisc -> Expr.UDisc(this.tk_,this.uni.toExpr())
    }
}
