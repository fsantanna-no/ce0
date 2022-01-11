fun check_02_after_tps (s: Stmt) {
    val funcs = mutableSetOf<Expr.Func>()   // funcs with checked [@] closure
    fun fe (e: Expr) {
        when (e) {
            is Expr.Var -> {    // check closures
                val (var_fdepth,var_bdepth) = e.env(e.tk_.str)!!.ups_tolist().let {
                    Pair (
                        it.filter { it is Expr.Func }.count(),
                        it.filter { it is Stmt.Block }.count()
                    )
                }
                val exp_fdepth = e.ups_tolist().filter { it is Expr.Func }.count()
                if (var_bdepth>0 && var_fdepth<exp_fdepth) {
                    // access is inside function, declaration is outside
                    val var_scope = e.env(e.tk_.str)!!.toType().scope()
                    val func = e.ups_first { it is Expr.Func } as Expr.Func
                    val clo = func.type_.clo1?.scope(func.type_)?.depth ?: 0
                    All_assert_tk(e.tk, clo>=var_scope.depth && func.ups.any { it.str==e.tk_.str }) {
                        "invalid access to \"${e.tk_.str}\" : invalid closure declaration (ln ${func.tk.lin})"
                    }
                    funcs.add(func)
                }
            }
            is Expr.Func -> {
                All_assert_tk(e.tk, funcs.contains(e) || e.type_.clo1 == null) {
                    "invalid function : unexpected closure declaration"
                }
                All_assert_tk(e.tk, !funcs.contains(e) || e.type_.clo1 != null) {
                    "invalid function : expected closure declaration"
                }
            }

            is Expr.UCons -> {
                val ok = when (e.type_) {
                    is Type.Ptr -> {
                        (e.tk_.num == 0) && e.arg is Expr.Unit && (e.type_.pln is Type.Rec || e.type_.pln.isrec())
                    }
                    is Type.Union -> {
                        (e.tk_.num > 0) && (e.type_.vec.size >= e.tk_.num) && e.type_.expand()[e.tk_.num - 1].isSupOf(e.arg.type!!)
                    }
                    else -> error("bug found")
                }
                All_assert_tk(e.tk, ok) {
                    "invalid constructor : type mismatch"
                }
            }
            is Expr.New -> {
                All_assert_tk(e.tk, e.arg.type is Type.Union && e.arg.tk_.num>0) {
                    "invalid `new` : expected constructor" // TODO: remove?
                }
            }
            is Expr.Call -> {
                val func = e.f.type
                val ret1 = e.type!!
                val arg1 = e.arg.type!!

                val (scps1,inp1,out1) = when (func) {
                    is Type.Func -> Triple(func.scp1s,func.inp,func.out)
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
                                val ret = tp.scp1.let { scp ->
                                    acc[scp.lbl].let { if (it == null) null else it[scp.num]!! }
                                }
                                if (ret == null) tp else {
                                    Type.Ptr(tp.tk_, ret, null, aux(tp.pln,dofunc)).build(e)
                                }
                            }
                            is Type.Func -> if (!dofunc) tp else {
                                val ret = tp.clo1.let { scp ->
                                    if (scp == null) {
                                        null
                                    } else {
                                        acc[scp.lbl].let { if (it == null) tp.clo1 else it[scp.num]!! }
                                    }
                                }
                                Type.Func(tp.tk_, ret, tp.scp1s, aux(tp.inp,dofunc), aux(tp.out,dofunc)).build(e)
                            }
                        }
                    }
                    Pair (
                        aux(inp1,false), //.lincol(func,func.tk.lin,func.tk.col),
                        aux(out1,true)   //.lincol(func,func.tk.lin,func.tk.col)
                    )
                }

                /*
                //print("INP1: ") ; println(inp1.tostr())
                print("INP2: ") ; println(inp2.tostr()) ; println(inp2.scope())
                print("ARG1: ") ; println(arg1.tostr()) ; println(arg1.scope())
                //print("ARG2: ") ; println(arg2.tostr())
                //println("OUT, RET1, RET2")
                //print("OUT1: ") ; println(out1.tostr())
                print("OUT2: ") ; println(out2.tostr())
                print("RET1: ") ; println(ret1.tostr())
                //print("RET2: ") ; println(ret2.tostr())
                */

                All_assert_tk(e.f.tk, inp2.isSupOf(arg1) && ret1.isSupOf(out2)) {
                    //println(inp2.isSupOf(arg1))
                    //println(ret1.isSupOf(out2))
                    "invalid call : type mismatch"
                }
            }
        }
    }
    fun fs (s: Stmt) {
        when (s) {
            is Stmt.If -> {
                All_assert_tk(s.tk, s.tst.type is Type.Nat) {
                    "invalid condition : type mismatch"
                }
            }
            is Stmt.Set -> {
                val dst = s.dst.type!!
                val src = s.src.type!!
                //println(">>> SET") ; println(s.dst) ; println(s.src) ; println(dst.tostr()) ; println(src.tostr())
                All_assert_tk(s.tk, dst.isSupOf(src)) {
                    val str = if (s.dst is Expr.Var && s.dst.tk_.str == "ret") "return" else "assignment"
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
