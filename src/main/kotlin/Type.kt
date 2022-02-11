fun Type.tostr (): String {
    fun Scope?.clo (): String {
        return if (this == null) "" else
            " @" + this.scp1.id + " ->"
    }
    return when (this) {
        is Type.Unit    -> "()"
        is Type.Alias   -> this.tk_.id + this.xscps!!.let { if (it.size==0) "" else " @["+it.map { it.scp1.id }.joinToString(",")+"]" }
        is Type.Nat     -> this.tk_.toce()
        is Type.Pointer -> this.xscp!!.let { "/" + this.pln.tostr() + " @" + it.scp1.id }
        is Type.Tuple   -> "[" + this.vec.map { it.tostr() }.joinToString(",") + "]"
        is Type.Union   -> "<" + this.vec.map { it.tostr() }.joinToString(",") + ">"
        is Type.Func    -> this.tk_.key + this.xscps.first.clo() + " @[" + this.xscps.second!!.map { it.scp1.id }.joinToString(",") + "] -> " + this.inp.tostr() + " -> " + this.pub.let { if (it == null) "" else it.tostr() + " -> " } + this.out.tostr()
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
        is Type.Unit, is Type.Nat, is Type.Alias -> listOf(this)
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
            is Type.Alias -> Type.Alias(this.tk_.copy(lin_ = lin, col_ = col), this.xisrec, this.xscps)
            is Type.Nat -> Type.Nat(this.tk_.copy(lin_ = lin, col_ = col))
            is Type.Tuple -> Type.Tuple(
                this.tk_.copy(lin_ = lin, col_ = col),
                this.vec.map { it.aux(lin, col) }
            )
            is Type.Union -> Type.Union(
                this.tk_.copy(lin_ = lin, col_ = col),
                this.vec.map { it.aux(lin, col) }
            )
            is Type.Func -> Type.Func(
                this.tk_.copy(lin_ = lin, col_ = col),
                Triple (
                    this.xscps.first.let { if (it==null) null else Scope(it.scp1.copy(lin_ = lin, col_ = col), it.scp2) },
                    this.xscps.second?.map { Scope(it.scp1.copy(lin_ = lin, col_ = col), it.scp2) },
                    this.xscps.third
                ),
                this.inp.aux(lin, col),
                this.pub?.aux(lin, col),
                this.out.aux(lin, col)
            )
            is Type.Spawn -> Type.Spawn (
                this.tk_.copy(lin_ = lin, col_ = col),
                this.tsk.aux(lin, col) as Type.Func
            )
            is Type.Spawns -> Type.Spawns (
                this.tk_.copy(lin_ = lin, col_ = col),
                this.tsk.aux(lin, col) as Type.Func
            )
            is Type.Pointer -> Type.Pointer(
                this.tk_.copy(lin_ = lin, col_ = col),
                Scope(this.xscp!!.scp1.copy(lin_=lin,col_=col), this.xscp!!.scp2),
                this.pln.aux(lin, col)
            )
        }.let {
            it.wup  = up
            it.wenv = up.getEnv()
            it
        }
    }
    return this.aux(lin,col)
}

fun Type.isrec (): Boolean {
    return (this is Type.Alias && this.xisrec)
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

        def.toType().mapScps (
            this.tk,
            this,
            Pair(def.xscp1s.first!!, this.xscps!!),
            false
        )
    }
}

fun Type.containsRec (): Boolean {
    return when (this) {
        is Type.Alias -> TODO()
        is Type.Unit, is Type.Nat, is Type.Pointer, is Type.Func, is Type.Spawn, is Type.Spawns -> false
        is Type.Tuple -> this.vec.any { it.containsRec() }
        is Type.Union -> this.vec.any { it.containsRec() }
    }
}

fun Type.toce (): String {
    return when (this) {
        is Type.Unit   -> "Unit"
        is Type.Pointer    -> "P_" + this.pln.toce() + "_P"
        is Type.Alias  -> this.tk_.id
        is Type.Nat    -> this.tk_.src.replace('*','_')
        is Type.Tuple  -> "T_" + this.vec.map { it.toce() }.joinToString("_") + "_T"
        is Type.Union  -> "U_" + this.vec.map { it.toce() }.joinToString("_") + "_U"
        is Type.Func   -> "F_" + (if (this.tk.enu==TK.TASK) "TK_" else "") + (if (this.xscps.first!=null) "CLO_" else "") + this.inp.toce() + "_" + this.out.toce() + "_F"
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
fun Type.mapScps (tk: Tk, up: Any, scps: Pair<List<Tk.Id>, List<Scope>>, dofunc: Boolean): Type {
    // from = scps.first
    // to   = scps.second
    val map: Map<String, Scope> = scps.first.map { it.id }.zip(scps.second).toMap()
    fun Type.aux (dofunc: Boolean): Type {
        return when (this) {
            is Type.Unit, is Type.Nat -> this
            is Type.Alias -> {
                // TODO: this works if Alias is the same as enclosing call, what if it is not?
                Type.Alias(this.tk_, this.xisrec, scps.second)
            }
            is Type.Tuple -> Type.Tuple(this.tk_, this.vec.map { it.aux(dofunc) })
            is Type.Union -> Type.Union(this.tk_, this.vec.map { it.aux(dofunc) })
            is Type.Pointer -> {
                map[this.xscp!!.scp1.id].let {
                    if (it == null) {
                        this
                    } else {
                        Type.Pointer(this.tk_, it, this.pln.aux(dofunc))
                    }
                }
            }
            is Type.Func -> if (!dofunc) this else {
                Type.Func(
                    this.tk_,
                    Triple (
                        this.xscps.first?.let { map[it.scp1.id] },
                        this.xscps.second,
                        this.xscps.third
                    ),
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