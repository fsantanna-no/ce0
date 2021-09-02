fun env_prelude (s: Stmt): Stmt {
    val stdo = Stmt.Func (
        Tk.Str(TK.XVAR,1,1,"output_std"),
        Type.Func (
            Tk.Sym(TK.ARROW, 1, 1, "->"),
            Type.Any(Tk.Chr(TK.CHAR,1,1,'?')),
            Type.Unit(Tk.Sym(TK.UNIT,1,1,"()"))
        ),
        null
    )
    return Stmt.Seq(stdo.tk, stdo, s)
}

fun Env.idToStmt (id: String): Stmt? {
    return this.find {
        id == when (it) {
            is Stmt.Var  -> it.tk_.str
            is Stmt.Func -> it.tk_.str
            else         -> null
        }
    }
}

fun Expr.toType (env: Env): Type {
    return when (this) {
        is Expr.Unk   -> Type.Any(this.tk_)
        is Expr.Unit  -> Type.Unit(this.tk_)
        is Expr.Nat   -> Type.Nat(this.tk_)
        is Expr.Upref -> Type.Ptr(this.tk_, this.pln.toType(env))
        is Expr.Dnref -> (this.ptr.toType(env) as Type.Ptr).pln
        is Expr.Var   -> env.idToStmt(this.tk_.str)!!.typeVarFunc()
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
    }
}

fun check_dcls (s: Stmt) {
    fun fe (env: Env, e: Expr) {
        if (e is Expr.Var) {
            All_assert_tk(e.tk, env.idToStmt(e.tk_.str) != null) {
                "undeclared variable \"${e.tk_.str}\""
            }
        }
    }
    fun fs (env: Env, s: Stmt) {
        val id = when (s) {
            is Stmt.Var  -> s.tk_.str
            is Stmt.Func -> s.tk_.str
            else -> null
        }
        if (id != null) {
            val dcl = env.idToStmt(id)
            All_assert_tk(s.tk, dcl==null) {
                "invalid declaration : \"${id}\" is already declared (ln ${dcl!!.tk.lin})"
            }
        }
    }
    s.visit(emptyList(), ::fs, null, ::fe)
}

fun Type.containsRec (): Boolean {
    return when (this) {
        is Type.None, is Type.Any, is Type.Unit, is Type.Nat, is Type.Ptr, is Type.Func -> false
        is Type.Rec   -> true
        is Type.Tuple -> this.vec.any { it.containsRec() }
        is Type.Union -> this.vec.any { it.containsRec() }
        is Type.UCons  -> this.arg.containsRec()
    }
}

fun Type.exactlyRec (): Boolean {
    return when (this) {
        is Type.Union -> (this.expand().toString() != this.toString())
        is Type.UCons -> error("bug found")
        else -> false
    }
}

fun Type.isSupOf (sub: Type): Boolean {
    return when {
        (this is Type.None || sub is Type.None) -> false
        (this is Type.Any  || sub is Type.Any) -> true
        (this is Type.Nat  || sub is Type.Nat) -> true
        (this is Type.Union && sub is Type.UCons) -> {
            if (sub.tk_.num==0 && this.exactlyRec()) {
                sub.arg is Type.Unit
            } else {
                val this2 = this.expand()
                this2.vec[sub.tk_.num-1].isSupOf(sub.arg)
            }
        }
        (this::class != sub::class) -> false
        (this is Type.Ptr && sub is Type.Ptr) -> this.pln.isSupOf(sub.pln)
        (this is Type.Tuple && sub is Type.Tuple) ->
            (this.vec.size==sub.vec.size) && this.vec.zip(sub.vec).all { (x,y) -> x.isSupOf(y) }
        (this is Type.Union && sub is Type.Union) ->
            (this.vec.size==sub.vec.size) && this.vec.zip(sub.vec).all { (x,y) -> x.isSupOf(y) }
        else -> true
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
    S.visit(emptyList(), ::fs, null, ::fe)
}

fun Expr.isconst (): Boolean {
    return when (this) {
        is Expr.Unit, is Expr.Unk, is Expr.Call, is Expr.TCons, is Expr.UCons, is Expr.UPred -> true
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

        //println(xe)
        //println(xp)
        val is_ptr_to_ctrec = (xp is Type.Ptr) && xp.pln.containsRec() && (xe.e !is Expr.Unk) && (xe.e !is Expr.Nat)
        when {
            (xe.x == null) -> All_assert_tk(xe.e.tk, !is_ptr_to_ctrec && (!xp_ctrec || (e_iscst && (!e_isvar||!xp_exrec||e_isnil)))) {
                "invalid expression : expected " + (if (is_ptr_to_ctrec) "`borrow` " else if (e_iscst) "`new` " else "") + "operation modifier"
            }
            (xe.x.enu == TK.BORROW) -> All_assert_tk(xe.x, xe.e.toType(env).let { it is Type.Ptr && it.pln.containsRec() }) {
                "invalid `borrow` : expected pointer to recursive variable"
            }
            (xe.x.enu == TK.COPY) -> All_assert_tk(xe.x, xp_ctrec && !e_iscst) {
                "invalid `copy` : expected recursive variable"
            }
            (xe.x.enu == TK.MOVE) -> All_assert_tk(xe.x, xp_ctrec && !e_iscst) {
                "invalid `move` : expected recursive variable"
            }
            (xe.x.enu == TK.NEW) -> All_assert_tk(xe.x, xp_exrec && e_isvar && !e_isnil) {
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

fun Expr.getDepth (env: Env, caller: Int, hold: Boolean): Pair<Int,Stmt?> {
    return when (this) {
        is Expr.Unk, is Expr.Unit, is Expr.Nat, is Expr.UPred -> Pair(0, null)
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
    fun s2id (s: Stmt): String {
        return when (s) {
            is Stmt.Var -> s.tk_.str
            is Stmt.Func -> s.tk_.str
            else -> error("bug found")
        }
    }
    fun fs (env: Env, s: Stmt) {
        when (s) {
            is Stmt.Var -> {
                val dst_depth = s.getDepth(env,false)
                val (src_depth, src_dcl) = s.src.e.getDepth(env, dst_depth, s.type.ishasptr())
                All_assert_tk(s.tk, dst_depth >= src_depth) {
                    "invalid assignment : cannot hold local pointer \"${s2id(src_dcl!!)}\" (ln ${src_dcl!!.tk.lin})"
                }
            }
            is Stmt.Set -> {
                val set_depth = s.getDepth(env,false)
                val (src_depth, src_dcl) = s.src.e.getDepth(env, set_depth, s.dst.toExpr().toType(env).ishasptr())
                //println("${s.dst.toExpr().getDepth(env, set_depth, s.dst.toExpr().toType(env).ishasptr()).first} >= $src_depth")
                All_assert_tk(s.tk, s.dst.toExpr().getDepth(env, set_depth, s.dst.toExpr().toType(env).ishasptr()).first >= src_depth) {
                    "invalid assignment : cannot hold local pointer \"${s2id(src_dcl!!)}\" (ln ${src_dcl!!.tk.lin})"
                }
            }
        }
    }
    S.visit(emptyList(), ::fs, null, null)
}

fun check_borrows (S: Stmt) {
    // y = borrow \x
    // z = borrow y
    // bws[x] = { y,z }
    // bws[y] = { z }
    val bws: MutableMap<Stmt.Var,MutableSet<Stmt.Var>> = mutableMapOf()

    fun s2id (s: Stmt): String {
        return when (s) {
            is Stmt.Var -> s.tk_.str
            is Stmt.Func -> s.tk_.str
            else -> error("bug found")
        }
    }

    fun fe (env: Env, e: Expr) {
        if (e is Expr.Var) {
            val s = env.idToStmt(e.tk_.str) as Stmt.Var
            if (bws[s] != null) {
                val ok = bws[s]!!.intersect(env).isEmpty()  // no borrow is on scope
                All_assert_tk(e.tk, ok) {
                    val ln = bws[s]!!.first().tk.lin
                    "invalid access to \"${e.tk_.str}\" : borrowed in line $ln"
                }
            }
        }
    }

    fun fs (env: Env, s: Stmt) {
        fun f (dst: Stmt.Var, xsrc: XExpr) {
            if (xsrc.x!=null && xsrc.x.enu==TK.BORROW) {
                val src = env.idToStmt(((xsrc.e as Expr.Upref).pln as Expr.Var).tk_.str) as Stmt.Var
                if (bws[src] == null) {
                    bws[src] = mutableSetOf()
                }
                bws[src]!!.add(dst)
                bws[src]!!.addAll(if (bws[dst] == null) emptySet() else bws[dst]!!)
            }
        }
        when (s) {
            is Stmt.Var -> f(s, s.src)
            is Stmt.Set -> f(env.idToStmt(((s.dst as Attr.Var).toExpr() as Expr.Var).tk_.str) as Stmt.Var, s.src)
        }
    }
    S.visit(emptyList(), ::fs, null, ::fe)
}
