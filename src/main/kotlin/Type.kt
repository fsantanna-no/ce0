data class Scope (val lvl: Int, val rel: String?, val depth: Int)

sealed class Type (val tk: Tk) {
    data class Any   (val tk_: Tk.Chr): Type(tk_)
    data class Unit  (val tk_: Tk.Sym): Type(tk_)
    data class Nat   (val tk_: Tk.Str): Type(tk_)
    data class Tuple (val tk_: Tk.Chr, val vec: Array<Type>): Type(tk_)
    data class Union (val tk_: Tk.Chr, val isrec: Boolean, val vec: Array<Type>): Type(tk_)
    data class UCons (val tk_: Tk.Num, val arg: Type): Type(tk_)
    data class Func  (val tk_: Tk.Sym, val clo: Tk.Scope, val inp: Type, val out: Type): Type(tk_)
    data class Ptr   (val tk_: Tk.Chr, val scope: Tk.Scope?, val pln: Type): Type(tk_)
    data class Rec   (val tk_: Tk.Up): Type(tk_)
    data class Pool  (val tk_: Tk.Scope): Type(tk_)
}

fun Type_Unit (tk: Tk): Type.Unit {
    return Type.Unit(Tk.Sym(TK.UNIT, tk.lin, tk.col, "()"))
}
fun Type_Any (tk: Tk): Type.Any {
    return Type.Any(Tk.Chr(TK.CHAR,tk.lin,tk.col,'?'))
}
fun Type_Nat (tk: Tk, str: String): Type.Nat {
    return Type.Nat(Tk.Str(TK.XNAT,tk.lin,tk.col,str))
}

fun Type.tostr (): String {
    fun Tk.Scope?.tostr (): String {
        return if (this == null) "" else {
            "@" + this.lbl + (if (this.num == null) "" else ("_" + this.num))
        }
    }
    return when (this) {
        is Type.Any   -> "?"
        is Type.Unit  -> "()"
        is Type.Nat   -> this.tk_.str
        is Type.Rec   -> "^".repeat(this.tk_.up)
        is Type.Ptr   -> this.scope.let { (if (it==null) "" else "(") + "/" + this.pln.tostr() + it.tostr() }
        is Type.Tuple -> "[" + this.vec.map { it.tostr() }.joinToString(",") + "]"
        is Type.Union -> "<" + this.vec.map { it.tostr() }.joinToString(",") + ">"
        is Type.UCons -> "<." + this.tk_.num + " " + this.arg.tostr() + ">"
        is Type.Func  -> "{" + this.clo.tostr() + "} " + this.inp.tostr() + " -> " + this.out.tostr()
        is Type.Pool  -> "@"+this.tk_.lbl + this.tk_.num.let { (if(it==null) "" else "_"+it) }
    }
}

fun Type.flatten (): List<Type> {
    // TODO: func/union do not make sense?
    return when (this) {
        is Type.Any, is Type.Unit, is Type.Nat, is Type.Rec, is Type.Pool -> listOf(this)
        is Type.Tuple -> this.vec.map { it.flatten() }.flatten() + this
        is Type.Union -> this.vec.map { it.flatten() }.flatten() + this
        is Type.UCons -> this.arg.flatten() + this
        is Type.Func  -> this.inp.flatten() + this.out.flatten() + this
        is Type.Ptr   -> this.pln.flatten() + this
    }
}

fun Type.lincol (lin: Int, col: Int): Type {
    return when (this) {
        is Type.Any   -> Type.Any(this.tk_.copy(lin_=lin,col_=col))
        is Type.Unit  -> Type.Unit(this.tk_.copy(lin_=lin,col_=col))
        is Type.Nat   -> Type.Nat(this.tk_.copy(lin_=lin,col_=col))
        is Type.Tuple -> Type.Tuple(this.tk_.copy(lin_=lin,col_=col), this.vec.map { it.lincol(lin,col) }.toTypedArray())
        is Type.Union -> Type.Union(this.tk_.copy(lin_=lin,col_=col), this.isrec, this.vec.map { it.lincol(lin,col) }.toTypedArray())
        is Type.UCons -> Type.UCons(this.tk_.copy(lin_=lin,col_=col), this.arg.lincol(lin,col))
        is Type.Func  -> Type.Func(this.tk_.copy(lin_=lin,col_=col), this.clo?.copy(lin_=lin,col_=col), this.inp.lincol(lin,col), this.out.lincol(lin,col))
        is Type.Ptr   -> Type.Ptr(this.tk_.copy(lin_=lin,col_=col), this.scope, this.pln.lincol(lin,col))
        is Type.Rec   -> Type.Rec(this.tk_.copy(lin_=lin,col_=col))
        is Type.Pool  -> Type.Pool(this.tk_.copy(lin_=lin,col_=col))
    }
}

fun Type.isrec (): Boolean {
    return (this is Type.Union) && this.isrec
}

fun Type.Union.expand (): Array<Type> {
    fun aux (cur: Type, up: Int): Type {
        return when (cur) {
            is Type.Rec   -> if (up == cur.tk_.up) this else { assert(up>cur.tk_.up) ; cur }
            is Type.Tuple -> Type.Tuple(cur.tk_, cur.vec.map { aux(it,up) }.toTypedArray()) .up(AUX.ups[cur]!!)
            is Type.Union -> Type.Union(cur.tk_, cur.isrec, cur.vec.map { aux(it,up+1) }.toTypedArray()) .up(AUX.ups[cur]!!)
            is Type.Ptr   -> Type.Ptr(cur.tk_, cur.scope, aux(cur.pln,up)) .up(AUX.ups[cur]!!)
            is Type.Func  -> Type.Func(cur.tk_, cur.clo, aux(cur.inp,up), aux(cur.out,up)) .up(AUX.ups[cur]!!)
            is Type.UCons -> error("bug found")
            else -> cur
        }
    }
    return this.vec.map { aux(it, 1) }.toTypedArray()
}

fun Type.containsRec (): Boolean {
    return when (this) {
        is Type.Any, is Type.Unit, is Type.Nat, is Type.Ptr, is Type.Func, is Type.Pool -> false
        is Type.Rec   -> true
        is Type.Tuple -> this.vec.any { it.containsRec() }
        is Type.Union -> this.vec.any { it.containsRec() }
        is Type.UCons -> this.arg.containsRec()
    }
}

///////////////////////////////////////////////////////////////////////////////

fun Type.pools (): List<Type.Pool> {
    return when (this) {
        is Type.Pool -> listOf(this)
        is Type.Tuple -> this.vec.filter { it is Type.Pool } as List<Type.Pool>
        else -> emptyList()
    }
}

fun Tk.Scope?.scope (up: Any): Scope {
    // Offset of @N (max) of all crossing functions:
    //  func /()->() {              // +1
    //      func [/()@1,/()@2] {    // +2
    //          ...                 // off=3
    fun off (ups: List<Any>): Int {
        return ups
            .filter { it is Expr.Func }
            .map {
                (it as Expr.Func).type.flatten().filter { it is Type.Ptr }
                    .map { (it as Type.Ptr).scope!!.lbl }
                    .map { it.toIntOrNull() }
                    .filterNotNull()
                    .maxOrNull() ?: 0
            }
            .sum()
    }

    // Level of function nesting:
    //  func ... {
    //      ...             // lvl=1
    //      func ... {
    //          ...         // lvl=1
    val lvl = up.ups_tolist().filter { it is Expr.Func }.count()
    // dropWhile(Type).drop(1) so that prototype skips up func
    //val lvl = up.ups_tolist().dropWhile { it is Type }.drop(1).filter { it is Expr.Func }.count()

    return when (this?.lbl) {
        null     -> Scope(lvl, null, up.ups_tolist().let { off(it) + it.count { it is Stmt.Block } })
        "global" -> Scope(lvl, null, 0)
        "local"  -> Scope(lvl, null, up.ups_tolist().let { off(it) + it.count { it is Stmt.Block } })
        else -> {
            val blk = up.ups_first { it is Stmt.Block && it.scope!=null && it.scope.lbl==this?.lbl }
            if (blk != null) {
                Scope(lvl, null, 1 + blk.ups_tolist().let { off(it) + it.count { it is Stmt.Block } })
            } else {    // false = relative to function block
                val n = up.ups_first { it is Expr.Func }.let { if (it == null) 0 else off(it.ups_tolist()) }
                Scope(lvl, this?.lbl, n + (this?.num ?: 0))
            }
        }
    }
}

fun Type.scope (): Scope {
    return when (this) {
        is Type.Pool -> this.tk_.scope(this)
        is Type.Ptr  -> this.scope.scope(this)
        else -> {
            val lvl = this.ups_tolist().filter { it is Expr.Func }.count()
            Scope(lvl, null, this.ups_tolist().let { it.count { it is Stmt.Block } })
        }
    }
}
