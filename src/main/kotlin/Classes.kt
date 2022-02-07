private var N = 1

sealed class Type (val n: Int, val tk: Tk, var wup: Any?, var wenv: Any?) {
    data class Unit    (val tk_: Tk.Sym): Type(N++, tk_, null, null)
    data class Nat     (val tk_: Tk.Nat): Type(N++, tk_, null, null)
    data class Tuple   (val tk_: Tk.Chr, val vec: Array<Type>): Type(N++, tk_, null, null)
    data class Union   (val tk_: Tk.Chr, val vec: Array<Type>): Type(N++, tk_, null, null)
    data class Pointer (val tk_: Tk.Chr, val xscp1: Tk.Id, var xscp2: Scp2?, val pln: Type): Type(N++, tk_, null, null)
    data class Spawn   (val tk_: Tk.Key, val tsk: Type.Func): Type(N++, tk_, null, null)
    data class Spawns  (val tk_: Tk.Key, val tsk: Type.Func): Type(N++, tk_, null, null)
    data class Func (
        val tk_: Tk.Key,
        val xscp1s: Triple<Tk.Id?,Array<Tk.Id>,Array<Pair<String,String>>>,   // [closure scope, input scopes, input scopes constraints]
        var xscp2s: Pair<Scp2?,Array<Scp2>>?,
        val inp: Type, val pub: Type?, val out: Type
    ): Type(N++, tk_, null, null)
    data class Alias (
        val tk_: Tk.Id,
        var xisrec: Boolean,
        val xscp1s: Array<Tk.Id>,
        var xscp2s: Array<Scp2>?
    ): Type(N++, tk_, null, null)
}

sealed class Attr (val n: Int, val tk: Tk) {
    data class Var   (val tk_: Tk.Id): Attr(N++, tk_)
    data class Nat   (val tk_: Tk.Nat, val type: Type): Attr(N++, tk_)
    data class Dnref (val tk_: Tk, val ptr: Attr): Attr(N++, tk_)
    data class TDisc (val tk_: Tk.Num, val tup: Attr): Attr(N++, tk_)
    data class UDisc (val tk_: Tk.Num, val uni: Attr): Attr(N++, tk_)
    data class Pub   (val tk_: Tk.Id, val tsk: Attr): Attr(N++, tk_)
}

sealed class Expr (val n: Int, val tk: Tk, var wup: Any?, var wenv: Any?, var wtype: Type?) {
    data class Unit  (val tk_: Tk.Sym): Expr(N++, tk_, null, null, Type.Unit(tk_))
    data class Var   (val tk_: Tk.Id): Expr(N++, tk_, null, null, null)
    data class Nat   (val tk_: Tk.Nat, val xtype: Type): Expr(N++, tk_, null, null, xtype)
    data class TCons (val tk_: Tk.Chr, val arg: Array<Expr>): Expr(N++, tk_, null, null, null)
    data class UCons (val tk_: Tk.Num, val xtype: Type, val arg: Expr): Expr(N++, tk_, null, null, xtype)
    data class UNull (val tk_: Tk.Num, val xtype: Type): Expr(N++, tk_, null, null, xtype)
    data class TDisc (val tk_: Tk.Num, val tup: Expr): Expr(N++, tk_, null, null, null)
    data class UDisc (val tk_: Tk.Num, val uni: Expr): Expr(N++, tk_, null, null, null)
    data class UPred (val tk_: Tk.Num, val uni: Expr): Expr(N++, tk_, null, null, null)
    data class New   (val tk_: Tk.Key, val xscp1: Tk.Id, var xscp2: Scp2?, val arg: Expr.UCons): Expr(N++, tk_, null, null, null)
    data class Dnref (val tk_: Tk,     val ptr: Expr): Expr(N++, tk_, null, null, null)
    data class Upref (val tk_: Tk.Chr, val pln: Expr): Expr(N++, tk_, null, null, null)
    data class Call  (val tk_: Tk, val f: Expr, val arg: Expr, val xscp1s: Pair<Array<Tk.Id>,Tk.Id?>, var xscp2s: Pair<Array<Scp2>,Scp2?>?): Expr(N++, tk_, null, null, null)
    data class Func  (val tk_: Tk.Key, val type: Type.Func, val ups: Array<Tk.Id>, val block: Stmt.Block) : Expr(N++, tk_, null, null, type)
    data class Pub   (val tk_: Tk.Id, val tsk: Expr): Expr(N++, tk_, null, null, null)
}

sealed class Stmt (val n: Int, val tk: Tk, var wup: Any?, var wenv: Any?) {
    data class Nop    (val tk_: Tk) : Stmt(N++, tk_, null, null)
    data class Var    (val tk_: Tk.Id, val xtype: Type) : Stmt(N++, tk_, null, null)
    data class Set    (val tk_: Tk.Chr, val dst: Expr, val src: Expr) : Stmt(N++, tk_, null, null)
    data class Native (val tk_: Tk.Nat, val istype: Boolean) : Stmt(N++, tk_, null, null)
    data class SCall  (val tk_: Tk.Key, val e: Expr.Call): Stmt(N++, tk_, null, null)
    data class SSpawn (val tk_: Tk.Key, val dst: Expr, val call: Expr.Call): Stmt(N++, tk_, null, null)
    data class DSpawn (val tk_: Tk.Key, val dst: Expr, val call: Expr.Call): Stmt(N++, tk_, null, null)
    data class Await  (val tk_: Tk.Key, val e: Expr): Stmt(N++, tk_, null, null)
    data class Bcast  (val tk_: Tk.Key, val scp1: Tk.Id, val e: Expr): Stmt(N++, tk_, null, null)
    data class Throw  (val tk_: Tk.Key): Stmt(N++, tk_, null, null)
    data class Input  (val tk_: Tk.Key, val xtype: Type, val dst: Expr?, val lib: Tk.Id, val arg: Expr): Stmt(N++, tk_, null, null)
    data class Output (val tk_: Tk.Key, val lib: Tk.Id, val arg: Expr): Stmt(N++, tk_, null, null)
    data class Seq    (val tk_: Tk, val s1: Stmt, val s2: Stmt) : Stmt(N++, tk_, null, null)
    data class If     (val tk_: Tk.Key, val tst: Expr, val true_: Block, val false_: Block) : Stmt(N++, tk_, null, null)
    data class Return (val tk_: Tk.Key) : Stmt(N++, tk_, null, null)
    data class Loop   (val tk_: Tk.Key, val block: Block) : Stmt(N++, tk_, null, null)
    data class DLoop  (val tk_: Tk.Key, val i: Expr.Var, val tsks: Expr, val block: Block) : Stmt(N++, tk_, null, null)
    data class Break  (val tk_: Tk.Key) : Stmt(N++, tk_, null, null)
    data class Block  (val tk_: Tk.Chr, val iscatch: Boolean, val xscp1: Tk.Id?, val body: Stmt) : Stmt(N++, tk_, null, null)
    data class Typedef (
        val tk_: Tk.Id,
        val xscp1s: Pair<Array<Tk.Id>,Array<Pair<String,String>>>,
        var xscp2s: Array<Scp2>?,
        val type: Type
    ) : Stmt(N++, tk_, null, null)
}
