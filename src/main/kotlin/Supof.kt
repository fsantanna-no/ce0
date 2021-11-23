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
    return this.isSupOf_(sub, emptyList(), emptyList())
}

fun Type.Ptr.scopeDepth (): Int {
    return when (this.scope) {
        null -> this.ups_tolist().count { it is Stmt.Block }
        '@' -> 0
        else -> TODO("scope ${this.scope}")
    }
}

fun Type.isSupOf_ (sub: Type, ups1: List<Type>, ups2: List<Type>): Boolean {
    return when {
        (this is Type.Any  || sub is Type.Any) -> true
        (this is Type.Nat  || sub is Type.Nat) -> true
        (this is Type.Rec  && sub is Type.Rec)  -> (this.tk_.up == sub.tk_.up)
        (this is Type.Rec) -> ups1[this.tk_.up-1].let { it.isSupOf_(sub, listOf(it)+ups1,ups2) }
        (sub  is Type.Rec) -> ups2[sub.tk_.up-1].let { this.isSupOf_(it,ups1, listOf(it)+ups2) }
        (this is Type.Union && sub is Type.UCons) -> {
            if (sub.tk_.num == 0) {
                this.isnull && sub.arg is Type.Unit
            } else {
                this.vec[sub.tk_.num-1].isSupOf_(sub.arg, listOf(this)+ups1, ups2)
            }
        }
        (this::class != sub::class) -> false
        (this is Type.Unit && sub is Type.Unit) -> true
        (this is Type.Func && sub is Type.Func) -> (this.inp.isSupOf_(sub.inp,ups1,ups2) && sub.inp.isSupOf_(this.inp,ups1,ups2) && this.out.isSupOf_(sub.out,ups1,ups2) && sub.out.isSupOf_(this.out,ups1,ups2))
        (this is Type.Ptr && sub is Type.Ptr) -> {
            println(sub.pln)
            println(sub.pln.ups_tolist())
            (this.scopeDepth() >= sub.scopeDepth()) && this.pln.isSupOf_(sub.pln,ups1,ups2)
        }
        (this is Type.Tuple && sub is Type.Tuple) ->
            (this.vec.size==sub.vec.size) && this.vec.zip(sub.vec).all { (x,y) -> x.isSupOf_(y,listOf(this)+ups1,listOf(sub)+ups2) }
        (this is Type.Union && sub is Type.Union) -> {
            if ((this.isnull == sub.isnull) && (this.vec.size == sub.vec.size)) {
                // ok
            } else {
                return false
            }
            val pair = Pair(this.toString(),sub.toString())
            if (xxx.contains(pair)) {
                return true
            }
            xxx.add(pair)
            return this.vec.zip(sub.vec).all { (x,y) -> x.isSupOf_(y,listOf(this)+ups1,listOf(sub)+ups2) }
        }
        else -> false
    }
}
