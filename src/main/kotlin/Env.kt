import java.lang.Exception

val env_PRV: MutableMap<Any,Stmt> = mutableMapOf()

fun env_prelude (s: Stmt): Stmt {
    val int = Stmt.User(Tk.Str(TK.XUSER,1,1,"Int"), false, emptyArray())
    val stdo = Stmt.Func (
        Tk.Str(TK.XVAR,1,1,"output_std"),
        Type.Func (
            Tk.Sym(TK.ARROW, 1, 1, "->"),
            Type.Any(Tk.Chr(TK.CHAR,1,1,'?')),
            Type.Unit(Tk.Sym(TK.UNIT,1,1,"()"))
        ),
        null
    )
    return Stmt.Seq(int.tk, int, Stmt.Seq(stdo.tk, stdo, s))
}

/*
 * Environment of each Stmt/Expr based on backlinks to previous Stmt.Var/Stmt.Func.
 * func f: ...
 * var x: ...
 * var y: ...
 * return f(x,y)    // x+y -> y -> x -> f -> null
 */
fun env_PRV_set (s: Stmt, cur_: Stmt?): Stmt? {
    fun aux (s: Stmt, cur_: Stmt?): Stmt? {
        var cur = cur_
        if (cur != null && (s is Stmt.Block || s is Stmt.Var || s is Stmt.User || s is Stmt.Func)) {
            val id = when (s) {
                is Stmt.Block -> null
                is Stmt.Var -> s.tk_.str
                is Stmt.User -> s.tk_.str
                is Stmt.Func -> s.tk_.str
                else -> error("impossible case")
            }
            if (id != null) {
                cur.idToStmt(id).let {
                    if (it != null) {
                        if (s is Stmt.User && it is Stmt.User && it.subs.isEmpty()) {
                            // type predeclaration
                            All_assert_tk(s.tk, it.isrec == s.isrec) {
                                "unmatching type declaration (ln ${it.tk.lin})"
                            }
                        } else {
                            All_assert_tk(s.tk, false) {
                                "invalid declaration : \"$id\" is already declared (ln ${it.tk.lin})"
                            }
                        }
                    }
                }
            }
        }
        if (cur != null) {
            env_PRV[s] = cur
        }

        fun fe (e: Expr): Boolean {
            if (cur != null && (e is Expr.Int || e is Expr.Var || e is Expr.Cons || e is Expr.Pred || e is Expr.Disc)) {
                env_PRV[e] = cur!!
            }
            return true
        }

        fun ft (tp: Type): Boolean {
            if (cur != null && tp is Type.User) {
                env_PRV[tp] = cur!!
            }
            return true
        }

        return when (s) {
            is Stmt.Pass, is Stmt.Nat, is Stmt.Break -> cur
            is Stmt.Var -> {
                s.type.visit(::ft); s.init.e.visit(::fe); s
            }
            is Stmt.User -> {
                if (s.isrec) cur = s; s.subs.forEach { it.second.visit(::ft) }; s
            }
            is Stmt.Set -> {
                s.dst.toExpr().visit(::fe); s.src.e.visit(::fe); cur
            }
            is Stmt.Call -> {
                s.call.visit(::fe); cur
            }
            is Stmt.Seq -> {
                val prv2 = aux(s.s1, cur); aux(s.s2, prv2)
            }
            is Stmt.If -> {
                s.tst.visit(::fe); aux(s.true_, cur); aux(s.false_, cur); cur
            }
            is Stmt.Func -> {
                cur = s; if (s.block != null) {
                    s.type.visit(::ft); aux(s.block, cur)
                }; s
            }
            is Stmt.Ret -> {
                s.e.e.visit(::fe); cur
            }
            is Stmt.Loop -> {
                aux(s.block, cur); cur
            }
            is Stmt.Block -> {
                aux(s.body, s); cur
            }
        }
    }
    return aux(s,cur_)
}

fun Any.env_dump () {
    val env = env_PRV[this]
    println(env)
    if (env != null) {
        env.env_dump()
    }
}

fun Any.idToStmt (id: String): Stmt? {
    //println("$id: $this")
    return when {
        (this is Stmt.Var  && this.tk_.str==id) -> this
        (this is Stmt.Func && this.tk_.str==id) -> this
        (this is Stmt.User && this.tk_.str==id) -> this
        (env_PRV[this] == null) -> null
        else -> env_PRV[this]!!.idToStmt(id)
    }
}

fun Expr.subType (): Type? {
    val (sup,sub) = when (this) {
        is Expr.Cons -> Pair(this.sup.str,this.sub.str)
        is Expr.Disc -> Pair((this.e.toType() as Type.User).tk_.str, this.tk_.str)
        is Expr.Pred -> Pair((this.e.toType() as Type.User).tk_.str, this.tk_.str)
        else -> error("bug found")
    }
    try {
        val user = this.idToStmt(sup) as Stmt.User
        return if (user.isrec && sub=="Nil") {
            Type.Unit(Tk.Sym(TK.UNIT,user.tk.lin,user.tk.col,"()"))
        } else {
            user.subs.first { (id, _) -> id.str == sub }.second
        }
    } catch (_: Exception) {
        return null
    }
}

fun Expr.toType (): Type {
    val outer = this
    fun Type.User.set_env (): Type.User {
        env_PRV[this] = env_PRV[outer]!!
        return this
    }
    return when (this) {
        is Expr.Unk   -> Type.Any(this.tk_)
        is Expr.Unit  -> Type.Unit(this.tk_)
        is Expr.Int   -> Type.User(Tk.Str(TK.XUSER,this.tk.lin,this.tk.col,"Int")).set_env()
        is Expr.Nat   -> Type.Nat(this.tk_)
        is Expr.Upref -> Type.Ptr(this.tk_, this.e.toType())
        is Expr.Dnref -> (this.e.toType() as Type.Ptr).tp
        is Expr.Var   -> {
            val s = this.idToStmt(this.tk_.str)!!
            when (s) {
                is Stmt.Var -> s.type
                is Stmt.Func -> s.type
                else -> error("bug found")
            }
        }
        is Expr.Tuple -> Type.Tuple(this.tk_, this.vec.map{it.e.toType()}.toTypedArray())
        is Expr.Call  -> if (this.f is Expr.Nat) Type.Nat(this.f.tk_) else (this.f.toType() as Type.Func).out
        is Expr.Index -> (this.e.toType() as Type.Tuple).vec[this.tk_.num-1]
        is Expr.Cons  -> Type.User(Tk.Str(TK.XUSER,this.tk.lin,this.tk.col, this.sup.str)).set_env()
        is Expr.Disc  -> this.subType()!!
        is Expr.Pred  -> Type.User(Tk.Str(TK.XUSER,this.tk.lin,this.tk.col, "Bool")).set_env()
    }
}

fun check_dcls (s: Stmt) {
    fun ft (tp: Type): Boolean {
        when (tp) {
            is Type.User -> {
                All_assert_tk(tp.tk, tp.idToStmt(tp.tk_.str) != null) {
                    "undeclared type \"${tp.tk_.str}\""
                }
            }
        }
        return true
    }
    fun fe (e: Expr): Boolean {
        when (e) {
            is Expr.Var -> {
                All_assert_tk(e.tk, e.idToStmt(e.tk_.str) != null) {
                    "undeclared variable \"${e.tk_.str}\""
                }
            }
            is Expr.Cons -> {
                val sup = (e.toType() as Type.User).tk_.str
                All_assert_tk(e.tk, e.idToStmt(sup) != null) {
                    "undeclared type \"$sup\""
                }
                All_assert_tk(e.tk, e.subType() != null) {
                    "undeclared subcase \"${e.sub.str}\""
                }
            }
            is Expr.Disc, is Expr.Pred -> {
                val ee = if (e is Expr.Disc) e.e else (e as Expr.Pred).e
                val tk = if (e is Expr.Disc) e.tk_ else (e as Expr.Pred).tk_
                val tpe = ee.toType()
                All_assert_tk(ee.tk, tpe is Type.User) {
                    "invalid `.´ : expected user type"
                }
                All_assert_tk(e.tk, e.subType() != null) {
                    "invalid `.´ : undeclared subcase \"${tk.str}\""
                }
            }
        }
        return true
    }
    fun fs (s: Stmt): Boolean {
        fun Stmt.User.isHasRec (): Boolean {
            fun aux (tp: Type): Boolean {
                return when (tp) {
                    is Type.Any, is Type.Unit, is Type.Nat, is Type.Ptr -> false
                    is Type.Tuple -> tp.vec.any { aux(it) }
                    is Type.User  -> (tp.idToStmt(tp.tk_.str) as Stmt.User).let { it.isrec || it.isHasRec() }
                    else -> false
                }
            }
            return this.subs.any { aux(it.second) }
        }
        when (s) {
            is Stmt.User -> {
                All_assert_tk(s.tk, s.subs.isEmpty() || s.isrec==s.isHasRec()) {
                    val exun = if (s.isrec) "unexpected" else "expected"
                    "invalid type declaration : $exun `@rec´"
                }
            }
        }
        return true
    }
    s.visit(::fs,::fe,::ft)
}

fun Type.isSupOf (sub: Type): Boolean {
    return when {
        (this is Type.Any || sub is Type.Any) -> true
        (this is Type.Nat || sub is Type.Nat) -> true
        (this::class != sub::class) -> false
        (this is Type.Ptr && sub is Type.Ptr) -> this.tp.isSupOf(sub.tp)
        (this is Type.Tuple && sub is Type.Tuple) ->
            (this.vec.size==sub.vec.size) && this.vec.zip(sub.vec).all { (x,y) -> x.isSupOf(y) }
        (this is Type.User && sub is Type.User) -> (this.tk_.str == sub.tk_.str)
        else -> true
    }
}

fun Type.ishasrec (): Boolean {
    return when (this) {
        is Type.Any, is Type.Unit, is Type.Nat, is Type.Ptr, is Type.Func -> false
        is Type.Tuple -> this.vec.any { it.ishasrec() }
        is Type.User  -> (this.idToStmt(this.tk_.str) as Stmt.User).isrec
    }
}

fun Type.ishasptr (): Boolean {
    return when (this) {
        is Type.Any, is Type.Unit, is Type.Nat, is Type.Func -> false
        is Type.Ptr   -> true
        is Type.Tuple -> this.vec.any { it.ishasptr() }
        is Type.User  -> (this.idToStmt(this.tk_.str) as Stmt.User).let {
            !it.isrec && it.subs.map { it.second }.any { it.ishasptr() }
        }
    }
}

fun check_types (S: Stmt) {
    fun fe (e: Expr): Boolean {
        when (e) {
            /*
            is Expr.Upref -> {
                All_assert_tk(e.e.tk, e.e.toType() !is Type.Ptr) {
                    "invalid `\\` : unexpected pointer type"
                }
            }
             */
            is Expr.Dnref -> {
                All_assert_tk(e.tk, e.e.toType() is Type.Ptr) {
                    "invalid `/` : expected pointer type"
                }
            }
            is Expr.Call -> {
                val inp = e.f.toType().let { if (it is Type.Func) it.inp else (it as Type.Nat) }
                All_assert_tk(e.f.tk, inp.isSupOf(e.arg.e.toType())) {
                    "invalid call to \"${(e.f as Expr.Var).tk_.str}\" : type mismatch"
                }
            }
            is Expr.Cons -> {
                All_assert_tk(e.sub, e.subType()!!.isSupOf(e.arg.e.toType())) {
                    "invalid constructor \"${e.sub.str}\" : type mismatch"
                }
            }
        }
        return true
    }
    fun fs (s: Stmt): Boolean {
        when (s) {
            is Stmt.Var -> {
                All_assert_tk(s.tk, s.type.isSupOf(s.init.e.toType())) {
                    "invalid assignment to \"${s.tk_.str}\" : type mismatch"
                }
            }
            is Stmt.Set -> {
                All_assert_tk(s.tk, s.dst.toExpr().toType().isSupOf(s.src.e.toType())) {
                    when {
                        (s.dst !is Attr.Var) -> "invalid assignment : type mismatch"
                        (s.dst.tk_.str == "_ret_") -> "invalid return : type mismatch"
                        else -> "invalid assignment to \"${s.dst.tk_.str}\" : type mismatch"
                    }
                }
            }
        }
        return true
    }
    S.visit(::fs, ::fe, null)
}

fun Expr.isconst (): Boolean {
    return when (this) {
        is Expr.Unit, is Expr.Unk, is Expr.Int, is Expr.Call, is Expr.Cons, is Expr.Tuple, is Expr.Pred -> true
        is Expr.Var, is Expr.Nat, is Expr.Dnref -> false
        is Expr.Index -> this.e.isconst()
        is Expr.Disc  -> this.e.isconst()
        is Expr.Upref -> this.e.isconst()
    }
}

fun check_xexprs (S: Stmt) {
    fun aux (e: XExpr) {
        val isrec = e.e.toType().ishasrec()
        val iscst = e.e.isconst()
        when {
            (e.x == null) -> All_assert_tk(e.e.tk, !isrec || iscst) {
                "invalid expression : expected operation modifier"
            }
            (e.x.enu == TK.COPY) -> All_assert_tk(e.x, isrec && !iscst) {
                "invalid `copy` : expected recursive variable"
            }
            (e.x.enu == TK.MOVE) -> All_assert_tk(e.x, isrec && !iscst) {
                "invalid `move` : expected recursive variable"
            }
            (e.x.enu == TK.BORROW) -> All_assert_tk(e.x, e.e.toType().let { it is Type.Ptr && it.tp.ishasrec() }) {
                "invalid `borrow` : expected pointer to recursive variable"
            }
        }
    }
    fun fs (s: Stmt): Boolean {
        when (s) {
            is Stmt.Var -> aux(s.init)
            is Stmt.Set -> aux(s.src)
            is Stmt.Ret -> aux(s.e)
        }
        return true
    }
    fun fe (e: Expr): Boolean {
        when (e) {
            is Expr.Tuple -> e.vec.map { aux(it) }
            is Expr.Call  -> aux(e.arg)
        }
        return true
    }
    S.visit(::fs, ::fe, null)
}

fun Stmt.getDepth (): Int {
    fun aux (s: Stmt): Int {
        return env_PRV[s].let {
            when {
                (it == null) -> 0
                (it is Stmt.Block) -> 1 + aux(it)
                //(it is Stmt.Func) -> 1 + aux(it)
                else -> aux(it)
            }
        }
    }
    return if (this is Stmt.Var && this.outer) {
        this.idToStmt("arg")!!.getDepth()
    } else {
        aux(this)
    }
}

fun Expr.getDepth (caller: Int, hold: Boolean): Pair<Int,Stmt?> {
    return when (this) {
        is Expr.Int, is Expr.Unk, is Expr.Unit, is Expr.Nat, is Expr.Pred -> Pair(0, null)
        is Expr.Var -> {
            val dcl = this.idToStmt(this.tk_.str)!!
            return if (hold) Pair(dcl.getDepth(), dcl) else Pair(0, null)
        }
        is Expr.Upref ->  {
            val (depth,dcl) = this.e.getDepth(caller, hold)
            if (dcl is Stmt.Var) {
                val inc = if (dcl.tk_.str == "arg" || dcl.outer) 1 else 0
                Pair(depth + inc, dcl)
            } else {
                Pair(depth, dcl)
            }
        }
        is Expr.Dnref -> this.e.getDepth(caller, hold)
        is Expr.Index -> this.e.getDepth(caller, hold)
        is Expr.Disc -> this.e.getDepth(caller, hold)
        is Expr.Call -> this.f.toType().let {
            when (it) {
                is Type.Nat -> Pair(0, null)
                is Type.Func -> if (!it.out.ishasptr()) Pair(0,null) else {
                    // substitute args depth/id by caller depth/id
                    Pair(caller, this.f.idToStmt((this.f as Expr.Var).tk_.str))
                }
                else -> error("bug found")
            }
        }
        is Expr.Tuple -> this.vec.map { it.e.getDepth(caller,it.e.toType().ishasptr()) }.maxByOrNull { it.first }!!
        is Expr.Cons -> this.arg.e.getDepth(caller, this.subType()!!.ishasptr())
    }
}

fun check_pointers (S: Stmt) {
    fun s2id (s: Stmt): String {
        return when (s) {
            is Stmt.Var -> s.tk_.str
            is Stmt.Func -> s.tk_.str
            else -> error("bug found")
        }
    }
    fun fs (s: Stmt): Boolean {
        when (s) {
            is Stmt.Var -> {
                val (src_depth, src_dcl) = s.init.e.getDepth(s.getDepth(), s.type.ishasptr())
                All_assert_tk(s.tk, s.getDepth() >= src_depth) {
                    "invalid assignment : cannot hold local pointer \"${s2id(src_dcl!!)}\" (ln ${src_dcl!!.tk.lin})"
                }
            }
            is Stmt.Set -> {
                val (src_depth, src_dcl) = s.src.e.getDepth(s.getDepth(), s.dst.toExpr().toType().ishasptr())
                All_assert_tk(s.tk, s.dst.toExpr().getDepth(s.getDepth(), s.dst.toExpr().toType().ishasptr()).first >= src_depth) {
                    "invalid assignment : cannot hold local pointer \"${s2id(src_dcl!!)}\" (ln ${src_dcl!!.tk.lin})"
                }
            }
        }
        return true
    }
    S.visit(::fs, null, null)
}