data class Scope (val lvl: Int, val arg: String?, val depth: Int)

sealed class Type (val tk: Tk) {
    data class Unit  (val tk_: Tk.Sym): Type(tk_)
    data class Nat   (val tk_: Tk.Nat): Type(tk_)
    data class Tuple (val tk_: Tk.Chr, val vec: Array<Type>): Type(tk_)
    data class Union (val tk_: Tk.Chr, val isrec: Boolean, val vec: Array<Type>): Type(tk_)
    data class Func  (val tk_: Tk.Key, val clo: Tk.Scope?, val scps: Array<Tk.Scope>, val inp: Type, val out: Type): Type(tk_)
    data class Ptr   (val tk_: Tk.Chr, val scope: Tk.Scope, val pln: Type): Type(tk_)
    data class Rec   (val tk_: Tk.Up): Type(tk_)
}

sealed class Attr (val tk: Tk) {
    data class Var   (val tk_: Tk.Str): Attr(tk_)
    data class Nat   (val tk_: Tk.Nat, val type: Type): Attr(tk_)
    data class Dnref (val tk_: Tk, val ptr: Attr): Attr(tk_)
    data class TDisc (val tk_: Tk.Num, val tup: Attr): Attr(tk_)
    data class UDisc (val tk_: Tk.Num, val uni: Attr): Attr(tk_)
}

sealed class Expr (val tk: Tk, var type: Type?) {
    data class Unit  (val tk_: Tk.Sym): Expr(tk_, Type.Unit(tk_))
    data class Var   (val tk_: Tk.Str): Expr(tk_, null)
    data class Nat   (val tk_: Tk.Nat, val type_: Type): Expr(tk_, type_)
    data class TCons (val tk_: Tk.Chr, val arg: Array<Expr>): Expr(tk_, null)
    data class UCons (val tk_: Tk.Num, val type_: Type, val arg: Expr): Expr(tk_, type_)
    data class TDisc (val tk_: Tk.Num, val tup: Expr): Expr(tk_, null)
    data class UDisc (val tk_: Tk.Num, val uni: Expr): Expr(tk_, null)
    data class UPred (val tk_: Tk.Num, val uni: Expr): Expr(tk_, null)
    data class New   (val tk_: Tk.Key, val scp: Tk.Scope, val arg: Expr.UCons): Expr(tk_, null)
    data class Dnref (val tk_: Tk,     val ptr: Expr): Expr(tk_, null)
    data class Upref (val tk_: Tk.Chr, val pln: Expr): Expr(tk_, null)
    data class Inp   (val tk_: Tk.Key, val type_: Type, val lib: Tk.Str): Expr(tk_, type_)
    data class Out   (val tk_: Tk.Key, val lib: Tk.Str, val arg: Expr): Expr(tk_, null)
    data class Call  (val tk_: Tk.Key, val sout: Tk.Scope?, val f: Expr, val sinps: Array<Tk.Scope>, val arg: Expr): Expr(tk_, null)
    data class Func  (val tk_: Tk.Key, val type_: Type.Func, val ups: Array<Tk.Str>, val block: Stmt.Block) : Expr(tk_, type_)
}

sealed class Stmt (val tk: Tk) {
    data class Nop   (val tk_: Tk) : Stmt(tk_)
    data class Var   (val tk_: Tk.Str, val type: Type) : Stmt(tk_)
    data class Set   (val tk_: Tk.Chr, val dst: Expr, val src: Expr) : Stmt(tk_)
    data class Nat   (val tk_: Tk.Nat) : Stmt(tk_)
    data class SExpr (val tk_: Tk.Key, val e: Expr) : Stmt(tk_)
    data class Seq   (val tk_: Tk, val s1: Stmt, val s2: Stmt) : Stmt(tk_)
    data class If    (val tk_: Tk.Key, val tst: Expr, val true_: Block, val false_: Block) : Stmt(tk_)
    data class Ret   (val tk_: Tk.Key) : Stmt(tk_)
    data class Loop  (val tk_: Tk.Key, val block: Block) : Stmt(tk_)
    data class Break (val tk_: Tk.Key) : Stmt(tk_)
    data class Block (val tk_: Tk.Chr, val scope: Tk.Scope?, val body: Stmt) : Stmt(tk_)
}
