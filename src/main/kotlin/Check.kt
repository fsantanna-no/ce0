fun check_01 (s: Stmt) {
    fun ft (tp: Type) {
        when (tp) {
            is Type.Ptr -> {
                All_assert_tk(tp.tk, tp.scopeDepth() != null) {
                    "undeclared scope \"${tp.scope}\""
                }
            }

        }
    }
    fun fs (s: Stmt) {
        when (s) {
            is Stmt.Set -> {
                val dst = TPS[s.dst]!!
                val src = TPS[s.src]!!
                All_assert_tk(s.tk, dst.isSupOf(src)) {
                    //println(dst.tostr())
                    //println(src.tostr())
                    val str = if (s.dst is Expr.Var && s.dst.tk_.str=="_ret_") "return" else "assignment"
                    "invalid $str : type mismatch"
                }
            }
        }
    }
    s.visit(::fs, null, ::ft)
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

fun check_02 (s: Stmt) {
    fun fe (e: Expr) {
        when (e) {
            is Expr.Var -> {
                All_assert_tk(e.tk, e.env()!=null) {
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
            is Expr.Dnref -> {
                All_assert_tk(e.tk, TPS[e.ptr] is Type.Ptr) {
                    "invalid operand to `\\´ : not a pointer"
                }
            }
            is Expr.UDisc -> {
                TPS[e.uni]!!.let {
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
                TPS[e.uni]!!.let {
                    All_assert_tk(e.tk, it is Type.Union) {
                        "invalid discriminator : type mismatch"
                    }
                    val (MIN,MAX) = Pair(if (it.isrec()) 0 else 1, (it as Type.Union).vec.size)
                    All_assert_tk(e.tk, MIN<=e.tk_.num && e.tk_.num<=MAX) {
                        "invalid discriminator : out of bounds XXX" // TODO: remove check
                    }
                }
            }
            is Expr.TDisc -> {
                TPS[e.tup].let {
                    All_assert_tk(e.tk, it is Type.Tuple) {
                        "invalid discriminator : type mismatch"
                    }
                    val (MIN,MAX) = Pair(1, (it as Type.Tuple).vec.size)
                    All_assert_tk(e.tk, MIN<=e.tk_.num && e.tk_.num<=MAX) {
                        "invalid discriminator : out of bounds"
                    }
                }
            }
            is Expr.TCons -> {
                All_assert_tk(e.tk, e.arg.size == (TPS[e] as Type.Tuple).vec.size) {
                    "invalid constructor : out of bounds XXX" // TODO: remove check
                }
            }
            is Expr.UCons -> {
                val xp = XPS[e] as Type.Union
                val (MIN, MAX) = Pair(if (xp.isnull) 0 else 1, xp.vec.size)
                All_assert_tk(e.tk, MIN <= e.tk_.num && e.tk_.num <= MAX) {
                    "invalid constructor : out of bounds XXX" // TODO: remove check
                }
                All_assert_tk(e.tk, e.tk_.num!=0 || TPS[e.arg]!!.isSupOf(Type_Unit(e.tk))) {
                    "invalid constructor : type mismatch"
                }
                if (xp.isrec()) {
                    All_assert_tk(e.tk, (e.tk_.num==0) || UPS[e] is Expr.New) {
                        "invalid constructor : expected `new`"
                    }
                }
            }
            is Expr.New -> {
                All_assert_tk(e.tk, TPS[e.arg].let { it is Type.UCons && it.tk_.num>0 }) {
                    "invalid `new` : expected constructor"
                }
                All_assert_tk(e.tk, XPS[e]!!.isrec()) {
                    "unexpected `new` : expected recursive type"
                }
            }
            is Expr.Call -> {
                val tp = TPS[e.f]
                All_assert_tk(e.f.tk, tp is Type.Func || tp is Type.Nat) {
                    "invalid call : not a function"
                }

                val xp = XPS[e]!!
                val arg = TPS[e.arg]!!

                // check scopes
                val (xp2,arg2) = if (tp !is Type.Func) Pair(xp,arg) else {
                    // all = expected return + arguments
                    val all = (XPS[e]!!.flatten() + TPS[e.arg]!!.flatten())
                        //.filter { it !is Type.Func } // (ignore pointers in function types)
                    // ptrs = all ptrs+depths inside args
                    val ptrs = all.filter { it is Type.Ptr }.map { (it as Type.Ptr).let { Pair(it.scopeDepth()!!,it) } }
                    // sorted = ptrs sorted by grouped depths, substitute depth by increasing index
                    val sorted = ptrs
                        .groupBy  { it.first.third }
                        .toList()
                        .sortedBy { it.first }
                        .mapIndexed { i,(_,l) -> l.map { Pair((i+1),it.second) } }
                        .flatten()
                        //.let { it } // List<Pair<Int, Type.Ptr>>
                    sorted.forEach { println(it.first.toString() + ": " + it.second.tostr()) }

                    // arg2 = scope in ptrs inside args are now increasing numbers (@1,@2,...)
                    val arg2 = TPS[e.arg]!!.map2 { ptr ->
                        if (ptr !is Type.Ptr) ptr else {
                            val idx = sorted.find { it.second == ptr }!!.first
                            Type.Ptr(ptr.tk_, "@"+idx, ptr.pln)
                        }
                    }
                    // xp2 = scope in ptrs inside xp are now increasing numbers (@1,@2,...)
                    val xp2 = XPS[e]!!.map2 { ptr ->
                        if (ptr !is Type.Ptr) ptr else {
                            val idx = sorted.find { it.second == ptr }!!.first
                            Type.Ptr(ptr.tk_, "@"+idx, ptr.pln)
                        }
                    }
                    println(xp2.tostr())
                    println(arg2.tostr())
                    Pair(xp2,arg2)
                }
                val inp = when (tp) {
                    is Type.Func -> tp.inp.map { if (it !is Type.Ptr || it.scope!=null) it else Type.Ptr(it.tk_, "@1", it.pln) }
                    is Type.Nat  -> tp
                    else -> error("impossible case")
                }
                //println(inp.tostr())
                //println(arg2.tostr())
                All_assert_tk(e.f.tk, inp.isSupOf(arg2)) {
                    "invalid call : type mismatch"
                }

                val out = when (tp) {
                    is Type.Func -> tp.out.map { if (it !is Type.Ptr) it else Type.Ptr(it.tk_, "@1", it.pln) }
                    is Type.Nat  -> tp
                    else -> error("impossible case")
                }
                All_assert_tk(e.f.tk, xp2.isSupOf(out)) {
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
                All_assert_tk(s.tk, TPS[s.tst] is Type.Nat) {
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
