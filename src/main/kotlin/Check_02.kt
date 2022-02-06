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

                val env = e.env(e.tk_.id)!!
                val dcl = env.toType().toScp2().lvl

                //println(e)
                //println(e.tk_.id)
                //println(env.toType().toScp2())
                //println(dcl)

                // these special variables and globals dont need closures
                if (e.tk_.id in arrayOf("arg","evt","pub","ret")) return
                if (env.ups_tolist().count { it is Expr.Func || it is Stmt.Block } == 0) return

                // take all funcs in between myself -> dcl level: check if they have closure
                val fs  = e.ups_tolist().filter { it is Expr.Func }.dropLast(dcl) as List<Expr.Func>
                fs.forEach {
                    funcs.add(it)
                    All_assert_tk(e.tk, it.ups.any { it.id==e.tk_.id }) {
                        "invalid access to \"${e.tk_.id}\" : invalid closure declaration (ln ${it.tk.lin})"
                    }

                }
/*
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
                    println(clo)
                    println(var_scope)
                    // TODO: remove ? HACK: from above and below
                    All_assert_tk(e.tk, clo>=var_scope.depth?:999 && func.ups.any { it.id==e.tk_.id }) {
                        "invalid access to \"${e.tk_.id}\" : invalid closure declaration (ln ${func.tk.lin})"
                    }
                    funcs.add(func)
                }
 */
            }
            is Expr.Func -> {
                if (e.ups.size > 0) {
                    All_assert_tk(e.tk, e.tk.enu == TK.TASK || funcs.contains(e)) {
                        "invalid function : unexpected closure declaration"
                    }
                }
            }

            is Expr.UNull -> e.check()
            is Expr.UCons -> {
                e.check()
                val uni = e.xtype.noalias() as Type.Union
                val sup = uni.expand()[e.tk_.num - 1]
                val sub = e.arg.wtype!!
                All_assert_tk(e.tk, sup.isSupOf(sub)) {
                    "invalid union constructor : ${mismatch(sup,sub)}"
                }
            }

            is Expr.New -> {
                All_assert_tk(e.tk, e.arg.wtype!!.noalias() is Type.Union && e.arg.tk_.num>0) {
                    "invalid `new` : expected constructor" // TODO: remove?
                }
            }
            is Expr.Call -> {
                val func = e.f.wtype
                val ret1 = e.wtype!!
                val arg1 = e.arg.wtype!!

                val (scps1,inp1,out1) = when (func) {
                    is Type.Spawn  -> Triple(Pair(func.tsk.xscp1s.second,func.tsk.xscp1s.third),func.tsk.inp,func.tsk.out)
                    is Type.Spawns -> Triple(Pair(func.tsk.xscp1s.second,func.tsk.xscp1s.third),func.tsk.inp,func.tsk.out)
                    is Type.Func   -> Triple(Pair(func.xscp1s.second,func.xscp1s.third),func.inp,func.out)
                    is Type.Nat    -> Triple(Pair(emptyArray(),emptyArray()),func,func)
                    else -> error("impossible case")
                }

                val s1 = scps1.first.size
                val s2 = e.xscp1s.first.size
                All_assert_tk(e.tk, s1 == s2) {
                    "invalid call : scope mismatch : expecting $s1, have $s2 argument(s)"
                }

                //println(e.xscp2s!!.first.toList())
                // [(a1, 2), (a2, 1)]
                val pairs = scps1.first.map { it.id }.zip(e.xscp2s!!.first)
                scps1.second.forEach { ctr ->   // for each constraint
                    // check if call args (x,y) respect this contraint
                    val x = pairs.find { it.first==ctr.first  }!!.second
                    val y = pairs.find { it.first==ctr.second }!!.second
                    All_assert_tk(e.tk, x.isNestIn(y,e)) {
                        "invalid call : scope mismatch : constraint mismatch"
                    }
                }

                // Original call:
                //      var f: (func @[a1]->/()@a1->())
                //      call f @[LOCAL] /x

                // Map from f->call
                //      { a1=(scp1(LOCAL),scp2(LOCAL) }
                val map = scps1.first
                    .map { it.id }
                    .zip(e.xscp1s.first.zip(e.xscp2s!!.first))
                    .toMap()

                // Transform f->call
                //      var f: (func @[LOCAL]->/()@LOCAL -> ())
                //      call f @[LOCAL] /x
                val (inp2,out2) = if (func !is Type.Func) Pair(inp1,out1) else
                {
                    fun Type.aux (dofunc: Boolean): Type {
                        return when (this) {
                            is Type.Unit, is Type.Nat, is Type.Rec, is Type.Alias -> this
                            is Type.Tuple -> Type.Tuple(this.tk_, this.vec.map { it.aux(dofunc) }.toTypedArray())
                            is Type.Union -> Type.Union(this.tk_, this.isrec, this.vec.map { it.aux(dofunc) }.toTypedArray())
                            is Type.Pointer -> {
                                map[this.xscp1.id].let {
                                    if (it == null) {
                                        this
                                    } else {
                                        Type.Pointer(this.tk_, it.first, it.second, this.pln.aux(dofunc))
                                    }
                                }
                            }
                            is Type.Func -> if (!dofunc) this else {
                                val ret = this.xscp1s.first?.let { me ->
                                    map[me.id].let { it ?: Pair(this.xscp1s.first,this.xscp2s!!.first) }
                                }
                                Type.Func (
                                    this.tk_,
                                    Triple(ret?.first,  this.xscp1s.second, this.xscp1s.third),
                                    Pair(ret?.second, this.xscp2s!!.second),
                                    this.inp.aux(dofunc),
                                    this.pub?.aux(dofunc),
                                    this.out.aux(dofunc)
                                )
                            }
                            is Type.Spawn, is Type.Spawns -> TODO()
                        }
                    }
                    Pair (
                        inp1.aux(false).clone(e,e.tk.lin,e.tk.col),
                        out1.aux(true).clone(e,e.tk.lin,e.tk.col)
                    )
                }

                //val (inp2,out2) = Pair(inp1,out1)
                /*
                //print("INP1: ") ; println(inp1.tostr())
                //print("INP2: ") ; println(inp2.tostr())
                //print("ARG1: ") ; println(arg1.tostr())
                //println("OUT, RET1, RET2")
                //print("OUT1: ") ; println(out1.tostr())
                //print("OUT2: ") ; println(out2.tostr())
                //print("RET1: ") ; println(ret1.tostr())
                */

                val run = (ret1 is Type.Spawn || ret1 is Type.Spawns)
                val ok1 = inp2.isSupOf(arg1)
                val ok2 = run || ret1.isSupOf(out2)
                All_assert_tk(e.f.tk, ok1 && ok2) {
                    if (ok1) {
                        "invalid call : ${mismatch(ret1,out2)}"
                    } else {
                        "invalid call : ${mismatch(inp2,arg1)}"
                    }
                }
            }
        }
    }
    fun fs (s: Stmt) {
        when (s) {
            is Stmt.Await -> {
                All_assert_tk(s.tk, s.e.wtype is Type.Nat) {
                    "invalid condition : type mismatch : expected _int : have ${s.e.wtype!!.tostr()}"
                }
            }
            is Stmt.SSpawn -> {
                All_assert_tk(s.dst.tk, s.dst.wtype is Type.Spawn) {
                    "invalid `spawn` : type mismatch : expected active task : have ${s.dst.wtype!!.tostr()}"
                }
                val dst  = (s.dst.wtype!! as Type.Spawn).tsk
                val call = s.call.f.wtype!!
                All_assert_tk(s.call.tk, call is Type.Func && call.tk.enu==TK.TASK) {
                    "invalid `spawn` : type mismatch : expected task : have ${call.tostr()}"
                }
                //println("invalid `spawn` : type mismatch : ${dst.tostr()} = ${call.tostr()}")
                All_assert_tk(s.tk, dst.isSupOf(call)) {
                    "invalid `spawn` : ${mismatch(dst,call)}"
                }
            }
            is Stmt.DSpawn -> {
                All_assert_tk(s.dst.tk, s.dst.wtype is Type.Spawns) {
                    "invalid `spawn` : type mismatch : expected active tasks : have ${s.dst.wtype!!.tostr()}"
                }
                val call = s.call.f.wtype!!
                All_assert_tk(s.call.tk, call is Type.Func && call.tk.enu==TK.TASK) {
                    "invalid `spawn` : type mismatch : expected task : have ${call.tostr()}"
                }
                val dst = (s.dst.wtype!! as Type.Spawns).tsk
                //println("invalid `spawn` : type mismatch : ${dst.tostr()} = ${call.tostr()}")
                All_assert_tk(s.tk, dst.isSupOf(call)) {
                    "invalid `spawn` : ${mismatch(dst,call)}"
                }
            }
            is Stmt.If -> {
                All_assert_tk(s.tk, s.tst.wtype is Type.Nat) {
                    "invalid condition : type mismatch : expected _int : have ${s.tst.wtype!!.tostr()}"
                }
            }
            is Stmt.DLoop -> {
                val i    = s.i.wtype!!
                val tsks = s.tsks.wtype!!
                All_assert_tk(s.i.tk, i is Type.Spawn) {
                    "invalid `loop` : type mismatch : expected task type : have ${i.tostr()}"
                }
                All_assert_tk(s.tsks.tk, tsks is Type.Spawns) {
                    "invalid `loop` : type mismatch : expected tasks type : have ${tsks.tostr()}"
                }
                All_assert_tk(s.tk, i.isSupOf(tsks)) {
                    "invalid `loop` : ${mismatch(i,tsks)}"
                }
            }
            is Stmt.Set -> {
                val dst = s.dst.wtype!!
                val src = s.src.wtype!!
                //println(">>> SET") ; println(s.dst) ; println(s.src) ; println(dst.tostr()) ; println(src.tostr())
                All_assert_tk(s.tk, dst.isSupOf(src)) {
                    val str = if (s.dst is Expr.Var && s.dst.tk_.id == "ret") "return" else "assignment"
                    "invalid $str : ${mismatch(dst,src)}"
                }
                if (s.dst is Expr.Var) {
                    val up = s.dst.ups_first { it is Expr.Func && it.ups.any { it.id == s.dst.tk_.id } }
                    All_assert_tk(s.tk, up==null) {
                        "invalid assignment : cannot modify an upalue"
                    }
                }
            }
        }
    }
    s.visit(false, ::fs, ::fe, null)
}
