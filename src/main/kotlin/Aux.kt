object AUX {
    // aux_01
    val ups = mutableMapOf<Any,Any>()
    val env = mutableMapOf<Any,Env>()
    // aux_02
    val tps = mutableMapOf<Expr,Type>()
    val xps = mutableMapOf<Expr,Type>()  // needed b/c of TCons/UCons
    val scp = mutableMapOf<Type.Ptr,String?>()
}

data class Env (val s: Stmt.Var, val prv: Env?)

fun Aux_01 (s: Stmt) {
    AUX.ups.clear()
    AUX.env.clear()
    AUX.tps.clear()
    AUX.xps.clear()
    s.aux_01(null, null)
}

fun Aux_02 (s: Stmt) {
    s.aux_02()
}

private
fun ups_add (v: Any, up: Any?) {
    if (up == null) return
    //assert(AUX.ups[v] == null)    // fails b/c of expands
    AUX.ups[v] = up
}

private
fun env_add (v: Any, env: Env?) {
    if (env == null) return
    assert(AUX.env[v] == null)
    AUX.env[v] = env
}

private
fun xps_add (e: Expr, tp: Type) {
    //assert(AUX.xps[e] == null)    // fails b/c of expands
    AUX.xps[e] = tp
}

fun Type.Ptr.scp (): String? {
    return this.scope ?: AUX.scp[this]  // use explicit pre calculated this.scope for call args
}

//////////////////////////////////////////////////////////////////////////////

fun Any.ups_tolist (): List<Any> {
    return when {
        (AUX.ups[this] == null) -> emptyList()
        else -> AUX.ups[this]!!.let { listOf(it) + it.ups_tolist() }
    }
}

fun Any.ups_first (f: (Any)->Boolean): Any? {
    val up = AUX.ups[this]
    return when {
        (up == null) -> null
        f(up) -> up
        else -> up.ups_first(f)
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
    return aux (AUX.env[this])
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

private
fun Type.aux_01 (up: Any) {
    ups_add(this, up)

    // Derived scope from pointers above myself:
    // /(/x)@a      --> /x is also @a
    // func /x->()  --> /x is @1 (default)
    fun up (tp: Type.Ptr): String? {
        var cur: Type = tp
        while (true) {
            val nxt = AUX.ups[cur]
            when {
                (cur is Type.Ptr && cur.scope!=null) -> return cur.scope!!
                (nxt == null) -> return null
                (nxt is Type.Func) -> return "@1"
                (nxt is Stmt.Var && nxt.tk_.str=="_ret_") -> return "@1"
                (nxt !is Type) -> return null
                else -> cur = nxt
            }
        }
    }

    when (this) {
        is Type.Tuple -> this.vec.forEach { it.aux_01(this) }
        is Type.Union -> this.vec.forEach { it.aux_01(this) }
        is Type.UCons -> this.arg.aux_01(this)
        is Type.Func  -> { this.inp.aux_01(this) ; this.out.aux_01(this) }
        is Type.Ptr   -> { AUX.scp[this]=up(this) ; this.pln.aux_01(this) }
    }
}

private
fun Expr.aux_01 (up: Any, env: Env?) {
    ups_add(this, up)
    env_add(this, env)
    when (this) {
        is Expr.TCons -> this.arg.forEachIndexed { _,e -> e.aux_01(this, env) }
        is Expr.UCons -> this.arg.aux_01(this, env)
        is Expr.New   -> this.arg.aux_01(this, env)
        is Expr.Dnref -> this.ptr.aux_01(this, env)
        is Expr.Upref -> this.pln.aux_01(this, env)
        is Expr.TDisc -> this.tup.aux_01(this, env)
        is Expr.UDisc -> this.uni.aux_01(this, env)
        is Expr.UPred -> this.uni.aux_01(this, env)
        is Expr.Call  -> {
            this.f.aux_01(this, env)
            this.arg.aux_01(this, env)
        }
        is Expr.Func  -> {
            this.type.aux_01(this)
            this.block.aux_01(this, env)
        }
    }
}

private
fun Stmt.aux_01 (up: Any?, env: Env?): Env? {
    ups_add(this, up)
    env_add(this, env)
    return when (this) {
        is Stmt.Pass, is Stmt.Nat, is Stmt.Ret, is Stmt.Break -> env
        is Stmt.Var -> {
            this.type.aux_01(this)
            Env(this,env)
        }
        is Stmt.Set -> {
            this.dst.aux_01(this, env)
            this.src.aux_01(this, env)
            env
        }
        is Stmt.Call -> { this.call.aux_01(this, env) ; env }
        is Stmt.Seq -> {
            val e1 = this.s1.aux_01(this, env)
            val e2 = this.s2.aux_01(this, e1)
            e2
        }
        is Stmt.If -> {
            this.tst.aux_01(this,env)
            this.true_.aux_01(this, env)
            this.false_.aux_01(this, env)
            env
        }
        is Stmt.Loop  -> { this.block.aux_01(this,env) ; env }
        is Stmt.Block -> { this.body.aux_01(this,env) ; env }
    }
}

///////////////////////////////////////////////////////////////////////////////

fun Expr.tps_add () {
    assert(AUX.tps[this] == null)
    AUX.tps[this] = try {
        when (this) {
            is Expr.Unit  -> Type.Unit(this.tk_).up(this)
            is Expr.Nat   -> Type.Nat(this.tk_).up(this)
            is Expr.Upref -> AUX.tps[this.pln]!!.let { Type.Ptr(this.tk_,null,it).up(it) }
            is Expr.Dnref -> (AUX.tps[this.ptr] as Type.Ptr).pln
            is Expr.TCons -> Type.Tuple(this.tk_, this.arg.map{AUX.tps[it]!!}.toTypedArray()).up(this)
            is Expr.UCons -> Type.UCons(this.tk_, AUX.tps[this.arg]!!).up(this)
            is Expr.New   -> AUX.tps[this.arg]!!
            is Expr.Call  -> {
                AUX.tps[this.f].let {
                    when (it) {
                        // scope of output is tested in the call through XP
                        // here, just returns the "top" scope to succeed
                        is Type.Func -> it.out.map { if (it !is Type.Ptr) it else Type.Ptr(it.tk_,"@global",it.pln) }
                        is Type.Nat  -> it //Type.Nat(it.tk_).ups(this)
                        else -> error("impossible case")
                    }
                }
            }
            is Expr.Func  -> this.type
            is Expr.UPred -> Type.Nat(Tk.Str(TK.XNAT, this.tk.lin, this.tk.col, "int")).up(this)
            is Expr.TDisc -> (AUX.tps[this.tup] as Type.Tuple).vec[this.tk_.num-1].up(this)
            is Expr.UDisc -> (AUX.tps[this.uni] as Type.Union).let {
                if (this.tk_.num == 0) {
                    assert(it.isrec()) { "bug found" }
                    Type_Unit(this.tk).up(this)
                } else {
                    it.expand()[this.tk_.num - 1].up(this)
                }
            }
            is Expr.Var -> this.env()!!.type
        }
    } catch (e: Exception) {
        Type_Any(this.tk)
    }
}

private
fun Expr.aux_02 (xp: Type) {
    xps_add(this, xp)
    when (this) {
        is Expr.TCons -> {
            if (xp !is Type.Tuple) {
                this.arg.forEachIndexed { _, e ->
                    e.aux_02(Type_Any(this.tk))
                }
            } else {
                this.arg.forEachIndexed { i, e ->
                    e.aux_02(if (xp.vec.size>i) xp.vec[i] else Type_Any(this.tk))
                }
            }
        }
        is Expr.UCons -> {
            val sub = when {
                (xp !is Type.Union) -> Type_Any(this.tk)
                (this.tk_.num == 0) -> Type_Unit(this.tk)
                (xp.vec.size < this.tk_.num) -> Type_Any(this.tk)
                else -> xp.expand()[this.tk_.num - 1]
            }
            this.arg.aux_02(sub)
        }
        is Expr.New -> this.arg.aux_02(xp)
        is Expr.Dnref -> {
            if (xp is Type.Ptr) {
                this.ptr.aux_02(Type_Any(this.tk))
            } else {
                this.ptr.aux_02(xp.keepAnyNat { Type.Ptr(Tk.Chr(TK.CHAR, this.tk.lin, this.tk.col, '\\'), null, xp) })
            }
        }
        is Expr.Upref -> {
            if (xp !is Type.Ptr) {
                this.pln.aux_02(Type_Any(this.tk))
            } else {
                this.pln.aux_02(xp.keepAnyNat { xp.pln })
            }
        }
        is Expr.TDisc -> this.tup.aux_02(Type_Any(this.tk))
        is Expr.UDisc -> this.uni.aux_02(Type_Any(this.tk))
        is Expr.UPred -> this.uni.aux_02(Type_Any(this.tk))
        is Expr.Call  -> {
            this.f.aux_02(Type_Any(this.tk))
            val xp2 = AUX.tps[this.f]!!.let { it.keepAnyNat{it} }
            this.arg.aux_02(if (xp2 is Type.Func) xp2.keepAnyNat{ xp2.inp } else Type_Any(this.tk))
        }
        is Expr.Func  -> this.block.aux_02()
    }
    this.tps_add()
}

private
fun Stmt.aux_02 () {
    when (this) {
        is Stmt.Set -> {
            this.dst.aux_02(Type_Any(this.tk))
            this.src.aux_02(AUX.tps[this.dst]!!)
        }
        is Stmt.Call -> this.call.aux_02(Type_Any(this.tk))
        is Stmt.Seq -> {
            this.s1.aux_02()
            this.s2.aux_02()
        }
        is Stmt.If -> {
            this.tst.aux_02(Type_Nat(this.tk,"int"))
            this.true_.aux_02()
            this.false_.aux_02()
        }
        is Stmt.Loop  -> this.block.aux_02()
        is Stmt.Block -> this.body.aux_02()
    }
}
