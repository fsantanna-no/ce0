import kotlin.math.absoluteValue

fun Type.toce (): String {
    return when (this) {
        is Type.UCons -> error("bug found")
        is Type.Rec   -> "Rec"
        is Type.Any   -> "Any"
        is Type.Unit  -> "Unit"
        is Type.Ptr   -> "P_" + this.pln.toce() + "_P"
        is Type.Nat   -> this.tk_.str.replace('*','_')
        is Type.Tuple -> "T_" + this.vec.map { it.toce() }.joinToString("_") + "_T"
        is Type.Union -> "U_" + this.vec.map { it.toce() }.joinToString("_") + "_U"
        is Type.Func  -> "F_" + this.inp.toce() + "_" + this.out.toce() + "_F"
    }
}

val TYPEX = mutableSetOf<String>()
val TYPES = mutableListOf<Triple<Pair<String,Set<String>>,String,String>>()

fun Type.pos (): String {
    return when (this) {
        is Type.Rec, is Type.UCons -> TODO(this.toString())
        is Type.Any, is Type.Unit  -> "void"
        is Type.Ptr   -> this.pln.pos() + "*"
        is Type.Nat   -> this.tk_.str
        is Type.Tuple -> "struct " + this.toce()
        is Type.Union -> if (this.isnullptr()) {
            (this.vec[0] as Type.Ptr).pln.pos() + "*"
        } else {
            "struct " + this.toce() //+ (if (this.exactlyRec())  "*" else "")
        }
        is Type.Func  -> this.toce() + "*"
    }
}

fun Type.output (c: String, arg: String): String {
    return when {
        this is Type.Ptr || this is Type.Func || this.isnullptr() -> {
            if (c == "_") "putchar('_');\n" else "puts(\"_\");\n"
        }
        else -> "output_std_${this.toce()}$c($arg);\n"
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
            val tpexp = tp.expand()

            val struct = Pair("""
                struct $ce;                    

            """.trimIndent(), """
                struct $ce {
                    ${tpexp  // do not filter to keep correct i
                        .mapIndexed { i,sub -> if (sub is Type.Unit) "" else { sub.pos() + " _" + (i+1).toString() + ";\n" } }
                        .joinToString("")
                    }
                };

            """.trimIndent())

            TYPES.add(Triple(
                //Pair(ce, deps(tpexp.toSet()).let { println(ce);println(it);it }),
                Pair(ce, deps(tpexp.toSet())),
    """
                ${if (tp.containsRec()) struct.first else struct.second }
                void output_std_${ce}_ (${tp.pos()} v);
                void output_std_${ce} (${tp.pos()} v);
                ${if (!tp.containsRec()) "" else """
                    void free_${ce} (${tp.pos()}* v);
                    ${tp.pos()} copy_${ce} (${tp.pos()} v);

                """
                }
            ""","""
                ${if (tp.containsRec()) struct.second else "" }
                void output_std_${ce}_ (${tp.pos()} v) {
                    printf("[");
                    ${tpexp
                        .mapIndexed { i,sub ->
                            sub.output("_", if (sub is Type.Unit) "" else "v._${i+1}")
                        }
                        .joinToString("putchar(',');\n")
                    }
                    printf("]");
                }
                void output_std_${ce} (${tp.pos()} v) {
                    output_std_${ce}_(v);
                    puts("");
                }

            """.trimIndent() + (if (!tp.containsRec()) "" else """
                void free_${ce} (${tp.pos()}* v) {
                    ${tpexp
                        .mapIndexed { i, sub ->
                            if (!sub.containsRec()) "" else """
                                free_${sub.toce()}(&v->_${i + 1});

                            """.trimIndent()
                        }
                        .joinToString("")
                    }
                }
                ${tp.pos()} copy_${ce} (${tp.pos()} v) {
                    ${tp.pos()} ret;
                    ${tpexp
                        .mapIndexed { i, sub -> if (sub is Type.Unit) "" else
                            "ret._${i + 1} = " +
                                (if (!sub.containsRec()) {
                                    "v._${i + 1}"
                                } else {
                                    "copy_${sub.toce()}(v._${i + 1})"
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
            if (tp.isnullptr()) {
                return
            }

            val ce    = tp.toce()
            val exrec = tp.exactlyRec()
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
                        ${tpexp  // do not filter to keep correct i
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
                Pair(ce, deps(tpexp.toSet())),
            """
                ${if (tp.containsRec()) struct.first else struct.second }
                void output_std_${ce}_ (${tp.pos()} v);
                void output_std_${ce} (${tp.pos()} v);
                ${if (!tp.containsRec()) "" else """
                    void free_${ce} (${tp.pos()}* v);
                    ${tp.pos()} copy_${ce} (${tp.pos()} v);

                """
            }

            """.trimIndent(),
            """
                ${if (tp.containsRec()) struct.second else "" }
                void output_std_${ce}_ (${tp.pos()} v) {
                    ${
                        if (!tp.isnull) "" else """
                            if (v == NULL) {
                                printf("<.0>");
                                return;
                            }

                        """.trimIndent()
                    }
                    printf("<.%d", $xv.tag);
                    switch ($xv.tag) {
                        ${tpexp
                            .mapIndexed { i,sub -> """
                                case ${i+1}:
                                ${
                                    if (sub is Type.Unit) {
                                        ""
                                    } else {
                                        "putchar(' ');\n" + sub.output("_", "$xv._${i+1}")
                                    }
                                }
                                break;

                            """.trimIndent()
                            }.joinToString("")
                        }
                    }
                    putchar('>');
                }
                void output_std_${ce} (${tp.pos()} v) {
                    output_std_${ce}_(v);
                    puts("");
                }

            """.trimIndent() + (if (!tp.containsRec()) "" else """
                void free_${ce} (${tp.pos()}* v) {
                    ${ "" /*if (!tp.isnullable) "" else "if (${xv} == NULL) return;\n"*/ }
                    if ($xv == NULL) return;
                    switch ($xv->tag) {
                        ${ tpexp
                            .mapIndexed { i,tp2 ->
                                if (!tp2.containsRec()) "" else """
                                    case ${i+1}:
                                        free_${tp2.toce()}(&($xv)->_${i+1});
                                        break;

                                """.trimIndent()
                            }
                            .joinToString("")
                        }
                    }
                    ${ if (!exrec) "" else "free($xv);\n" }
                }
                ${tp.pos()} copy_${ce} (${tp.pos()} v) {
                    ${ if (!exrec) "${tp.pos()} ret = { $xv.tag };\n" else {
                        val nul = if (!tp.isnull) "" else "if (v == NULL) return NULL;"
                        """
                            $nul
                            ${tp.pos()} ret = malloc(sizeof(*ret));
                            assert(ret != NULL && "not enough memory");
                            ($ret).tag = $xv.tag;

                        """.trimIndent()
                    } }
                    switch ($xv.tag) {
                        ${ tpexp
                            .mapIndexed { i,sub -> if (sub is Type.Unit) "" else
                                "case ${i+1}:\n" + (
                                    if (sub.containsRec()) {
                                        "($ret)._${i+1} = copy_${sub.toce()}($xv._${i+1});\nbreak;\n"
                                    } else {
                                        "($ret)._${i+1} = $xv._${i + 1};\nbreak;\n"
                                    }
                                )
                            }
                            .joinToString("")
                        }
                    }
                    return ret;
                }

            """.trimIndent())))
        }
    }
}

val EXPRS = ArrayDeque<Pair<String,String>>()

fun Expr.UDisc.deref (str: String): String {
    return if (TPS[this.uni]!!.exactlyRec()) {
        "(*($str))"
    } else {
        str
    }
}
fun Expr.UPred.deref (str: String): String {
    return if (TPS[this.uni]!!.exactlyRec()) {
        "(*($str))"
    } else {
        str
    }
}

fun code_fe (e: Expr) {
    val xp = XPS[e]!!
    val tp = TPS[e]
    EXPRS.addFirst(when (e) {
        is Expr.Unit -> Pair("", "")
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
            val ee = e.deref(it.second)
            val uni = TPS[e.uni]!!
            val pre = if (e.tk_.num == 0) {
                """
                assert(${it.second} == NULL);

                """.trimIndent()
            } else {
                """
                ${ if (uni.let { it is Type.Union && it.isnull }) "assert(${it.second} != NULL);\n" else "" }
                ${ if (uni.isnullptr()) "" else "assert($ee.tag == ${e.tk_.num});" }

                """.trimIndent()
            }
            val pos = if (uni.isnullptr()) ee else ee+"._"+e.tk_.num
            Pair(it.first + pre, pos)
        }
        is Expr.UPred -> EXPRS.removeFirst().let {
            val ee = e.deref(it.second)
            val pre = if (e.tk_.num == 0) {
                "(${it.second} == NULL)"
            } else {
                (if (TPS[e.uni].let { it is Type.Union && it.isnull }) "(${it.second} != NULL) && " else "") +
                "($ee.tag == ${e.tk_.num})"
            }
            Pair(it.first, pre)
        }
        is Expr.New  -> {
            val top = EXPRS.removeFirst()
            val ID  = "__tmp_" + e.hashCode().absoluteValue
            val sup = XPS[e]!!.pos()
            val pre = """
                $sup $ID = malloc(sizeof(*$ID));
                assert($ID!=NULL && "not enough memory");
                *$ID = ${top.second};

            """.trimIndent()
            Pair(top.first+pre, ID)
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
            val arg = if (TPS[e.arg] is Type.Unit) "" else (", ._${e.tk_.num} = " + top.second)
            val sup = "struct " + xp.toce()
            val pre = "$sup $ID = (($sup) { ${e.tk_.num} $arg });\n"
            when {
                (e.tk_.num == 0) -> Pair("","NULL")
                xp.isnullptr() -> Pair("", top.second)
                else -> Pair(top.first + pre, ID)
            }
        }
        is Expr.Call  -> {
            val arg = EXPRS.removeFirst()
            val f   = EXPRS.removeFirst()
            val snd =
                if (e.f is Expr.Var && e.f.tk_.str=="output_std") {
                    TPS[e.arg]!!.output("", arg.second)
                } else {
                    f.second + "(" + arg.second + ")"
                }
            if (TPS[e] is Type.Unit) {
                Pair(f.first + arg.first + snd+";\n", "")
            } else {
                Pair(f.first + arg.first, snd)
            }
        }
        is Expr.Func  -> {
            val ID  = "_func_" + e.hashCode().absoluteValue
            val out = e.type.out.let { if (it is Type.Unit) "void" else it.pos() }
            val (inp,dcl) = e.type.inp.let { if (it is Type.Unit) Pair("void","int _arg_;") else Pair(it.pos()+" _arg_","") }
            val pre = """
                auto $out $ID ($inp) {
                    $dcl
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

val CODE = ArrayDeque<String>()

fun code_fs (s: Stmt) {
    CODE.addFirst(when (s) {
        is Stmt.Pass -> ""
        is Stmt.Nat  -> s.tk_.str + "\n"
        is Stmt.Seq  -> { val s2=CODE.removeFirst() ; val s1=CODE.removeFirst() ; s1+s2 }
        is Stmt.Set  -> {
            val src = EXPRS.removeFirst()
            val dst = EXPRS.removeFirst()
            val tp = TPS[s.dst]
            dst.first + src.first +
                (if (tp is Type.Unit) "" else (dst.second + " = ")) + src.second + ";\n"
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
            //EXPRS.removeFirst()
            val f = s.ups_tolist().first { it is Expr.Func } as Expr.Func
            "return" + if (f.type.out is Type.Unit) ";\n" else " _ret_;\n"
            //"return" + if (s.e.e.toType() is Type.Unit) ";\n" else " _ret_;\n"
        }
        is Stmt.Var   -> {
            when {
                s.tk_.str == "_arg_" -> "int ${s.tk_.str};\n"
                (s.type is Type.Unit) -> ""
                else -> "${s.type.pos()} ${s.tk_.str};\n"
            }
        }
    })
}

fun Stmt.code (): String {
    TYPEX.clear()
    TYPES.clear()
    this.visit( null, null, ::code_ft)
    this.visit(::code_fs, ::code_fe, null)
    //val TYPES = mutableListOf<Triple<Pair<String,Set<String>>,String,String>>()

    val ord = TYPES.map { it.first }.toMap() // [ce={ce}]
    fun gt (a: String, b: String): Boolean {
        return (ord[a]!=null && (ord[a]!!.contains(b) || ord[a]!!.any { gt(it,b) }))
    }
    val TPS = TYPES
    /*
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
     */
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
