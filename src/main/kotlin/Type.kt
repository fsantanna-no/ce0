data class Scope (val level: Int, val isabs: Boolean, val depth: Int)

sealed class Type (val tk: Tk) {
    data class Any   (val tk_: Tk.Chr): Type(tk_)
    data class Unit  (val tk_: Tk.Sym): Type(tk_)
    data class Nat   (val tk_: Tk.Str): Type(tk_)
    data class Tuple (val tk_: Tk.Chr, val vec: Array<Type>): Type(tk_)
    data class Union (val tk_: Tk.Chr, val isrec: Boolean, val vec: Array<Type>): Type(tk_)
    data class UCons (val tk_: Tk.Num, val arg: Type): Type(tk_)
    data class Func  (val tk_: Tk.Sym, val inp: Type, val out: Type): Type(tk_)
    data class Ptr   (val tk_: Tk.Chr, val scope: Tk.Scope?, val pln: Type): Type(tk_)
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
        is Type.Ptr   -> "/" + this.pln.tostr() + (if (this.scope==null) "" else this.scope.scp)
        is Type.Tuple -> "[" + this.vec.map { it.tostr() }.joinToString(",") + "]"
        is Type.Union -> "<" + this.vec.map { it.tostr() }.joinToString(",") + ">"
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
        is Type.Union -> Type.Union(this.tk_.copy(lin_=lin,col_=col), this.isrec, this.vec.map { it.lincol(lin,col) }.toTypedArray())
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
        is Type.Union -> f(Type.Union(this.tk_, this.isrec, this.vec.map { it.map(f) }.toTypedArray()))
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

fun Type.Union.expand (): Array<Type> {
    fun aux (cur: Type, up: Int): Type {
        return when (cur) {
            is Type.Rec   -> if (up == cur.tk_.up) this else { assert(up>cur.tk_.up) ; cur }
            is Type.Tuple -> Type.Tuple(cur.tk_, cur.vec.map { aux(it,up) }.toTypedArray()) .up(AUX.ups[cur]!!)
            is Type.Union -> Type.Union(cur.tk_, cur.isrec, cur.vec.map { aux(it,up+1) }.toTypedArray()) .up(AUX.ups[cur]!!)
            is Type.Ptr   -> Type.Ptr(cur.tk_, cur.scope, aux(cur.pln,up)) .up(AUX.ups[cur]!!)
            is Type.Func  -> Type.Func(cur.tk_, aux(cur.inp,up), aux(cur.out,up)) .up(AUX.ups[cur]!!)
            is Type.UCons -> error("bug found")
            else -> cur
        }
    }
    return this.vec.map { aux(it, 1) }.toTypedArray()
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

///////////////////////////////////////////////////////////////////////////////

fun Type.Ptr.topool (): String {
    return this.scope().let {
        when {
            !it.isabs -> "__news__${it.depth}"
            else -> "__news_${it.depth}"
        }
    }
}

fun Type.Ptr.scope (): Scope {

    // Offset of @N (max) of all crossing functions:
    //  func /()->() {              // +1
    //      func [/()@1,/()@2] {    // +2
    //          ...                 // off=3
    fun off (ups: List<Any>): Int {
        return ups
            .filter { it is Expr.Func }
            .map {
                (it as Expr.Func).type.flatten().filter { it is Type.Ptr }
                    .map { (it as Type.Ptr).scope!!.scp }
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
    val lvl = this.ups_tolist().filter { it is Expr.Func }.count()
    // dropWhile(Type).drop(1) so that prototype skips up func
    //val lvl = this.ups_tolist().dropWhile { it is Type }.drop(1).filter { it is Expr.Func }.count()

    val id = this.scope?.scp
    return when (id) {
        null -> Scope(lvl, true, this.ups_tolist().let { off(it) + it.count { it is Stmt.Block } })
        "@global" -> Scope(lvl, true, 0)
        "@local"  -> Scope(lvl, true, this.ups_tolist().let { off(it) + it.count { it is Stmt.Block } })
        else -> {
            val num = id.drop(1).toIntOrNull()
            if (num == null) {  // @aaa
                val blk = this.ups_first { it is Stmt.Block && it.scope!=null && it.scope.scp == id }!!
                Scope(lvl, true, 1 + blk.ups_tolist().let { off(it) + it.count { it is Stmt.Block } })
            } else {            // @1
                val n = this.ups_first { it is Expr.Func }.let { if (it == null) 0 else off(it.ups_tolist()) }
                Scope(lvl, false, n + num)    // false = relative to function block
            }
        }
    }
}
