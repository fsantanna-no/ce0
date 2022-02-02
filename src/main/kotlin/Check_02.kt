fun check_02_after_tps (s: Stmt) {
    val funcs = mutableSetOf<Expr.Func>()   // funcs with checked [@] closure
    fun fe (e: Expr) {
        when (e) {
            is Expr.Var -> {    // check closures
                /*
                // ignore upvals declarations, search above if found
                val env = e.ups_first { it is Expr.Func }.let {
                    if (it != null && (it as Expr.Func).ups.any { it.str == e.tk_.str }) {
                        it.env(e.tk_.str)!!
                    } else {
                        e.env(e.tk_.str)!!
                    }
                }
                 */
                val env = e.env(e.tk_.str)!!
                val (var_fdepth,var_bdepth) = env.ups_tolist().let {
                    Pair (
                        it.filter { it is Expr.Func }.count(),
                        it.filter { it is Stmt.Block }.count()
                    )
                }
                val exp_fdepth = e.ups_tolist().filter { it is Expr.Func }.count()
                if (var_bdepth>0 && var_fdepth<exp_fdepth) {
                    // access is inside function, declaration is outside
                    val var_scope = env.toType().toScp2()
                    val func = e.ups_first { it is Expr.Func } as Expr.Func
                    val clo = func.type.xscp2s!!.first?.depth ?: 0
                    All_assert_tk(e.tk, clo>=var_scope.depth && func.ups.any { it.str==e.tk_.str }) {
                        "invalid access to \"${e.tk_.str}\" : invalid closure declaration (ln ${func.tk.lin})"
                    }
                    funcs.add(func)
                }
            }
            is Expr.Func -> {
                All_assert_tk(e.tk, e.tk.enu==TK.TASK || funcs.contains(e) || e.type.xscp1s.first==null) {
                    "invalid function : unexpected closure declaration"
                }
                All_assert_tk(e.tk, !funcs.contains(e) || e.type.xscp1s.first!=null) {
                    "invalid function : expected closure declaration"
                }
            }

            is Expr.UCons -> {
                e.check()
                val uni = e.xtype as Type.Union
                All_assert_tk(e.tk, uni.expand()[e.tk_.num - 1].isSupOf(e.arg.wtype!!)) {
                    "invalid constructor : type mismatch"
                }
            }

            is Expr.New -> {
                All_assert_tk(e.tk, e.arg.wtype is Type.Union && e.arg.tk_.num>0) {
                    "invalid `new` : expected constructor" // TODO: remove?
                }
            }
            is Expr.Call -> {
                val func = e.f.wtype
                val ret1 = e.wtype!!
                val arg1 = e.arg.wtype!!

                val (scps1,inp1,out1) = when (func) {
                    is Type.Run  -> Triple(func.tsk.xscp1s.second,func.tsk.inp,func.tsk.out)
                    is Type.Runs -> Triple(func.tsk.xscp1s.second,func.tsk.inp,func.tsk.out)
                    is Type.Func -> Triple(func.xscp1s.second,func.inp,func.out)
                    is Type.Nat  -> Triple(null,func,func)
                    else -> error("impossible case")
                }

                // { [lbl]={1=depth,2=depth} }
                // { [""]={[1]=@LOCAL, [2]=@aaa, ...}
                val acc = mutableMapOf<String,MutableMap<Int,Pair<Tk.Scp1,Scp2>>>()

                // check scopes, build acc
                // var f: (... -> {@i1,@_2,...} -> ...)
                // call f {@a,@b,...} ...
                if (scps1 != null) {
                    All_assert_tk(e.tk, scps1.size == e.xscp1s.first.size) {
                        "invalid call : scope mismatch"
                    }
                    scps1.zip(e.xscp1s.first.zip(e.xscp2s!!.first)).forEach { (ff,ee) ->
                        val num   = ff.num!!
                        acc[ff.lbl].let {
                            if (it == null) {
                                acc[ff.lbl] = mutableMapOf(Pair(num,ee))
                            } else {
                                val d1 = ee.second.depth
                                val ok = it.all {
                                    val d2 = it.value.second.depth
                                    when {
                                        (it.key == num) -> (d2 == d1)
                                        (it.key > num)  -> (d2 >= d1)
                                        (it.key < num)  -> (d2 <= d1)
                                        else -> error("bug found")
                                    }
                                }
                                All_assert_tk(ee.first, ok) {
                                    "invalid call : scope mismatch"
                                }
                                it[num] = ee
                            }
                        }
                    }
                }

                // transform inp2, out2 to use scopes from the call @LOCAL... (vs arg scopes @a1...)
                val (inp2,out2) = if (func !is Type.Func) Pair(inp1,out1) else
                {
                    fun Type.aux (dofunc: Boolean): Type {
                        return when (this) {
                            is Type.Unit, is Type.Nat, is Type.Rec -> this
                            is Type.Tuple -> Type.Tuple(this.tk_, this.vec.map { it.aux(dofunc) }.toTypedArray())
                            is Type.Union -> Type.Union(this.tk_, this.isrec, this.vec.map { it.aux(dofunc) }.toTypedArray())
                            is Type.Ptr -> {
                                val ret = this.xscp1.let { scp ->
                                    acc[scp.lbl].let { if (it == null) null else it[scp.num]!! }
                                }
                                if (ret == null) this else {
                                    Type.Ptr(this.tk_, ret.first, ret.second, this.pln.aux(dofunc))
                                }
                            }
                            is Type.Func -> if (!dofunc) this else {
                                val ret = this.xscp1s.first.let { scp ->
                                    if (scp == null) {
                                        null
                                    } else {
                                        acc[scp.lbl].let { if (it == null) Pair(this.xscp1s.first,this.xscp2s!!.first) else it[scp.num]!! }
                                    }
                                }
                                Type.Func (
                                    this.tk_,
                                    Pair(ret?.first,  this.xscp1s.second),
                                    Pair(ret?.second, this.xscp2s!!.second),
                                    this.inp.aux(dofunc),
                                    this.pub?.aux(dofunc),
                                    this.out.aux(dofunc)
                                )
                            }
                            is Type.Run, is Type.Runs -> TODO()
                        }
                    }
                    Pair (
                        inp1.aux(false).clone(e,e.tk.lin,e.tk.col),
                        out1.aux(true).clone(e,e.tk.lin,e.tk.col)
                    )
                }

                /*
                //print("INP1: ") ; println(inp1.tostr())
                //print("INP2: ") ; println(inp2.tostr())
                //print("ARG1: ") ; println(arg1.tostr())
                //println("OUT, RET1, RET2")
                //print("OUT1: ") ; println(out1.tostr())
                //print("OUT2: ") ; println(out2.tostr())
                //print("RET1: ") ; println(ret1.tostr())
                */

                val run = (ret1 is Type.Run || ret1 is Type.Runs)
                All_assert_tk(e.f.tk, inp2.isSupOf(arg1) && (run || ret1.isSupOf(out2))) {
                    //println(inp2.isSupOf(arg1))
                    //println(ret1.isSupOf(out2))
                    "invalid call : type mismatch"
                }
            }
        }
    }
    fun fs (s: Stmt) {
        when (s) {
            is Stmt.Await -> {
                All_assert_tk(s.tk, s.e.wtype is Type.Nat) {
                    "invalid condition : type mismatch"
                }
            }
            is Stmt.Awake -> {
                All_assert_tk(s.tk, s.e.wtype is Type.Run) {
                    "invalid `awake` : type mismatch : expected running task"
                }
            }
            is Stmt.SSpawn -> {
                val dst  = (s.dst.wtype!! as Type.Run).tsk
                val call = s.call.f.wtype!!
                //println("invalid `spawn` : type mismatch : ${dst.tostr()} = ${call.tostr()}")
                All_assert_tk(s.tk, dst.isSupOf(call)) {
                    "invalid `spawn` : type mismatch\n    ${dst.tostr()}\n    ${call.tostr()}"
                }
            }
            is Stmt.If -> {
                All_assert_tk(s.tk, s.tst.wtype is Type.Nat) {
                    "invalid condition : type mismatch"
                }
            }
            is Stmt.DLoop -> {
                All_assert_tk(s.i.tk, s.i.wtype.let { it is Type.Func && it.tk.enu==TK.TASK }) {
                    "invalid loop : type mismatch : expected task type"
                }
                All_assert_tk(s.tsks.tk, s.tsks.wtype is Type.Runs) {
                    "invalid loop : type mismatch : expected tasks type"
                }
            }
            is Stmt.Set -> {
                val dst = s.dst.wtype!!
                val src = s.src.wtype!!
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
    s.visit(false, ::fs, ::fe, null)
}
