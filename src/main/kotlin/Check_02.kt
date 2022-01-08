fun check_02_after_tps (s: Stmt) {
    val funcs = mutableSetOf<Expr.Func>()   // funcs with checked [@] closure
    fun fe (e: Expr) {
        val tp = AUX.tps[e]
        when (e) {
            is Expr.Var -> {    // check closures
                if (e.tk_.str=="arg" || e.tk_.str=="_ret_") {
                    // ok
                } else {
                    val (var_fdepth,var_bdepth) = e.env(e.tk_.str)!!.ups_tolist().let {
                        Pair (
                            it.filter { it is Expr.Func }.count(),
                            it.filter { it is Stmt.Block }.count()
                        )
                    }
                    val exp_fdepth = e.ups_tolist().filter { it is Expr.Func }.count()
                    if (var_bdepth>0 && var_fdepth<exp_fdepth) {
                        // access is inside function, declaration is outside
                        val var_scope = e.env(e.tk_.str)!!.type!!.scope()
                        val func = e.ups_first { it is Expr.Func } as Expr.Func
                        val clo = func.type.clo?.scope(func.type)?.depth ?: 0
                        All_assert_tk(e.tk, clo>=var_scope.depth && func.ups.any { it.str==e.tk_.str }) {
                            "invalid access to \"${e.tk_.str}\" : invalid closure declaration (ln ${func.tk.lin})"
                        }
                        funcs.add(func)
                    }
                }
            }
            is Expr.Func -> {
                All_assert_tk(e.tk, funcs.contains(e) || e.type.clo == null) {
                    "invalid function : unexpected closure declaration"
                }
                All_assert_tk(e.tk, !funcs.contains(e) || e.type.clo != null) {
                    "invalid function : expected closure declaration"
                }
            }

            is Expr.UCons -> {
                val ok = when (e.type) {
                    is Type.Ptr -> {
                        (e.tk_.num == 0) && e.arg is Expr.Unit && (e.type.pln is Type.Rec || e.type.pln.isrec())
                    }
                    is Type.Union -> {
                        (e.tk_.num > 0) && (e.type.vec.size >= e.tk_.num) && e.type.expand()[e.tk_.num - 1].isSupOf(AUX.tps[e.arg]!!)
                    }
                    else -> error("bug found")
                }
                All_assert_tk(e.tk, ok) {
                    "invalid constructor : type mismatch"
                }
            }
            is Expr.New -> {
                All_assert_tk(e.tk, AUX.tps[e.arg] is Type.Union && e.arg.tk_.num>0) {
                    "invalid `new` : expected constructor" // TODO: remove?
                }
            }
            is Expr.Call -> {
                val func = AUX.tps[e.f]
                val ret1 = AUX.tps[e]!!
                val arg1 = AUX.tps[e.arg]!!

                val (scps1,inp1,out1) = when (func) {
                    is Type.Func -> Triple(func.scps,func.inp,func.out)
                    is Type.Nat  -> Triple(null,func,func)
                    else -> error("impossible case")
                }

                // { [lbl]={1=depth,2=depth} }
                // { [""]={[1]=@local, [2]=@aaa, ...}
                val acc = mutableMapOf<String,MutableMap<Int,Tk.Scope>>()

                // check scopes, build acc
                // var f: /(... -> {@_1,@_2,...} -> ...)
                // call f\ {@a,@b,...} ...
                if (scps1 != null) {
                    All_assert_tk(e.tk, scps1.size == e.sinps.size) {
                        "invalid call : scope mismatch"
                    }
                    scps1.zip(e.sinps).forEach { (ff,ee) ->
                        val num   = ff.num!!
                        acc[ff.lbl].let {
                            if (it == null) {
                                acc[ff.lbl] = mutableMapOf(Pair(num,ee))
                            } else {
                                val d1 = ee.scope(e).depth
                                val ok = it.all {
                                    val d2 = it.value.scope(e).depth
                                    when {
                                        (it.key == num) -> (d2 == d1)
                                        (it.key > num)  -> (d2 >= d1)
                                        (it.key < num)  -> (d2 <= d1)
                                        else -> error("bug found")
                                    }
                                }
                                All_assert_tk(ee, ok) {
                                    "invalid call : scope mismatch"
                                }
                                it[num] = ee
                            }
                        }
                    }
                }

                val (inp2,out2) = if (func !is Type.Func) Pair(inp1,out1) else
                {
                    fun aux (tp: Type, dofunc: Boolean): Type {
                        return when (tp) {
                            is Type.Unit, is Type.Nat, is Type.Rec -> tp
                            is Type.Tuple -> Type.Tuple(tp.tk_, tp.vec.map { aux(it,dofunc) }.toTypedArray())
                            is Type.Union -> Type.Union(tp.tk_, tp.isrec, tp.vec.map { aux(it,dofunc) }.toTypedArray())
                            is Type.Ptr -> {
                                val ret = tp.scope.let { scp ->
                                    acc[scp.lbl].let { if (it == null) null else it[scp.num]!! }
                                }
                                if (ret == null) tp else {
                                    Type.Ptr(tp.tk_, ret, aux(tp.pln,dofunc)).up(e)
                                }
                            }
                            is Type.Func -> if (!dofunc) tp else {
                                val ret = tp.clo.let { scp ->
                                    if (scp == null) {
                                        null
                                    } else {
                                        acc[scp.lbl].let { if (it == null) tp.clo else it[scp.num]!! }
                                    }
                                }
                                Type.Func(tp.tk_, ret, tp.scps, aux(tp.inp,dofunc), aux(tp.out,dofunc)).up(e)
                            }
                        }
                    }
                    Pair(aux(inp1,false), aux(out1,true))
                }

                /*
                //print("INP1: ") ; println(inp1.tostr())
                print("INP2: ") ; println(inp2.tostr())
                print("ARG1: ") ; println(arg1.tostr())
                //print("ARG2: ") ; println(arg2.tostr())
                //println("OUT, RET1, RET2")
                print("OUT1: ") ; println(out1.tostr())
                print("OUT2: ") ; println(out2.tostr())
                print("RET1: ") ; println(ret1.tostr())
                //print("RET2: ") ; println(ret2.tostr())
                */

                All_assert_tk(e.f.tk, inp2.isSupOf(arg1) && ret1.isSupOf(out2)) {
                    "invalid call : type mismatch"
                }
            }
        }
    }
    fun fs (s: Stmt) {
        when (s) {
            is Stmt.If -> {
                All_assert_tk(s.tk, AUX.tps[s.tst] is Type.Nat) {
                    "invalid condition : type mismatch"
                }
            }
            is Stmt.Set -> {
                val dst = AUX.tps[s.dst]!!
                val src = AUX.tps[s.src]!!
                //println(">>> SET") ; println(s.dst) ; println(s.src) ; println(dst.tostr()) ; println(src.tostr())
                All_assert_tk(s.tk, dst.isSupOf(src)) {
                    val str = if (s.dst is Expr.Var && s.dst.tk_.str == "_ret_") "return" else "assignment"
                    "invalid $str : type mismatch"
                }
                if (s.dst is Expr.Var) {
                    val up = s.dst.ups_first { it is Expr.Func && it.ups.any { it.str == s.dst.tk_.str } }
                    All_assert_tk(s.tk, up==null) {
                        "invalid assignment : cannot modify an upalue"
                    }
                }
            }
        }
    }
    s.visit(::fs, ::fe, null)
}
