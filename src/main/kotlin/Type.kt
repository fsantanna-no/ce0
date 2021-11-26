sealed class Type (val tk: Tk) {
    data class Any   (val tk_: Tk.Chr): Type(tk_)
    data class Unit  (val tk_: Tk.Sym): Type(tk_)
    data class Nat   (val tk_: Tk.Str): Type(tk_)
    data class Tuple (val tk_: Tk.Chr, val vec: Array<Type>): Type(tk_)
    data class Union (val tk_: Tk.Chr, val isrec: Boolean, val isnull: Boolean, val vec: Array<Type>): Type(tk_)
    data class UCons (val tk_: Tk.Num, val arg: Type): Type(tk_)
    data class Func  (val tk_: Tk.Sym, val inp: Type, val out: Type): Type(tk_)
    data class Ptr   (val tk_: Tk.Chr, val scope: String?, val pln: Type): Type(tk_)
    data class Rec   (val tk_: Tk.Up): Type(tk_)
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
    return when (this) {
        is Type.Any   -> "?"
        is Type.Unit  -> "()"
        is Type.Nat   -> this.tk_.str
        is Type.Rec   -> "^".repeat(this.tk_.up)
        is Type.Ptr   -> "/" + this.pln.tostr() + if (this.scope==null) "" else this.scope
        is Type.Tuple -> "[" + this.vec.map { it.tostr() }.joinToString(",") + "]"
        is Type.Union -> "<" + (if (this.isnull) "? " else "") + this.vec.map { it.tostr() }.joinToString(",") + ">"
        is Type.UCons -> "<." + this.tk_.num + " " + this.arg.tostr() + ">"
        is Type.Func  -> this.inp.tostr() + " -> " + this.out.tostr()
    }
}

fun Type.flatten (): List<Type> {
    // TODO: func/union do not make sense?
    return when (this) {
        is Type.Any, is Type.Unit, is Type.Nat, is Type.Rec -> listOf(this)
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
        is Type.Union -> Type.Union(this.tk_.copy(lin_=lin,col_=col), this.isrec, this.isnull, this.vec.map { it.lincol(lin,col) }.toTypedArray())
        is Type.UCons -> Type.UCons(this.tk_.copy(lin_=lin,col_=col), this.arg.lincol(lin,col))
        is Type.Func  -> Type.Func(this.tk_.copy(lin_=lin,col_=col), this.inp.lincol(lin,col), this.out.lincol(lin,col))
        is Type.Ptr   -> Type.Ptr(this.tk_.copy(lin_=lin,col_=col), this.scope, this.pln.lincol(lin,col))
        is Type.Rec   -> Type.Rec(this.tk_.copy(lin_=lin,col_=col))
    }
}

fun Type.map (f: (Type)->Type): Type {
    return when (this) {
        is Type.Any, is Type.Unit, is Type.Nat, is Type.Rec -> f(this)
        is Type.Tuple -> f(Type.Tuple(this.tk_, this.vec.map { it.map(f) }.toTypedArray()))
        is Type.Union -> f(Type.Union(this.tk_, this.isrec, this.isnull, this.vec.map { it.map(f) }.toTypedArray()))
        is Type.UCons -> f(Type.UCons(this.tk_, f(this.arg)))
        is Type.Func  -> f(Type.Func(this.tk_, this.inp.map(f), this.out.map(f)))
        is Type.Ptr   -> f(Type.Ptr(this.tk_, this.scope, this.pln.map(f)))
    }
}

fun Type.keepAnyNat (other: ()->Type): Type {
    return when (this) {
        is Type.Any, is Type.Nat -> this
        else -> other()
    }
}

fun Type.isrec (): Boolean {
    return (this is Type.Union) && this.isrec
}

fun Type.isnullptr (): Boolean {
    return this is Type.Union && this.isnull && this.vec.size==1 && this.vec[0] is Type.Ptr
}

// TODO: use it to detect recursive unions that do not require tags b/c of single subtype+null pointer
// (e.g., lists). Remove field/tests from the struct.
fun Type.isnullexrec (): Boolean {
    return this is Type.Union && this.isrec() && this.isnull && this.vec.size==1
}

fun Type.Union.expand (): Array<Type> {
    fun aux (cur: Type, up: Int): Type {
        return when (cur) {
            is Type.Rec   -> if (up == cur.tk_.up) this else { assert(up>cur.tk_.up) ; cur }
            is Type.Tuple -> Type.Tuple(cur.tk_, cur.vec.map { aux(it,up) }.toTypedArray())
            is Type.Union -> Type.Union(cur.tk_, cur.isrec, cur.isnull, cur.vec.map { aux(it,up+1) }.toTypedArray())
            is Type.Ptr   -> Type.Ptr(cur.tk_, cur.scope, aux(cur.pln,up))
            is Type.Func  -> Type.Func(cur.tk_, aux(cur.inp,up), aux(cur.out,up))
            is Type.UCons -> error("bug found")
            else -> cur
        }
    }
    return this.vec.map { aux(it, 1) }.toTypedArray()
}

fun Type.Ptr.scopeDepth (): Int? {
    return when (this.scope) {
        null -> this.ups_tolist().count { it is Stmt.Block }
        "@global" -> 0
        else -> {
            val num = this.scope.drop(1).toIntOrNull()
            if (num == null) {
                val blk = this.ups_first { it is Stmt.Block && it.scope == this.scope }
                return if (blk == null) null else {
                    1 + blk.ups_tolist().count { it is Stmt.Block }
                }
            } else {
                num
            }
        }
    }
}

fun Type.containsRec (): Boolean {
    return when (this) {
        is Type.Any, is Type.Unit, is Type.Nat, is Type.Ptr, is Type.Func -> false
        is Type.Rec   -> true
        is Type.Tuple -> this.vec.any { it.containsRec() }
        is Type.Union -> this.vec.any { it.containsRec() }
        is Type.UCons -> this.arg.containsRec()
    }
}
