fun Tk.Scope.check (up: Any) {
    val (lbl, num) = this.let { Pair(it.lbl, it.num) }
    val ok = when {
        (lbl == "global") -> true
        (lbl == "local") -> true
        //(up is Type.Func) -> true                             // ... -> ... [@_1]
        (up.ups_first { it is Type.Func } != null) -> true      // @_1 -> ...
        (up.ups_first { it is Stmt.Block && it.scope != null && it.scope.lbl == lbl && it.scope.num == num } != null) -> true
        (up.ups_first {
            it is Expr.Func && it.type.inp.pools().any { it.tk_.lbl == lbl && it.tk_.num == num }
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
            is Type.Pool -> {
                tp.tk_.check(tp)
                val isfunc = (tp.ups_first { it is Type.Func } != null)
                val isarg  = (tp.ups_first { it is Stmt.Var && it.tk_.str=="arg" } != null)
                if (isfunc || isarg) {
                    All_assert_tk(tp.tk, tp.tk_.num != null) {
                        "invalid pool : expected `_N´ depth"
                    }
                }
            }
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
                val tps   = tp.flatten()
                val ptrs  = (tps.filter { it is Type.Ptr  } as List<Type.Ptr>).filter { it.scope != null }
                val pools = tps.filter { it is Type.Pool } as List<Type.Pool>
                val ok1 = ptrs.all { ptr -> pools.any { ptr.scope!!.lbl==it.tk_.lbl && ptr.scope!!.num==it.tk_.num } }
                All_assert_tk(tp.tk, ok1) {
                    "invalid function type : missing pool argument"
                }
                val ok2 = pools
                    .groupBy { it.tk_.lbl }             // { "a"=[...], "b"=[...]
                    .all {
                        it.value                        // [...]
                            .map { it.tk_.num!! }       // [1,2,3,1,...]
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
            is Expr.Pool -> e.tk_.check(e)
            is Expr.New  -> e.scope.check(e)
            is Expr.Call -> e.scope.let { if (it != null) it.check(e) }
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
                if (tp is Type.Func || e.tk_.str=="arg" || e.tk_.str=="_ret_") {
                    // ok
                } else {
                    val (var_bdepth, var_fdepth) = e.env(e.tk_.str)!!.ups_tolist().let {
                        Pair(it.filter { it is Stmt.Block }.count(), it.filter { it is Expr.Func }.count())
                    }
                    val (exp_bdepth, exp_fdepth) = e.ups_tolist().let {
                        Pair(it.filter { it is Stmt.Block }.count(), it.filter { it is Expr.Func }.count())
                    }
                    if (var_bdepth>0 && var_fdepth<exp_fdepth) {
                        // access is inside function, declaration is outside
                        val func = e.ups_first { it is Expr.Func } as Expr.Func
                        val clo = func.type.clo.scope(func.type).depth
                        println("$clo >= $var_bdepth")
                        All_assert_tk(e.tk, clo >= var_bdepth) {
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
                val tp1 = Type.UCons(e.tk_, AUX.tps[e.arg]!!).up(e)
                val tp2 = if (e.tk_.num != 0) tp1 else {
                    Type.Ptr (
                        Tk.Chr(TK.CHAR, e.tk.lin, e.tk.col, '\\'),
                        Tk.Scope(TK.XSCOPE,e.tk.lin,e.tk.col,"global",null),
                        tp1
                    ).up(e)
                }
                All_assert_tk(e.tk, e.type.isSupOf(tp2)) {
                    "invalid constructor : type mismatch"
                }
            }
            is Expr.New -> {
                All_assert_tk(e.tk, AUX.tps[e.arg] is Type.Union && e.arg.tk_.num>0) {
                    "invalid `new` : expected constructor" // TODO: remove?
                }
            }
            is Expr.Call -> {
                val tp_ret = AUX.tps[e]!!
                val tp_f   = AUX.tps[e.f]
                val tp_arg = AUX.tps[e.arg]!!

                val (inp,out) = when (tp_f) {
                    is Type.Func -> Pair(tp_f.inp,tp_f.out)
                    is Type.Nat  -> Pair(tp_f,tp_f)
                    else -> error("impossible case")
                }

                fun map (inp:Type,out:Type, arg:Type,ret:Type): Pair<Type,Type> {
                    val acc = mutableMapOf<String,MutableMap<Int,Int>>()

                    fun aux (func:Type, call:Type): Type {

                        fun lbl_num (tk: Tk.Scope): Pair<String,Int> {
                            val key = tk.num!!
                            val new = call.scope().depth
                            /*
                            println("===")
                            println(key)
                            println(new)
                            println(acc)
                             */
                            acc[tk.lbl].let {
                                if (it == null) {
                                    acc[tk.lbl] = mutableMapOf(Pair(key,new))
                                } else {
                                    val ok = it.all {
                                        when {
                                            (it.key == key) -> (it.value == new)
                                            (it.key > key)  -> (it.value >= new)
                                            (it.key < key)  -> (it.value <= new)
                                            else -> error("bug found")
                                        }
                                    }
                                    All_assert_tk(call.tk, ok) {
                                        "invalid call : incompatible scopes"
                                    }
                                    it[key] = new
                                }
                            }
                            return Pair(tk.lbl,tk.num)
                        }

                        return when (call) {
                            is Type.Any, is Type.Unit, is Type.Nat, is Type.Rec, is Type.Func -> call
                            is Type.UCons -> error("bug found")
                            is Type.Tuple -> if (func !is Type.Tuple) call else {
                                Type.Tuple(call.tk_, /*********/ call.vec.mapIndexed { i,tp -> aux(func.vec[i], tp) }.toTypedArray())
                            }
                            is Type.Union -> if (func !is Type.Union) call else {
                                Type.Union(call.tk_, call.isrec, call.vec.mapIndexed { i,tp -> aux(func.vec[i], tp) }.toTypedArray())
                            }
                            is Type.Pool -> {
                                if (func !is Type.Pool) call else {
                                    val (lbl, num) = lbl_num(func.tk_)
                                    Type.Pool(Tk.Scope(TK.XSCOPE, call.tk.lin, call.tk.col, lbl, num))
                                }
                            }
                            is Type.Ptr -> {
                                if (func !is Type.Ptr) call else {
                                    val (lbl, num) = lbl_num(func.scope!!)
                                    val pln = aux(func.pln, call.pln)
                                    //println(call)
                                    Type.Ptr(call.tk_, Tk.Scope(TK.XSCOPE, call.tk.lin, call.tk.col, lbl, num), pln)
                                }
                            }
                        }
                    }

                    val arg2 = aux(inp,arg)
                    val ret2 = aux(out,ret)
                    return Pair(arg2, ret2)
                }

                val (arg2,ret2) = if (tp_f !is Type.Func) Pair(tp_arg,tp_ret) else map(inp,out, tp_arg,tp_ret)
                /*
                println("INP, ARG2")
                println(inp.tostr())
                println(arg2.tostr())
                println("XP2, OUT")
                println(ret2.tostr())
                println(out.tostr())
                println(">>>") ; println(inp) ; println(arg2)
                 */
                All_assert_tk(e.f.tk, inp.isSupOf(arg2) && ret2.isSupOf(out)) {
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
                //println(">>> SET") ; println(s.dst) ; println(s.src)
                All_assert_tk(s.tk, dst.isSupOf(src)) {
                    val str = if (s.dst is Expr.Var && s.dst.tk_.str == "_ret_") "return" else "assignment"
                    "invalid $str : type mismatch"
                }
            }
        }
    }
    s.visit(::fs, ::fe, null)
}
