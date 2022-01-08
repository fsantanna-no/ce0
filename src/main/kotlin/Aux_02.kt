fun Stmt.aux_tps () {
    fun fe (e: Expr) {
        AUX.tps[e] = when (e) {
            is Expr.Unit  -> Type.Unit(e.tk_).up(e)
            is Expr.Nat   -> e.type ?: Type.Nat(e.tk_).up(e)
            is Expr.Upref -> AUX.tps[e.pln]!!.let {
                Type.Ptr(e.tk_, Tk.Scope(TK.XSCOPE,e.tk.lin,e.tk.col,"var",null), it).up(it)
            }
            is Expr.Dnref -> AUX.tps[e.ptr].let {
                if (it is Type.Nat) it else {
                    All_assert_tk(e.tk, it is Type.Ptr) {
                        "invalid operand to `\\´ : not a pointer"
                    }
                    (it as Type.Ptr).pln
                }
            }
            is Expr.TCons -> Type.Tuple(e.tk_, e.arg.map { AUX.tps[it]!! }.toTypedArray()).up(e)
            is Expr.UCons -> e.type
            is Expr.New   -> Type.Ptr(Tk.Chr(TK.CHAR,e.tk.lin,e.tk.col,'/'), e.scp!!, AUX.tps[e.arg]!!).up(e)
            is Expr.Inp   -> e.type
            is Expr.Out   -> Type.Unit(Tk.Sym(TK.UNIT, e.tk.lin, e.tk.col, "()")).up(e)
            is Expr.Call -> {
                AUX.tps[e.f].let {
                    when (it) {
                        is Type.Nat -> it
                        is Type.Func -> {
                            val MAP = it.scps.map { Pair(it.lbl,it.num) }.zip(e.sinps.map { Pair(it.lbl,it.num) }).toMap()
                            fun f (tk: Tk.Scope): Tk.Scope {
                                return MAP[Pair(tk.lbl,tk.num)].let { if (it == null) tk else
                                    Tk.Scope(TK.XSCOPE, tk.lin, tk.col, it.first, it.second)
                                }
                            }
                            fun map (tp: Type): Type {
                                return when (tp) {
                                    is Type.Ptr   -> Type.Ptr(tp.tk_, f(tp.scope), map(tp.pln))
                                    is Type.Tuple -> Type.Tuple(tp.tk_, tp.vec.map { map(it) }.toTypedArray())
                                    is Type.Union -> Type.Union(tp.tk_, tp.isrec, tp.vec.map { map(it) }.toTypedArray())
                                    is Type.Func  -> Type.Func(tp.tk_, if (tp.clo==null) tp.clo else f(tp.clo), tp.scps.map { f(it) }.toTypedArray(), map(tp.inp), map(tp.out))
                                    else -> tp
                                }
                            }
                            map(it.out)
                        }
                        else -> {
                            All_assert_tk(e.f.tk, false) {
                                "invalid call : not a function"
                            }
                            error("impossible case")
                        }
                    }
                }.lincol(e.tk.lin,e.tk.col).let {
                    it.aux_upsenvs(e)
                    it
                }
            }
            is Expr.Func -> e.type
            is Expr.TDisc -> AUX.tps[e.tup].let {
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
                val tp = AUX.tps[uni]!!

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
                        Type.Unit(Tk.Sym(TK.UNIT, e.tk.lin, e.tk.col, "()")).up(e)
                    } else {
                        tp.expand()[e.tk_.num - 1]
                    }
                    is Expr.UPred -> Type.Nat(Tk.Nat(TK.XNAT, e.tk.lin, e.tk.col, null, "int")).up(e)
                    else -> error("bug found")
                }
            }
            is Expr.Var -> e.env()!!.type
        }
    }
    this.visit(null, ::fe, null)
}
