import kotlin.math.absoluteValue

fun Type.toce (ctrec: Boolean = false): String {
    val _ref_ptr = (if (this.exactlyRec()) "_ref" else "") + (if (ctrec && this.containsRec()) "_ptr" else "")
    return when (this) {
        is Type.None, is Type.UCons -> error("bug found")
        is Type.Rec   -> "Rec"
        is Type.Any   -> "Any"
        is Type.Unit  -> "Unit"
        is Type.Ptr   -> if (this.pln is Type.Tuple || this.pln is Type.Union) this.pln.toce(false) + "_ptr" else "Ptr"
        is Type.Nat   -> this.tk_.str.replace('*','_')
        is Type.Tuple -> "TUPLE_p_" + this.vec.map { it.toce(false) }.joinToString("__") + "_d_" + _ref_ptr
        is Type.Union -> "UNION_p_" + this.vec.map { it.toce(false) }.joinToString("__") + "_d_" + _ref_ptr
        is Type.Func  -> "FUNC_p_" + this.inp.toce() + "__" + this.out.toce() + "_d_"
    }
}

val TYPEX = mutableSetOf<String>()
val TYPES = mutableListOf<Triple<Pair<String,Set<String>>,String,String>>()

fun Type.pos (ctrec: Boolean = false): String {
    val x = (if (this.exactlyRec())  "*" else "") + (if (ctrec && this.containsRec()) "*" else "")
    return when (this) {
        is Type.None, is Type.Rec, is Type.UCons -> TODO(this.toString())
        is Type.Any, is Type.Unit  -> "void"
        is Type.Ptr   -> this.pln.pos() + "*"
        is Type.Nat   -> this.tk_.str
        is Type.Tuple -> "struct " + this.toce() + x
        is Type.Union -> "struct " + this.toce() + x
        is Type.Func  -> this.toce() + "*"
    }
}

fun Type.output (arg: String): String {
    val ce = this.toce(true)
    return when (this) {
        is Type.Ptr, is Type.Func -> "putchar('_');\n"
        else -> "output_std_${ce}_($arg);\n"
    }
}

fun deps (tps: Set<Type>): Set<String> {
    return tps
        .filter { it !is Type.Rec }
        //.filter { !it.exactlyRec() }
        .map { Pair(it.toce(),it.pos()) }
        .filter { it.second.last()!='*' }
        .map { it.first }
        .toSet()
}

fun code_ft (tp: Type) {
    tp.toce().let {
        if (TYPEX.contains(it)) {
            return
        }
        TYPEX.add(it)
    }

    when (tp) {
        is Type.Func -> TYPES.add(Triple(
            Pair(tp.toce(), deps(setOf(tp.inp,tp.out))),
            "typedef ${tp.out.pos()} ${tp.toce()} (${tp.inp.pos()});\n",
            ""
        ))
        is Type.Tuple -> {
            val ce    = tp.toce()
            val _ptr  = tp.toce(true)
            val ctrec = tp.containsRec()
            val xv    = if (ctrec) "(*v)" else "v"

            val struct = Pair("""
                struct $ce;                    

            """.trimIndent(), """
                struct $ce {
                    ${tp.vec  // do not filter to keep correct i
                        .mapIndexed { i,sub -> if (sub is Type.Unit) "" else { sub.pos() + " _" + (i+1).toString() + ";\n" } }
                        .joinToString("")
                    }
                };

            """.trimIndent())

            TYPES.add(Triple(
                //Pair(ce, deps(tp.vec.toSet()).let { println(ce);println(it);it }),
                Pair(ce, deps(tp.vec.toSet())),
    """
                ${if (tp.containsRec()) struct.first else struct.second }
                void output_std_${_ptr}_ (${tp.pos(true)} v);
                void output_std_${_ptr} (${tp.pos(true)} v);
                ${if (!tp.containsRec()) "" else """
                    void free_${ce} (${tp.pos(true)} v);
                    ${tp.pos()} copy_${ce} (${tp.pos(true)} v);
                    ${tp.pos()} move_${ce} (${tp.pos(true)} v);

                """
                }
            ""","""
                ${if (tp.containsRec()) struct.second else "" }
                void output_std_${_ptr}_ (${tp.pos(true)} v) {
                    printf("[");
                    ${tp.vec
                        .mapIndexed { i,sub ->
                            val amp = if (sub.containsRec()) "&" else ""
                            sub.output(if (sub is Type.Unit) "" else "$amp$xv._${i+1}")
                        }
                        .joinToString("putchar(',');\n")
                    }
                    printf("]");
                }
                void output_std_${_ptr} (${tp.pos(true)} v) {
                    output_std_${_ptr}_(v);
                    puts("");
                }

            """.trimIndent() + (if (!tp.containsRec()) "" else """
                void free_${ce} (${tp.pos(true)} v) {
                    ${tp.vec
                        .mapIndexed { i, sub ->
                            if (!sub.containsRec()) "" else """
                                free_${sub.toce()}(&v->_${i + 1});

                            """.trimIndent()
                        }
                        .joinToString("")
                    }
                }
                ${tp.pos()} copy_${ce} (${tp.pos(true)} v) {
                    ${tp.pos()} ret;
                    ${tp.vec
                        .mapIndexed { i, sub -> if (sub is Type.Unit) "" else
                            "ret._${i + 1} = " +
                                (if (!sub.containsRec()) {
                                    "v->_${i + 1}"
                                } else {
                                    "copy_${sub.toce()}(&v->_${i + 1})"
                                }) +
                            ";\n"
                       }
                        .joinToString("")
                    }
                    return ret;
                }
                ${tp.pos()} move_${ce} (${tp.pos(true)} v) {
                    ${tp.pos()} ret;
                    ${tp.vec
                        .mapIndexed { i, sub -> if (sub is Type.Unit) "" else
                            "ret._${i + 1} = " +
                                (if (!sub.containsRec()) {
                                    "v->_${i + 1}"
                                } else {
                                    "move_${sub.toce()}(&v->_${i + 1})"
                                }) +
                            ";\n"
                        }
                        .joinToString("")
                    }
                    return ret;
                }

            """.trimIndent())))
        }
        is Type.Union -> {
            val ce    = tp.toce()
            val _ptr  = tp.toce(true)
            val ctrec = tp.containsRec()
            val exrec = tp.exactlyRec()
            val xxv   = (if (ctrec) "(*v)" else "v").let { if (exrec) "(*$it)" else it }
            val xv    = (if (exrec) "(*v)" else "v")
            val ret   = (if (exrec) "(*ret)" else "ret")
            val tpexp = tp.expand()

            val struct = Pair ("""
                struct $ce;

            """.trimIndent(),
            """
                struct $ce {
                    int tag;
                    union {
                        ${tpexp.vec  // do not filter to keep correct i
                        .mapIndexed { i,sub ->
                            when {
                                sub is Type.Unit -> ""
                                sub is Type.Rec -> error("bug found") //"struct $ce* _${i+1};\n"
                                else -> "${sub.pos()} _${i+1};\n"
                            }
                        }
                        .joinToString("")
                    }
                    };
                };

            """.trimIndent())

            TYPES.add(Triple(
                //Pair(ce, deps(tpexp.vec.toSet()).let { println(ce);println(it);it }),
                Pair(ce, deps(tpexp.vec.toSet())),
            """
                ${if (tp.containsRec()) struct.first else struct.second }
                void output_std_${_ptr}_ (${tp.pos(true)} v);
                void output_std_${_ptr} (${tp.pos(true)} v);
                ${if (!tp.containsRec()) "" else """
                    void free_${ce} (${tp.pos(true)} v);
                    ${tp.pos()} copy_${ce} (${tp.pos(true)} v);
                    ${tp.pos()} move_${ce} (${tp.pos(true)} v);

                """
            }

            """.trimIndent(),
            """
                ${if (tp.containsRec()) struct.second else "" }
                void output_std_${_ptr}_ (${tp.pos(true)} v) {
                    ${
                        if (!tp.isnullable) "" else """
                            if ($xv == NULL) {
                                printf("<.0>");
                                return;
                            }

                        """.trimIndent()
                    }
                    printf("<.%d", $xxv.tag);
                    switch ($xxv.tag) {
                        ${tpexp.vec
                            .mapIndexed { i,sub -> """
                                case ${i+1}:
                                ${
                                    if (sub is Type.Unit) {
                                        ""
                                    } else {
                                        val amp = if (sub.containsRec()) "&" else ""
                                        "putchar(' ');\n" + sub.output("$amp$xxv._${i+1}")
                                    }
                                }
                                break;

                            """.trimIndent()
                            }.joinToString("")
                        }
                    }
                    putchar('>');
                }
                void output_std_${_ptr} (${tp.pos(true)} v) {
                    output_std_${_ptr}_(v);
                    puts("");
                }

            """.trimIndent() + (if (!tp.containsRec()) "" else """
                void free_${ce} (${tp.pos(true)} v) {
                    ${ "" /*if (!tp.isnullable) "" else "if (${xv} == NULL) return;\n"*/ }
                    if (${xv} == NULL) return;
                    switch ((${xxv}).tag) {
                        ${ tpexp.vec
                            .mapIndexed { i,tp2 ->
                                if (!tp2.containsRec()) "" else """
                                    case ${i+1}:
                                        free_${tp2.toce()}(&(${xxv})._${i+1});
                                        break;

                                """.trimIndent()
                            }
                            .joinToString("")
                        }
                    }
                    ${ if (!exrec) "" else "free(${xv});\n" }
                }
                ${tp.pos()} copy_${ce} (${tp.pos(true)} v) {
                    ${ if (!exrec) "${tp.pos()} ret = { ${xxv}.tag };\n" else {
                        val nul = if (!tp.isnullable) "" else "if (${xv} == NULL) return NULL;"
                        """
                            $nul
                            ${tp.pos()} ret = malloc(sizeof(*ret));
                            assert(ret != NULL && "not enough memory");
                            ($ret).tag = ($xxv).tag;

                        """.trimIndent()
                    } }
                    switch ((${xxv}).tag) {
                        ${ tpexp.vec
                            .mapIndexed { i,sub -> if (sub is Type.Unit) "" else
                                "case ${i+1}:\n" + (
                                    if (sub.containsRec()) {
                                        "($ret)._${i+1} = copy_${sub.toce()}(&($xxv)._${i+1});\nbreak;\n"
                                    } else {
                                        "($ret)._${i+1} = ($xxv)._${i + 1};\nbreak;\n"
                                    }
                                )
                            }
                            .joinToString("")
                        }
                    }
                    return ret;
                }

            """.trimIndent() + (if (!tp.exactlyRec()) "" else """
                ${tp.pos()} move_${ce} (${tp.pos(true)} v) {
                    ${tp.pos()} ret = $xv;
                    $xv = NULL;
                    return ret;
                }

            """.trimIndent()))))
        }
    }
}

val EXPRS = ArrayDeque<Pair<String,String>>()

fun Expr.UDisc.deref (env: Env, str: String): String {
    return if (this.uni.toType(env).exactlyRec()) {
        "(*($str))"
    } else {
        str
    }
}
fun Expr.UPred.deref (env: Env, str: String): String {
    return if (this.uni.toType(env).exactlyRec()) {
        "(*($str))"
    } else {
        str
    }
}

fun code_fe (env: Env, e: Expr, xp: Type) {
    val tp = e.toType(env)
    EXPRS.addFirst(when (e) {
        is Expr.Unk, is Expr.Unit -> Pair("", "")
        is Expr.Nat -> Pair("", e.tk_.str)
        is Expr.Var -> Pair("", if (tp is Type.Unit) "" else e.tk_.str)
        is Expr.Upref -> {
            val sub = EXPRS.removeFirst()
            Pair(sub.first, "(&" + sub.second + ")")
        }
        is Expr.Dnref -> {
            val sub = EXPRS.removeFirst()
            Pair(sub.first, "(*" + sub.second + ")")
        }
        is Expr.TDisc -> EXPRS.removeFirst().let { Pair(it.first, it.second + "._" + e.tk_.num) }
        is Expr.UDisc -> EXPRS.removeFirst().let {
            val ee = e.deref(env,it.second)
            val pre = if (e.tk_.num == 0) {
                """
                assert(${it.second} == NULL);

                """.trimIndent()
            } else {
                """
                ${ if (e.uni.toType(env).let { it is Type.Union && it.isnullable }) "assert(${it.second} != NULL);\n" else "" }
                assert($ee.tag == ${e.tk_.num});

                """.trimIndent()
            }
            Pair(it.first + pre, ee + "._" + e.tk_.num)
        }
        is Expr.UPred -> EXPRS.removeFirst().let {
            val ee = e.deref(env,it.second)
            val pre = if (e.tk_.num == 0) {
                "(${it.second} == NULL)"
            } else {
                (if (e.uni.toType(env).let { it is Type.Union && it.isnullable }) "(${it.second} != NULL) && " else "") +
                "($ee.tag == ${e.tk_.num})"
            }
            Pair(it.first, pre)
        }
        is Expr.TCons -> {
            val (pre,pos) = (1..e.arg.size).map { EXPRS.removeFirst() }.reversed().unzip()
            Pair (
                pre.joinToString(""),
                pos.filter { it!="" }.joinToString(", ").let { "((${xp.pos()}) { $it })" }
            )
        }
        is Expr.UCons -> {
            val top = EXPRS.removeFirst()
            val ID  = "_tmp_" + e.hashCode().absoluteValue
            val arg = if (e.arg.e.toType(env) is Type.Unit) "" else (", ._${e.tk_.num} = " + top.second)
            val sup = "struct " + xp.toce()
            val pre = "$sup $ID = (($sup) { ${e.tk_.num} $arg });\n"
            if (e.tk_.num == 0) Pair("","NULL") else Pair(top.first + pre, ID)
        }
        is Expr.Call  -> {
            val arg = EXPRS.removeFirst()
            val f   = EXPRS.removeFirst()
            Pair (
                f.first + arg.first,
                f.second + (
                    if (e.f is Expr.Var && e.f.tk_.str=="output_std") {
                        "_" + e.arg.e.toType(env).toce()
                    } else {
                        ""
                    }
                ) + "(" + arg.second + ")"
            )
        }
        is Expr.Func  -> {
            val ID  = "_func_" + e.hashCode().absoluteValue
            val out = e.type.out.let { if (it is Type.Unit) "void" else it.pos() }
            val inp = e.type.inp.let { if (it is Type.Unit) "void" else (it.pos()+" _arg_") }
            val pre = """
                auto $out $ID ($inp) {
                    ${CODE.removeFirst()}
                }

            """.trimIndent()
            Pair(pre, ID)
        }
    }.let {
        Pair (
            it.first,
            if ((tp is Type.Unit) && (e !is Expr.Call)) "" else it.second
        )
    })
}

fun code_fx (env: Env, xe: XExpr, xp: Type) {
    val top = EXPRS.removeFirst()
    val ID  = "__tmp_" + xe.hashCode().absoluteValue

    EXPRS.addFirst(when (xe) {
        is XExpr.None, is XExpr.Borrow -> top
        is XExpr.New  -> {
            assert(xe.e is Expr.UCons)
            val xee = xe.e as Expr.UCons
            val sup = xp.pos()
            val pre = """
                $sup $ID = malloc(sizeof(*$ID));
                assert($ID!=NULL && "not enough memory");
                *$ID = ${top.second};

            """.trimIndent()
            Pair(top.first+pre, if (xee.tk_.num == 0) "NULL" else ID)
        }
        is XExpr.Copy -> Pair(top.first, "copy_${xp.toce()}(&${top.second})")
        is XExpr.Move -> Pair(top.first, "move_${xp.toce()}(&${top.second})")
    })
}

val CODE = ArrayDeque<String>()

fun code_fs (env: Env, s: Stmt) {
    CODE.addFirst(when (s) {
        is Stmt.Pass -> ""
        is Stmt.Nat  -> s.tk_.str + "\n"
        is Stmt.Seq  -> { val s2=CODE.removeFirst() ; val s1=CODE.removeFirst() ; s1+s2 }
        is Stmt.Set  -> {
            val src = EXPRS.removeFirst()
            val dst = EXPRS.removeFirst()
            dst.first + src.first +
                (if (s.dst.toExpr().toType(env) is Type.Unit) "" else (dst.second+" = ")) + src.second + ";\n"
        }
        is Stmt.If -> {
            val tst = EXPRS.removeFirst()
            val false_ = CODE.removeFirst()
            val true_  = CODE.removeFirst()
            tst.first + """
                if (${tst.second}) {
                    ${true_}
                } else {
                    ${false_}
                }

            """.trimIndent()
        }
        is Stmt.Loop  -> """
            while (1) {
                ${CODE.removeFirst()}
            }

        """.trimIndent()
        is Stmt.Break -> "break;\n"
        is Stmt.Call  -> {
            val e = EXPRS.removeFirst()
            e.first + e.second + ";\n"
        }
        is Stmt.Block -> "{\n" + CODE.removeFirst() + "}\n"
        is Stmt.Ret   -> {
            EXPRS.removeFirst()
            "return" + if (s.e.e.toType(env) is Type.Unit) ";\n" else " _ret_;\n"
        }
        is Stmt.Var   -> {
            val src = EXPRS.removeFirst()
            if (s.type is Type.Unit) {
                src.first
            } else {
                val fst = "${s.type.pos()} ${s.tk_.str}" +
                    (when {
                        (s.tk_.str == "_ret_") -> ""
                        !s.type.containsRec() -> ""
                        else -> {
                            " __attribute__ ((__cleanup__(free_${s.type.toce()})))"
                        }
                    }) + ";\n"
                val snd = if (s.src.e is Expr.Unk) "" else {
                    "${s.tk_.str} = ${src.second};\n"
                }
                fst + src.first + snd
            }
        }
    })
}

fun Stmt.code (): String {
    TYPEX.clear()
    TYPES.clear()
    this.visit(emptyList(), null, null, null, ::code_ft)
    this.visitXP(emptyList(), ::code_fs, ::code_fx, ::code_fe)
    //val TYPES = mutableListOf<Triple<Pair<String,Set<String>>,String,String>>()

    val ord = TYPES.map { it.first }.toMap() // [ce={ce}]
    fun gt (a: String, b: String): Boolean {
        return (ord[a]!=null && (ord[a]!!.contains(b) || ord[a]!!.any { gt(it,b) }))
    }
    val TPS =
        TYPES.sortedWith(Comparator { x: Triple<Pair<String, Set<String>>, String, String>, y: Triple<Pair<String, Set<String>>, String, String> ->
            when {
                gt(x.first.first, y.first.first) ->  1
                gt(y.first.first, x.first.first) -> -1
                else -> 0
            }
            /*
            when {
                (x.first.second.contains(y.first.first)) ->  1
                (y.first.second.contains(x.first.first)) -> -1
                else -> 0
            }
             */
        })
    //TPS.forEach { println(it.first.first) }
    assert(EXPRS.size == 0)
    assert(CODE.size == 1)
    return ("""
        #include <assert.h>
        #include <stdio.h>
        #include <stdlib.h>
        #define output_std_Unit_()   printf("()")
        #define output_std_Unit()    (output_std_Unit_(), puts(""))
        #define output_std_int_(x)   printf("%d",x)
        #define output_std_int(x)    (output_std_int_(x), puts(""))
        #define output_std_char__(x) printf("\"%s\"",x)
        #define output_std_char_(x)  (output_std_int_(x), puts(""))
        #define output_std_Ptr_(x)   printf("%p",x)
        #define output_std_Ptr(x)    (output_std_Ptr_(x), puts(""))
        ${TPS.map { it.second }.joinToString("")}
        ${TPS.map { it.third }.joinToString("")}
        int main (void) {
            ${CODE.removeFirst()}
        }

    """).trimIndent()
}
