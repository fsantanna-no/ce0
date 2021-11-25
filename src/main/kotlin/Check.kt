fun check_01 (s: Stmt) {
    fun fs (s: Stmt) {
        when (s) {
            is Stmt.Set -> {
                val dst = TPS[s.dst]!!
                val src = TPS[s.src]!!
                All_assert_tk(s.tk, dst.isSupOf(src)) {
                    val str = if (s.dst is Expr.Var && s.dst.tk_.str=="_ret_") "return" else "assignment"
                    "invalid $str : type mismatch"
                }
            }
        }
    }
    s.visit(::fs, null, null)
}

///////////////////////////////////////////////////////////////////////////////

fun Expr.order (): List<Expr> {
    return when (this) {
        is Expr.Unit, is Expr.Var, is Expr.Nat, is Expr.Func -> listOf(this)
        is Expr.TDisc -> this.tup.order() + this
        is Expr.UDisc -> this.uni.order() + this
        is Expr.UPred -> this.uni.order() + this
        is Expr.New   -> this.arg.order() + this
        is Expr.Dnref -> this.ptr.order() + this
        is Expr.Upref -> this.pln.order() + this
        else -> { println(this) ; TODO() }
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
                for (ee in e.order()) {
                    println(ee)
                    count = when (ee) {
                        is Expr.UDisc -> { track=true ; 1 }
                        is Expr.Dnref -> count+1
                        is Expr.Upref -> count-1
                        else -> count
                    }
                }
                println(count)
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
                val inp = when (tp) {
                    is Type.Func -> tp.inp
                    is Type.Nat  -> tp
                    else -> error("impossible case")
                }
                All_assert_tk(e.f.tk, inp.isSupOf(TPS[e.arg]!!)) {
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
                val ok = s.ups_tolist().firstOrNull { it is Expr.Func } != null
                All_assert_tk(s.tk, ok) {
                    "invalid return : no enclosing function"
                }
            }
        }
    }
    s.visit(::fs, ::fe, null)
}

fun Type.containsRec (): Boolean {
    return when (this) {
        is Type.Any, is Type.Unit, is Type.Nat, is Type.Ptr, is Type.Func -> false
        is Type.Rec   -> true
        is Type.Tuple -> this.vec.any { it.containsRec() }
        is Type.Union -> this.vec.any { it.containsRec() }
        is Type.UCons -> this.arg.containsRec()
    }
}

fun<T> Set<Set<T>>.unionAll (): Set<T> {
    return this.fold(emptySet(), {x,y->x+y})
}
