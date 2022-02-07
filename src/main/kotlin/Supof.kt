// Are these types the same?
// <(),<(),^^>> = <(),<(),<(),^^>>>
//
// They have the same prefixes (1,2):
// <(),<(),^^>> = <(),<(),<(),^^>>>
//   1  2  ?       1   2  ?
// So, is `?` the same in both types?
//
// We can remove the parts in common:
// ^^ = <(),^^>
//
// To answer if they are the same, we want to find an expansion that makes them different.
// If we cannot find a conflicting expansion and we converge to tested/non-conflicting types only,
// then we can say that the types are the same.
//
// We expand the simplified types above:
// <(),<(),^^>> = <(),<(),<(),^^>>>
//
// We now reached the same initial state:
// - We could not find a conflicting expansion.
// - We tested all possible expansions.
// - All prefixes in the expansions matched.
//
// Hence, the types are equivalent.

val xxx: MutableSet<Pair<String,String>> = mutableSetOf()

// Convert function signatures to increasing scopes for comparison
// var g: func @[] -> {@i,@j,@k} -> [/</^@i>@i,/</^@j>@j] -> /</^@k>@k
//      becomes
// var g: func @[] -> {@a,@b,@c} -> [/</^@a>@a,/</^@b>@b] -> /</^@c>@c
fun Type.Func.mapLabels (up: Any): Type.Func {
    val fst = this.xscp1s.first.let { if (it==null) emptyList() else listOf(it) }
    val snd = this.xscp1s.second.map { it }
    val scps: List<String> = (fst + snd).map { it.id }
    val MAP: Map<String, String> = scps.zip((1..scps.size).map { 'a'+it-1+"" }).toMap()
    fun Type.aux (): Type {
        return when (this) {
            is Type.Spawn, is Type.Spawns -> TODO()
            is Type.Unit, is Type.Nat, is Type.Alias -> this
            is Type.Tuple   -> Type.Tuple(this.tk_, this.vec.map { it.aux() }.toTypedArray())
            is Type.Union   -> Type.Union(this.tk_, this.vec.map { it.aux() }.toTypedArray())
            is Type.Func    -> this
            is Type.Pointer -> this.xscp1.let {
                val id = MAP[it.id]
                if (id == null) {
                    this
                } else {
                    val tk = Tk.Id(TK.XID, it.lin, it.col, id)
                    Type.Pointer(this.tk_, tk, this.xscp2!!.copy(par=it.id), this.pln.aux())
                }
            }
        }
    }
    return Type.Func (
        this.tk_,
        this.xscp1s, // TODO: xscp1s/xscp2s are not used in supOf, so we ignore them here
        this.xscp2s,
        this.inp.aux(),
        this.pub?.aux(),
        this.out.aux()
    ).clone(up,this.tk.lin,this.tk.col) as Type.Func
}

fun Scp2.isNestIn (sub: Scp2, up: Any): Boolean {
    val bothcst = (this.par==null && sub.par==null)
    val bothpar = (this.par!=null && sub.par!=null)

    //println(this.xscp1)
    //println(sub.xscp1)
    //println(bothcst)
    //println(bothpar)
    //println(this)
    //println(sub)
    //println(this.ups_tolist())

    return when {
        (sub.par==null && sub.depth==0) -> true           // global as source is always ok
        bothcst -> (this.depth!! >= sub.depth!!)
        bothpar -> this.par!! == sub.par!! || (up.ups_first { it is Expr.Func } as Expr.Func).let {
            // look for (this.id > sub.id) in constraints
            it.type.xscp1s.third.any { it.first==this.par!! && it.second==sub.par!! }
        }
        else -> (sub.par!=null && this.lvl==sub.lvl)
        // diff abs/rel -> this must be par and bot must be at the same lvl
    }
}

fun Type.isSupOf (sub: Type, isproto: Boolean=false): Boolean {
    return when {
        (this is Type.Nat  || sub is Type.Nat) -> true
        (this is Type.Spawn && sub is Type.Spawns) -> this.tsk.isSupOf(sub.tsk)
        (this is Type.Alias || sub is Type.Alias) -> {
            when {
                (this is Type.Alias && sub is Type.Alias) -> this.tk_.id == sub.tk_.id
                (this is Type.Alias) -> (this.noalias()).isSupOf(sub)
                (sub  is Type.Alias) -> this.isSupOf(sub.noalias())
                else -> error("bug found")
            }
        }
        (this::class != sub::class) -> false
        (this is Type.Unit && sub is Type.Unit) -> true
        (this is Type.Func && sub is Type.Func) -> {
            val sup2 = this.mapLabels(this.wup!!)
            val sub2 = sub.mapLabels(sub.wup!!)
            (
                sup2.xscp2s!!.first?.depth == sub2.xscp2s!!.first?.depth &&
                sup2.inp.isSupOf(sub2.inp,true) &&
                sub2.inp.isSupOf(sup2.inp,true) &&
                sup2.out.isSupOf(sub2.out,true) &&
                sub2.out.isSupOf(sup2.out,true) &&
                (
                    (sup2.pub==null && sub2.pub==null) ||
                    ( sup2.pub!=null && sub2.pub!=null &&
                      sup2.pub.isSupOf(sub2.pub,true) &&
                      sub2.pub.isSupOf(sup2.pub,true) )
                )
            )
        }
        (this is Type.Pointer && sub is Type.Pointer) -> {
            /*
            println("===")
            println(this)
            println(sub)
            println(this.tostr())
            println(sub.tostr())
            //println("SUPOF [$isproto] ${this.tk.lin}: ${this.scope()} = ${sub.scope()} /// ${this.scope}")
            */
            val ok = if (isproto) { // comparing func prototypes does not depend on scope calculation
                (this.xscp1.id == sub.xscp1.id)
            } else {
                this.xscp2!!.isNestIn(sub.xscp2!!, this)
            }
            ok && this.pln.isSupOf(sub.pln,isproto)
        }
        (this is Type.Tuple && sub is Type.Tuple) ->
            (this.vec.size==sub.vec.size) && this.vec.zip(sub.vec).all { (x,y) -> x.isSupOf(y,isproto) }
        (this is Type.Union && sub is Type.Union) -> {
            if ((this.vec.size == sub.vec.size)) {
                // ok
            } else {
                return false
            }
            val pair = Pair(this.toString(),sub.toString())
            if (xxx.contains(pair)) {
                return true
            }
            xxx.add(pair)
            return this.vec.zip(sub.vec).all { (x,y) -> x.isSupOf(y,isproto) }
        }
        else -> false
    }
}
