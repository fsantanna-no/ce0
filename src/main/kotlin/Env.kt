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
        is Expr.Upref -> Type.Ptr(this.tk_, this.sub.toType(env))
        is Expr.Dnref -> (this.sub.toType(env) as Type.Ptr).tp
        is Expr.Var   -> env.idToStmt(this.tk_.str)!!.typeVarFunc()
        is Expr.Tuple -> Type.Tuple(this.tk_, this.vec.map{it.e.toType(env)}.toTypedArray())
        is Expr.Case -> Type.Case(this.tk_, this.arg.e.toType(env))
        is Expr.Call  -> if (this.f is Expr.Nat) Type.Nat(this.f.tk_) else (this.f.toType(env) as Type.Func).out
        is Expr.Index -> {
            val cons = this.pre.toType(env)
            if (this.tk_.idx == 0) {
                assert(cons is Type.Union && cons.exactlyRec()) { "bug found" }
                Type_Unit(this.tk)
            } else {
                (when (cons) {
                    is Type.Tuple -> cons.vec
                    is Type.Union -> cons.vec
                    else -> error("bug found")
                })[this.tk_.idx - 1]
            }
        }
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
    s.visit(emptyList(), ::fs, ::fe)
}

fun Type.containsRec (): Boolean {
    return when (this) {
        is Type.None, is Type.Any, is Type.Unit, is Type.Nat, is Type.Ptr, is Type.Func -> false
        is Type.Rec   -> true
        is Type.Tuple -> this.vec.any { it.containsRec() }
        is Type.Union -> this.vec.any { it.containsRec() }
        is Type.Case  -> this.tp.containsRec()
    }
}

fun Type.exactlyRec (): Boolean {
    return when (this) {
        is Type.None, is Type.Any, is Type.Unit, is Type.Nat, is Type.Ptr, is Type.Func, is Type.Tuple -> false
        is Type.Rec   -> true
        is Type.Union -> this.vec.any { it.containsRec() }
        is Type.Case  -> error("bug found")
    }
}

fun Type.isSupOf (sub: Type): Boolean {
    return when {
        (this is Type.None || sub is Type.None) -> false
        (this is Type.Any  || sub is Type.Any) -> true
        (this is Type.Nat  || sub is Type.Nat) -> true
        (this is Type.Union && sub is Type.Case) -> {
            if (sub.tk_.idx==0 && this.exactlyRec()) {
                sub.tp is Type.Unit
            } else {
                val this2 = this.map { if (it is Type.Rec) this else it } as Type.Tuple
                this2.vec[sub.tk_.idx-1].isSupOf(sub.tp)
            }
        }
        (this::class != sub::class) -> false
        (this is Type.Ptr && sub is Type.Ptr) -> this.tp.isSupOf(sub.tp)
        (this is Type.Tuple && sub is Type.Tuple) ->
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
        is Type.Case  -> error("bug found")
    }
}

fun check_types (S: Stmt) {
    fun fe (env: Env, e: Expr) {
        when (e) {
            is Expr.Dnref -> {
                All_assert_tk(e.tk, e.sub.toType(env) is Type.Ptr) {
                    "invalid `/` : expected pointer type"
                }
            }
            is Expr.Index -> {
                All_assert_tk(e.tk, e.pre.toType(env).let { it is Type.Tuple || it is Type.Union }) {
                    "invalid index : type mismatch"
                }
                val (MIN,MAX) = e.pre.toType(env).let {
                    when (it) {
                        is Type.Tuple -> Pair(1, it.vec.size)
                        is Type.Union -> Pair(if (it.exactlyRec()) 0 else 1, it.vec.size)
                        else -> error("bug found")
                    }
                }
                All_assert_tk(e.tk, MIN<=e.tk_.idx && e.tk_.idx<=MAX) {
                    "invalid index : out of bounds"
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
                /*
                if (s.type.exactlyRec() && s.init.e is Expr.Varia) {
                    All_assert_tk(s.tk, s.init.x.let { it!=null && it.enu==TK.NEW }) {
                        "invalid assignment : expected `new` operation modifier"
                    }
                }
                 */
            }
            is Stmt.Set -> {
                val str = if (s.dst is Attr.Var && s.dst.tk_.str=="_ret_") "return" else "assignment"
                All_assert_tk(s.tk, s.dst.toExpr().toType(env).isSupOf(s.src.e.toType(env))) {
                    "invalid $str : type mismatch"
                }
                /*
                if (s.dst.toExpr().toType(env).exactlyRec() && s.src.e is Expr.Varia) {
                    All_assert_tk(s.tk, s.src.x.let { it!=null && it.enu==TK.NEW }) {
                        "invalid $str : expected `new` operation modifier"
                    }
                }
                 */
            }
        }
    }
    S.visit(emptyList(), ::fs, ::fe)
}

fun Expr.isconst (): Boolean {
    return when (this) {
        is Expr.Unit, is Expr.Unk, is Expr.Call, is Expr.Tuple, is Expr.Case -> true
        is Expr.Var, is Expr.Nat, is Expr.Dnref -> false
        is Expr.Index -> this.pre.isconst()
        is Expr.Upref -> this.sub.isconst()
    }
}

fun check_xexprs (S: Stmt) {
    fun fx (env: Env, xe: XExpr, xp: Type) {
        val xp_ctrec = xp.containsRec()
        val xp_exrec = xp.exactlyRec()
        val e_iscst  = xe.e.isconst()
        val e_isvar  = xe.e is Expr.Case
        val e_isnil  = e_isvar && ((xe.e as Expr.Case).tk_.idx==0)
        when {
            (xe.x == null) -> All_assert_tk(xe.e.tk, !xp_ctrec || (e_iscst && (!e_isvar||e_isnil))) {
                "invalid expression : expected " + (if (e_iscst) "`new` " else "") + "operation modifier"
            }
            (xe.x.enu == TK.BORROW) -> All_assert_tk(xe.x, xe.e.toType(env).let { it is Type.Ptr && it.tp.containsRec() }) {
                "invalid `borrow` : expected pointer to recursive variable"
            }
            (xe.x.enu == TK.COPY) -> All_assert_tk(xe.x, xp_ctrec && !e_iscst) {
                "invalid `copy` : expected recursive variable"
            }
            (xe.x.enu == TK.MOVE) -> All_assert_tk(xe.x, xp_ctrec && !e_iscst) {
                "invalid `move` : expected recursive variable"
            }
            (xe.x.enu == TK.NEW) -> All_assert_tk(xe.x, xp_exrec && e_isvar) {
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
        is Expr.Unk, is Expr.Unit, is Expr.Nat -> Pair(0, null)
        is Expr.Var -> {
            val dcl = env.idToStmt(this.tk_.str)!!
            return if (hold) Pair(dcl.getDepth(env,true), dcl) else Pair(0, null)
        }
        is Expr.Upref ->  {
            val (depth,dcl) = this.sub.getDepth(env, caller, hold)
            if (dcl is Stmt.Var) {
                val inc = if (dcl.tk_.str == "arg" || dcl.outer) 1 else 0
                Pair(depth + inc, dcl)
            } else {
                Pair(depth, dcl)
            }
        }
        is Expr.Dnref -> this.sub.getDepth(env, caller, hold)
        is Expr.Index -> this.pre.getDepth(env, caller, hold)
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
        is Expr.Tuple -> this.vec.map { it.e.getDepth(env, caller,it.e.toType(env).ishasptr()) }.maxByOrNull { it.first }!!
        is Expr.Case -> this.arg.e.getDepth(env, caller, hold)
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
    S.visit(emptyList(), ::fs, null)
}