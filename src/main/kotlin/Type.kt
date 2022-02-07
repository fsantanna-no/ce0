fun Type.tostr (): String {
    fun Tk.Id?.clo (): String {
        return if (this == null) "" else
            " @" + this.id + " ->"
    }
    return when (this) {
        is Type.Unit    -> "()"
        is Type.Alias   -> this.tk_.id + this.xscp1s.let { if (it.size==0) "" else " @["+it.map { it.id }.joinToString(",")+"]" }
        is Type.Nat     -> this.tk_.toce()
        is Type.Rec     -> "^".repeat(this.tk_.up)
        is Type.Pointer -> this.xscp1.let { "/" + this.pln.tostr() + " @" + it.id }
        is Type.Tuple   -> "[" + this.vec.map { it.tostr() }.joinToString(",") + "]"
        is Type.Union   -> "<" + this.vec.map { it.tostr() }.joinToString(",") + ">"
        is Type.Func    -> this.tk_.key + this.xscp1s.first.clo() + " @[" + this.xscp1s.second!!.map { it.id }.joinToString(",") + "] -> " + this.inp.tostr() + " -> " + this.pub.let { if (it == null) "" else it.tostr() + " -> " } + this.out.tostr()
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
        is Type.Pointer   -> listOf(this) + this.pln.flattenLeft()
    }
}

fun Type.clone (up: Any, lin: Int, col: Int): Type {
    fun Type.aux (lin: Int, col: Int): Type {
        return when (this) {
            is Type.Unit -> Type.Unit(this.tk_.copy(lin_ = lin, col_ = col))
            is Type.Alias -> Type.Alias(this.tk_.copy(lin_ = lin, col_ = col), this.xisrec, this.xscp1s, this.xscp2s)
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
                Triple (
                    this.xscp1s.first?.copy(lin_ = lin, col_ = col),
                    this.xscp1s.second.map { it.copy(lin_ = lin, col_ = col) }.toTypedArray(),
                    this.xscp1s.third
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
            is Type.Pointer -> Type.Pointer(
                this.tk_.copy(lin_ = lin, col_ = col),
                this.xscp1?.copy(lin_=lin,col_=col),
                this.xscp2,
                this.pln.aux(lin, col)
            )
            is Type.Rec -> Type.Rec(this.tk_.copy(lin_ = lin, col_ = col))
        }.let {
            it.wup  = up
            it.wenv = up.getEnv()
            it
        }
    }
    return this.aux(lin,col)
}

fun Type.cloneX (up: Any, lin: Int, col: Int): Type {
    fun Type.aux (lin: Int, col: Int): Type {
        return when (this) {
            is Type.Unit -> Type.Unit(this.tk_.copy(lin_ = lin, col_ = col))
            is Type.Alias -> Type.Alias(this.tk_.copy(lin_ = lin, col_ = col), this.xisrec, this.xscp1s, this.xscp2s)
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
                Triple (
                    this.xscp1s.first?.copy(lin_ = lin, col_ = col),
                    this.xscp1s.second!!.map { it.copy(lin_ = lin, col_ = col) }.toTypedArray(),
                    this.xscp1s.third
                ),
                this.xscp2s,
                this.inp.aux(lin, col),
                this.pub?.aux(lin, col),
                this.out.aux(lin, col)
            )
            is Type.Spawn, is Type.Spawns -> TODO()
            is Type.Pointer -> Type.Pointer(this.tk_.copy(lin_ = lin, col_ = col), this.xscp1?.copy(lin_=lin,col_=col), this.xscp2, this.pln.aux(lin, col))
            is Type.Rec -> Type.Rec(this.tk_.copy(lin_ = lin, col_ = col))
        }.let {
            it.wup  = up
            it.wenv = up.getEnv()
            it
        }
    }
    return this.aux(lin,col)
}

fun Type.isrec (): Boolean {
    return when (this) {
        is Type.Union -> this.isrec     // TODO: will be false, only alias can be rec
        is Type.Alias -> this.xisrec
        else -> false
    }
}

fun Type.noalias (): Type {
    return if (this !is Type.Alias) this else {
        //println(this.tk_.id)
        //println(this.env(this.tk_.id))
        val def = this.env(this.tk_.id)!! as Stmt.Typedef

        // Original constructor:
        //      typedef Pair @[a] = [/_int@a,/_int@a]
        //      var xy: Pair @[LOCAL] = [/x,/y]
        // Transform typedef -> type
        //      typedef Pair @[LOCAL] = [/_int@LOCAL,/_int@LOCAL]
        //      var xy: Pair @[LOCAL] = [/x,/y]

        def.toType().mapScps(this.tk,this, Pair(def.xscp1s.first, this.xscp1s.zip(this.xscp2s ?: emptyArray()).toTypedArray()), false)
    }
}

fun Type.containsRec (): Boolean {
    return when (this) {
        is Type.Alias -> TODO()
        is Type.Unit, is Type.Nat, is Type.Pointer, is Type.Func, is Type.Spawn, is Type.Spawns -> false
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
            is Type.Pointer   -> Type.Pointer(this.tk_, this.xscp1, this.xscp2, this.pln.aux(up))
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
        is Type.Pointer    -> "P_" + this.pln.toce() + "_P"
        is Type.Alias  -> this.tk_.id
        is Type.Nat    -> this.tk_.src.replace('*','_')
        is Type.Tuple  -> "T_" + this.vec.map { it.toce() }.joinToString("_") + "_T"
        is Type.Union  -> "U_" + this.vec.map { it.toce() }.joinToString("_") + "_U"
        is Type.Func   -> "F_" + (if (this.tk.enu==TK.TASK) "TK_" else "") + (if (this.xscp1s.first!=null) "CLO_" else "") + this.inp.toce() + "_" + this.out.toce() + "_F"
        is Type.Spawn  -> "S_" + this.tsk.toce() + "_S"
        is Type.Spawns -> "SS_" + this.tsk.toce() + "_SS"
    }
}

fun mismatch (sup: Type, sub: Type): String {
    return "type mismatch :\n    ${sup.tostr()}\n    ${sub.tostr()}"
}

// Original call:
//      var f: (func @[a1]->/()@a1->())
//      call f @[LOCAL] /x
// Map from f->call
//      { a1=(scp1(LOCAL),scp2(LOCAL) }
// Transform f->call
//      var f: (func @[LOCAL]->/()@LOCAL -> ())
//      call f @[LOCAL] /x
// Transform typedef -> type
//      typedef Pair @[LOCAL] = [/_int@LOCAL,/_int@LOCAL]
//      var xy: Pair @[LOCAL] = [/x,/y]
fun Type.mapScps (tk: Tk, up: Any, scps: Pair<Array<Tk.Id>, Array<Pair<Tk.Id,Scp2>>>, dofunc: Boolean): Type {
    val map: Map<String, Pair<Tk.Id, Scp2?>> = scps.first.map { it.id }.zip(scps.second).toMap()
    fun Type.aux (dofunc: Boolean): Type {
        return when (this) {
            is Type.Unit, is Type.Nat, is Type.Rec, is Type.Alias -> this
            is Type.Tuple -> Type.Tuple(this.tk_, this.vec.map { it.aux(dofunc) }.toTypedArray())
            is Type.Union -> Type.Union(this.tk_, this.isrec, this.vec.map { it.aux(dofunc) }.toTypedArray())
            is Type.Pointer -> {
                map[this.xscp1.id].let {
                    if (it == null) {
                        this
                    } else {
                        Type.Pointer(this.tk_, it.first, it.second, this.pln.aux(dofunc))
                    }
                }
            }
            is Type.Func -> if (!dofunc) this else {
                val ret = this.xscp1s.first?.let { me ->
                    map[me.id].let { it ?: Pair(this.xscp1s.first, this.xscp2s!!.first) }
                }
                Type.Func(
                    this.tk_,
                    Triple(ret?.first, this.xscp1s.second, this.xscp1s.third),
                    Pair(ret?.second, this.xscp2s!!.second),
                    this.inp.aux(dofunc),
                    this.pub?.aux(dofunc),
                    this.out.aux(dofunc)
                )
            }
            is Type.Spawn, is Type.Spawns -> TODO()
        }
    }
    return this.aux(dofunc).clone(up,tk.lin,tk.col)
}