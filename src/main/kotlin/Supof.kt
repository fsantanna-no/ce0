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
    return this.isSupOf_(sub, true, emptyList(), emptyList())
}

fun Type.isSupOf_ (sub: Type, depth: Boolean, ups1: List<Type.Union>, ups2: List<Type.Union>): Boolean {
    return when {
        (this is Type.Any  || sub is Type.Any) -> true
        (this is Type.Nat  || sub is Type.Nat) -> (sub !is Type.UCons)
        (this is Type.Rec  && sub is Type.Rec)  -> (this.tk_.up == sub.tk_.up)
        (this is Type.Rec) -> ups1[this.tk_.up-1].let { it.isSupOf_(sub, depth, ups1.drop(this.tk_.up),ups2) }
        (sub  is Type.Rec) -> ups2[sub.tk_.up-1].let { this.isSupOf_(it, depth, ups1,ups2.drop(sub.tk_.up)) }
        (this is Type.Ptr && sub is Type.UCons) -> {
            (sub.tk_.num==0 && sub.arg is Type.Unit && (this.pln is Type.Rec || this.pln.isrec()))
        }
        (this is Type.Union && sub is Type.UCons) -> {
            when {
                (sub.tk_.num == 0) -> this.isrec && sub.arg is Type.Unit
                (this.vec.size < sub.tk_.num) -> false
                else -> {
                    // TODO: use this for sub??
                    this.vec[sub.tk_.num-1].isSupOf_(sub.arg, depth, listOf(this)+ups1, listOf(this)+ups2)
                }
            }
        }
        (this::class != sub::class) -> false
        (this is Type.Unit && sub is Type.Unit) -> true
        (this is Type.Func && sub is Type.Func) -> { (
            this.inp.isSupOf_(sub.inp,false,ups1,ups2) &&
            sub.inp.isSupOf_(this.inp,false,ups1,ups2) &&
            this.out.isSupOf_(sub.out,false,ups1,ups2) &&
            sub.out.isSupOf_(this.out,false,ups1,ups2)
        ) }
        (this is Type.Ptr && sub is Type.Ptr) -> {
            //println("SUPOF [$depth] ${this.tk.lin}: ${this.scope()} = ${sub.scope()}")
            val ok = if (depth) {
                val dthis = this.scope()
                val dsub  = sub.scope()
                // (dthis.isbas==dsub.isabs): abs vs abs || rel vs rel // (no @aaa vs @1)
                // (dthis.level==dsub.level): unless @aaa=@1 are in the same function (then always @1<=@aaa)
                // (dsub.depth == 0):         globals as source are always ok
                ((dthis.isabs==dsub.isabs || dthis.level==dsub.level || dsub.depth==0) && dthis.depth>=dsub.depth)
            } else {
                this.scope.scp == sub.scope.scp // comparing func prototypes does not depend on scope calculation
            }
            //println(ok)
            ok && this.pln.isSupOf_(sub.pln,depth,ups1,ups2)
        }
        (this is Type.Tuple && sub is Type.Tuple) ->
            (this.vec.size==sub.vec.size) && this.vec.zip(sub.vec).all { (x,y) -> x.isSupOf_(y,depth,ups1,ups2) }
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
            return this.vec.zip(sub.vec).all { (x,y) -> x.isSupOf_(y,depth,listOf(this)+ups1,listOf(sub)+ups2) }
        }
        else -> false
    }
}
