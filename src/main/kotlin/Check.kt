fun env_prelude (s: Stmt): Stmt {
    val stdo = Stmt.Var (
        Tk.Str(TK.XVAR,1,1,"output_std"),
        false,
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
    fun ft (tp: Type) {
        when (tp) {
            is Type.Union -> All_assert_tk(tp.tk, !tp.isnullable || tp.exactlyRec()) {
                "invalid type declaration : unexpected `?´"
            }
            else -> {}
        }
    }
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
    s.visit(::fs, null, null, ::ft)
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
    return when (this) {
        //is Type.Union -> this.isrec
        is Type.Union -> (this.expand().toString() != this.toString())
        is Type.UCons -> error("bug found")
        else -> false
    }
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

fun Type.isSupOf (sub: Type): Boolean {
    return when {
        (this is Type.None || sub is Type.None) -> false
        (this is Type.Any  || sub is Type.Any) -> true
        (this is Type.Nat  || sub is Type.Nat) -> true
        (this is Type.Union && sub is Type.UCons) -> {
            if (sub.tk_.num == 0) {
                this.exactlyRec() && this.isnullable && sub.arg is Type.Unit
            } else {
                val this2 = this.expand()
                this2.vec[sub.tk_.num-1].isSupOf(sub.arg)
            }
        }
        (this::class != sub::class) -> false
        (this is Type.Unit && sub is Type.Unit) -> true
        (this is Type.Func && sub is Type.Func) -> (this.inp.isSupOf(sub.inp) && sub.inp.isSupOf(this.inp) && this.out.isSupOf(sub.out) && sub.out.isSupOf(this.out))
        (this is Type.Rec  && sub is Type.Rec)  -> (this.tk_.up == sub.tk_.up)
        (this is Type.Ptr && sub is Type.Ptr) -> this.pln.isSupOf(sub.pln)
        (this is Type.Tuple && sub is Type.Tuple) ->
            (this.vec.size==sub.vec.size) && this.vec.zip(sub.vec).all { (x,y) -> x.isSupOf(y) }
        (this is Type.Union && sub is Type.Union) ->
            (this.isnullable == sub.isnullable) && (this.vec.size==sub.vec.size) && this.vec.zip(sub.vec).all { (x,y) -> x.isSupOf(y) }
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

fun check_types (S: Stmt) {
    fun fe (e: Expr) {
        when (e) {
            is Expr.Dnref -> {
                All_assert_tk(e.tk, e.ptr.toType() is Type.Ptr) {
                    "invalid `/` : expected pointer type"
                }
            }
            is Expr.TDisc -> e.tup.toType().let {
                All_assert_tk(e.tk, it is Type.Tuple) {
                    "invalid discriminator : type mismatch"
                }
                val (MIN,MAX) = Pair(1, (it as Type.Tuple).vec.size)
                All_assert_tk(e.tk, MIN<=e.tk_.num && e.tk_.num<=MAX) {
                    "invalid discriminator : out of bounds"
                }
            }
            is Expr.UDisc, is Expr.UPred -> {
                val (uni,tk) = when (e) {
                    is Expr.UDisc -> Pair(e.uni, e.tk_)
                    is Expr.UPred -> Pair(e.uni, e.tk_)
                    else -> error("impossible case")
                }
                uni.toType().let {
                    All_assert_tk(e.tk, it is Type.Union) {
                        "invalid discriminator : type mismatch"
                    }
                    val (MIN,MAX) = Pair(if (it.exactlyRec()) 0 else 1, (it as Type.Union).vec.size)
                    All_assert_tk(e.tk, MIN<=tk.num && tk.num<=MAX) {
                        "invalid discriminator : out of bounds"
                    }
                }
            }
            is Expr.Call -> {
                val inp = e.f.toType().let { if (it is Type.Func) it.inp else (it as Type.Nat) }
                All_assert_tk(e.f.tk, inp.isSupOf(e.arg.e.toType())) {
                    "invalid call : type mismatch"
                }
            }
        }
    }
    fun fs (s: Stmt) {
        when (s) {
            is Stmt.Var -> {
                All_assert_tk(s.tk, s.type.isSupOf(s.src.e.toType())) {
                    "invalid assignment : type mismatch"
                }
            }
            is Stmt.Set -> {
                val str = if (s.dst is Expr.Var && s.dst.tk_.str=="_ret_") "return" else "assignment"
                All_assert_tk(s.tk, s.dst.toType().isSupOf(s.src.e.toType())) {
                    "invalid $str : type mismatch"
                }
            }
            is Stmt.If -> {
                All_assert_tk(s.tk, s.tst.toType() is Type.Nat) {
                    "invalid condition : type mismatch"
                }
            }
        }
    }
    fun ft (tp: Type) {
        if (tp is Type.Func) {
            All_assert_tk(tp.out.tk, !tp.out.containsFunc()) {
                "invalid type : cannot return function type : currently not supported"
            }
        }
    }
    S.visit(::fs, null, ::fe, ::ft)
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
        when (xe) {
            is XExpr.None -> All_assert_tk(xe.e.tk, !is_ptr_to_udisc && !is_ptr_to_ctrec && (!xp_ctrec || (e_iscst && (!e_isvar||!xp_exrec||e_isnil)))) {
                "invalid expression : expected " + (if (is_ptr_to_udisc||is_ptr_to_ctrec) "`borrow` " else if (e_iscst) "`new` " else "") + "operation modifier"
            }
            is XExpr.Borrow -> All_assert_tk(xe.e.tk, is_ptr_to_udisc||is_ptr_to_ctrec) {
                "invalid `borrow` : expected pointer to recursive variable"
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
            else -> error("bug found")
        }
    }
    S.visit(null, ::fx, null, null)
}

fun Stmt.getDepth (): Int {
    return if (this is Stmt.Var && this.outer) {
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
                val inc = if (dcl.tk_.str == "arg" || dcl.outer) 1 else 0
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

fun Expr.leftMost (xe: XExpr?): Set<Pair<XExpr?,Expr.Var>> {
    return when (this) {
        is Expr.Var -> setOf(Pair(xe,this))
        is Expr.TDisc -> this.tup.leftMost(xe)
        is Expr.UDisc -> this.uni.leftMost(xe)
        is Expr.Dnref -> this.ptr.leftMost(xe)
        is Expr.Upref -> this.pln.leftMost(xe)
        is Expr.TCons -> this.arg.map { it.e.leftMost(it) }.toSet().unionAll()
        is Expr.UCons -> this.arg.e.leftMost((this.arg))
        is Expr.Call  -> emptySet() //TODO("may return args")
        else -> emptySet()
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

fun check_borrows_consumes (S: Stmt) {
    class State: IState {
        // y = borrow \x
        // z = borrow y
        // bws[x] = { y,z }
        // bws[y] = { z }
        var bws: MutableMap<Stmt.Var,MutableSet<Stmt.Var>> = mutableMapOf()
        var cns: MutableMap<Stmt.Var,Expr.Var> = mutableMapOf()

        // f = func A ...
        // g = func B ...
        // f = g
        // fs[f] = { A,B }
        var fcs: MutableMap<Stmt.Var,MutableSet<Expr.Func>> = mutableMapOf()

        fun chk_bw (env: Set<Stmt>, s: Stmt.Var, tk: Tk, err: String) {
            if (this.bws[s] != null) {
                val ok = this.bws[s]!!.intersect(env).isEmpty()  // no borrow is on scope
                //val ok = this.bws[s]!!.isEmpty()  // no borrow is on scope
                val ln = this.bws[s]!!.first().tk.lin
                All_assert_tk(tk, ok) { err + " : borrowed in line $ln" }
            }
        }

        fun chk_cn (s: Stmt.Var, e: Expr.Var?, tk: Tk, err: String) {
            All_assert_tk(tk, !this.cns.contains(s) || this.cns[s]==e) { err + " : consumed in line ${this.cns[s]!!.tk.lin}" }
        }

        fun bws_cns_add (dst: Stmt.Var, xsrc: XExpr) {
            for ((xe,lf) in xsrc.e.leftMost(xsrc)) {
                if (xe is XExpr.Borrow) {
                    val src = lf.env() as Stmt.Var
                    if (this.bws[src] == null) {
                        this.bws[src] = mutableSetOf()
                    }
                    this.bws[src]!!.add(dst)
                    this.bws[src]!!.addAll(if (this.bws[dst] == null) emptySet() else this.bws[dst]!!)
                }
                if (xe is XExpr.Consume) {
                    // x = consume y
                    val src = lf.env() as Stmt.Var
                    this.cns[src] = lf // <- y consumed, all bws containing y are also consumed
                    this.bws.filterValues { it.contains(src) }.keys.forEach {
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
        override fun funcs (f: Expr): Set<Stmt.Block> {
            return emptySet()
        }
    }

    fun fx (xe: XExpr, st: IState) {
        for ((xe,lf) in xe.e.leftMost(xe)) {
            if (xe is XExpr.Replace || xe is XExpr.Consume) {
                val s = lf.env() as Stmt.Var
                (st as State).chk_bw(lf.env_toset(), s, xe.e.tk, "invalid operation on \"${lf.tk_.str}\"")
            }
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
                else -> src.leftMost(null)
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
                s.dst.leftMost(null)
                    //.let { assert(it.size==1) ; it }
                    .first()
                    .let {
                        //if (it == null) return
                        val dcl = it.second.env(it.second.tk_.str)!!

                        val tp = s.dst.toType()
                        if (tp is Type.Func) {
                            fs_add(dcl, s.src.e)
                        } else {
                            //if (s.src.x!=null && s.src.x.enu==TK.BORROW) {
                            val isuniptr = tp.containsUnion() || (tp is Type.Ptr && tp.pln.containsUnion())
                            val isrecptr = tp.containsRec() || (tp is Type.Ptr && tp.pln.containsRec())
                            if (isuniptr || isrecptr) {
                                st.bws_cns_add(dcl, s.src)
                                st.chk_bw(it.second.env_toset(), dcl, s.tk, "invalid assignment of \"${dcl.tk_.str}\"")
                            }
                        }

                        if (s.dst is Expr.Var) {
                            st.cns.remove(dcl)
                        } else {
                            st.chk_cn(dcl, null, s.tk, "invalid assignment of \"${dcl.tk_.str}\"")
                        }
                    }
            }
        }
    }

    val X = ArrayDeque<Expr.Func>()

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
    S.simul(State(), ::fs, ::fx, ::fe, emptyList())
}
