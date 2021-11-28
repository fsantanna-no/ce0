fun check_01 (s: Stmt) {
    fun ft (tp: Type) {
        when (tp) {
            is Type.Ptr -> {
                val ok = when {
                    (tp.scope == null) -> true
                    (tp.scope == "@global") -> true
                    (tp.scope.drop(1).toIntOrNull() != null) -> true
                    (tp.ups_first { it is Stmt.Block && it.scope==tp.scope } != null) -> true
                    else -> false
                }
                All_assert_tk(tp.tk, ok) {
                    "undeclared scope \"${tp.scope}\""
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
        }
    }
    s.visit(null, ::fe, ::ft)
}

///////////////////////////////////////////////////////////////////////////////

fun check_02 (s: Stmt) {
    fun fs(s: Stmt) {
        when (s) {
            is Stmt.Set -> {
                val dst = AUX.tps[s.dst]!!
                val src = AUX.tps[s.src]!!
                println("SET (${s.tk.lin}): ${dst.tostr()} = ${src.tostr()}")
                //println(s.dst)
                All_assert_tk(s.tk, dst.isSupOf(src)) {
                    val str = if (s.dst is Expr.Var && s.dst.tk_.str == "_ret_") "return" else "assignment"
                    "invalid $str : type mismatch"
                }
            }
        }
    }
    s.visit(::fs, null, null)
}

///////////////////////////////////////////////////////////////////////////////

fun Type.map2 (f: (Type)->Type): Type {
    return when (this) {
        is Type.Any, is Type.Unit, is Type.Nat, is Type.Rec -> f(this)
        is Type.Tuple -> f(Type.Tuple(this.tk_, this.vec.map { it.map2(f) }.toTypedArray()))
        is Type.Union -> f(Type.Union(this.tk_, this.isrec, this.isnull, this.vec.map { it.map2(f) }.toTypedArray()))
        is Type.UCons -> f(Type.UCons(this.tk_, f(this.arg)))
        is Type.Func  -> f(Type.Func(this.tk_, this.inp.map2(f), this.out.map2(f)))
        is Type.Ptr   -> {
            // cannot map pln before
            //   - that would change identity of original Ptr
            //   - test relies on identity
            val ret1 = f(this) as Type.Ptr
            val ret2 = Type.Ptr(ret1.tk_, ret1.scope, this.pln.map2(f))
            ret2
        }
    }
}

fun check_03 (s: Stmt) {
    fun fe (e: Expr) {
        when (e) {
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
            is Expr.Dnref -> {
                All_assert_tk(e.tk, AUX.tps[e.ptr] is Type.Ptr) {
                    "invalid operand to `\\´ : not a pointer"
                }
            }
            is Expr.UDisc -> {
                AUX.tps[e.uni]!!.let {
                    All_assert_tk(e.tk, it is Type.Union) {
                        "invalid discriminator : type mismatch"
                    }
                    val (MIN,MAX) = Pair(if (it.isrec()) 0 else 1, (it as Type.Union).vec.size)
                    All_assert_tk(e.tk, MIN<=e.tk_.num && e.tk_.num<=MAX) {
                        "invalid discriminator : out of bounds"
                    }
                }
            }
            is Expr.UPred -> {
                AUX.tps[e.uni]!!.let {
                    All_assert_tk(e.tk, it is Type.Union) {
                        "invalid discriminator : type mismatch"
                    }
                }
            }
            is Expr.TDisc -> {
                AUX.tps[e.tup].let {
                    All_assert_tk(e.tk, it is Type.Tuple) {
                        "invalid discriminator : type mismatch"
                    }
                    val (MIN,MAX) = Pair(1, (it as Type.Tuple).vec.size)
                    All_assert_tk(e.tk, MIN<=e.tk_.num && e.tk_.num<=MAX) {
                        "invalid discriminator : out of bounds"
                    }
                }
            }
            is Expr.UCons -> {
                val xp = AUX.xps[e] as Type.Union
                All_assert_tk(e.tk, e.tk_.num!=0 || AUX.tps[e.arg]!!.isSupOf(Type_Unit(e.tk))) {
                    "invalid constructor : type mismatch"
                }
                if (xp.isrec()) {
                    All_assert_tk(e.tk, (e.tk_.num==0) || AUX.ups[e] is Expr.New) {
                        "invalid constructor : expected `new`"
                    }
                }
            }
            is Expr.New -> {
                All_assert_tk(e.tk, AUX.tps[e.arg].let { it is Type.UCons && it.tk_.num>0 }) {
                    "invalid `new` : expected constructor"
                }
                All_assert_tk(e.tk, AUX.xps[e]!!.isrec()) {
                    "unexpected `new` : expected recursive type"
                }
            }
            is Expr.Call -> {
                val tp_ret = AUX.tps[e]!!
                val tp_f   = AUX.tps[e.f]
                val tp_arg = AUX.tps[e.arg]!!

                All_assert_tk(e.f.tk, tp_f is Type.Func || tp_f is Type.Nat) {
                    "invalid call : not a function"
                }

                // check scopes
                val (xp2,arg2) = if (tp_f !is Type.Func) Pair(tp_ret,tp_arg) else {
                    // all = expected return + arguments
                    val all = (AUX.xps[e]!!.flatten() + AUX.tps[e.arg]!!.flatten())
                        //.filter { it !is Type.Func } // (ignore pointers in function types)
                    // ptrs = all ptrs+depths inside args
                    val ptrs = all.filter { it is Type.Ptr }.map { (it as Type.Ptr).let { Pair(it.toScope()!!,it) } }
                    // sorted = ptrs sorted by grouped depths, substitute depth by increasing index
                    val sorted = ptrs
                        .groupBy  { it.first.depth }
                        .toList()
                        .sortedBy { it.first }
                        .mapIndexed { i,(_,l) -> l.map { Pair((i+1),it.second) } }
                        .flatten()
                        //.let { it } // List<Pair<Int, Type.Ptr>>
                    //println("SORTED") ; sorted.forEach { println(it.first.toString() + ": " + it.second.tostr()) }

                    // arg2 = scope in ptrs inside args are now increasing numbers (@1,@2,...)
                    val arg2 = AUX.tps[e.arg]!!.map2 { ptr ->
                        if (ptr !is Type.Ptr) ptr else {
                            val idx = sorted.find { it.second == ptr }!!.first
                            Type.Ptr(ptr.tk_, "@"+idx, ptr.pln)
                        }
                    }
                    // xp2 = scope in ptrs inside xp are now increasing numbers (@1,@2,...)
                    val xp2 = AUX.xps[e]!!.map2 { ptr ->
                        if (ptr !is Type.Ptr) ptr else {
                            val idx = sorted.find { it.second == ptr }!!.first
                            Type.Ptr(ptr.tk_, "@"+idx, ptr.pln)
                        }
                    }
                    Pair(xp2,arg2)
                }
                val (inp,out) = when (tp_f) {
                    is Type.Func -> Pair(tp_f.inp,tp_f.out)
                    is Type.Nat  -> Pair(tp_f,tp_f)
                    else -> error("impossible case")
                }
                //println("INP, ARG2")
                //println(inp.tostr())
                //println(arg2.tostr())
                //println("XP2, OUT")
                //println(xp2.tostr())
                //println(out.tostr())
                All_assert_tk(e.f.tk, inp.isSupOf(arg2) && xp2.isSupOf(out)) {
                    "invalid call : type mismatch"
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
            is Stmt.If -> {
                All_assert_tk(s.tk, AUX.tps[s.tst] is Type.Nat) {
                    "invalid condition : type mismatch"
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
    s.visit(::fs, ::fe, null)
}
