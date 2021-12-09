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
        is Type.Union -> "struct " + this.toce()
        is Type.Func  -> this.toce() + "*"
    }
}

fun Type.output (c: String, arg: String): String {
    val tupuni = this is Type.Ptr && (this.pln is Type.Tuple || this.pln is Type.Union)
    return when {
        tupuni -> "output_std_${(this as Type.Ptr).pln.toce()}$c($arg);\n"
        this is Type.Ptr || this is Type.Func -> {
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
            val ce = tp.toce()

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
                void output_std_${ce}_ (${tp.pos()}* v);
                void output_std_${ce} (${tp.pos()}* v);
                
            ""","""
                ${if (tp.containsRec()) struct.second else "" }
                void output_std_${ce}_ (${tp.pos()}* v) {
                    printf("[");
                    ${tp.vec
                        .mapIndexed { i,sub ->
                            val s = when (sub) {
                                is Type.Unit -> ""
                                is Type.Union, is Type.Tuple -> "&v->_${i + 1}"
                                else -> "v->_${i + 1}"
                            }
                            sub.output("_", s)
                        }
                        .joinToString("putchar(',');\n")
                    }
                    printf("]");
                }
                void output_std_${ce} (${tp.pos()}* v) {
                    output_std_${ce}_(v);
                    puts("");
                }

            """.trimIndent()))
        }
        is Type.Union -> {
            val ce    = tp.toce()
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
                void output_std_${ce}_ (${tp.pos()}* v);
                void output_std_${ce} (${tp.pos()}* v);

            """.trimIndent(),
            """
                ${if (tp.containsRec()) struct.second else "" }
                void output_std_${ce}_ (${tp.pos()}* v) {
                    ${
                        if (!tp.isrec) "" else """
                            if (v == NULL) {
                                printf("<.0>");
                                return;
                            }

                        """.trimIndent()
                    }
                    printf("<.%d", v->tag);
                    switch (v->tag) {
                        ${tpexp
                            .mapIndexed { i,sub ->
                                val s = when (sub) {
                                    is Type.Unit -> ""
                                    is Type.Union, is Type.Tuple -> "putchar(' ');\n" + sub.output("_", "&v->_${i+1}")
                                    else -> "putchar(' ');\n" + sub.output("_", "v->_${i+1}")
                                }
                                """
                                case ${i+1}:
                                    $s
                                break;

                                """.trimIndent()
                            }.joinToString("")
                        }
                    }
                    putchar('>');
                }
                void output_std_${ce} (${tp.pos()}* v) {
                    output_std_${ce}_(v);
                    puts("");
                }

            """.trimIndent()))
        }
    }
}

val EXPRS = ArrayDeque<Pair<String,String>>()

fun Expr.UDisc.deref (str: String): String {
    return if (AUX.tps[this.uni]!!.isrec()) {
        //"(*($str))"
        str
    } else {
        str
    }
}
fun Expr.UPred.deref (str: String): String {
    return if (AUX.tps[this.uni]!!.isrec()) {
        //"(*($str))"
        str
    } else {
        str
    }
}

fun code_fe (e: Expr) {
    val xp = AUX.xps[e]!!
    val isunit = AUX.tps[e] is Type.Unit
    EXPRS.addFirst(when (e) {
        is Expr.Unit -> Pair("", "")
        is Expr.Nat -> Pair("", e.tk_.str)
        is Expr.Var -> Pair("", if (isunit) "" else e.tk_.str)
        is Expr.Upref -> EXPRS.removeFirst().let {
            Pair(it.first, "(&" + it.second + ")")
        }
        is Expr.Dnref -> EXPRS.removeFirst().let {
            Pair(it.first, "(*" + it.second + ")")
        }
        is Expr.TDisc -> EXPRS.removeFirst().let { Pair(it.first, it.second + "._" + e.tk_.num) }
        is Expr.UDisc -> EXPRS.removeFirst().let {
            val ee = e.deref(it.second)
            val uni = AUX.tps[e.uni]!!
            val pre = if (e.tk_.num == 0) {
                """
                assert(&${it.second} == NULL);

                """.trimIndent()
            } else {
                """
                ${ if (uni.let { it is Type.Union && it.isrec }) "assert(&${it.second} != NULL);\n" else "" }
                assert($ee.tag == ${e.tk_.num});

                """.trimIndent()
            }
            Pair(it.first + pre, ee+"._"+e.tk_.num)
        }
        is Expr.UPred -> EXPRS.removeFirst().let {
            val ee = e.deref(it.second)
            val pos = if (e.tk_.num == 0) {
                "(&${it.second} == NULL)"
            } else {
                (if (AUX.tps[e.uni].let { it is Type.Union && it.isrec }) "(&${it.second} != NULL) && " else "") +
                "($ee.tag == ${e.tk_.num})"
            }
            Pair(it.first, pos)
        }
        is Expr.New  -> EXPRS.removeFirst().let {
            val ID  = "__tmp_" + e.hashCode().absoluteValue
            val ptr = AUX.xps[e] as Type.Ptr
            val sup = ptr.pos()
            val pool = ptr.scope().let {
                when {
                    !it.isabs -> "__news__${it.depth}"
                    (ptr.scope == null) -> "__news_cur"
                    else -> "__news_${it.depth}"
                }
            }
            //println(ptr.scope)
            //println(scp)
            val pre = """
                $sup $ID = malloc(sizeof(*$ID));
                assert($ID!=NULL && "not enough memory");
                *$ID = ${it.second};
                {
                    __News* __new = malloc(sizeof(__News));
                    assert(__new!=NULL && "not enough memory");
                    __new->val = $ID;
                    __new->nxt = *__news_cur;
                    *$pool = __new;
                }

            """.trimIndent()
            Pair(it.first+pre, ID)
        }
        is Expr.TCons -> {
            val (pre,pos) = (1..e.arg.size).map { EXPRS.removeFirst() }.reversed().unzip()
            Pair (
                pre.joinToString(""),
                pos.filter { it!="" }.joinToString(", ").let { "((${xp.pos()}) { $it })" }
            )
        }
        is Expr.UCons -> EXPRS.removeFirst().let {
            val ID  = "_tmp_" + e.hashCode().absoluteValue
            val arg = if (AUX.tps[e.arg] is Type.Unit) "" else (", ._${e.tk_.num} = " + it.second)
            val sup = "struct " + xp.toce()
            val pre = "$sup $ID = (($sup) { ${e.tk_.num} $arg });\n"
            if (e.tk_.num == 0) Pair("","NULL") else Pair(it.first + pre, ID)
        }
        is Expr.Call  -> {
            val arg = EXPRS.removeFirst()
            val f   = EXPRS.removeFirst()
            val snd =
                if (e.f is Expr.Var && e.f.tk_.str=="output_std") {
                    AUX.tps[e.arg]!!.output("", arg.second)
                } else {
                    f.second + "(" + arg.second + ")"
                }
            if (AUX.tps[e] is Type.Unit) {
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
            if (isunit && (e !is Expr.Call)) "" else it.second
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
            val tp = AUX.tps[s.dst]
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
        is Stmt.Block -> """
            {
                __News* __news  __attribute__((__cleanup__(__news_free))) = NULL;
                __News** __news_cur = &__news;
                ${ if (s.scope == null) "" else {
                    "__News** __news_${1 + s.ups_tolist().count{ it is Stmt.Block }} = &__news;"
                }}
                ${CODE.removeFirst()}
            }
            
            """.trimIndent()
        is Stmt.Ret   -> {
            //EXPRS.removeFirst()
            val f = s.ups_first { it is Expr.Func } as Expr.Func
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
    XPD = true
    this.visit( null, null, ::code_ft)
    XPD = false
    this.visit(::code_fs, ::code_fe, null)
    //val TYPES = mutableListOf<Triple<Pair<String,Set<String>>,String,String>>()

    val ord = TYPES.map { it.first }.toMap() // [ce={ce}]
    fun gt (a: String, b: String): Boolean {
        return (ord[a]!=null && (ord[a]!!.contains(b) || ord[a]!!.any { gt(it,b) }))
    }
    val TPS = //TYPES
    ///*
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
    //*/
    //AUX.tps.forEach { println(it.first.first) }
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
        
        typedef struct __News {
            void* val;
            struct __News* nxt;
        } __News;
        
        void __news_free (__News** news) {
            while (*news != NULL) {
                __News* cur = *news;
                *news = cur->nxt;
                free(cur->val);
                free(cur);
            }
            *news = NULL;
        }
        
        int main (void) {
            __News* __news  __attribute__((__cleanup__(__news_free))) = NULL;
            __News** __news_cur = &__news;
            ${CODE.removeFirst()}
        }

    """).trimIndent()
}
