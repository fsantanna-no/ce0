fun Stmt.setTypes () {
    fun fe (e: Expr) {
        e.wtype = when (e) {
            is Expr.Unit, is Expr.Nat, is Expr.UCons, is Expr.UNull, is Expr.Func -> e.wtype!!
            is Expr.Upref -> e.pln.wtype!!.let {
                val lbl = e.toBaseVar()?.tk_?.str ?: "GLOBAL"
                val scp1 = Tk.Scp1(TK.XSCPCST,e.tk.lin,e.tk.col, lbl,null)
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
            is Expr.Call -> e.f.wtype.let { tpd ->
                when (tpd) {
                    is Type.Nat, is Type.Spawn, is Type.Spawns -> tpd
                    is Type.Func -> {
                        // calculates return of "e" call based on "e.f" function type
                        // "e" passes "e.arg" with "e.scp1s.first" scopes which may affect "e.f" return scopes
                        // we want to map these input scopes into "e.f" return scopes
                        //  var f: func /@a1 -> /@b1
                        //              /     /---/
                        //  call f {@scp1,@scp2}  -->  /@scp2
                        //  f passes two scopes, first goes to @a1, second goes to @b1 which is the return
                        //  so @scp2 maps to @b1
                        // zip [[{@scp1a,@scp1b},{@scp2a,@scp2b}],{@a1,@b1}]
                        if (tpd.xscp1s.second.size != e.xscp1s.first.size) {
                            // TODO: may fail before check2, return anything
                            Type.Nat(Tk.Nat(TK.NATIVE, e.tk.lin, e.tk.col, null, "ERR")).clone(e, e.tk.lin, e.tk.col)
                        } else {
                            val MAP: List<Pair<Tk.Scp1, Pair<Tk.Scp1, Scp2>>> =
                                tpd.xscp1s.second.zip(e.xscp1s.first.zip(e.xscp2s!!.first))

                            fun Tk.Scp1.get(scp2: Scp2): Pair<Tk.Scp1, Scp2> {
                                return MAP.find { it.first.let { it.lbl == this.lbl && it.num == this.num } }?.second
                                    ?: Pair(this, scp2)
                            }

                            fun Type.map(): Type {
                                return when (this) {
                                    is Type.Ptr -> this.xscp1.get(this.xscp2!!).let {
                                        Type.Ptr(this.tk_, it.first, it.second, this.pln.map())
                                            .clone(e, e.tk.lin, e.tk.col)
                                    }
                                    is Type.Tuple -> Type.Tuple(this.tk_, this.vec.map { it.map() }.toTypedArray())
                                        .clone(e, e.tk.lin, e.tk.col)
                                    is Type.Union -> Type.Union(
                                        this.tk_,
                                        this.isrec,
                                        this.vec.map { it.map() }.toTypedArray()
                                    ).clone(e, e.tk.lin, e.tk.col)
                                    is Type.Func -> {
                                        val clo = this.xscp1s.first?.get(this.xscp2s!!.first!!)
                                        val (x1, x2) = this.xscp1s.second.zip(this.xscp2s!!.second)
                                            .map { it.first.get(it.second) }
                                            .unzip()
                                        Type.Func(
                                            this.tk_,
                                            Pair(clo?.first, x1.toTypedArray()),
                                            Pair(clo?.second, x2.toTypedArray()),
                                            this.inp.map(),
                                            this.pub?.map(),
                                            this.out.map()
                                        ).clone(e, e.tk.lin, e.tk.col)
                                    }
                                    else -> this
                                }
                            }
                            tpd.out.map()
                        }
                    }
                    else -> {
                        All_assert_tk(e.f.tk, false) {
                            "invalid call : not a function"
                        }
                        error("impossible case")
                    }
                }
            }
            is Expr.TDisc -> e.tup.wtype.let {
                All_assert_tk(e.tk, it is Type.Tuple) {
                    "invalid discriminator : type mismatch : expected tuple"
                }
                val (MIN, MAX) = Pair(1, (it as Type.Tuple).vec.size)
                All_assert_tk(e.tk, MIN <= e.tk_.num && e.tk_.num <= MAX) {
                    "invalid discriminator : out of bounds"
                }
                it.vec[e.tk_.num - 1]
            }
            is Expr.Pub -> e.tsk.wtype.let {
                All_assert_tk(e.tk, it is Type.Spawn) {
                    "invalid \"pub\" : type mismatch : expected active task"
                }
                (it as Type.Spawn).tsk.pub!!
            }
            is Expr.UDisc, is Expr.UPred -> {
                val (tk_,uni) = when (e) {
                    is Expr.UPred -> Pair(e.tk_,e.uni)
                    is Expr.UDisc -> Pair(e.tk_,e.uni)
                    else -> error("impossible case")
                }

                val isrec = uni.wtype!!.isrec()
                val tp = uni.wtype!!.noalias()

                All_assert_tk(e.tk, tp is Type.Union) {
                    "invalid discriminator : not an union"
                }
                assert(tk_.num!=0 || isrec) { "bug found" }

                val (MIN, MAX) = Pair(if (isrec) 0 else 1, (tp as Type.Union).vec.size)
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
            is Expr.Var -> e.env(e.tk_.str)!!.toType()
            else -> TODO()
        }
    }
    this.visit(false, null, ::fe, null)
}
