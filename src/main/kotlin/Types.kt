fun Stmt.setTypes () {
    fun fe (e: Expr) {
        e.wtype = when (e) {
            is Expr.Unit, is Expr.Nat, is Expr.UCons, is Expr.UNull, is Expr.Func -> e.wtype!!
            is Expr.Upref -> e.pln.wtype!!.let {
                val id = e.toBaseVar()?.tk_?.id ?: "GLOBAL"   // uppercase /x -> /X
                val scp1 = Tk.Id(TK.XID,e.tk.lin,e.tk.col, id.toUpperCase())
                Type.Pointer(e.tk_, scp1, scp1.toScp2(e), it)
            }
            is Expr.Dnref -> e.ptr.wtype.let {
                if (it is Type.Nat) it else {
                    All_assert_tk(e.tk, it is Type.Pointer) {
                        "invalid operand to `\\´ : not a pointer"
                    }
                    (it as Type.Pointer).pln
                }
            }
            is Expr.TCons -> Type.Tuple(e.tk_, e.arg.map { it.wtype!! }.toTypedArray())
            is Expr.New   -> Type.Pointer(Tk.Chr(TK.CHAR,e.tk.lin,e.tk.col,'/'), e.scp!!, null, /*e.xscp2!!,*/ e.arg.wtype!!)
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
                        if (tpd.scps.second.size != e.scps.first.size) {
                            // TODO: may fail before check2, return anything
                            Type.Nat(Tk.Nat(TK.NATIVE, e.tk.lin, e.tk.col, null, "ERR"))
                        } else {
                            val MAP: List<Pair<Tk.Id, Tk.Id>> =
                                tpd.scps.second.zip(e.scps.first)
                            fun Tk.Id.get(): Tk.Id {
                                return MAP.find { it.first.let { it.id == this.id } }?.second
                                    ?: this
                            }
                            fun Type.map(): Type {
                                return when (this) {
                                    is Type.Pointer -> this.scp.get().let {
                                        Type.Pointer(this.tk_, it, null, this.pln.map())
                                    }
                                    is Type.Tuple -> Type.Tuple(this.tk_, this.vec.map { it.map() }.toTypedArray())
                                    is Type.Union -> Type.Union(
                                        this.tk_,
                                        this.vec.map { it.map() }.toTypedArray()
                                    )
                                    is Type.Func -> {
                                        val clo = this.scps.first?.get()
                                        val x1  = this.scps.second.map { it.get() }
                                        Type.Func(
                                            this.tk_,
                                            Triple(clo, x1.toTypedArray(), this.scps.third), // TODO: third
                                            null,
                                            this.inp.map(),
                                            this.pub?.map(),
                                            this.out.map()
                                        )
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
                    "invalid discriminator : type mismatch : expected tuple : have ${it!!.tostr()}"
                }
                val (MIN, MAX) = Pair(1, (it as Type.Tuple).vec.size)
                All_assert_tk(e.tk, MIN <= e.tk_.num && e.tk_.num <= MAX) {
                    "invalid discriminator : out of bounds"
                }
                it.vec[e.tk_.num - 1]
            }
            is Expr.Pub -> e.tsk.wtype.let {
                All_assert_tk(e.tk, it is Type.Spawn) {
                    "invalid \"pub\" : type mismatch : expected active task : have ${e.tsk.wtype!!.tostr()}"
                }
                (it as Type.Spawn).tsk.pub!!
            }
            is Expr.UDisc, is Expr.UPred -> {
                val (tk_,uni) = when (e) {
                    is Expr.UPred -> Pair(e.tk_,e.uni)
                    is Expr.UDisc -> Pair(e.tk_,e.uni)
                    else -> error("impossible case")
                }
                val str = if (e is Expr.UDisc) "discriminator" else "predicate"

                val isrec = uni.wtype!!.isrec()
                val tp = uni.wtype!!.noalias()

                All_assert_tk(e.tk, tp is Type.Union) {
                    "invalid $str : not an union"
                }
                assert(tk_.num!=0 || isrec) {
                    "invalid $str : expected recursive union"
                }

                val (MIN, MAX) = Pair(if (isrec) 0 else 1, (tp as Type.Union).vec.size)
                All_assert_tk(e.tk, MIN <= tk_.num && tk_.num <= MAX) {
                    "invalid $str : out of bounds"
                }

                when (e) {
                    is Expr.UDisc -> if (e.tk_.num == 0) {
                        Type.Unit(Tk.Sym(TK.UNIT, e.tk.lin, e.tk.col, "()"))
                    } else {
                        tp.vec[e.tk_.num - 1]
                    }
                    is Expr.UPred -> Type.Nat(Tk.Nat(TK.XNAT, e.tk.lin, e.tk.col, null, "int"))
                    else -> error("bug found")
                }
            }
            is Expr.Var -> e.env(e.tk_.id)!!.toType()
            else -> TODO()
        }.clone(e,e.tk.lin,e.tk.col)
    }
    this.visit(null, ::fe, null)
}
