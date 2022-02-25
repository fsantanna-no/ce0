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

    fun fe (e: Expr) {
        when (e) {
            is Expr.As -> {
                //val def = e.env(e.type.tk_.id) as Stmt.Typedef
                val okalias = e.type
                val noalias = e.type.noalias()
                val (sup,sub) = when (e.tk_.sym) {
                    ":+" -> Pair(noalias, e.e.wtype!!)    // () :+ Unit
                    ":-" -> Pair(e.e.wtype!!, okalias)    // Unit :- ()
                    else -> error("bug found")
                }
                All_assert_tk(e.tk, sup.isSupOf(sub)) {
                    //println(sup.tostr())
                    //println(sub.tostr())
                    "invalid type cast : ${mismatch(e.e.wtype!!,e.type)}"
                }
            }
            is Expr.UCons -> {
                e.check(e.xtype!!)
                val sup = e.xtype.vec[e.tk_.num - 1]
                val sub = e.arg.wtype!!
                All_assert_tk(e.tk, sup.isSupOf(sub)) {
                    "invalid union constructor : ${mismatch(sup,sub)}"
                }
            }
            is Expr.New -> {
                All_assert_tk(e.tk, e.arg.type.xisrec) {
                    "invalid `new` : expected recursive type : have "
                }
            }

            is Expr.Call -> {
                val func = e.f.wtype
                val ret1 = e.wtype!!
                val arg1 = e.arg.wtype!!

                val (scp1s,inp1,out1) = when (func) {
                    is Type.Active  -> Triple(Pair(func.tsk.xscps.second,func.tsk.xscps.third),func.tsk.inp,func.tsk.out)
                    is Type.Actives -> Triple(Pair(func.tsk.xscps.second,func.tsk.xscps.third),func.tsk.inp,func.tsk.out)
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

                val run = (ret1 is Type.Active || ret1 is Type.Actives)
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
                val call = s.call.f.wtype!!
                All_assert_tk(s.call.tk, call is Type.Func && call.tk.enu == TK.TASK) {
                    "invalid `spawn` : type mismatch : expected task : have ${call.tostr()}"
                }
                if (s.dst != null) {
                    All_assert_tk(s.dst.tk, s.dst.wtype is Type.Active) {
                        "invalid `spawn` : type mismatch : expected active task : have ${s.dst.wtype!!.tostr()}"
                    }
                    val dst = (s.dst.wtype!! as Type.Active).tsk
                    //println("invalid `spawn` : type mismatch : ${dst.tostr()} = ${call.tostr()}")
                    All_assert_tk(s.tk, dst.isSupOf(call)) {
                        "invalid `spawn` : ${mismatch(dst, call)}"
                    }
                }
            }
            is Stmt.DSpawn -> {
                All_assert_tk(s.dst.tk, s.dst.wtype is Type.Actives) {
                    "invalid `spawn` : type mismatch : expected active tasks : have ${s.dst.wtype!!.tostr()}"
                }
                val call = s.call.f.wtype!!
                All_assert_tk(s.call.tk, call is Type.Func && call.tk.enu==TK.TASK) {
                    "invalid `spawn` : type mismatch : expected task : have ${call.tostr()}"
                }
                val dst = (s.dst.wtype!! as Type.Actives).tsk
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
                All_assert_tk(s.i.tk, i is Type.Active) {
                    "invalid `loop` : type mismatch : expected task type : have ${i.tostr()}"
                }
                All_assert_tk(s.tsks.tk, tsks is Type.Actives) {
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
            }
        }
    }
    s.visit(::fs, ::fe, ::ft, null)
}
