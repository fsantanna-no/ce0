/*
fun Type.link1 (up: Any): Type {
    this.up  = up
    this.env = up.getEnv()
    return this
}
*/

fun Type.link (up: Any): Type {
    this.setUps(up)
    this.setEnvs(up.getEnv())
    this.visit(false, { this.wenv = up.getEnv() })
    return this
}

fun Type.linkX (up: Any): Type {
    this.setUps(up)
    this.setEnvs(up.getEnv())
    //this.visit(false, { this.env = up.getEnv() })
    return this
}

fun Tk.Scp1.tostr (): String {
    return "@" + this.lbl + (this.num?:"")
}

fun Type.tostr (): String {
    fun Tk.Scp1?.clo (): String {
        return if (this == null) "" else
            this.tostr() + " -> "
    }
    return when (this) {
        is Type.Unit  -> "()"
        is Type.Alias -> this.tk_.str
        is Type.Nat   -> this.tk_.toce()
        is Type.Rec   -> "^".repeat(this.tk_.up)
        is Type.Ptr   -> this.xscp1.let { "/" + this.pln.tostr() + (it?.tostr() ?: "")}
        is Type.Tuple -> "[" + this.vec.map { it.tostr() }.joinToString(",") + "]"
        is Type.Union -> "<" + this.vec.map { it.tostr() }.joinToString(",") + ">"
        is Type.Func  -> this.tk_.key + " " + this.xscp1s.first.clo() + "@[" + this.xscp1s.second!!.map { it.tostr() }.joinToString(",") + "] -> " + this.inp.tostr() + " -> " + this.pub.let { if (it == null) "" else it.tostr() + " -> " } + this.out.tostr()
        is Type.Spawn   -> "active " + this.tsk.tostr()
        is Type.Spawns  -> "active " + this.tsk.tostr()
    }
}

/*
fun Type.flattenRight (): List<Type> {
    // TODO: func/union do not make sense?
    return when (this) {
        is Type.Unit, is Type.Nat, is Type.Rec -> listOf(this)
        is Type.Tuple -> this.vec.map { it.flattenRight() }.flatten() + this
        is Type.Union -> this.vec.map { it.flattenRight() }.flatten() + this
        is Type.Func  -> listOf(this) //this.inp.flattenRight() + this.out.flattenRight() + this
        is Type.Ptr   -> this.pln.flattenRight() + this
    }
}
 */

fun Type.flattenLeft (): List<Type> {
    // TODO: func/union do not make sense?
    return when (this) {
        is Type.Unit, is Type.Nat, is Type.Rec, is Type.Alias -> listOf(this)
        is Type.Tuple -> listOf(this) + this.vec.map { it.flattenLeft() }.flatten()
        is Type.Union -> listOf(this) + this.vec.map { it.flattenLeft() }.flatten()
        is Type.Func  -> listOf(this) //this.inp.flatten() + this.out.flatten()
        is Type.Spawn, is Type.Spawns -> TODO()
        is Type.Ptr   -> listOf(this) + this.pln.flattenLeft()
    }
}

fun Type.clone (up: Any, lin: Int, col: Int): Type {
    fun Type.aux (lin: Int, col: Int): Type {
        return when (this) {
            is Type.Unit -> Type.Unit(this.tk_.copy(lin_ = lin, col_ = col))
            is Type.Alias -> Type.Alias(this.tk_.copy(lin_ = lin, col_ = col))
            is Type.Nat -> Type.Nat(this.tk_.copy(lin_ = lin, col_ = col))
            is Type.Tuple -> Type.Tuple(
                this.tk_.copy(lin_ = lin, col_ = col),
                this.vec.map { it.aux(lin, col) }.toTypedArray()
            )
            is Type.Union -> Type.Union(
                this.tk_.copy(lin_ = lin, col_ = col),
                this.isrec,
                this.vec.map { it.aux(lin, col) }.toTypedArray()
            )
            is Type.Func -> Type.Func(
                this.tk_.copy(lin_ = lin, col_ = col),
                Pair (
                    this.xscp1s.first?.copy(lin_ = lin, col_ = col),
                    this.xscp1s.second!!.map { it.copy(lin_ = lin, col_ = col) }.toTypedArray()
                ),
                this.xscp2s,
                this.inp.aux(lin, col),
                this.pub?.aux(lin, col),
                this.out.aux(lin, col)
            )
            is Type.Spawn -> Type.Spawn (
                this.tk_.copy(lin_ = lin, col_ = col),
                this.tsk.aux(lin, col) as Type.Func
            )
            is Type.Spawns -> TODO()
            is Type.Ptr -> Type.Ptr(
                this.tk_.copy(lin_ = lin, col_ = col),
                this.xscp1?.copy(lin_=lin,col_=col),
                this.xscp2,
                this.pln.aux(lin, col)
            )
            is Type.Rec -> Type.Rec(this.tk_.copy(lin_ = lin, col_ = col))
        }
    }
    return this.aux(lin,col).link(up)
}

fun Type.cloneX (up: Any, lin: Int, col: Int): Type {
    fun Type.aux (lin: Int, col: Int): Type {
        return when (this) {
            is Type.Unit -> Type.Unit(this.tk_.copy(lin_ = lin, col_ = col))
            is Type.Alias -> Type.Alias(this.tk_.copy(lin_ = lin, col_ = col))
            is Type.Nat -> Type.Nat(this.tk_.copy(lin_ = lin, col_ = col))
            is Type.Tuple -> Type.Tuple(
                this.tk_.copy(lin_ = lin, col_ = col),
                this.vec.map { it.aux(lin, col) }.toTypedArray()
            )
            is Type.Union -> Type.Union(
                this.tk_.copy(lin_ = lin, col_ = col),
                this.isrec,
                this.vec.map { it.aux(lin, col) }.toTypedArray()
            )
            is Type.Func -> Type.Func(
                this.tk_.copy(lin_ = lin, col_ = col),
                Pair (
                    this.xscp1s.first?.copy(lin_ = lin, col_ = col),
                    this.xscp1s.second!!.map { it.copy(lin_ = lin, col_ = col) }.toTypedArray()
                ),
                this.xscp2s,
                this.inp.aux(lin, col),
                this.pub?.aux(lin, col),
                this.out.aux(lin, col)
            )
            is Type.Spawn, is Type.Spawns -> TODO()
            is Type.Ptr -> Type.Ptr(this.tk_.copy(lin_ = lin, col_ = col), this.xscp1?.copy(lin_=lin,col_=col), this.xscp2, this.pln.aux(lin, col))
            is Type.Rec -> Type.Rec(this.tk_.copy(lin_ = lin, col_ = col))
        }
    }
    return this.aux(lin,col).linkX(up)
}

fun Type.isrec (): Boolean {
    return (this is Type.Union) && this.isrec
}

fun Type.noalias (): Type {
    return if (this is Type.Alias) this.env(this.tk_.str)!!.toType() else this
}

fun Type.containsRec (): Boolean {
    return when (this) {
        is Type.Alias -> TODO()
        is Type.Unit, is Type.Nat, is Type.Ptr, is Type.Func, is Type.Spawn, is Type.Spawns -> false
        is Type.Rec   -> true
        is Type.Tuple -> this.vec.any { it.containsRec() }
        is Type.Union -> this.vec.any { it.containsRec() }
    }
}

fun Type.Union.expand (): Array<Type> {
    val outer = this
    fun Type.aux (up: Int): Type {
        return when (this) {
            is Type.Rec   -> if (up == this.tk_.up) outer else { assert(up>this.tk_.up) ; this }
            is Type.Tuple -> Type.Tuple(this.tk_, this.vec.map { it.aux(up) }.toTypedArray())
            is Type.Union -> Type.Union(this.tk_, this.isrec, this.vec.map { it.aux(up+1) }.toTypedArray())
            is Type.Ptr   -> Type.Ptr(this.tk_, this.xscp1, this.xscp2, this.pln.aux(up))
            is Type.Func  -> Type.Func(this.tk_, this.xscp1s, this.xscp2s, this.inp.aux(up), this.pub?.aux(up), this.out.aux(up))
            else -> this
        }
    }
    return this.vec.map { it.aux(1).cloneX(this,this.tk.lin,this.tk.col) }.toTypedArray()
}

fun Type.toce (): String {
    return when (this) {
        is Type.Rec    -> "Rec"
        is Type.Unit   -> "Unit"
        is Type.Ptr    -> "P_" + this.pln.toce() + "_P"
        is Type.Alias  -> this.tk_.str
        is Type.Nat    -> this.tk_.src.replace('*','_')
        is Type.Tuple  -> "T_" + this.vec.map { it.toce() }.joinToString("_") + "_T"
        is Type.Union  -> "U_" + this.vec.map { it.toce() }.joinToString("_") + "_U"
        is Type.Func   -> "F_" + (if (this.tk.enu==TK.TASK) "TK_" else "") + (if (this.xscp1s.first!=null) "CLO_" else "") + this.inp.toce() + "_" + this.out.toce() + "_F"
        is Type.Spawn  -> "S_" + this.tsk.toce() + "_S"
        is Type.Spawns -> "SS_" + this.tsk.toce() + "_SS"
    }
}
