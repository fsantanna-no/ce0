data class Scp2 (val lvl: Int, val arg: String?, val depth: Int)

sealed class Type (val tk: Tk, var xup: Any?, var xenv: Any?) {
    data class Unit  (val tk_: Tk.Sym): Type(tk_, null, null)
    data class Nat   (val tk_: Tk.Nat): Type(tk_, null, null)
    data class Tuple (val tk_: Tk.Chr, val vec: Array<Type>): Type(tk_, null, null)
    data class Union (val tk_: Tk.Chr, val isrec: Boolean, val vec: Array<Type>): Type(tk_, null, null)
    data class Ptr   (val tk_: Tk.Chr, val scp1: Tk.Scp1, var xscp2: Scp2?, val pln: Type): Type(tk_, null, null)
    data class Rec   (val tk_: Tk.Up): Type(tk_, null, null)
    data class Func  (
        val tk_: Tk.Key,
        val scp1s: Pair<Tk.Scp1?,Array<Tk.Scp1>>,   // first=closure scope, second=input scopes
        var xscp2s: Pair<Scp2?,Array<Scp2>>?,
        val inp: Type, val out: Type
    ): Type(tk_, null, null)
}

sealed class Attr (val tk: Tk, var xup: Any?) {
    data class Var   (val tk_: Tk.Str): Attr(tk_, null)
    data class Nat   (val tk_: Tk.Nat, val type: Type): Attr(tk_, null)
    data class Dnref (val tk_: Tk, val ptr: Attr): Attr(tk_, null)
    data class TDisc (val tk_: Tk.Num, val tup: Attr): Attr(tk_, null)
    data class UDisc (val tk_: Tk.Num, val uni: Attr): Attr(tk_, null)
}

sealed class Expr (val tk: Tk, var xup: Any?, var xenv: Any?, var xtype: Type?) {
    data class Unit  (val tk_: Tk.Sym): Expr(tk_, null, null, Type.Unit(tk_))
    data class Var   (val tk_: Tk.Str): Expr(tk_, null, null, null)
    data class Nat   (val tk_: Tk.Nat, val type: Type): Expr(tk_, null, null, type)
    data class TCons (val tk_: Tk.Chr, val arg: Array<Expr>): Expr(tk_, null, null, null)
    data class UCons (val tk_: Tk.Num, val type: Type, val arg: Expr): Expr(tk_, null, null, type)
    data class TDisc (val tk_: Tk.Num, val tup: Expr): Expr(tk_, null, null, null)
    data class UDisc (val tk_: Tk.Num, val uni: Expr): Expr(tk_, null, null, null)
    data class UPred (val tk_: Tk.Num, val uni: Expr): Expr(tk_, null, null, null)
    data class New   (val tk_: Tk.Key, val scp1: Tk.Scp1, var xscp2: Scp2?, val arg: Expr.UCons): Expr(tk_, null, null, null)
    data class Dnref (val tk_: Tk,     val ptr: Expr): Expr(tk_, null, null, null)
    data class Upref (val tk_: Tk.Chr, val pln: Expr): Expr(tk_, null, null, null)
    data class Inp   (val tk_: Tk.Key, val type: Type, val lib: Tk.Str): Expr(tk_, null, null, type)
    data class Out   (val tk_: Tk.Key, val lib: Tk.Str, val arg: Expr): Expr(tk_, null, null, null)
    data class Call  (val tk_: Tk.Key, val f: Expr, val arg: Expr, val scp1s: Pair<Array<Tk.Scp1>,Tk.Scp1?>, var xscp2s: Pair<Array<Scp2>,Scp2?>?): Expr(tk_, null, null, null)
    data class Func  (val tk_: Tk.Key, val type: Type.Func, val ups: Array<Tk.Str>, val block: Stmt.Block) : Expr(tk_, null, null, type)
}

sealed class Stmt (val tk: Tk, var xup: Any?, var xenv: Any?) {
    data class Nop   (val tk_: Tk) : Stmt(tk_, null, null)
    data class Var   (val tk_: Tk.Str, val type: Type) : Stmt(tk_, null, null)
    data class Set   (val tk_: Tk.Chr, val dst: Expr, val src: Expr) : Stmt(tk_, null, null)
    data class Nat   (val tk_: Tk.Nat) : Stmt(tk_, null, null)
    data class SExpr (val tk_: Tk.Key, val e: Expr) : Stmt(tk_, null, null)
    data class Seq   (val tk_: Tk, val s1: Stmt, val s2: Stmt) : Stmt(tk_, null, null)
    data class If    (val tk_: Tk.Key, val tst: Expr, val true_: Block, val false_: Block) : Stmt(tk_, null, null)
    data class Ret   (val tk_: Tk.Key) : Stmt(tk_, null, null)
    data class Loop  (val tk_: Tk.Key, val block: Block) : Stmt(tk_, null, null)
    data class Break (val tk_: Tk.Key) : Stmt(tk_, null, null)
    data class Block (val tk_: Tk.Chr, val scp1: Tk.Scp1?, val body: Stmt) : Stmt(tk_, null, null)
}
