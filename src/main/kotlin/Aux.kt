val UPS = mutableMapOf<Any,Any>()
val ENV = mutableMapOf<Any,Env>()
val XPS = mutableMapOf<Expr,Type>()

data class Env (val s: Stmt.Var, val prv: Env?)

fun aux (s: Stmt) {
    UPS.clear()
    ENV.clear()
    XPS.clear()
    s.aux(null, null)
}

private
fun ups_add (v: Any, up: Any?) {
    if (up == null) return
    assert(UPS[v] == null)
    UPS[v] = up
}

private
fun env_add (v: Any, env: Env?) {
    if (env == null) return
    assert(ENV[v] == null)
    ENV[v] = env
}

private
fun xps_add (e: Expr, tp: Type) {
    assert(XPS[e] == null)
    XPS[e] = tp
}

//////////////////////////////////////////////////////////////////////////////

fun Any.ups_tolist (): List<Any> {
    return when {
        (UPS[this] == null) -> emptyList()
        else -> UPS[this]!!.let { listOf(it) + it.ups_tolist() }
    }
}

//////////////////////////////////////////////////////////////////////////////

fun Any.env_first (f: (Stmt)->Boolean): Stmt? {
    fun aux (env: Env?): Stmt? {
        return when {
            (env == null) -> null
            f(env.s) -> env.s
            else -> aux(env.prv)
        }
    }
    return aux (ENV[this])
}

fun Any.env_all (f: (Stmt)->Boolean): Set<Stmt> {
    fun aux (env: Env?): Set<Stmt> {
        return when {
            (env == null) -> emptySet()
            f(env.s) -> setOf(env.s) + aux(env.prv)
            else -> aux(env.prv)
        }
    }
    return aux (ENV[this])
}

fun Any.env_toset (): Set<Stmt> {
    return this.env_all { it is Stmt.Var }
}

fun Any.env (id: String): Stmt.Var? {
    return this.env_first { it is Stmt.Var && it.tk_.str==id } as Stmt.Var?
}

fun Expr.Var.env (): Stmt.Var? {
    return this.env_first { it is Stmt.Var && it.tk_.str==this.tk_.str } as Stmt.Var?
}

//////////////////////////////////////////////////////////////////////////////

fun Expr.toType (): Type {
    return when (this) {
        is Expr.Unit  -> Type.Unit(this.tk_)
        is Expr.Nat   -> Type.Nat(this.tk_)
        is Expr.Upref -> Type.Ptr(this.tk_, null, this.pln.toType())
        is Expr.Dnref -> (this.ptr.toType() as Type.Ptr).pln
        is Expr.TCons -> Type.Tuple(this.tk_, this.arg.map{it.e.toType()}.toTypedArray())
        is Expr.UCons -> Type.UCons(this.tk_, this.arg.e.toType())
        is Expr.Call  -> {
            this.f.toType().let {
                when (it) {
                    is Type.Func -> it.out
                    is Type.Nat  -> Type.Nat(it.tk_)
                    else -> error("impossible case")
                }
            }
        }
        is Expr.Func  -> this.type
        is Expr.UPred -> Type.Nat(Tk.Str(TK.XNAT, this.tk.lin, this.tk.col, "int"))
        is Expr.TDisc -> (this.tup.toType() as Type.Tuple).expand()[this.tk_.num-1]
        is Expr.UDisc -> (this.uni.toType() as Type.Union).let {
            if (this.tk_.num == 0) {
                assert(it.exactlyRec()) { "bug found" }
                Type_Unit(this.tk)
            } else {
                it.expand()[this.tk_.num - 1]
            }
        }
        is Expr.Var -> this.env()!!.type
    }
}

private
fun Type.aux (up: Any) {
    ups_add(this, up)
    when (this) {
        is Type.Tuple -> this.vec.forEach { it.aux(this) }
        is Type.Union -> this.vec.forEach { it.aux(this) }
        is Type.UCons -> this.arg.aux(this)
        is Type.Func  -> { this.inp.aux(this) ; this.out.aux(this) }
        is Type.Ptr   -> this.pln.aux(this)
    }
}

private
fun XExpr.aux (up: Any, env: Env?, xp: Type) {
    ups_add(this, up)
    //env_add(this, env)
    //xps_add(this, xp)
    if (this is XExpr.Replace) {
        this.new.aux(this, env, xp)
    }
    this.e.aux(this, env, xp)
}

private
fun Expr.aux (up: Any, env: Env?, xp: Type) {
    ups_add(this, up)
    env_add(this, env)
    xps_add(this, xp)
    when (this) {
        is Expr.Var -> {
            All_assert_tk(this.tk, this.env()!=null) {
                "undeclared variable \"${this.tk_.str}\""
            }
        }
        is Expr.TCons -> {
            All_assert_tk(this.tk, (xp !is Type.Tuple)|| this.arg.size==xp.vec.size) {
                "invalid constructor : out of bounds"
            }
            this.arg.forEachIndexed { i,xe ->
                xe.aux(this, env, if (xp is Type.Tuple) xp.expand()[i] else Type_Any(this.tk))
            }
        }
        is Expr.UCons -> {
            val sub = if (xp is Type.Union) {
                val (MIN,MAX) = Pair(if (xp.isnull) 0 else 1, xp.vec.size)
                All_assert_tk(this.tk, MIN<=this.tk_.num && this.tk_.num<=MAX) {
                    "invalid constructor : out of bounds"
                }
                if (this.tk_.num > 0) xp.expand()[this.tk_.num - 1] else Type_Unit(this.tk)
            } else {
                Type_Any(this.tk)
            }
            this.arg.aux(this, env, sub)
        }
        is Expr.Dnref -> {
            this.ptr.aux(this, env, xp.keepAnyNat { Type.Ptr(Tk.Chr(TK.CHAR,this.tk.lin,this.tk.col,'\\'),null,xp) })
            All_assert_tk(this.tk, this.ptr.toType() is Type.Ptr) {
                "invalid `/´ : expected pointer type"
            }
        }
        is Expr.Upref -> this.pln.aux(this, env, if (xp is Type.Ptr) xp.keepAnyNat{xp.pln} else Type_Any(this.tk))
        is Expr.TDisc -> {
            this.tup.aux(this, env, Type_Any(this.tk))
            this.tup.toType().let {
                All_assert_tk(this.tk, it is Type.Tuple) {
                    "invalid discriminator : type mismatch"
                }
                val (MIN,MAX) = Pair(1, (it as Type.Tuple).vec.size)
                All_assert_tk(this.tk, MIN<=this.tk_.num && this.tk_.num<=MAX) {
                    "invalid discriminator : out of bounds"
                }
            }
        }
        is Expr.UDisc -> {
            this.uni.aux(this, env, Type_Any(this.tk))
            this.uni.toType().let {
                All_assert_tk(this.tk, it is Type.Union) {
                    "invalid discriminator : type mismatch"
                }
                val (MIN,MAX) = Pair(if (it.exactlyRec()) 0 else 1, (it as Type.Union).vec.size)
                All_assert_tk(this.tk, MIN<=this.tk_.num && this.tk_.num<=MAX) {
                    "invalid discriminator : out of bounds"
                }
            }
        }
        is Expr.UPred -> {
            this.uni.aux(this, env, Type_Any(this.tk))
            this.uni.toType().let {
                All_assert_tk(this.tk, it is Type.Union) {
                    "invalid discriminator : type mismatch"
                }
                val (MIN,MAX) = Pair(if (it.exactlyRec()) 0 else 1, (it as Type.Union).vec.size)
                All_assert_tk(this.tk, MIN<=this.tk_.num && this.tk_.num<=MAX) {
                    "invalid discriminator : out of bounds"
                }
            }
        }
        is Expr.Call  -> {
            this.f.aux(this, env, Type_Any(this.tk))
            val tp = this.f.toType()
            All_assert_tk(this.f.tk, tp is Type.Func || tp is Type.Nat) {
                "invalid call : not a function"
            }
            val xp2 = this.f.toType().let { it.keepAnyNat{it} }
            this.arg.aux(this, env, if (xp2 is Type.Func) xp2.keepAnyNat{ xp2.inp } else Type_Any(this.tk))
            val inp = when (tp) {
                is Type.Func -> tp.inp
                is Type.Nat  -> tp
                else -> error("impossible case")
            }
            All_assert_tk(this.f.tk, inp.isSupOf(this.arg.e.toType())) {
                "invalid call : type mismatch"
            }
        }
        is Expr.Func  -> this.block.aux(this, env) //{ this.type.ups(this) ; this.block.aux(this, env) }
    }
}

private
fun Stmt.aux (up: Any?, env: Env?): Env? {
    ups_add(this, up)
    env_add(this, env)
    return when (this) {
        is Stmt.Var -> {
            val new = Env(this,env)
            this.src.aux(this, new, this.type)
            All_assert_tk(this.tk, this.type.isSupOf(this.src.e.toType())) {
                "invalid assignment : type mismatch"
            }
            new
        }
        is Stmt.Set -> {
            this.dst.aux(this, env, Type_Any(this.tk))
            this.src.aux(this, env, this.dst.toType())
            val str = if (this.dst is Expr.Var && this.dst.tk_.str=="_ret_") "return" else "assignment"
            All_assert_tk(this.tk, this.dst.toType().isSupOf(this.src.e.toType())) {
                "invalid $str : type mismatch"
            }
            env
        }
        is Stmt.Call -> { this.call.aux(this, env, Type_Any(this.tk)) ; env }
        is Stmt.Seq -> {
            val e1 = this.s1.aux(this, env)
            val e2 = this.s2.aux(this, e1)
            e2
        }
        is Stmt.If -> {
            this.tst.aux(this,env,Type_Nat(this.tk,"int"))
            All_assert_tk(this.tk, this.tst.toType() is Type.Nat) {
                "invalid condition : type mismatch"
            }
            this.true_.aux(this, env)
            this.false_.aux(this, env)
            env
        }
        is Stmt.Loop  -> { this.block.aux(this,env) ; env }
        is Stmt.Block -> { this.body.aux(this,env) ; env }
        else -> env
    }
}
