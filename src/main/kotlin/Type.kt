data class Scope (val lvl: Int, val rel: String?, val depth: Int)

sealed class Type (val tk: Tk) {
    data class Unit  (val tk_: Tk.Sym): Type(tk_)
    data class Nat   (val tk_: Tk.Str): Type(tk_)
    data class Tuple (val tk_: Tk.Chr, val vec: Array<Type>): Type(tk_)
    data class Union (val tk_: Tk.Chr, val isrec: Boolean, val vec: Array<Type>): Type(tk_)
    data class Func  (val tk_: Tk.Chr, val clo: Tk.Scope?, val scps: Array<Tk.Scope>, val inp: Type, val out: Type): Type(tk_)
    data class Ptr   (val tk_: Tk.Chr, val scope: Tk.Scope, val pln: Type): Type(tk_)
    data class Rec   (val tk_: Tk.Up): Type(tk_)
}

fun Type.tostr (): String {
    fun Tk.Scope?.tostr (): String {
        return if (this == null) "" else {
            "@" + this.lbl + (if (this.num == null) "" else ("_" + this.num))
        }
    }
    return when (this) {
        is Type.Unit  -> "()"
        is Type.Nat   -> this.tk_.str
        is Type.Rec   -> "^".repeat(this.tk_.up)
        is Type.Ptr   -> this.scope.let { "/" + this.pln.tostr() + it.tostr() }
        is Type.Tuple -> "[" + this.vec.map { it.tostr() }.joinToString(",") + "]"
        is Type.Union -> "<" + this.vec.map { it.tostr() }.joinToString(",") + ">"
        is Type.Func  -> "{" + this.clo.tostr() + "} -> {" + this.scps.map { it.tostr() }.joinToString(",") + "} -> " + this.inp.tostr() + " -> " + this.out.tostr()
    }
}

fun Type.flatten (): List<Type> {
    // TODO: func/union do not make sense?
    return when (this) {
        is Type.Unit, is Type.Nat, is Type.Rec -> listOf(this)
        is Type.Tuple -> this.vec.map { it.flatten() }.flatten() + this
        is Type.Union -> this.vec.map { it.flatten() }.flatten() + this
        is Type.Func  -> this.inp.flatten() + this.out.flatten() + this
        is Type.Ptr   -> this.pln.flatten() + this
    }
}

fun Type.lincol (lin: Int, col: Int): Type {
    return when (this) {
        is Type.Unit  -> Type.Unit(this.tk_.copy(lin_=lin,col_=col))
        is Type.Nat   -> Type.Nat(this.tk_.copy(lin_=lin,col_=col))
        is Type.Tuple -> Type.Tuple(this.tk_.copy(lin_=lin,col_=col), this.vec.map { it.lincol(lin,col) }.toTypedArray())
        is Type.Union -> Type.Union(this.tk_.copy(lin_=lin,col_=col), this.isrec, this.vec.map { it.lincol(lin,col) }.toTypedArray())
        is Type.Func  -> Type.Func(this.tk_.copy(lin_=lin,col_=col), this.clo?.copy(lin_=lin,col_=col), this.scps.map { it.copy(lin_=lin,col_=col) }.toTypedArray(), this.inp.lincol(lin,col), this.out.lincol(lin,col))
        is Type.Ptr   -> Type.Ptr(this.tk_.copy(lin_=lin,col_=col), this.scope, this.pln.lincol(lin,col))
        is Type.Rec   -> Type.Rec(this.tk_.copy(lin_=lin,col_=col))
    }
}

fun Type.isrec (): Boolean {
    return (this is Type.Union) && this.isrec
}

fun Type.containsRec (): Boolean {
    return when (this) {
        is Type.Unit, is Type.Nat, is Type.Ptr, is Type.Func -> false
        is Type.Rec   -> true
        is Type.Tuple -> this.vec.any { it.containsRec() }
        is Type.Union -> this.vec.any { it.containsRec() }
    }
}
