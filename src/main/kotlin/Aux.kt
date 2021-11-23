val UPS = mutableMapOf<Any,Any>()
val ENV = mutableMapOf<Any,Env>()
val TPS = mutableMapOf<Expr,Type>()
val XPS = mutableMapOf<Expr,Type>()

data class Env (val s: Stmt.Var, val prv: Env?)

fun aux (s: Stmt) {
    UPS.clear()
    ENV.clear()
    TPS.clear()
    XPS.clear()
    s.aux(null, null)
}

private
fun ups_add (v: Any, up: Any?) {
    if (up == null) return
    //assert(UPS[v] == null)    // fails b/c of expands
    UPS[v] = up
}

private
fun env_add (v: Any, env: Env?) {
    if (env == null) return
    assert(ENV[v] == null)
    ENV[v] = env
}

private
fun tps_add (e: Expr, tp: Type) {
    assert(TPS[e] == null)
    TPS[e] = tp
}

private
fun xps_add (e: Expr, tp: Type) {
    //assert(XPS[e] == null)    // fails b/c of expands
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

fun env_prelude (s: Stmt): Stmt {
    val stdo = Stmt.Var (
        Tk.Str(TK.XVAR,1,1,"output_std"),
        Type.Func (
            Tk.Sym(TK.ARROW, 1, 1, "->"),
            Type.Any(Tk.Chr(TK.CHAR,1,1,'?')),
            Type.Unit(Tk.Sym(TK.UNIT,1,1,"()"))
        )
    )
    return Stmt.Seq(stdo.tk, stdo, s)
}

//////////////////////////////////////////////////////////////////////////////

fun Type.up (up: Any): Type {
    ups_add(this, up)
    return this
}

fun Expr.toType (): Type {
    return when (this) {
        is Expr.Unit  -> Type.Unit(this.tk_).up(this)
        is Expr.Nat   -> Type.Nat(this.tk_).up(this)
        is Expr.Upref -> TPS[this.pln]!!.let { Type.Ptr(this.tk_,null,it).up(it) }
        is Expr.Dnref -> (TPS[this.ptr] as Type.Ptr).pln
        is Expr.TCons -> Type.Tuple(this.tk_, this.arg.map{TPS[it.e]!!}.toTypedArray()).up(this)
        is Expr.UCons -> Type.UCons(this.tk_, TPS[this.arg.e]!!).up(this)
        is Expr.Call  -> {
            TPS[this.f].let {
                when (it) {
                    is Type.Func -> it.out
                    is Type.Nat  -> it //Type.Nat(it.tk_).ups(this)
                    else -> error("impossible case")
                }
            }
        }
        is Expr.Func  -> this.type
        is Expr.UPred -> Type.Nat(Tk.Str(TK.XNAT, this.tk.lin, this.tk.col, "int")).up(this)
        is Expr.TDisc -> (TPS[this.tup] as Type.Tuple).expand()[this.tk_.num-1].up(this)
        is Expr.UDisc -> (TPS[this.uni] as Type.Union).let {
            if (this.tk_.num == 0) {
                assert(it.exactlyRec()) { "bug found" }
                Type_Unit(this.tk).up(this)
            } else {
                it.expand()[this.tk_.num - 1].up(this)
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
            All_assert_tk(this.tk, TPS[this.ptr] is Type.Ptr) {
                "invalid `/´ : expected pointer type"
            }
        }
        is Expr.Upref -> this.pln.aux(this, env, if (xp is Type.Ptr) xp.keepAnyNat{xp.pln} else Type_Any(this.tk))
        is Expr.TDisc -> {
            this.tup.aux(this, env, Type_Any(this.tk))
            TPS[this.tup].let {
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
            TPS[this.uni]!!.let {
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
            TPS[this.uni]!!.let {
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
            val tp = TPS[this.f]
            All_assert_tk(this.f.tk, tp is Type.Func || tp is Type.Nat) {
                "invalid call : not a function"
            }
            val xp2 = TPS[this.f]!!.let { it.keepAnyNat{it} }
            this.arg.aux(this, env, if (xp2 is Type.Func) xp2.keepAnyNat{ xp2.inp } else Type_Any(this.tk))
            val inp = when (tp) {
                is Type.Func -> tp.inp
                is Type.Nat  -> tp
                else -> error("impossible case")
            }
            All_assert_tk(this.f.tk, inp.isSupOf(TPS[this.arg.e]!!)) {
                "invalid call : type mismatch"
            }
        }
        is Expr.Func  -> { this.type.aux(this) ; this.block.aux(this, env) }
    }
    tps_add(this, this.toType())
}

private
fun Stmt.aux (up: Any?, env: Env?): Env? {
    ups_add(this, up)
    env_add(this, env)
    return when (this) {
        is Stmt.Var -> {
            this.type.aux(this)
            Env(this,env)
        }
        is Stmt.Set -> {
            this.dst.aux(this, env, Type_Any(this.tk))
            this.src.aux(this, env, TPS[this.dst]!!)
            val str = if (this.dst is Expr.Var && this.dst.tk_.str=="_ret_") "return" else "assignment"
            All_assert_tk(this.tk, TPS[this.dst]!!.isSupOf(TPS[this.src.e]!!)) {
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
            All_assert_tk(this.tk, TPS[this.tst] is Type.Nat) {
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
