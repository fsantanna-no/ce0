sealed class Stmt (val tk: Tk) {
    data class Pass  (val tk_: Tk) : Stmt(tk_)
    data class Var   (val tk_: Tk.Str, val type: Type) : Stmt(tk_)
    data class Set   (val tk_: Tk.Chr, val dst: Expr, val src: Expr) : Stmt(tk_)
    data class Nat   (val tk_: Tk.Str) : Stmt(tk_)
    data class Call  (val tk_: Tk.Key, val call: Expr.Call) : Stmt(tk_)
    data class Seq   (val tk_: Tk, val s1: Stmt, val s2: Stmt) : Stmt(tk_)
    data class If    (val tk_: Tk.Key, val tst: Expr, val true_: Block, val false_: Block) : Stmt(tk_)
    data class Ret   (val tk_: Tk.Key) : Stmt(tk_)
    data class Loop  (val tk_: Tk.Key, val block: Block) : Stmt(tk_)
    data class Break (val tk_: Tk.Key) : Stmt(tk_)
    data class Block (val tk_: Tk.Chr, val scope: Tk.Scope?, val body: Stmt) : Stmt(tk_)
}
