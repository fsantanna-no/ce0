

fun check_ctrs (up: Any, dcl_scps: Pair<List<Tk.Id>, List<Pair<String, String>>>, use_scps: List<Scope>): Boolean {
    val pairs = dcl_scps.first.map { it.id }.zip(use_scps!!)
    dcl_scps.second.forEach { ctr ->   // for each constraint
        // check if call args (x,y) respect this contraint
        val x = pairs.find { it.first==ctr.first  }!!.second
        val y = pairs.find { it.first==ctr.second }!!.second
        if (!x.isNestIn(y,up)) {
            return false
        }
    }
    return true
}

fun check_02_after_tps (s: Stmt) {
    fun ft (tp: Type) {
        when (tp) {
            is Type.Alias -> {
                val def = tp.env(tp.tk_.id) as Stmt.Typedef
                val s1 = def.xscp1s.first!!.size
                val s2 = tp.xscps!!.size
                All_assert_tk(tp.tk, s1 == s2) {    // xsc1ps may not be available in Check_01
                    "invalid type : scope mismatch : expecting $s1, have $s2 argument(s)"
                }
                All_assert_tk(tp.tk, check_ctrs(tp,def.xscp1s,tp.xscps!!)) {
                    "invalid type : scope mismatch : constraint mismatch"
                }
            }
        }
    }

    val funcs = mutableSetOf<Expr.Func>()   // funcs with checked [@] closure
    fun fe (e: Expr) {
        when (e) {
            // >>> check closures
            is Expr.Var -> {
                val env = e.env(e.tk_.id)!!
                val dcl = env.ups_tolist().count { it is Expr.Func }

                // these special variables and globals dont need closures
                if (e.tk_.id in listOf("arg","evt","pub","ret")) return
                if (env.ups_tolist().count { it is Expr.Func || it is Stmt.Block } == 0) return

                // take all funcs in between myself -> dcl level: check if they have closure
                val fs  = e.ups_tolist().filter { it is Expr.Func }.dropLast(dcl) as List<Expr.Func>
                fs.forEach {
                    funcs.add(it)
                    All_assert_tk(e.tk, it.ups.any { it.id==e.tk_.id }) {
                        "invalid access to \"${e.tk_.id}\" : invalid closure declaration (ln ${it.tk.lin})"
                    }

                }
            }
            is Expr.Func -> {
                if (e.type.xscps.first != null) {
                    All_assert_tk(e.tk, e.tk.enu==TK.TASK || funcs.contains(e)) {
                        "invalid function : unexpected closure declaration"
                    }
                }
            }
            // <<< check closures

            is Expr.UNull -> e.check()
            is Expr.UCons -> {
                e.check()
                val uni = e.xtype.noalias() as Type.Union
                val sup = uni.vec[e.tk_.num - 1]
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

                val (scp1s,inp1,out1) = when (func) {
                    is Type.Spawn  -> Triple(Pair(func.tsk.xscps.second,func.tsk.xscps.third),func.tsk.inp,func.tsk.out)
                    is Type.Spawns -> Triple(Pair(func.tsk.xscps.second,func.tsk.xscps.third),func.tsk.inp,func.tsk.out)
                    is Type.Func   -> Triple(Pair(func.xscps.second,func.xscps.third),func.inp,func.out)
                    is Type.Nat    -> Triple(Pair(emptyList(),emptyList()),func,func)
                    else -> error("impossible case")
                }

                val s1 = scp1s.first.size
                val s2 = e.xscps.first.size
                All_assert_tk(e.tk, s1 == s2) {
                    "invalid call : scope mismatch : expecting $s1, have $s2 argument(s)"
                }
                All_assert_tk(e.tk, check_ctrs(e,Pair(scp1s.first.map { it.scp1 },scp1s.second),e.xscps!!.first)) {
                    "invalid call : scope mismatch : constraint mismatch"
                }

                val (inp2,out2) = if (func is Type.Func) {
                    val map = scp1s.first.map { it.scp1.id }.zip(e.xscps.first).toMap()
                    Pair (
                        inp1.mapScps(false, map).clone(e,e.tk.lin,e.tk.col),
                        out1.mapScps(true,  map).clone(e,e.tk.lin,e.tk.col)
                    )
                } else {
                    Pair(inp1,out1)
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
            is Stmt.Emit -> {
                All_assert_tk(s.tk, s.e.wtype.let { it is Type.Alias && it.tk_.id=="Event" }) {
                    "invalid `emit` : type mismatch : expected Event : have ${s.e.wtype!!.tostr()}"
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
                //println("-=-=-")
                //val pdst = dst as Type.Pointer
                //println(pdst.xscp2)
                //println(pdst)
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
    s.visit(::fs, ::fe, ::ft, null)
}
