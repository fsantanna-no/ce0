import kotlin.math.absoluteValue

fun Type.toce (): String {
    return when (this) {
        is Type.None, is Type.UCons -> error("bug found")
        is Type.Rec   -> "Rec"
        is Type.Any   -> "Any"
        is Type.Unit  -> "Unit"
        is Type.Ptr   -> this.pln.toce() + "_ptr"
        is Type.Nat   -> this.tk_.str.replace('*','_')
        is Type.Tuple -> "TUPLE__" + this.vec.map { it.toce() }.joinToString("__")
        is Type.Union -> "UNION__" + this.vec.map { it.toce() }.joinToString("__")
        is Type.Func  -> "FUNC__" + this.inp.toce() + "__" + this.out.toce()
    }
}

fun Type.pre (): String {
    return when (this) {
        is Type.Func  -> {
            val ce = this.toce()
            this.out.pre() + this.inp.pre() + """
                #ifndef __${ce}__
                #define __${ce}__
                typedef ${this.out.pos()} $ce (${this.inp.pos()});
                #endif

            """.trimIndent()
        }
        is Type.Tuple -> {
            val pre = this.vec.map { it.pre() }.joinToString("")
            val ce = this.toce()
            pre + """
                #ifndef __${ce}__
                #define __${ce}__
                typedef struct {
                    ${this.vec  // do not filter to keep correct i
                        .mapIndexed { i,sub -> if (sub is Type.Unit) "" else { sub.pos() + " _" + (i+1).toString() + ";\n" } }
                        .joinToString("")
                    }
                } $ce;
                void output_std_${ce}_ ($ce v) {
                    printf("[");
                    ${this.vec
                        .mapIndexed { i,sub ->
                            "output_std_${sub.toce()}_(" + (if (sub is Type.Unit) "" else "v._${i + 1}") + ");\n"
                        }
                        .joinToString("putchar(',');\n")
                    }
                    printf("]");
                }
                void output_std_$ce ($ce v) {
                    output_std_${ce}_(v);
                    puts("");
                }
                #endif

            """.trimIndent()
        }
        is Type.Union -> {
            val pre = this.vec.map { it.pre() }.joinToString("")
            val ce = this.toce()

            val ctrec = this.exactlyRec()
            val cex  = if (ctrec) ce+"*" else ce
            val vx   = if (ctrec) "(*v)" else "v"
            val _ptr = if (ctrec) "_ptr" else ""

            pre + """
                #ifndef __${ce}__
                #define __${ce}__
                typedef struct $ce {
                    int tag;
                    union {
                        ${this.vec  // do not filter to keep correct i
                            .mapIndexed { i,sub ->
                                when {
                                    sub is Type.Unit -> ""
                                    sub is Type.Rec -> "struct $ce* _${i+1};\n"
                                    else -> "${sub.pos()} _${i+1};\n"
                                }
                            }
                            .joinToString("")
                        }
                    };
                } $ce;
                void output_std_$ce${_ptr}_ ($cex v) {
                    ${
                        if (!ctrec) "" else """
                            if (v == NULL) {
                                printf("<.0>");
                                return;
                            }
                        """.trimIndent()
                    }
                    printf("<.%d", $vx.tag);
                    switch ($vx.tag) {
                        ${this.expand().vec
                            .mapIndexed { i,tp -> """
                                case ${i+1}:
                                ${
                                    let {
                                        val ctrec2 = tp.containsRec()
                                        val _ptr2 = if (ctrec2) "_ptr" else ""
                                        when (tp) {
                                            is Type.Unit  -> ""
                                            is Type.Nat, is Type.Ptr -> "putchar(' '); putchar('_');"
                                            is Type.Tuple -> "putchar(' '); output_std_${tp.toce()}${_ptr2}_($vx._${i+1});"
                                            is Type.Union -> "putchar(' '); output_std_${tp.toce()}${_ptr2}_($vx._${i+1});"
                                            else -> TODO(tp.toString())
                                        }
                                    }
                                }
                                break;

                            """.trimIndent()
                            }.joinToString("")
                        }
                    }
                    putchar('>');
                }
                void output_std_$ce${_ptr} ($cex v) {
                    output_std_$ce${_ptr}_(v);
                    puts("");
                }

            """.trimIndent() + (if (!this.containsRec()) "" else """
                void free_${ce} ($ce** p) {
                    if (*p == NULL) return;
                    switch ((*p)->tag) {
                        ${ this.expand().vec
                            .mapIndexed { i,tp ->
                                if (!tp.containsRec()) "" else """
                                    case ${i+1}:
                                        free_${tp.toce()}(&(*p)->_${i+1});
                                        break;

                                """.trimIndent()
                            }
                            .joinToString("")
                        }
                        default:
                            break;
                    }
                    free(*p);
                }

            """.trimIndent()) + """
            #endif

            """.trimIndent()
        }
        else -> ""
    }
}

fun Type.pos (): String {
    return when (this) {
        is Type.None, is Type.Rec, is Type.UCons -> TODO(this.toString())
        is Type.Any, is Type.Unit  -> "void"
        is Type.Ptr   -> this.pln.pos() + "*"
        is Type.Nat   -> this.tk_.str
        is Type.Tuple -> this.toce()
        is Type.Union -> this.toce() + (if (this.exactlyRec()) "*" else "")
        is Type.Func  -> this.toce() + "*"
    }
}

val EXPRS = ArrayDeque<Pair<String,String>>()

fun Expr.UDisc.defref (env: Env, str: String): String {
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
            Pair(sub.first, "(" + (if (e.pln.toType(env).exactlyRec()) "" else "&") + sub.second + ")")
        }
        is Expr.Dnref -> {
            val sub = EXPRS.removeFirst()
            Pair(sub.first, "(*" + sub.second + ")")
        }
        is Expr.TDisc -> EXPRS.removeFirst().let { Pair(it.first, it.second /*+TODO("deref=true")*/ + "._" + e.tk_.num) }
        is Expr.UDisc -> EXPRS.removeFirst().let {
            val ee = e.defref(env,it.second)
            val pre = if (e.tk_.num == 0) {
                """
                assert(${it.second} == NULL);

                """.trimIndent()
            } else {
                """
                ${ if (e.uni.toType(env).exactlyRec()) "assert(${it.second} != NULL);\n" else "" }
                assert($ee.tag == ${e.tk_.num});

                """.trimIndent()
            }
            Pair(it.first + pre, ee + "._" + e.tk_.num)
        }
        is Expr.UPred -> EXPRS.removeFirst().let { Pair(it.first, "(${it.second /*deref=true*/}.tag == ${e.tk_.num})") }
        is Expr.TCons -> {
            val (pre,pos) = (1..e.arg.size).map { EXPRS.removeFirst() }.reversed().unzip()
            Pair (
                xp.pre() + pre.joinToString(""),
                pos.filter { it!="" }.joinToString(", ").let { "((${xp.pos()}) { $it })" }
            )
        }
        is Expr.UCons -> {
            val top = EXPRS.removeFirst()
            val ID  = "_tmp_" + e.hashCode().absoluteValue
            val arg = if (e.arg.e.toType(env) is Type.Unit) "" else (", " + top.second)
            val sup = xp.toce()
            val pre = "$sup $ID = (($sup) { ${e.tk_.num} $arg });\n"
            Pair(xp.pre() + top.first + pre, if (e.tk_.num == 0) "NULL" else ID)
        }
        is Expr.Call  -> {
            val arg = EXPRS.removeFirst()
            val f   = EXPRS.removeFirst()
            Pair (
                f.first + arg.first,
                f.second /*+ TODO("deref=true")*/ + (
                    if (e.f is Expr.Var && e.f.tk_.str=="output_std") {
                        "_" + e.arg.e.toType(env).toce()
                    } else {
                        ""
                    }
                ) + "(" + arg.second /*+ TODO("deref=true")*/ + ")"
            )
        }
        else -> TODO(e.toString())
    }.let {
        Pair (
            it.first,
            if ((tp is Type.Unit) && (e !is Expr.Call)) "" else it.second
        )
    })
}

fun code_fx (env: Env, xe: XExpr, xp: Type) {
    val top = EXPRS.removeFirst()
    val ID  = "_tmp_" + xe.hashCode().absoluteValue

    EXPRS.addFirst(when {
        (xe.x == null) -> top
        (xe.x.enu == TK.NEW) -> {
            assert(xe.e is Expr.UCons)
            val xee = xe.e as Expr.UCons
            val sup = xp.toce()
            val pre = """
                $sup* $ID = ($sup*) malloc(sizeof($sup));
                assert($ID!=NULL && "not enough memory");
                *$ID = ${top.second};

            """.trimIndent()
            Pair(top.first+pre, if (xee.tk_.num == 0) "NULL" else ID)
        }
        (xe.x.enu == TK.MOVE) -> {
            val pre = """
               typeof(${top.second}) $ID = ${top.second};
               ${top.second} = NULL;

            """.trimIndent()
            Pair(pre, ID)
        }
        else -> top
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
                if (${tst.second /*TODO("deref=true")*/}) {
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
            e.first + e.second /*+ TODO("deref=true")*/ + ";\n"
        }
        is Stmt.Block -> "{\n" + CODE.removeFirst() + "}\n"
        is Stmt.Ret   -> {
            EXPRS.removeFirst()
            "return" + if (s.e.e.toType(env) is Type.Unit) ";\n" else " _ret_;\n"
        }
        is Stmt.Var   -> {
            val src = EXPRS.removeFirst()
            s.type.pre() + src.first + (if (s.type is Type.Unit) "" else {
                "${s.type.pos()} ${s.tk_.str}" + (    // List* l
                    if (s.tk_.str == "_ret_") "" else {
                        (if (!s.type.containsRec()) "" else {
                            " __attribute__ ((__cleanup__(free_${s.type.toce()})))"
                        }) + (if (s.src.e is Expr.Unk) "" else {
                            " = " + src.second
                        })
                    }
                ) + ";\n"
            })
        }
        is Stmt.Func  -> {
            s.type.pre() + when (s.tk_.str) {
                "output_std" -> ""
                else -> {
                    val out = s.type.out.let { if (it is Type.Unit) "void" else it.pos() }
                    val inp = s.type.inp.let { if (it is Type.Unit) "void" else (it.pos()+" _arg_") }
                    """
                        auto $out ${s.tk_.str} ($inp) {
                            ${CODE.removeFirst()}
                        }

                    """.trimIndent()
                }
            }
        }
    })
}

fun Stmt.code (): String {
    this.visitXP(emptyList(), ::code_fs, ::code_fx, ::code_fe)
    //println(CODE)
    assert(EXPRS.size == 0)
    assert(CODE.size == 1)
    return ("""
        #include <assert.h>
        #include <stdio.h>
        #include <stdlib.h>
        #define output_std_Unit_() printf("()")
        #define output_std_Unit()  (output_std_Unit_(), puts(""))
        #define output_std_int_(x) printf("%d",x)
        #define output_std_int(x)  (output_std_int_(x), puts(""))
        int main (void) {
    """ + CODE.removeFirst() + """
        }
    """).trimIndent()
}