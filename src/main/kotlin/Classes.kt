private var N = 1

sealed class Type (val n: Int, val tk: Tk, var wup: Any?, var wenv: Any?) {
    data class Unit  (val tk_: Tk.Sym): Type(N++, tk_, null, null)
    data class Nat   (val tk_: Tk.Nat): Type(N++, tk_, null, null)
    data class Tuple (val tk_: Tk.Chr, val vec: Array<Type>): Type(N++, tk_, null, null)
    data class Union (val tk_: Tk.Chr, val isrec: Boolean, val vec: Array<Type>): Type(N++, tk_, null, null)
    data class Ptr   (val tk_: Tk.Chr, val xscp1: Tk.Scp1, var xscp2: Scp2?, val pln: Type): Type(N++, tk_, null, null)
    data class Rec   (val tk_: Tk.Up): Type(N++, tk_, null, null)
    data class Func  (
        val tk_: Tk.Key,
        val xscp1s: Pair<Tk.Scp1?,Array<Tk.Scp1>>,   // first=closure scope, second=input scopes
        var xscp2s: Pair<Scp2?,Array<Scp2>>?,
        val inp: Type, val out: Type
    ): Type(N++, tk_, null, null)
}

sealed class Attr (val n: Int, val tk: Tk) {
    data class Var   (val tk_: Tk.Str): Attr(N++, tk_)
    data class Nat   (val tk_: Tk.Nat, val type: Type): Attr(N++, tk_)
    data class Dnref (val tk_: Tk, val ptr: Attr): Attr(N++, tk_)
    data class TDisc (val tk_: Tk.Num, val tup: Attr): Attr(N++, tk_)
    data class UDisc (val tk_: Tk.Num, val uni: Attr): Attr(N++, tk_)
}

sealed class Expr (val n: Int, val tk: Tk, var wup: Any?, var wenv: Any?, var wtype: Type?) {
    data class Unit  (val tk_: Tk.Sym): Expr(N++, tk_, null, null, Type.Unit(tk_))
    data class Var   (val tk_: Tk.Str): Expr(N++, tk_, null, null, null)
    data class Nat   (val tk_: Tk.Nat, val xtype: Type): Expr(N++, tk_, null, null, xtype)
    data class TCons (val tk_: Tk.Chr, val arg: Array<Expr>): Expr(N++, tk_, null, null, null)
    data class UCons (val tk_: Tk.Num, val xtype: Type, val arg: Expr): Expr(N++, tk_, null, null, xtype)
    data class UNull (val tk_: Tk.Num, val xtype: Type): Expr(N++, tk_, null, null, xtype)
    data class TDisc (val tk_: Tk.Num, val tup: Expr): Expr(N++, tk_, null, null, null)
    data class UDisc (val tk_: Tk.Num, val uni: Expr): Expr(N++, tk_, null, null, null)
    data class UPred (val tk_: Tk.Num, val uni: Expr): Expr(N++, tk_, null, null, null)
    data class New   (val tk_: Tk.Key, val xscp1: Tk.Scp1, var xscp2: Scp2?, val arg: Expr.UCons): Expr(N++, tk_, null, null, null)
    data class Dnref (val tk_: Tk,     val ptr: Expr): Expr(N++, tk_, null, null, null)
    data class Upref (val tk_: Tk.Chr, val pln: Expr): Expr(N++, tk_, null, null, null)
    data class Call  (val tk_: Tk, val f: Expr, val arg: Expr, val xscp1s: Pair<Array<Tk.Scp1>,Tk.Scp1?>, var xscp2s: Pair<Array<Scp2>,Scp2?>?): Expr(N++, tk_, null, null, null)
    data class Func  (val tk_: Tk.Key, val type: Type.Func, val ups: Array<Tk.Str>, val block: Stmt.Block) : Expr(N++, tk_, null, null, type)
}

sealed class Stmt (val n: Int, val tk: Tk, var wup: Any?, var wenv: Any?) {
    data class Nop   (val tk_: Tk) : Stmt(N++, tk_, null, null)
    data class Var   (val tk_: Tk.Str, val xtype: Type) : Stmt(N++, tk_, null, null)
    data class SSet  (val tk_: Tk.Chr, val dst: Expr, val src: Stmt.Inp) : Stmt(N++, tk_, null, null)
    data class ESet  (val tk_: Tk.Chr, val dst: Expr, val src: Expr) : Stmt(N++, tk_, null, null)
    data class Nat   (val tk_: Tk.Nat) : Stmt(N++, tk_, null, null)
    data class SCall (val tk_: Tk.Key, val e: Expr.Call): Stmt(N++, tk_, null, null)
    data class Spawn (val tk_: Tk.Key, val e: Expr.Call): Stmt(N++, tk_, null, null)
    data class Await (val tk_: Tk.Key, val e: Expr): Stmt(N++, tk_, null, null)
    data class Awake (val tk_: Tk.Key, val e: Expr.Call): Stmt(N++, tk_, null, null)
    data class Bcast (val tk_: Tk.Key, val scp1: Tk.Scp1, val e: Expr): Stmt(N++, tk_, null, null)
    data class Inp   (val tk_: Tk.Key, val xtype: Type, val lib: Tk.Str, val arg: Expr): Stmt(N++, tk_, null, null)
    data class Out   (val tk_: Tk.Key, val lib: Tk.Str, val arg: Expr): Stmt(N++, tk_, null, null)
    data class Seq   (val tk_: Tk, val s1: Stmt, val s2: Stmt) : Stmt(N++, tk_, null, null)
    data class If    (val tk_: Tk.Key, val tst: Expr, val true_: Block, val false_: Block) : Stmt(N++, tk_, null, null)
    data class Ret   (val tk_: Tk.Key) : Stmt(N++, tk_, null, null)
    data class Loop  (val tk_: Tk.Key, val block: Block) : Stmt(N++, tk_, null, null)
    data class Break (val tk_: Tk.Key) : Stmt(N++, tk_, null, null)
    data class Block (val tk_: Tk.Chr, val xscp1: Tk.Scp1?, val body: Stmt) : Stmt(N++, tk_, null, null)
}
