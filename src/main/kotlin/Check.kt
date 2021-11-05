fun env_prelude (s: Stmt): Stmt {
    val stdo = Stmt.Var (
        Tk.Str(TK.XVAR,1,1,"output_std"),
        false, true,
        Type.Func (
            Tk.Sym(TK.ARROW, 1, 1, "->"),
            Type.Any(Tk.Chr(TK.CHAR,1,1,'?')),
            Type.Unit(Tk.Sym(TK.UNIT,1,1,"()"))
        ),
        XExpr.None(Expr.Unk(Tk.Chr(TK.CHAR,s.tk.lin,s.tk.col,'?')))
    )
    return Stmt.Seq(stdo.tk, stdo, s)
}

fun check_dcls (s: Stmt) {
    fun fs (s: Stmt) {
        when (s) {
            is Stmt.Var -> {
                val dcl = s.env(s.tk_.str)
                All_assert_tk(s.tk, dcl == null || dcl.tk_.str in arrayOf("arg", "_ret_")) {
                    "invalid declaration : \"${s.tk_.str}\" is already declared (ln ${dcl!!.tk.lin})"
                }
            }
            is Stmt.Ret -> {
                val ok = s.ups_tolist().firstOrNull { it is Expr.Func } != null
                All_assert_tk(s.tk, ok) {
                    "invalid return : no enclosing function"
                }
            }
        }
    }
    s.visit(::fs, null, null, null)
}

fun Type.containsRec (): Boolean {
    return when (this) {
        is Type.None, is Type.Any, is Type.Unit, is Type.Nat, is Type.Ptr, is Type.Func -> false
        is Type.Rec   -> true
        is Type.Tuple -> this.vec.any { it.containsRec() }
        is Type.Union -> this.vec.any { it.containsRec() }
        is Type.UCons -> this.arg.containsRec()
    }
}

fun Type.exactlyRec (): Boolean {
    return (this is Type.Union) && this.isrec
}

fun Type.containsFunc (): Boolean {
    return when (this) {
        is Type.None, is Type.Any, is Type.Unit, is Type.Nat, is Type.Ptr, is Type.Rec -> false
        is Type.Func  -> true
        is Type.Tuple -> this.vec.any { it.containsFunc() }
        is Type.Union -> this.vec.any { it.containsFunc() }
        is Type.UCons -> this.arg.containsFunc()
    }
}

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

fun Type.isSupOf_ (sub: Type, ups1: List<Type>, ups2: List<Type>): Boolean {
    //println(this.tostr() + " = " + sub.tostr())
    return when {
        (this is Type.None || sub is Type.None) -> false
        (this is Type.Any  || sub is Type.Any) -> true
        (this is Type.Nat  || sub is Type.Nat) -> true
        (this is Type.Rec  && sub is Type.Rec)  -> (this.tk_.up == sub.tk_.up)
        (this is Type.Rec) -> ups1[this.tk_.up-1].isSupOf_(sub,ups1,ups2)
        (sub  is Type.Rec) -> this.isSupOf_(ups2[sub.tk_.up-1],ups1,ups2)
        (this is Type.Union && sub is Type.UCons) -> {
            if (sub.tk_.num == 0) {
                this.exactlyRec() && this.isnull && sub.arg is Type.Unit
            } else {
                //println(">>> ${this.expand().vec[sub.tk_.num-1].tostr()} = ${sub.arg.tostr()}")
                //println(">>> ${this.expand().vec[sub.tk_.num-1].expand().tostr()} = ${sub.arg.expand().tostr()}")
                this.vec[sub.tk_.num-1].isSupOf_(sub.arg, listOf(this)+ups1, ups2)
            }
        }
        (this::class != sub::class) -> false
        (this is Type.Unit && sub is Type.Unit) -> true
        (this is Type.Func && sub is Type.Func) -> (this.inp.isSupOf_(sub.inp,ups1,ups2) && sub.inp.isSupOf_(this.inp,ups1,ups2) && this.out.isSupOf_(sub.out,ups1,ups2) && sub.out.isSupOf_(this.out,ups1,ups2))
        (this is Type.Ptr && sub is Type.Ptr) -> this.pln.isSupOf_(sub.pln,ups1,ups2)
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

fun Type.ishasptr (): Boolean {
    return when (this) {
        is Type.None, is Type.Any, is Type.Unit, is Type.Nat, is Type.Func, is Type.Rec -> false
        is Type.Ptr   -> true
        is Type.Tuple -> this.vec.any { it.ishasptr() }
        is Type.Union -> this.vec.any { it.ishasptr() }
        is Type.UCons  -> this.arg.ishasptr()
    }
}

fun Expr.isconst (): Boolean {
    return when (this) {
        is Expr.Unit, is Expr.Unk, is Expr.Call, is Expr.TCons, is Expr.UCons, is Expr.UPred, is Expr.Func -> true
        is Expr.Var, is Expr.Nat, is Expr.Dnref -> false
        is Expr.TDisc -> this.tup.isconst()
        is Expr.UDisc -> this.uni.isconst()
        is Expr.Upref -> this.pln.isconst()
    }
}

fun check_xexprs (S: Stmt) {
    fun fx (xe: XExpr) {
        val xp = XPS[xe.e]!!
        val xp_ctrec = xp.containsRec()
        val xp_exrec = xp.exactlyRec()
        val e_iscst  = xe.e.isconst()
        val e_isvar  = xe.e is Expr.UCons
        val e_isnil  = e_isvar && ((xe.e as Expr.UCons).tk_.num==0)

        val is_ptr_to_udisc = (xp is Type.Ptr) && xe.e.containsUDisc() //xp.pln.containsUDisc() && (xe.e !is Expr.Unk) && (xe.e !is Expr.Nat)
        val is_ptr_to_ctrec = (xp is Type.Ptr) && xp.pln.containsRec() && (xe.e !is Expr.Unk) && (xe.e !is Expr.Nat)
        val is_ptr_to_hold  = (xp is Type.Ptr) && xp.pln.exactlyRec() && (xp.pln as Type.Union).ishold

        when (xe) {
            is XExpr.None -> All_assert_tk(xe.e.tk, !is_ptr_to_udisc && !is_ptr_to_ctrec && (!xp_ctrec || (e_iscst && (!e_isvar||!xp_exrec||e_isnil)))) {
                val op = when {
                    is_ptr_to_hold -> "`hold` "
                    (is_ptr_to_udisc || is_ptr_to_ctrec) && !is_ptr_to_hold -> "`borrow` "
                    e_iscst -> "`new` "
                    else -> ""
                }
                "invalid expression : expected " + op + "operation modifier"
            }
            is XExpr.Borrow -> All_assert_tk(xe.e.tk, (is_ptr_to_udisc||is_ptr_to_ctrec) && !is_ptr_to_hold) {
                "invalid `borrow` : expected pointer to recursive variable"
            }
            is XExpr.Hold -> All_assert_tk(xe.e.tk, is_ptr_to_hold) {
                "invalid `hold` : expected pointer to recursive variable"
            }
            is XExpr.Copy -> All_assert_tk(xe.e.tk, xp_ctrec && !e_iscst) {
                "invalid `copy` : expected recursive variable"
            }
            is XExpr.Replace -> All_assert_tk(xe.e.tk, xp_exrec && !e_iscst) {
                "invalid `replace` : expected recursive variable"
            }
            is XExpr.Consume -> All_assert_tk(xe.e.tk, xp_exrec && !e_iscst) {
                "invalid `consume` : expected recursive variable"
            }
            is XExpr.New -> All_assert_tk(xe.e.tk, xp_exrec && e_isvar && !e_isnil) {
                "invalid `new` : expected variant constructor"
            }
        }
    }
    S.visit(null, ::fx, null, null)
}

fun Stmt.getDepth (): Int {
    return if (this is Stmt.Var && this.isout) {
        this.env("arg")!!.getDepth()
    } else {
        this.ups_tolist().count { it is Stmt.Block }
    }
}

fun Expr.getDepth (caller: Int, hold: Boolean): Pair<Int,Stmt.Var?> {
    return when (this) {
        is Expr.Unk, is Expr.Unit, is Expr.Nat, is Expr.UPred, is Expr.Func -> Pair(0, null)
        is Expr.Var -> {
            val dcl = this.env()!!
            return if (hold) Pair(dcl.getDepth(), dcl) else Pair(0, null)
        }
        is Expr.Upref ->  {
            val (depth,dcl) = this.pln.getDepth(caller, hold)
            if (dcl is Stmt.Var) {
                val inc = if (dcl.tk_.str == "arg" || dcl.isout) 1 else 0
                Pair(depth + inc, dcl)
            } else {
                Pair(depth, dcl)
            }
        }
        is Expr.Dnref -> this.ptr.getDepth(caller, hold)
        is Expr.TDisc -> this.tup.getDepth(caller, hold)
        is Expr.UDisc -> this.uni.getDepth(caller, hold)
        is Expr.Call -> this.f.toType().let {
            when (it) {
                is Type.Nat -> Pair(0, null)
                is Type.Func -> if (!it.out.ishasptr()) Pair(0,null) else {
                    // substitute args depth/id by caller depth/id
                    Pair(caller, (this.f as Expr.Var).env())
                }
                else -> error("bug found")
            }
        }
        is Expr.TCons -> this.arg.map { it.e.getDepth(caller,it.e.toType().ishasptr()) }.maxByOrNull { it.first }!!
        is Expr.UCons -> this.arg.e.getDepth(caller, hold)
    }
}

fun check_pointers (S: Stmt) {
    fun fs (s: Stmt) {
        when (s) {
            is Stmt.Var -> {
                val dst_depth = s.getDepth()
                val (src_depth, src_dcl) = s.src.e.getDepth(dst_depth, s.type.ishasptr())
                All_assert_tk(s.tk, dst_depth >= src_depth) {
                    "invalid assignment : cannot hold local pointer \"${src_dcl!!.tk_.str}\" (ln ${src_dcl!!.tk.lin})"
                }
            }
            is Stmt.Set -> {
                val set_depth = s.getDepth()
                val (src_depth, src_dcl) = s.src.e.getDepth(set_depth, s.dst.toType().ishasptr())
                All_assert_tk(s.tk, s.dst.getDepth(set_depth, s.dst.toType().ishasptr()).first >= src_depth) {
                    "invalid assignment : cannot hold local pointer \"${src_dcl!!.tk_.str}\" (ln ${src_dcl!!.tk.lin})"
                }
            }
        }
    }
    S.visit(::fs, null, null, null)
}

fun<T> Set<Set<T>>.unionAll (): Set<T> {
    return this.fold(emptySet(), {x,y->x+y})
}

fun Expr.leftMosts (xe: XExpr?): Set<Pair<XExpr?,Expr.Var>> {
    return when (this) {
        is Expr.Var -> setOf(Pair(xe,this))
        is Expr.TDisc -> this.tup.leftMosts(xe)
        is Expr.UDisc -> this.uni.leftMosts(xe)
        is Expr.Dnref -> this.ptr.leftMosts(xe)
        is Expr.Upref -> this.pln.leftMosts(xe)
        is Expr.TCons -> this.arg.map { it.e.leftMosts(it) }.toSet().unionAll()
        is Expr.UCons -> this.arg.e.leftMosts((this.arg))
        is Expr.Call  -> emptySet() //TODO("may return args")
        else -> emptySet()
    }
}

fun Expr.leftMost (): Expr.Var? {
    return when (this) {
        is Expr.Var -> this
        is Expr.TDisc -> this.tup.leftMost()
        is Expr.UDisc -> this.uni.leftMost()
        is Expr.Dnref -> this.ptr.leftMost()
        is Expr.Upref -> this.pln.leftMost()
        else -> null
    }
}

fun Expr.containsUDisc (): Boolean {
    return when (this) {
        is Expr.UDisc -> true
        is Expr.TDisc -> this.tup.containsUDisc()
        is Expr.Dnref -> this.ptr.containsUDisc()
        is Expr.Upref -> this.pln.containsUDisc()
        else          -> false
    }
}

fun Type.containsUnion (): Boolean {
    return when (this) {
        is Type.Union -> true
        is Type.Tuple -> this.vec.any { it.containsUnion() }
        is Type.Ptr   -> this.pln.containsUnion()
        else          -> false
    }
}

fun check_borrows_consumes_holds (S: Stmt) {
    class State: IState {
        // y = borrow \x
        // z = borrow y
        // bws[x] = { y,z }
        // bws[y] = { z }
        var bws: MutableMap<Stmt.Var,MutableSet<Pair<Stmt.Var,Int>>> = mutableMapOf()
        var cns: MutableMap<Stmt.Var,Expr.Var> = mutableMapOf()

        // f = func A ...
        // g = func B ...
        // f = g
        // fs[f] = { A,B }
        var fcs: MutableMap<Stmt.Var,MutableSet<Expr.Func>> = mutableMapOf()

        fun chk_bw (env: Set<Stmt>, s: Stmt.Var, tk: Tk, err: String) {
            if (this.bws[s] != null) {
                val ok = this.bws[s]!!.map { it.first }.intersect(env).isEmpty()  // no borrow is on scope
                //val ok = this.bws[s]!!.isEmpty()  // no borrow is on scope
                val ln = this.bws[s]!!.first().second
                All_assert_tk(tk, ok) { err + " : borrowed in line $ln" }
            }
        }

        fun chk_cn (s: Stmt.Var, e: Expr.Var?, tk: Tk, err: String) {
            All_assert_tk(tk, !this.cns.contains(s) || this.cns[s]==e) { err + " : consumed in line ${this.cns[s]!!.tk.lin}" }
        }

        fun bws_cns_add (dst: Stmt.Var, xsrc: XExpr) {
            for ((xe,lf) in xsrc.e.leftMosts(xsrc)) {
                if (xe is XExpr.Borrow) {
                    val src = lf.env() as Stmt.Var
                    if (this.bws[src] == null) {
                        this.bws[src] = mutableSetOf()
                    }
                    this.bws[src]!!.add(Pair(dst, xsrc.e.tk.lin))
                    this.bws[src]!!.addAll(if (this.bws[dst] == null) emptySet() else this.bws[dst]!!)
                }
                if (xe is XExpr.Consume) {
                    // x = consume y
                    val src = lf.env() as Stmt.Var
                    this.cns[src] = lf // <- y consumed, all bws containing y are also consumed
                    this.bws.filterValues { it.map { it.first }.contains(src) }.keys.forEach {
                        this.cns[it] = lf
                    }
                }
            }
        }

        override fun copy (): State {
            val new = State()
            new.bws = this.bws.toMutableMap()
            new.cns = this.cns.toMutableMap()
            new.fcs = this.fcs.toMutableMap()
            return new
        }
    }

    fun fx (xe: XExpr, st: IState) {
        if (xe is XExpr.Replace || xe is XExpr.Consume) {
            val lf = xe.e.leftMost()!!
            val s = lf.env() as Stmt.Var
            (st as State).chk_bw(lf.env_toset(), s, xe.e.tk, "invalid operation on \"${lf.tk_.str}\"")
        }
    }

    fun fs (s: Stmt, st_: IState) {
        val st = st_ as State
        fun fs_add (dst: Stmt.Var, src: Expr) {
            if (st.fcs[dst] == null) {
                st.fcs[dst] = mutableSetOf()
            }
            when (src) {
                is Expr.Unk, is Expr.Nat -> {} // ok
                is Expr.Func -> st.fcs[dst]!!.add(src)
                else -> src.leftMosts(null)
                    .map { it.second.env()!! }
                    .forEach {
                        // TODO: should substitute instead of addAll (but ifs...)
                        st.fcs[dst]!!.addAll(if (st.fcs[it] == null) emptySet() else st.fcs[it]!!)
                    }
            }
        }
        when (s) {
            is Stmt.Var -> {
                if (s.type is Type.Func) {
                    fs_add(s, s.src.e)
                } else {
                    st.bws_cns_add(s, s.src)
                }
            }
            is Stmt.Set -> {
                val lf = s.dst.leftMost()!!
                val dcl = lf.env(lf.tk_.str)!!

                val tp = s.dst.toType()
                if (tp is Type.Func) {
                    fs_add(dcl, s.src.e)
                } else {
                    //if (s.src.x!=null && s.src.x.enu==TK.BORROW) {
                    val isptruni = tp.containsUnion() || (tp is Type.Ptr && tp.pln.containsUnion())
                    val isptrrec = tp.containsRec()   || (tp is Type.Ptr && tp.pln.containsRec())
                    if (isptruni || isptrrec) {
                        st.bws_cns_add(dcl, s.src)
                        st.chk_bw(lf.env_toset(), dcl, s.tk, "invalid assignment of \"${dcl.tk_.str}\"")
                    }
                }

                if (s.dst is Expr.Var) {
                    st.cns.remove(dcl)
                } else {
                    st.chk_cn(dcl, null, s.tk, "invalid assignment of \"${dcl.tk_.str}\"")
                    if (tp is Type.Union && tp.ishold) {
                        All_assert_tk(s.tk, tp.containsUnion()) { "TODO" }
                    }
                }
            }
        }
    }

    fun fe (e: Expr, st_: IState) {
        val st = st_ as State
        when (e) {
            is Expr.Var -> {
                var ok = false
                val up = UPS[e]
                if (up is Stmt.Set) {
                    val dst = up.dst
                    if (dst is Expr.Var) {
                        ok = (dst.env() == e.env())
                    }
                }
                if (ok) {
                    // set x = ... -- x is consumed, but I want to reset it, it's not an access
                } else {
                    st.chk_cn(e.env()!!, e, e.tk_, "invalid access to \"${e.tk_.str}\"")
                }
            }
        }
    }

    fun funcs (e: Expr) {
        if (e is Expr.Func) {
            e.block.simul(State(), ::fs, ::fx, ::fe, emptyList())
        }
    }
    S.visit(null, null, ::funcs, null)
    S.simul(State(), ::fs, ::fx, ::fe, emptyList())
}
