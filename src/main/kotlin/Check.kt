fun Expr.Func.pools (): List<String> {
    val inp = this.type.inp
    return when (inp) {
        is Type.Pool -> listOf(inp.tk_.lbl)
        is Type.Tuple -> inp.vec.filter { it is Type.Pool }.map { (it as Type.Pool).tk_.lbl }
        else -> emptyList()
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
            is Type.Ptr -> {
                val lbl = tp.scope!!.lbl
                val ok = when {
                    (lbl == "global") -> true
                    (lbl == "local")  -> true
                    (tp.ups_first { it is Type.Func } != null) -> true
                    (tp.ups_first { it is Stmt.Block && it.scope!=null && it.scope.lbl==lbl } != null) -> true
                    (tp.ups_first { it is Expr.Func && it.pools().any { it==lbl } } != null) -> true
                    else -> false
                }
                All_assert_tk(tp.tk, ok) {
                    "undeclared scope \"@$lbl\""
                }
            }
            is Type.Func -> {
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
                            .map { it.tk_.num ?: 0 }    // [1,0,3,1,...]
                            .toSet()                    // [1,0,3,...]
                            .let {
                                when (it.size) {
                                    0 -> error("bug found")
                                    1 -> (it.elementAt(0) == 0)
                                    else -> (it.minOrNull() == 1) && (it.maxOrNull() == it.size)
                                }
                            }
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
        }
    }
    s.visit(::fs, ::fe, ::ft)
}

///////////////////////////////////////////////////////////////////////////////

fun Type.map2 (f: (Type)->Type): Type {
    return when (this) {
        is Type.Any, is Type.Unit, is Type.Nat, is Type.Rec, is Type.Pool -> f(this)
        is Type.Tuple -> f(Type.Tuple(this.tk_, this.vec.map { it.map2(f) }.toTypedArray()))
        is Type.Union -> f(Type.Union(this.tk_, this.isrec, this.vec.map { it.map2(f) }.toTypedArray()))
        is Type.UCons -> f(Type.UCons(this.tk_, f(this.arg)))
        is Type.Func  -> f(Type.Func(this.tk_, this.inp.map2(f), this.out.map2(f)))
        is Type.Ptr   -> {
            // cannot map pln before
            //   - that would change identity of original Ptr
            //   - test relies on identity
            val ret1 = f(this) as Type.Ptr
            Type.Ptr(ret1.tk_, ret1.scope, this.pln.map2(f))
        }
    }
}

fun check_02_after_tps (s: Stmt) {
    fun fe (e: Expr) {
        when (e) {
            is Expr.UCons -> {
                val tp1 = Type.UCons(e.tk_, AUX.tps[e.arg]!!).up(e)
                val tp2 = if (e.tk_.num != 0) tp1 else {
                    Type.Ptr (
                        Tk.Chr(TK.CHAR, e.tk.lin, e.tk.col, '\\'),
                        Tk.Scope(TK.XSCOPE,e.tk.lin,e.tk.col,"global", null), // NULL is global
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

                // check scopes
                val (xp2,arg2) = if (tp_f !is Type.Func) Pair(tp_ret,tp_arg) else {
                    // all = expected return + arguments
                    val all = (AUX.tps[e]!!.flatten() + AUX.tps[e.arg]!!.flatten())
                    //.filter { it !is Type.Func } // (ignore pointers in function types)
                    // ptrs = all ptrs+depths inside args
                    val ptrs = all.filter { it is Type.Ptr }.map { (it as Type.Ptr).let { Pair(it.scope(),it) } }
                    // sorted = ptrs sorted by grouped depths, substitute depth by increasing index
                    val sorted = ptrs
                        .groupBy  { it.first.depth }
                        .toList()
                        .sortedBy { it.first }
                        .mapIndexed { i,(_,l) -> l.map { Pair((i+1),it.second) } }
                        .flatten()
                    //.let { it } // List<Pair<Int, Type.Ptr>>
                    println("SORTED") ; sorted.forEach { println(it.first.toString() + ": " + it.second.tostr()) }

                    // arg2 = scope in ptrs inside args are now increasing numbers (@1,@2,...)
                    val arg2 = AUX.tps[e.arg]!!.map2 { ptr ->
                        if (ptr !is Type.Ptr) ptr else {
                            val idx = sorted.find { it.second == ptr }!!.first
                            Type.Ptr(ptr.tk_, Tk.Scope(TK.XSCOPE,ptr.tk.lin,ptr.tk.col,"a",idx), ptr.pln)
                        }
                    }
                    // xp2 = scope in ptrs inside xp are now increasing numbers (@1,@2,...)
                    val xp2 = AUX.tps[e]!!.map2 { ptr ->
                        if (ptr !is Type.Ptr) ptr else {
                            val idx = sorted.find { it.second == ptr }!!.first
                            Type.Ptr(ptr.tk_, Tk.Scope(TK.XSCOPE,ptr.tk.lin,ptr.tk.col,"a",idx), ptr.pln)
                        }
                    }
                    Pair(xp2,arg2)
                }
                val (inp,out) = when (tp_f) {
                    is Type.Func -> Pair(tp_f.inp,tp_f.out)
                    is Type.Nat  -> Pair(tp_f,tp_f)
                    else -> error("impossible case")
                }
                println("INP, ARG2")
                println(inp.tostr())
                println(arg2.tostr())
                println("XP2, OUT")
                println(xp2.tostr())
                println(out.tostr())
                All_assert_tk(e.f.tk, inp.isSupOf(arg2) && xp2.isSupOf(out)) {
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
                //print("SET ") ; println(s.dst) ; println(s.src)
                //val scp = (dst as Type.Ptr).scope()
                //print("scope ") ; print(scp)
                All_assert_tk(s.tk, dst.isSupOf(src)) {
                    //println("SET (${s.tk.lin}): ${dst.tostr()} = ${src.tostr()}")
                    //println(dst)
                    //println(src)
                    val str = if (s.dst is Expr.Var && s.dst.tk_.str == "_ret_") "return" else "assignment"
                    "invalid $str : type mismatch"
                }
            }
        }
    }
    s.visit(::fs, ::fe, null)
}
