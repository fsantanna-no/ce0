fun Tk.Scope.check (up: Any) {
    val (lbl, num) = this.let { Pair(it.lbl, it.num) }
    val ok = when {
        (lbl == "global") -> true                               // @global
        (lbl == "local") -> true                                // @local
        //(up is Type.Func) -> true                             // ... -> ... [@_1]
        (up.ups_first { it is Type.Func } != null) -> true      // (@_1 -> ...)
        (up.ups_first {                                         // { @aaa ... @aaa }
            it is Stmt.Block && it.scope!=null && it.scope.lbl==lbl && it.scope.num==num
        } != null) -> true
        (up.ups_first {                                         // [@_1, ...] { @_1 }
            it is Expr.Func && it.type.scps.any { it.lbl==lbl && it.num==num }
        } != null) -> true
        else -> false
    }
    All_assert_tk(this, ok) {
        val n = if (num == null) "" else "_$num"
        "undeclared scope \"@$lbl$n\""
    }
}

fun check_01_before_tps (s: Stmt) {
    fun ft (tp: Type) {
        when (tp) {
            is Type.Rec -> {
                val str = "^".repeat(tp.tk_.up)
                All_assert_tk(tp.tk, AUX.ups[tp] is Type.Ptr) {
                    "invalid `$str´ : expected pointer type"
                }
                val unions = tp.ups_tolist().count { it is Type.Union }
                All_assert_tk(tp.tk, unions >= tp.tk_.up) {
                    "invalid `$str´ : missing enclosing recursive type"
                }

            }
            is Type.Ptr -> tp.scope!!.check(tp)
            is Type.Func -> {
                tp.clo.check(tp)
                //tp.scps.forEach { it.check(tp) }
                val ptrs  = tp.flatten()
                    .filter { it is Type.Ptr }
                    .let    { it as List<Type.Ptr>}
                    .filter { it.scope != null }
                val ok1 = ptrs.all {
                    val ptr = it.scope!!
                    when {
                        (ptr.lbl == "global") -> true       // @global
                        //(ptr.lbl == "local")  -> true       // @local
                        tp.clo.let { ptr.lbl==it.lbl && ptr.num==it.num } -> true   // {@a} ...@a
                        tp.scps.any {                         // (@_1 -> ...@_1...)
                            ptr.lbl==it.lbl && ptr.num==it.num
                        } -> true
                        (tp.ups_first {                     // { @aaa \n ...@aaa... }
                            it is Stmt.Block && it.scope!=null && it.scope.lbl==ptr.lbl && it.scope.num==ptr.num
                        } != null) -> true
                        else -> false
                    }
                }
                All_assert_tk(tp.tk, ok1) {
                    "invalid function type : missing pool argument"
                }
                val ok2 = tp.scps
                    .groupBy { it.lbl }             // { "a"=[...], "b"=[...]
                    .all {
                        it.value                        // [...]
                            .map { it.num!! }       // [1,2,3,1,...]
                            .toSet()                    // [1,2,3,...]
                            .let { (it.minOrNull() == 1) && (it.maxOrNull() == it.size) }
                    }
                All_assert_tk(tp.tk, ok2) {
                    "invalid function type : pool arguments are not continuous"
                }
            }
        }
    }
    fun fe (e: Expr) {
        when (e) {
            is Expr.Var -> {
                All_assert_tk(e.tk, e.env() != null) {
                    "undeclared variable \"${e.tk_.str}\""
                }
            }
            is Expr.Upref -> {
                var track = false   // start tracking count if crosses UDisc
                var count = 1       // must remain positive after track (no uprefs)
                for (ee in e.flatten()) {
                    count = when (ee) {
                        is Expr.UDisc -> { track=true ; 1 }
                        is Expr.Dnref -> count+1
                        is Expr.Upref -> count-1
                        else -> count
                    }
                }
                All_assert_tk(e.tk, !track || count>0) {
                    "invalid operand to `/´ : union discriminator"
                }
            }
            is Expr.Func -> {
                val funcs = e.ups_tolist().filter { it is Expr.Func } as List<Expr.Func>
                for (f in funcs) {
                    val err = f.type.scps.find { tk2 -> e.type.scps.any { tk1 -> tk1.lbl==tk2.lbl } }
                    All_assert_tk(e.tk, err==null) {
                        "invalid pool : \"@${err!!.lbl}\" is already declared (ln ${err!!.lin})"
                    }
                }
            }

            is Expr.New  -> e.scope.check(e)
            is Expr.Call -> {
                e.scope.let { if (it != null) it.check(e) }
                e.scps.forEach { it.check(e) }
            }
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
                val ok = s.ups_first { it is Expr.Func } != null
                All_assert_tk(s.tk, ok) {
                    "invalid return : no enclosing function"
                }
            }
            is Stmt.Block -> {
                if (s.scope != null) {
                    All_assert_tk(s.scope, s.scope.num == null) {
                        "invalid pool : unexpected `_${s.scope!!.num}´ depth"
                    }
                    val ok = s.ups_first { it is Stmt.Block && it.scope?.lbl==s.scope.lbl } as Stmt.Block?
                    All_assert_tk(s.scope, ok==null) {
                        "invalid pool : \"@${s.scope.lbl}\" is already declared (ln ${ok!!.tk.lin})"
                    }
                }
            }
        }
    }
    s.visit(::fs, ::fe, ::ft)
}

///////////////////////////////////////////////////////////////////////////////

fun check_02_after_tps (s: Stmt) {
    val funcs = mutableSetOf<Expr.Func>()   // funcs with checked [@] closure
    fun fe (e: Expr) {
        when (e) {
            is Expr.Var -> {    // check closures
                val tp = AUX.tps[e]!!
                if (e.tk_.str=="arg" || e.tk_.str=="_ret_") {
                    // ok
                } else {
                    val var_scope = e.env(e.tk_.str)!!.type.scope()
                    val (exp_bdepth, exp_fdepth) = e.ups_tolist().let {
                        Pair(it.filter { it is Stmt.Block }.count(), it.filter { it is Expr.Func }.count())
                    }
                    if (var_scope.depth>0 && var_scope.lvl<exp_fdepth) {
                        // access is inside function, declaration is outside
                        val func = e.ups_first { it is Expr.Func } as Expr.Func
                        val clo = func.type.clo.scope(func.type).depth
                        All_assert_tk(e.tk, clo >= var_scope.depth) {
                            "invalid access to \"${e.tk_.str}\" : invalid closure declaration (ln ${func.tk.lin})"
                        }
                        funcs.add(func)
                    }
                }
            }
            is Expr.Func -> {
                All_assert_tk(e.tk, funcs.contains(e) || e.type.clo.lbl == "global") {
                    "invalid function : unexpected closure declaration"
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
                    All_assert_tk(e.tk, scps1.size == e.scps.size) {
                        "invalid call : scope mismatch"
                    }
                    scps1.zip(e.scps).forEach { (ff,ee) ->
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
                    fun aux (tp: Type): Type {
                        return when (tp) {
                            is Type.Any, is Type.Unit, is Type.Nat, is Type.Rec -> tp
                            is Type.Tuple -> if (tp !is Type.Tuple) tp else {
                                Type.Tuple(tp.tk_, tp.vec.map { aux(it) }.toTypedArray())
                            }
                            is Type.Union -> if (tp !is Type.Union) tp else {
                                Type.Union(tp.tk_, tp.isrec, tp.vec.map { aux(it) }.toTypedArray())
                            }
                            is Type.Ptr -> if (tp !is Type.Ptr) tp else {
                                val scp = tp.scope!!.let { acc[it.lbl]!![it.num]!! }
                                print(">>> ") ; println(scp)
                                Type.Ptr(tp.tk_, scp, aux(tp.pln)).up(e)
                            }
                            is Type.Func -> tp
                            /*
                            is Type.Func -> {
                                if (func !is Type.Func) call else {
                                    println(">>>: ${func.clo}")
                                    val (lbl, num) = lbl_num(func.clo)
                                    println("<<<")
                                    val clo = Tk.Scope(TK.XSCOPE, call.tk.lin, call.tk.col, lbl, num)
                                    val inp = aux(func.inp, call.inp)
                                    val out = aux(func.out, call.out)
                                    Type.Func(call.tk_, clo, inp, out)
                                }
                            }
                            */
                        }
                    }
                    Pair(aux(inp1), aux(out1))
                }

                ///*
                print("INP1: ") ; println(inp1.tostr())
                print("INP2: ") ; println(inp2.tostr())
                print("ARG1: ") ; println(arg1.tostr())
                //print("ARG2: ") ; println(arg2.tostr())
                //println("OUT, RET1, RET2")
                print("OUT1: ") ; println(out1.tostr())
                print("OUT2: ") ; println(out2.tostr())
                print("RET1: ") ; println(ret1.tostr())
                //print("RET2: ") ; println(ret2.tostr())
                //*/

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
            }
        }
    }
    s.visit(::fs, ::fe, null)
}
