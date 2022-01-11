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

fun Type.isSupOf (sub: Type): Boolean {
    return this.isSupOf_(sub, false, emptyList(), emptyList())
}

fun Type.isSupOf_ (sub: Type, isproto: Boolean, ups1: List<Type.Union>, ups2: List<Type.Union>): Boolean {
    return when {
        (this is Type.Nat  || sub is Type.Nat) -> true
        (this is Type.Rec  && sub is Type.Rec)  -> (this.tk_.up == sub.tk_.up)
        (this is Type.Rec) -> ups1[this.tk_.up-1].let { it.isSupOf_(sub, isproto, ups1.drop(this.tk_.up),ups2) }
        (sub  is Type.Rec) -> ups2[sub.tk_.up-1].let { this.isSupOf_(it, isproto, ups1,ups2.drop(sub.tk_.up)) }
        (this::class != sub::class) -> false
        (this is Type.Unit && sub is Type.Unit) -> true
        (this is Type.Func && sub is Type.Func) -> { (
            this.clo1?.scope(this)?.depth == sub.clo1?.scope(sub)?.depth &&
            this.inp.isSupOf_(sub.inp,true,ups1,ups2) &&
            sub.inp.isSupOf_(this.inp,true,ups1,ups2) &&
            this.out.isSupOf_(sub.out,true,ups1,ups2) &&
            sub.out.isSupOf_(this.out,true,ups1,ups2)
        )}
        (this is Type.Ptr && sub is Type.Ptr) -> {
            /*
            println("===")
            println(this)
            println(sub)
            println(this.tostr())
            println(sub.tostr())
            println("SUPOF [$isproto] ${this.tk.lin}: ${this.scope()} = ${sub.scope()} /// ${this.scope}")
            */
            val ok = if (isproto) { // comparing func prototypes does not depend on scope calculation
                (this.scp1.lbl == sub.scp1.lbl) && (this.scp1.num == sub.scp1.num)
            } else {
                val dst = this.scope()
                val src = sub.scope()
                // (dthis.rel==dsub.rel): abs vs abs || rel vs rel // (no @aaa vs @1)
                // (dthis.level==dsub.level && dthis.rel==null): unless @aaa=@1 are in the same function (then always @1<=@aaa)
                when {
                    (src.depth == 0) -> true                          // global as source is always ok
                    (dst.arg == src.arg) -> (dst.depth >= src.depth)  // same abs/rel -> just checks depth
                    else -> (dst.depth >= src.depth) && (dst.lvl == src.lvl && dst.arg == null)
                        // diff abs/rel -> checks depth, func level, and destiny must not be arg
                }
            }
            ok && this.pln.isSupOf_(sub.pln,isproto,ups1,ups2)
        }
        (this is Type.Tuple && sub is Type.Tuple) ->
            (this.vec.size==sub.vec.size) && this.vec.zip(sub.vec).all { (x,y) -> x.isSupOf_(y,isproto,ups1,ups2) }
        (this is Type.Union && sub is Type.Union) -> {
            if ((this.isrec == sub.isrec) && (this.vec.size == sub.vec.size)) {
                // ok
            } else {
                return false
            }
            val pair = Pair(this.toString(),sub.toString())
            if (xxx.contains(pair)) {
                return true
            }
            xxx.add(pair)
            return this.vec.zip(sub.vec).all { (x,y) -> x.isSupOf_(y,isproto,listOf(this)+ups1,listOf(sub)+ups2) }
        }
        else -> false
    }
}
