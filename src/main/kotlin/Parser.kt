sealed class Type (val tk: Tk) {
    data class Any   (val tk_: Tk.Chr): Type(tk_)
    data class Unit  (val tk_: Tk.Sym): Type(tk_)
    data class Nat   (val tk_: Tk.Str): Type(tk_)
    data class User  (val tk_: Tk.Str): Type(tk_)
    data class Tuple (val tk_: Tk.Chr, val vec: Array<Type>): Type(tk_)
    data class Func  (val tk_: Tk.Sym, val inp: Type, val out: Type): Type(tk_)
    data class Ptr   (val tk_: Tk.Chr, val tp: Type): Type(tk_)
}

sealed class Expr (val tk: Tk) {
    data class Unk   (val tk_: Tk.Chr): Expr(tk_)
    data class Unit  (val tk_: Tk.Sym): Expr(tk_)
    data class Int   (val tk_: Tk.Num): Expr(tk_)
    data class Var   (val tk_: Tk.Str): Expr(tk_)
    data class Nat   (val tk_: Tk.Str): Expr(tk_)
    data class Empty (val tk_: Tk.Str): Expr(tk_)
    data class Tuple (val tk_: Tk.Chr, val vec: Array<Expr>): Expr(tk_)
    data class Cons  (val tk_: Tk.Chr, val sup: Tk.Str, val sub: Tk.Str, val arg: Expr): Expr(tk_)
    data class Dnref (val tk_: Tk, val e: Expr): Expr(tk_)
    data class Upref (val tk_: Tk.Chr, val e: Expr): Expr(tk_)
    data class Index (val tk_: Tk.Num, val e: Expr): Expr(tk_)
    data class Pred  (val tk_: Tk.Str, val e: Expr): Expr(tk_)
    data class Disc  (val tk_: Tk.Str, val e: Expr): Expr(tk_)
    data class Call  (val tk_: Tk.Key, val f: Expr, val arg: Expr): Expr(tk_)
}

sealed class Stmt (val tk: Tk) {
    data class Pass  (val tk_: Tk) : Stmt(tk_)
    data class Var   (val tk_: Tk.Str, val type: Type, val init: Expr) : Stmt(tk_)
    data class Set   (val tk_: Tk.Chr, val dst: Expr, val src: Expr) : Stmt(tk_)
    data class User  (val tk_: Tk.Str, val isrec: Boolean, val subs: Array<Pair<Tk.Str,Type>>) : Stmt(tk_)
    data class Nat   (val tk_: Tk.Str) : Stmt(tk_)
    data class Call  (val tk_: Tk.Key, val call: Expr.Call) : Stmt(tk_)
    data class Seq   (val tk_: Tk, val s1: Stmt, val s2: Stmt) : Stmt(tk_)
    data class If    (val tk_: Tk.Key, val tst: Expr, val true_: Block, val false_: Block) : Stmt(tk_)
    data class Func  (val tk_: Tk.Str, val type: Type.Func, val block: Block?) : Stmt(tk_)
    data class Ret   (val tk_: Tk.Key, val e: Expr) : Stmt(tk_)
    data class Loop  (val tk_: Tk.Key, val block: Block) : Stmt(tk_)
    data class Break (val tk_: Tk.Key) : Stmt(tk_)
    data class Block (val tk_: Tk.Chr, val body: Stmt) : Stmt(tk_)
}

fun parser_type (all: All): Type? {
    fun one (): Type? { // Unit, Nat, User, Tuple
        return when {
            all.accept(TK.UNIT)  -> Type.Unit(all.tk0 as Tk.Sym)
            all.accept(TK.XNAT)  -> Type.Nat(all.tk0 as Tk.Str)
            all.accept(TK.XUSER) -> Type.User(all.tk0 as Tk.Str)
            all.accept(TK.CHAR,'\\') -> {
                val tk0 = all.tk0 as Tk.Chr
                val tp = one()
                if (tp == null) {
                    return null
                }
                return Type.Ptr(tk0, tp)
            }
            all.accept(TK.CHAR,'(') -> { // Type.Tuple
                val tk0 = all.tk0 as Tk.Chr
                val tp = parser_type(all)
                when {
                    (tp == null)                      -> return null
                    all.accept(TK.CHAR,')')      -> return tp
                    !all.accept_err(TK.CHAR,',') -> return null
                }
                val tps = arrayListOf(tp!!)
                while (true) {
                    val tp2 = parser_type(all)
                    if (tp2 == null) {
                        return null
                    }
                    tps.add(tp2)
                    if (!all.accept(TK.CHAR,',')) {
                        break
                    }
                }
                if (!all.accept_err(TK.CHAR,')')) {
                    return null
                }
                return Type.Tuple(tk0, tps.toTypedArray())
            }
            else -> { all.err_expected("type") ; null }
        }
    }

    // Func: right associative
    val ret = one()
    return when {
        (ret == null) -> null
        all.accept(TK.ARROW) -> {
            val tk0 = all.tk0 as Tk.Sym
            val oth = parser_type(all) // right associative
            if (oth==null) null else Type.Func(tk0, ret, oth)
        }
        else -> ret
    }
}

fun parser_expr (all: All, canpre: Boolean): Expr? {
    fun one (): Expr? {
        return when {
            all.accept(TK.UNIT)   -> Expr.Unit(all.tk0 as Tk.Sym)
            all.accept(TK.XNUM)   -> Expr.Int(all.tk0 as Tk.Num)
            all.accept(TK.XVAR)   -> Expr.Var(all.tk0 as Tk.Str)
            all.accept(TK.XNAT)   -> Expr.Nat(all.tk0 as Tk.Str)
            all.accept(TK.XEMPTY) -> Expr.Empty(all.tk0 as Tk.Str)
            all.accept(TK.XUSER) -> {
                val sup = all.tk0 as Tk.Str
                if (!all.accept_err(TK.CHAR,'.')) {
                    return null
                }
                val tk0 = all.tk0 as Tk.Chr
                if (!all.accept_err(TK.XUSER)) {
                    return null
                }
                val sub = all.tk0 as Tk.Str
                val arg = parser_expr(all, false)
                when {
                    (arg != null) -> Expr.Cons(tk0, sup, sub, arg)
                    !all.consumed(sub) -> Expr.Cons(tk0, sup, sub, Expr.Unit(Tk.Sym(TK.UNIT,all.tk1.lin,all.tk1.col,"()")))
                    else -> null
                }
            }
            all.accept(TK.CHAR,'\\') -> {
                val tk0 = all.tk0 as Tk.Chr
                val e = parser_expr(all,false)
                when (e) {
                    null -> null
                    is Expr.Nat, is Expr.Var, is Expr.Index -> Expr.Upref(tk0,e)
                    else -> { all.err_tk(all.tk0, "unexpected operand to `\\´") ; null }
                }
            }
            all.accept(TK.CHAR,'(') -> { // Expr.Tuple
                val tk0 = all.tk0 as Tk.Chr
                val e = parser_expr(all,false)
                when {
                    (e == null)                       -> return null
                    all.accept(TK.CHAR,')')      -> return e
                    !all.accept_err(TK.CHAR,',') -> return null
                }
                val es = arrayListOf(e!!)
                while (true) {
                    val e2 = parser_expr(all,false)
                    if (e2 == null) {
                        return null
                    }
                    es.add(e2)
                    if (!all.accept(TK.CHAR,',')) {
                        break
                    }
                }
                if (!all.accept_err(TK.CHAR,')')) {
                    return null
                }
                return Expr.Tuple(tk0, es.toTypedArray())
            }
            else -> { all.err_expected("expression") ; null }
        }
    }

    var ispre = (canpre && (all.accept(TK.CALL) || all.accept(TK.OUT)))
    val tk_pre = all.tk0

    var ret = one()
    if (ret == null) {
        return null
    }

    while (true) {
        val tk_bef = all.tk0
        when {
            // INDEX, DISC, PRED
            !ispre && all.accept(TK.CHAR,'.') -> {
                when {
                    all.accept(TK.XNUM) -> {
                        ret = Expr.Index(all.tk0 as Tk.Num, ret!!)
                    }
                    all.accept(TK.XUSER) || all.accept(TK.XEMPTY) -> {
                        val tk = all.tk0 as Tk.Str
                        when {
                            all.accept(TK.CHAR,'?') -> ret = Expr.Pred(tk,ret!!)
                            all.accept(TK.CHAR,'!') -> ret = Expr.Disc(tk,ret!!)
                            else -> {
                                all.err_expected("`?´ or `!´")
                                return null
                            }
                        }
                    }
                    else -> {
                        all.err_expected("index or subtype")
                        return null
                    }
                }
            }
            // DNREF
            !ispre && all.accept(TK.CHAR,'\\') -> when (ret) {
                is Expr.Nat, is Expr.Var, is Expr.Upref, is Expr.Dnref, is Expr.Index, is Expr.Call -> ret = Expr.Dnref(all.tk0,ret)
                else -> { all.err_tk(all.tk0, "unexpected operand to `\\´") ; return null }
            }
            // CALL
            else -> {
                var e = parser_expr(all, false)
                if (e == null) {
                    when {
                        all.consumed(tk_bef) -> return null // failed parser_expr and consumed input: error
                        ispre    -> e = Expr.Unit(Tk.Sym(TK.UNIT,all.tk1.lin,all.tk1.col,"()")) // call f -> call f ()
                        !ispre   -> break // just e (not a call)
                    }
                }
                if (ret is Expr.Var || (ret is Expr.Nat && (!ispre || tk_pre.enu==TK.CALL))) {
                        // ok
                } else {
                    all.err_tk(ret!!.tk, "expected function")
                    return null
                }
                val tk0 = if (ispre) tk_pre as Tk.Key else Tk.Key(TK.CALL,tk_pre.lin,tk_pre.col,"call")
                if (ispre && tk_pre.enu==TK.OUT) {
                    assert(ret is Expr.Var)
                    ret = Expr.Var (
                        Tk.Str(TK.XVAR,ret.tk.lin,ret.tk.col,"output_"+(ret.tk as Tk.Str).str)
                    )
                }
                ret = Expr.Call(tk0, ret, e!!)
            }
        }
        ispre = false
    }
    return ret
}

fun parser_stmt (all: All): Stmt? {
    fun parser_block (): Stmt.Block? {
        if (!all.accept_err(TK.CHAR,'{')) {
            return null
        }
        val tk0 = all.tk0 as Tk.Chr
        val ret = parser_stmts(all, Pair(TK.CHAR,'}'))
        return when {
            (ret == null) -> null
            !all.accept_err(TK.CHAR,'}') -> null
            else -> Stmt.Block(tk0, ret)
        }
    }
    when {
        all.accept(TK.VAR)   -> {
            if (!all.accept_err(TK.XVAR)) {
                return null
            }
            val tk_id = all.tk0 as Tk.Str
            if (!all.accept_err(TK.CHAR,':')) {
                return null
            }
            val tp = parser_type(all)
            if (tp == null) {
                return null
            }
            if (!all.accept_err(TK.CHAR,'=')) {
                return null
            }
            val e = parser_expr(all, true)
            if (e == null) {
                return null
            }
            return Stmt.Var(tk_id, tp, e)
        }
        all.accept(TK.SET)   -> {
            val dst = parser_expr(all, false)
            if (dst == null) {
                return null
            }
            if (!all.accept_err(TK.CHAR,'=')) {
                return null
            }
            val tk0 = all.tk0 as Tk.Chr
            val src = parser_expr(all, true)
            if (src == null) {
                return null
            }
            return Stmt.Set(tk0, dst, src)
        }
        all.accept(TK.NAT)   -> {
            if (!all.accept_err(TK.XNAT)) {
                return null
            }
            return Stmt.Nat(all.tk0 as Tk.Str)
        }
        all.accept(TK.TYPE)  -> {
            if (!all.accept_err(TK.XUSER)) {
                return null
            }
            val tk_id = all.tk0 as Tk.Str
            if (!all.accept_err(TK.CHAR,'{')) {
                return null
            }

            fun parser_sub (): Pair<Tk.Str,Type>? {
                if (!all.accept_err(TK.XUSER)) {
                    return null
                }
                val tk = all.tk0 as Tk.Str
                if (!all.accept_err(TK.CHAR,':')) {
                    return null
                }
                val tp = parser_type(all)
                if (tp == null) {
                    return null
                }
                return Pair(tk,tp)
            }

            val sub1 = parser_sub()
            if (sub1 == null) {
                return null
            }

            val subs = arrayListOf(sub1)
            while (true) {
                all.accept(TK.CHAR,';')
                val subi = parser_sub()
                if (subi == null) {
                    break
                }
                subs.add(subi)
            }

            if (!all.accept_err(TK.CHAR,'}')) {
                return null
            }

            return Stmt.User(tk_id,false,subs.toTypedArray())
        }
        all.check(TK.CALL) || all.check(TK.OUT) -> {
            val tk0 = all.tk1 as Tk.Key
            val e = parser_expr(all, true)
            if (e == null) {
                return null
            }
            return Stmt.Call(tk0, e as Expr.Call)
        }
        all.accept(TK.IF)    -> {
            val tk0 = all.tk0 as Tk.Key
            val tst = parser_expr(all, false)
            if (tst == null) {
                return null
            }
            val true_ = parser_block()
            if (true_ == null) {
                return null
            }
            if (!all.accept(TK.ELSE)) {
                return Stmt.If(tk0, tst, true_, Stmt.Block(Tk.Chr(TK.CHAR,all.tk1.lin,all.tk1.col,'{'),Stmt.Pass(all.tk0)))
            }
            val false_ = parser_block()
            if (false_ == null) {
                return null
            }
            return Stmt.If(tk0, tst, true_, false_)
        }
        all.accept(TK.FUNC)  -> {
            if (!all.accept_err(TK.XVAR)) {
                return null
            }
            val tk_id = all.tk0 as Tk.Str
            if (!all.accept_err(TK.CHAR,':')) {
                return null
            }
            val tp = parser_type(all)
            when (tp) {
                null -> return null
                !is Type.Func -> { all.err_tk(all.tk0, "expected function type") ; return null}
            }
            assert(tp is Type.Func)
            val block = parser_block()
            if (block == null) {
                return null
            }

            val lin = block.tk.lin
            val col = block.tk.col
            val xblock = Stmt.Block(block.tk_,
                Stmt.Seq(block.tk,
                    Stmt.Var (
                        Tk.Str(TK.XVAR,lin,col,"arg"),
                        (tp as Type.Func).inp,
                        Expr.Nat(Tk.Str(TK.XNAT,lin,col,"_arg_"))
                    ),
                    Stmt.Seq(block.tk,
                        Stmt.Var (
                            Tk.Str(TK.XVAR,lin,col,"_ret_"),
                            tp.out,
                            Expr.Unk(Tk.Chr(TK.CHAR,lin,col,'?'))
                        ),
                        block,
                    )
                )
            )
            return Stmt.Func(tk_id, tp, xblock)
        }
        all.accept(TK.RET)   -> {
            val tk0 = all.tk0 as Tk.Key
            var e = parser_expr(all, false)
            when {
                (e != null)       -> {}
                all.consumed(tk0) -> return null
                else              -> e = Expr.Unit(Tk.Sym(TK.UNIT,all.tk1.lin,all.tk1.col,"()"))
            }
            return Stmt.Seq (tk0,
                Stmt.Set (
                    Tk.Chr(TK.CHAR,tk0.lin,tk0.col,'='),
                    Expr.Var(Tk.Str(TK.XVAR,tk0.lin,tk0.col,"_ret_")),
                    e
                ),
                Stmt.Ret(tk0, e)
            )
        }
        all.accept(TK.LOOP)  -> {
            val tk0 = all.tk0 as Tk.Key
            val block = parser_block()
            if (block == null) {
                return null
            }
            return Stmt.Loop(tk0, block)
        }
        all.accept(TK.BREAK) -> return Stmt.Break(all.tk0 as Tk.Key)
        all.check(TK.CHAR,'{') -> return parser_block()
        else -> { all.err_expected("statement") ; return null }
    }
}

fun parser_stmts (all: All, opt: Pair<TK,Char?>): Stmt? {
    fun enseq (s1: Stmt?, s2: Stmt?): Stmt? {
        return when {
            (s1 == null)      -> s2
            (s2 == null)      -> s1
            (s1 is Stmt.Pass) -> s2
            (s2 is Stmt.Pass) -> s1
            else -> Stmt.Seq(s1.tk, s1, s2)
        }
    }
    var ret: Stmt = Stmt.Pass(all.tk0)
    while (true) {
        all.accept(TK.CHAR, ';')
        val tk_bef = all.tk0
        val s = parser_stmt(all)
        when {
            (s != null) -> ret = enseq(ret,s)!!
            all.consumed(tk_bef) -> return null
            all.check(opt.first, opt.second) -> break
            else -> return null
        }
    }
    return ret
}