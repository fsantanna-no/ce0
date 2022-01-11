fun Stmt.setTypes () {
    fun fe (e: Expr) {
        e.wtype = e.wtype ?: when (e) {
            is Expr.Upref -> e.pln.wtype!!.let {
                val lbl = e.toBaseVar()?.tk_?.str ?: "global"
                val scp1 = Tk.Scp1(TK.XSCOPE,e.tk.lin,e.tk.col, lbl,null)
                Type.Ptr(e.tk_, scp1, scp1.toScp2(e), it).clone(e,e.tk.lin,e.tk.col)
            }
            is Expr.Dnref -> e.ptr.wtype.let {
                if (it is Type.Nat) it else {
                    All_assert_tk(e.tk, it is Type.Ptr) {
                        "invalid operand to `\\´ : not a pointer"
                    }
                    (it as Type.Ptr).pln
                }
            }
            is Expr.TCons -> Type.Tuple(e.tk_, e.arg.map { it.wtype!! }.toTypedArray()).clone(e,e.tk.lin,e.tk.col)
            is Expr.New   -> Type.Ptr(Tk.Chr(TK.CHAR,e.tk.lin,e.tk.col,'/'), e.xscp1!!, e.xscp2!!, e.arg.wtype!!).clone(e,e.tk.lin,e.tk.col)
            is Expr.Out   -> Type.Unit(Tk.Sym(TK.UNIT, e.tk.lin, e.tk.col, "()")).clone(e,e.tk.lin,e.tk.col)
            is Expr.Call -> {
                e.f.wtype.let {
                    when (it) {
                        is Type.Nat -> it
                        is Type.Func -> {
                            // calculates return of "e" call based on "e.f" function type
                            // "e" passes "e.arg" with "e.scp1s.first" scopes which may affect "e.f" return scopes
                            // we want to map these input scopes into "e.f" return scopes
                            //  var f: func /@a_1 -> /@b_1
                            //              /     /---/
                            //  call f {@scp1,@scp2}  -->  /@scp2
                            //  f passes two scopes, first goes to @a_1, second goes to @b_1 which is the return
                            //  so @scp2 maps to @b_1
                            // zip [[{@scp1a,@scp1b},{@scp2a,@scp2b}],{@a_1,@b_1}]
                            //assert(it.scp1s.second.size == e.scp1s.first.size) // TODO: may fail before check2
                            val MAP: List<Pair<Tk.Scp1,Pair<Tk.Scp1,Scp2>>> = it.xscp1s.second.zip(e.scp1s.first.zip(e.xscp2s!!.first))
                            fun Tk.Scp1.get (scp2: Scp2): Pair<Tk.Scp1,Scp2> {
                                return MAP.find { it.first.let { it.lbl==this.lbl && it.num==this.num } }?.second ?: Pair(this,scp2)
                            }
                            fun map (tp: Type): Type {
                                return when (tp) {
                                    is Type.Ptr   -> tp.scp1.get(tp.xscp2!!).let {
                                        Type.Ptr(tp.tk_, it.first, it.second, map(tp.pln)).clone(e,e.tk.lin,e.tk.col)
                                    }
                                    is Type.Tuple -> Type.Tuple(tp.tk_, tp.vec.map { map(it) }.toTypedArray()).clone(e,e.tk.lin,e.tk.col)
                                    is Type.Union -> Type.Union(tp.tk_, tp.isrec, tp.vec.map { map(it) }.toTypedArray()).clone(e,e.tk.lin,e.tk.col)
                                    is Type.Func  -> {
                                        val clo = tp.xscp1s.first?.get(tp.xscp2s!!.first!!)
                                        val (x1,x2) = tp.xscp1s.second.zip(tp.xscp2s!!.second)
                                            .map { it.first.get(it.second) }
                                            .unzip()
                                        Type.Func (
                                            tp.tk_,
                                            Pair(clo?.first, x1.toTypedArray()),
                                            Pair(clo?.second, x2.toTypedArray()),
                                            map(tp.inp),
                                            map(tp.out)
                                        ).clone(e,e.tk.lin,e.tk.col)
                                    }
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
                }
            }
            is Expr.TDisc -> e.tup.wtype.let {
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
                val tp = uni.wtype!!

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
                        Type.Unit(Tk.Sym(TK.UNIT, e.tk.lin, e.tk.col, "()")).clone(e,e.tk.lin,e.tk.col)
                    } else {
                        tp.expand()[e.tk_.num - 1]
                    }
                    is Expr.UPred -> Type.Nat(Tk.Nat(TK.XNAT, e.tk.lin, e.tk.col, null, "int")).clone(e,e.tk.lin,e.tk.col)
                    else -> error("bug found")
                }
            }
            is Expr.Var -> e.env()!!.toType()
            else -> error("bug found")
        }
    }
    this.visit(false, null, ::fe, null)
}
