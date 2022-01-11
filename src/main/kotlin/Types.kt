fun Stmt.setTypes () {
    fun fe (e: Expr) {
        e.type = e.type ?: when (e) {
            is Expr.Upref -> e.pln.type!!.let {
                val lbl = e.toBaseVar()?.tk_?.str ?: "global"
                Type.Ptr(e.tk_, Tk.Scope(TK.XSCOPE,e.tk.lin,e.tk.col, lbl,null), null, it).build(e)
            }
            is Expr.Dnref -> e.ptr.type.let {
                if (it is Type.Nat) it else {
                    All_assert_tk(e.tk, it is Type.Ptr) {
                        "invalid operand to `\\´ : not a pointer"
                    }
                    (it as Type.Ptr).pln
                }
            }
            is Expr.TCons -> Type.Tuple(e.tk_, e.arg.map { it.type!! }.toTypedArray()).build(e)
            is Expr.New   -> Type.Ptr(Tk.Chr(TK.CHAR,e.tk.lin,e.tk.col,'/'), e.scp1!!, null, e.arg.type!!).build(e)
            is Expr.Out   -> Type.Unit(Tk.Sym(TK.UNIT, e.tk.lin, e.tk.col, "()")).build(e)
            is Expr.Call -> {
                e.f.type.let {
                    when (it) {
                        is Type.Nat -> it
                        is Type.Func -> {
                            val MAP = it.scp1s.map { Pair(it.lbl,it.num) }.zip(e.scp1s.first.map { Pair(it.lbl,it.num) }).toMap()
                            fun f (tk: Tk.Scope): Tk.Scope {
                                return MAP[Pair(tk.lbl,tk.num)].let { if (it == null) tk else
                                    Tk.Scope(TK.XSCOPE, tk.lin, tk.col, it.first, it.second)
                                }
                            }
                            fun map (tp: Type): Type {
                                return when (tp) {
                                    is Type.Ptr   -> Type.Ptr(tp.tk_, f(tp.scp1), null, map(tp.pln))
                                    is Type.Tuple -> Type.Tuple(tp.tk_, tp.vec.map { map(it) }.toTypedArray())
                                    is Type.Union -> Type.Union(tp.tk_, tp.isrec, tp.vec.map { map(it) }.toTypedArray())
                                    is Type.Func  -> Type.Func(tp.tk_, if (tp.clo1==null) tp.clo1 else f(tp.clo1), tp.scp1s.map { f(it) }.toTypedArray(), map(tp.inp), map(tp.out))
                                    else -> tp
                                }
                            }
                            map(it.out).buildAll(e)
                        }
                        else -> {
                            All_assert_tk(e.f.tk, false) {
                                "invalid call : not a function"
                            }
                            error("impossible case")
                        }
                    }
                }.clone(e.f, e.f.tk.lin, e.f.tk.col)
            }
            is Expr.TDisc -> e.tup.type.let {
                All_assert_tk(e.tk, it is Type.Tuple) {
                    "invalid discriminator : type mismatch"
                }
                val (MIN, MAX) = Pair(1, (it as Type.Tuple).vec.size)
                All_assert_tk(e.tk, MIN <= e.tk_.num && e.tk_.num <= MAX) {
                    "invalid discriminator : out of bounds"
                }
                it.vec[e.tk_.num - 1]
            }
            is Expr.UDisc, is Expr.UPred -> {
                val (tk_,uni) = when (e) {
                    is Expr.UPred -> Pair(e.tk_,e.uni)
                    is Expr.UDisc -> Pair(e.tk_,e.uni)
                    else -> error("impossible case")
                }
                val tp = uni.type!!

                All_assert_tk(e.tk, tp is Type.Union) {
                    "invalid discriminator : not an union"
                }
                assert(tk_.num!=0 || tp.isrec()) { "bug found" }

                val (MIN, MAX) = Pair(if (tp.isrec()) 0 else 1, (tp as Type.Union).vec.size)
                All_assert_tk(e.tk, MIN <= tk_.num && tk_.num <= MAX) {
                    "invalid discriminator : out of bounds"
                }

                when (e) {
                    is Expr.UDisc -> if (e.tk_.num == 0) {
                        Type.Unit(Tk.Sym(TK.UNIT, e.tk.lin, e.tk.col, "()")).build(e)
                    } else {
                        tp.expand()[e.tk_.num - 1]
                    }
                    is Expr.UPred -> Type.Nat(Tk.Nat(TK.XNAT, e.tk.lin, e.tk.col, null, "int")).build(e)
                    else -> error("bug found")
                }
            }
            is Expr.Var -> e.env()!!.toType()
            else -> error("bug found")
        }
    }
    this.visit(null, ::fe, null)
}

fun Tk.Scope.scope (up: Any): Scope {
    val lvl = up.ups_tolist().filter { it is Expr.Func }.count() // level of function nesting
    return when (this.lbl) { // (... || it is Expr.Func) b/c of arg/ret, otherwise no block up to outer func
        "global" -> Scope(lvl, null, 0)
        "local"  -> Scope(lvl, null, up.ups_tolist().let { it.count { it is Stmt.Block || it is Expr.Func } })
        else -> {
            val blk = up.env(this.lbl)
            /*
            println(this.lbl)
            println(up)
            println(up.env_all())
             */
            if (blk != null) {
                //println(this.lbl)
                val one = if (blk is Stmt.Block) 1 else 0
                Scope(lvl, null, one + blk.ups_tolist().let { it.count { it is Stmt.Block || it is Expr.Func } })
            } else {    // false = relative to function block
                Scope(lvl, this.lbl, (this.num ?: 0))
            }
        }
    }
}

fun Stmt.setScopes () {
    fun ft (tp: Type) {
        when (tp) {
            is Type.Ptr -> {
                tp.scp2 = tp.scp1.scope(tp)
            }
        }
    }
    this.visit(null, null, ::ft)
}