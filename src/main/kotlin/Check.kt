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
            it is Expr.Func && it.type.inp.pools().any { it.tk_.lbl==lbl && it.tk_.num==num }
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
            is Type.Pool -> tp.tk_.check(tp)
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
                val ptrs  = (tps.filter { it is Type.Ptr } as List<Type.Ptr>).filter { it.scope != null }
                val pools = tps.filter { it is Type.Pool } as List<Type.Pool>
                All_assert_tk(tp.tk, pools.all { it.tk_.num != null }) {
                    "invalid pool : expected `_N´ depth"
                }
                val ok1 = ptrs.all {
                    val ptr = it.scope!!
                    when {
                        (ptr.lbl == "global") -> true       // @global
                        //(ptr.lbl == "local")  -> true       // @local
                        tp.clo.let { ptr.lbl==it.lbl && ptr.num==it.num } -> true   // {@a} ...@a
                        pools.any {                         // (@_1 -> ...@_1...)
                            ptr.lbl==it.tk_.lbl && ptr.num==it.tk_.num
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
            is Expr.Func -> {
                val pools = e.type.flatten().filter { it is Type.Pool } as List<Type.Pool>
                val funcs = e.ups_tolist().filter { it is Expr.Func } as List<Expr.Func>
                for (f in funcs) {
                    val pools2 = f.type.flatten().filter { it is Type.Pool } as List<Type.Pool>
                    val err = pools2.find { pool2 -> pools.any { pool -> pool.tk_.lbl==pool2.tk_.lbl } }
                    All_assert_tk(e.tk, err==null) {
                        "invalid pool : \"@${err!!.tk_.lbl}\" is already declared (ln ${err!!.tk.lin})"
                    }
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
                val ret1 = AUX.tps[e]!!
                val tp_f = AUX.tps[e.f]
                val arg1 = AUX.tps[e.arg]!!

                val (inp,out) = when (tp_f) {
                    is Type.Func -> Pair(tp_f.inp,tp_f.out)
                    is Type.Nat  -> Pair(tp_f,tp_f)
                    else -> error("impossible case")
                }

                fun map (inp:Type,out:Type, arg:Type,ret:Type): Pair<Type,Type> {
                    val acc = mutableMapOf<String,MutableMap<Int,Int>>()

                    fun aux (func:Type, call:Type): Type {
                        println("-=-=-")
                        println(func.tostr())
                        println(call.tostr())

                        fun lbl_num (tk: Tk.Scope): Pair<String,Int?> {
                            if (tk.num == null) {
                                return Pair(tk.lbl,null)
                            }

                            val new = call.scope().depth
                            println(call.scope())
                            acc[tk.lbl].let {
                                if (it == null) {
                                    acc[tk.lbl] = mutableMapOf(Pair(tk.num,new))
                                } else {
                                    val ok = it.all {
                                        println("[${tk.lbl}] ${it.key} vs ${tk.num} // ${it.value} vs $new")
                                        when {
                                            (it.key == tk.num) -> (it.value == new)
                                            (it.key > tk.num)  -> (it.value >= new)
                                            (it.key < tk.num)  -> (it.value <= new)
                                            else -> error("bug found")
                                        }
                                    }
                                    All_assert_tk(call.tk, ok) {
                                        "invalid call : incompatible scopes"
                                    }
                                    it[tk.num] = new
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
                                    val scp = Tk.Scope(TK.XSCOPE, call.tk.lin, call.tk.col, lbl, num)
                                    val pln = aux(func.pln, call.pln)
                                    Type.Ptr(call.tk_, scp, pln)
                                }
                            }
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

                    val arg2 = aux(inp,arg)
                    val ret2 = aux(out,ret)
                    return Pair(arg2, ret2)
                }

                ///*
                println("INP, ARG1, ARG2")
                println(inp.tostr())
                println(arg1.tostr())
                //println(arg2.tostr())
                println("OUT, RET1, RET2")
                println(out.tostr())
                println(ret1.tostr())
                //println(ret2.tostr())
                //println(">>>") ; println(inp) ; println(arg2)
                //*/
                val (arg2,ret2) = if (tp_f !is Type.Func) Pair(arg1,ret1) else map(inp,out, arg1,ret1)
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
