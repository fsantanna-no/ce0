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

fun Env.idToStmt (id: String): Stmt.Var? {
    return this.find {
        id == when (it) {
            is Stmt.Var  -> it.tk_.str
            else         -> null
        }
    } as Stmt.Var?
}

fun Expr.toType (env: Env): Type {
    return when (this) {
        is Expr.Unk   -> Type.Any(this.tk_)
        is Expr.Unit  -> Type.Unit(this.tk_)
        is Expr.Nat   -> Type.Nat(this.tk_)
        is Expr.Upref -> Type.Ptr(this.tk_, this.pln.toType(env))
        is Expr.Dnref -> (this.ptr.toType(env) as Type.Ptr).pln
        is Expr.Var   -> env.idToStmt(this.tk_.str)!!.type
        is Expr.TCons -> Type.Tuple(this.tk_, this.arg.map{it.e.toType(env)}.toTypedArray())
        is Expr.UCons -> Type.UCons(this.tk_, this.arg.e.toType(env))
        is Expr.Call  -> if (this.f is Expr.Nat) Type.Nat(this.f.tk_) else (this.f.toType(env) as Type.Func).out
        is Expr.TDisc -> (this.tup.toType(env) as Type.Tuple).vec[this.tk_.num-1]
        is Expr.UDisc -> (this.uni.toType(env) as Type.Union).let {
            if (this.tk_.num == 0) {
                assert(it.exactlyRec()) { "bug found" }
                Type_Unit(this.tk)
            } else {
                it.expand().vec[this.tk_.num - 1]
            }
        }
        is Expr.UPred -> Type.Nat(Tk.Str(TK.XNAT, this.tk.lin, this.tk.col, "int"))
        is Expr.Func  -> this.type
    }
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
    fun fe (env: Env, e: Expr) {
        if (e is Expr.Var) {
            All_assert_tk(e.tk, env.idToStmt(e.tk_.str) != null) {
                "undeclared variable \"${e.tk_.str}\""
            }
        }
    }
    fun fs (env: Env, s: Stmt) {
        if (s is Stmt.Var) {
            val dcl = env.idToStmt(s.tk_.str)
            All_assert_tk(s.tk, dcl==null || dcl.tk_.str in arrayOf("arg","_ret_")) {
                "invalid declaration : \"${s.tk_.str}\" is already declared (ln ${dcl!!.tk.lin})"
            }
        }
    }
    s.visit(emptyList(), ::fs, null, ::fe, ::ft)
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
    fun fe (env: Env, e: Expr) {
        when (e) {
            is Expr.Dnref -> {
                All_assert_tk(e.tk, e.ptr.toType(env) is Type.Ptr) {
                    "invalid `/` : expected pointer type"
                }
            }
            is Expr.TDisc -> e.tup.toType(env).let {
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
                uni.toType(env).let {
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
                val inp = e.f.toType(env).let { if (it is Type.Func) it.inp else (it as Type.Nat) }
                All_assert_tk(e.f.tk, inp.isSupOf(e.arg.e.toType(env))) {
                    "invalid call : type mismatch"
                }
            }
        }
    }
    fun fs (env: Env, s: Stmt) {
        when (s) {
            is Stmt.Var -> {
                All_assert_tk(s.tk, s.type.isSupOf(s.src.e.toType(env))) {
                    "invalid assignment : type mismatch"
                }
            }
            is Stmt.Set -> {
                val str = if (s.dst is Attr.Var && s.dst.tk_.str=="_ret_") "return" else "assignment"
                All_assert_tk(s.tk, s.dst.toExpr().toType(env).isSupOf(s.src.e.toType(env))) {
                    "invalid $str : type mismatch"
                }
            }
            is Stmt.If -> {
                All_assert_tk(s.tk, s.tst.toType(env) is Type.Nat) {
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
    S.visit(emptyList(), ::fs, null, ::fe, ::ft)
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
    fun fx (env: Env, xe: XExpr, xp: Type) {
        val xp_ctrec = xp.containsRec()
        val xp_exrec = xp.exactlyRec()
        val e_iscst  = xe.e.isconst()
        val e_isvar  = xe.e is Expr.UCons
        val e_isnil  = e_isvar && ((xe.e as Expr.UCons).tk_.num==0)

        //println(xp)
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
    S.visitXP(emptyList(), null, ::fx, null)
}

fun Env.fromStmt (s: Stmt): Env {
    return this.dropWhile { it != s }
}

fun Stmt.getDepth (env: Env, drop: Boolean): Int {
    return if (this is Stmt.Var && this.outer) {
        env.idToStmt("arg")!!.getDepth(env, true)
    } else {
        val env2 = if (!drop) env else env.fromStmt(this)
        env2.count { it is Stmt.Block }
    }
}

fun Expr.getDepth (env: Env, caller: Int, hold: Boolean): Pair<Int,Stmt.Var?> {
    return when (this) {
        is Expr.Unk, is Expr.Unit, is Expr.Nat, is Expr.UPred, is Expr.Func -> Pair(0, null)
        is Expr.Var -> {
            val dcl = env.idToStmt(this.tk_.str)!!
            return if (hold) Pair(dcl.getDepth(env,true), dcl) else Pair(0, null)
        }
        is Expr.Upref ->  {
            val (depth,dcl) = this.pln.getDepth(env, caller, hold)
            if (dcl is Stmt.Var) {
                val inc = if (dcl.tk_.str == "arg" || dcl.outer) 1 else 0
                Pair(depth + inc, dcl)
            } else {
                Pair(depth, dcl)
            }
        }
        is Expr.Dnref -> this.ptr.getDepth(env, caller, hold)
        is Expr.TDisc -> this.tup.getDepth(env, caller, hold)
        is Expr.UDisc -> this.uni.getDepth(env, caller, hold)
        is Expr.Call -> this.f.toType(env).let {
            when (it) {
                is Type.Nat -> Pair(0, null)
                is Type.Func -> if (!it.out.ishasptr()) Pair(0,null) else {
                    // substitute args depth/id by caller depth/id
                    Pair(caller, env.idToStmt((this.f as Expr.Var).tk_.str))
                }
                else -> error("bug found")
            }
        }
        is Expr.TCons -> this.arg.map { it.e.getDepth(env, caller,it.e.toType(env).ishasptr()) }.maxByOrNull { it.first }!!
        is Expr.UCons -> this.arg.e.getDepth(env, caller, hold)
    }
}

fun check_pointers (S: Stmt) {
    fun fs (env: Env, s: Stmt) {
        when (s) {
            is Stmt.Var -> {
                val dst_depth = s.getDepth(env,false)
                val (src_depth, src_dcl) = s.src.e.getDepth(env, dst_depth, s.type.ishasptr())
                All_assert_tk(s.tk, dst_depth >= src_depth) {
                    "invalid assignment : cannot hold local pointer \"${src_dcl!!.tk_.str}\" (ln ${src_dcl!!.tk.lin})"
                }
            }
            is Stmt.Set -> {
                val set_depth = s.getDepth(env,false)
                val (src_depth, src_dcl) = s.src.e.getDepth(env, set_depth, s.dst.toExpr().toType(env).ishasptr())
                //println("${s.dst.toExpr().getDepth(env, set_depth, s.dst.toExpr().toType(env).ishasptr()).first} >= $src_depth")
                All_assert_tk(s.tk, s.dst.toExpr().getDepth(env, set_depth, s.dst.toExpr().toType(env).ishasptr()).first >= src_depth) {
                    "invalid assignment : cannot hold local pointer \"${src_dcl!!.tk_.str}\" (ln ${src_dcl!!.tk.lin})"
                }
            }
        }
    }
    S.visit(emptyList(), ::fs, null, null, null)
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
    // y = borrow \x
    // z = borrow y
    // bws[x] = { y,z }
    // bws[y] = { z }
    val bws: MutableMap<Stmt.Var,MutableSet<Stmt.Var>> = mutableMapOf()
    val cns: MutableMap<Stmt.Var,Int> = mutableMapOf()

    // f = func A ...
    // g = func B ...
    // f = g
    // fs[f] = { A,B }
    val fcs: MutableMap<Stmt.Var,MutableSet<Pair<Env,Expr.Func>>> = mutableMapOf()

    fun chk_bw (env: Env, s: Stmt.Var, tk: Tk, err: String) {
        if (bws[s] != null) {
            val ok = bws[s]!!.intersect(env).isEmpty()  // no borrow is on scope
            val ln = bws[s]!!.first().tk.lin
            All_assert_tk(tk, ok) { err + " : borrowed in line $ln" }
        }
    }

    fun chk_cn (env: Env, s: Stmt.Var, tk: Tk, err: String) {
        All_assert_tk(tk, !cns.contains(s)) { err + " : consumed in line ${cns[s]}" }
    }

    fun fx (env: Env, xe: XExpr) {
        for ((xe,lf) in xe.e.leftMost(xe)) {
            if (xe is XExpr.Replace || xe is XExpr.Consume) {
                val s = env.idToStmt(lf.tk_.str) as Stmt.Var
                chk_bw(env, s, xe.e.tk, "invalid operation on \"${lf.tk_.str}\"")
            }
        }
    }

    fun bws_cns_add (env: Env, dst: Stmt.Var, xsrc: XExpr) {
        for ((xe,lf) in xsrc.e.leftMost(xsrc)) {
            if (xe is XExpr.Borrow) {
                val src = env.idToStmt(lf.tk_.str) as Stmt.Var
                if (bws[src] == null) {
                    bws[src] = mutableSetOf()
                }
                bws[src]!!.add(dst)
                bws[src]!!.addAll(if (bws[dst] == null) emptySet() else bws[dst]!!)
            }
            if (xe is XExpr.Consume) {
                // x = consume y
                val src = env.idToStmt(lf.tk_.str) as Stmt.Var
                cns[src] = xsrc.e.tk.lin // <- y consumed, all bws containing y are also consumed
                bws.filterValues { it.contains(src) }.keys.forEach {
                    cns[it] = xsrc.e.tk.lin
                }
            }
        }
    }

    fun fs (env: Env, s: Stmt) {
        fun fs_add (dst: Stmt.Var, src: Expr) {
            if (fcs[dst] == null) {
                fcs[dst] = mutableSetOf()
            }
            when (src) {
                is Expr.Unk, is Expr.Nat -> {} // ok
                is Expr.Func -> fcs[dst]!!.add(Pair(env,src))
                else -> src.leftMost(null)
                    .map { env.idToStmt(it.second!!.tk_.str)!! }
                    .forEach {
                        // TODO: should substitute instead of addAll (but ifs...)
                        fcs[dst]!!.addAll(if (fcs[it] == null) emptySet() else fcs[it]!!)
                    }
            }
        }
        when (s) {
            is Stmt.Var -> {
                if (s.type is Type.Func) {
                    fs_add(s, s.src.e)
                } else {
                    bws_cns_add(env, s, s.src)
                }
            }
            is Stmt.Set -> {
                s.dst.toExpr().leftMost(null)
                    //.let { assert(it.size==1) ; it }
                    .first()
                    .let {
                        //if (it == null) return
                        val dcl = env.idToStmt(it.second.tk_.str)!!

                        val tp = s.dst.toExpr().toType(env)
                        if (tp is Type.Func) {
                            fs_add(dcl, s.src.e)
                        } else {
                            //if (s.src.x!=null && s.src.x.enu==TK.BORROW) {
                            val isuniptr = tp.containsUnion() || (tp is Type.Ptr && tp.pln.containsUnion())
                            val isrecptr = tp.containsRec() || (tp is Type.Ptr && tp.pln.containsRec())
                            if (isuniptr || isrecptr) {
                                bws_cns_add(env, dcl, s.src)
                                chk_bw(env, dcl, s.tk, "invalid assignment of \"${dcl.tk_.str}\"")
                            }
                        }

                        if (s.dst is Attr.Var) {
                            cns.remove(dcl)
                        } else {
                            chk_cn(env, dcl, s.tk, "invalid assignment of \"${dcl.tk_.str}\"")
                        }
                    }
            }
        }
    }

    //val X = ArrayDeque<Expr.Func>();

    fun fe (env: Env, e: Expr) {
        when (e) {
            is Expr.Var -> {
                val top = VISIT.first()
                if (top is Stmt.Set && top.dst.toExpr()==e) {
                    // set x = ... -- x is consumed, but I want to reset it, it's not an access
                } else {
                    chk_cn(env, env.idToStmt(e.tk_.str)!!, e.tk_, "invalid access to \"${e.tk_.str}\"")
                }
            }
            // TODO: handle globals inside functions
            /*
            is Expr.Call -> {
                when {
                    e.f is Expr.Nat -> {
                    } // ok
                    else -> e.f.leftMost(null)
                        .map { env.idToStmt(it.second!!.tk_.str)!! }
                        .forEach {
                            fcs[it].let {
                                if (it != null) {
                                    for (f in it) {
                                        val arg = (f.second.block.body as Stmt.Seq).s1 as Stmt.Var
                                        assert(arg.tk_.str == "arg")
                                        bws_cns_add(env, arg, e.arg)
                                        // TODO: this env is not the correct one of f
                                        // it should be f.first, but than not all possible bws will be on scope
                                        //f.second.block.visit(f.first, ::fs, ::fx, ::fe, null)
                                        if (!X.contains(f.second)) {
                                            X.addFirst(f.second)
                                            f.second.block.visit(env, ::fs, ::fx, ::fe, null)
                                            X.removeFirst()
                                        }
                                        bws.remove(arg)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            */
        }
    }
    S.visit(emptyList(), ::fs, ::fx, ::fe, null)
}