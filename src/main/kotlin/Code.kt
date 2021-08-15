import kotlin.math.absoluteValue

fun Type.toce (): String {
    return when (this) {
        is Type.Any  -> "Any"
        is Type.Unit -> "Unit"
        is Type.Ptr  -> this.tp.toce() + "_ptr"
        is Type.Nat  -> this.tk_.str.replace('*','_')
        is Type.Cons -> "TUPLE__" + this.vec.map { it.toce() }.joinToString("__")
        is Type.Func -> "FUNC__" + this.inp.toce() + "__" + this.out.toce()
        is Type.Rec  -> error("TODO")
        is Type.Varia -> error("TODO")
    }
}

fun Type.pre (): String {
    return when (this) {
        is Type.Cons -> {
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
        is Type.Func  -> {
            val ce = this.toce()
            this.out.pre() + this.inp.pre() +
            """
                #ifndef __${ce}__
                #define __${ce}__
                typedef ${this.out.pos()} $ce (${this.inp.pos()});
                #endif

            """.trimIndent()
        }
        else -> ""
    }
}

fun Type.pos (): String {
    return when (this) {
        is Type.Any, is Type.Unit  -> "void"
        is Type.Ptr  -> this.tp.pos() + "*"
        is Type.Nat  -> this.tk_.str
        is Type.Cons -> this.toce()
        is Type.Func -> this.toce() + "*"
        is Type.Rec  -> error("TODO")
        is Type.Varia -> error("TODO")
    }
}

fun XExpr.pre (env: Env): String {
    val ismove = (this.x!=null && this.x.enu==TK.MOVE)
    val pre = if (!ismove) "" else {
        """
       typeof(${this.e.pos(env,false)}) _tmp_${this.hashCode().absoluteValue} = ${this.e.pos(env,false)};
       ${this.e.pos(env,false)} = NULL;
        """
    }
    return pre + this.e.pre(env)
}

fun XExpr.pos (env: Env, deref: Boolean): String {
    val ismove = (this.x!=null && this.x.enu==TK.MOVE)
    return if (ismove) {
        assert(!deref)  // TODO: i'm not sure
        "_tmp_${this.hashCode().absoluteValue}"
    } else {
        this.e.pos(env,deref)
    }
}

fun Expr.pre (env: Env): String {
    return when (this) {
        is Expr.Unk, is Expr.Unit, is Expr.Var, is Expr.Nat -> ""
        is Expr.Tuple -> this.toType(env).pre() + this.vec.map { it.pre(env) }.joinToString("")
        is Expr.Varia -> error("TODO-Varia")/*{
            val user = this.idToStmt(this.sup.str)!! as Stmt.User
            val tp   = this.subType()
            val arg  = if (tp is Type.Unit) "" else (", " + this.arg.pos(false))
            val N    = this.hashCode().absoluteValue
            val sup  = this.sup.str
            this.arg.pre() + if (this.sub.str=="Nil") "" else """
                ${
                    if (user.isrec) {
                        """
                        $sup* _tmp_$N = ($sup*) malloc(sizeof($sup));
                        assert(_tmp_$N!=NULL && "not enough memory");
                        *_tmp_$N = 
                        """
                    } else {
                        """
                        $sup _tmp_$N =
                        """.trimIndent()
                    }
                }
                (($sup) { ${sup}_${this.sub.str}$arg });
                
            """
        }*/
        is Expr.Dnref -> this.e.pre(env)
        is Expr.Upref -> this.e.pre(env)
        is Expr.Index -> this.e.pre(env)
        is Expr.Call  -> this.f.pre(env) + this.e.pre(env)
    }
}

fun Expr.pos (env: Env, deref: Boolean): String {
    val TP = this.toType(env)
    if ((TP is Type.Unit) && (this !is Expr.Call)) {
        return ""
    }
    return when (this) {
        is Expr.Unit  -> ""
        is Expr.Nat   -> this.tk_.str
        is Expr.Var   -> if (TP is Type.Unit) "" else this.tk_.str
        is Expr.Upref -> "&" + this.e.pos(env, false)
        is Expr.Dnref -> "*" + this.e.pos(env, false)
        is Expr.Index -> this.e.pos(env, true) + "._" + this.tk_.idx
        /*
        is Expr.Disc  -> this.e.pos(true) + "._" + this.tk_.str
        is Expr.Pred  -> "((${this.e.pos(true)}.sub == ${(this.e.toType() as Type.User).tk_.str}_${this.tk_.str}) ? (Bool){Bool_True} : (Bool){Bool_False})"
        is Expr.Union  -> if (this.sub.str=="Nil") "NULL" else "_tmp_${this.hashCode().absoluteValue}"
         */
        is Expr.Tuple -> {
            val vec = this.vec
                .filter { it.e.toType(env) !is Type.Unit }
                .map { it.e.pos(env, false) }
                .joinToString(", ")

            "((${TP.pos()}) { $vec })"
        }
        is Expr.Call  -> {
            this.f.pos(env, true) + (
                if (this.f is Expr.Var && this.f.tk_.str=="output_std") {
                    "_" + this.e.e.toType(env).toce()
                } else {
                    ""
                }
            ) + "(" + this.e.e.pos(env, true) + ")"
        }
        else -> { println(this) ; error("TODO-2") }
    }.let {
        if (deref && TP.containsRec()) "(*($it))" else it
    }
}

val CODE = ArrayDeque<String>()

fun code_fs (env: Env, s: Stmt) {
    CODE.addFirst(when (s) {
        is Stmt.Pass -> ""
        is Stmt.Nat  -> s.tk_.str + "\n"
        is Stmt.Seq  -> { val s2=CODE.removeFirst() ; val s1=CODE.removeFirst() ; s1+s2 }
        is Stmt.Set  -> {
            s.dst.toExpr().pre(env) + s.src.pre(env) + (
                    (if (s.dst.toExpr().toType(env) is Type.Unit) "" else (s.dst.toExpr().pos(env,false)+" = ")) +
                            s.src.pos(env,false) + ";\n"
                    )
        }
        is Stmt.If -> {
            val false_ = CODE.removeFirst()
            val true_  = CODE.removeFirst()
            s.tst.pre(env) + """
            if (${s.tst.pos(env,true)}.sub) {
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
        is Stmt.Call  -> s.call.pre(env) + s.call.pos(env,true) + ";\n"
        is Stmt.Block -> "{\n" + CODE.removeFirst() + "}\n"
        is Stmt.Ret   -> "return" + if (s.e.e.toType(env) is Type.Unit) ";\n" else " _ret_;\n"
        is Stmt.Var   -> {
            s.init.pre(env) +
                    if (s.type is Type.Unit) "" else {
                        "${s.type.pos()} ${s.tk_.str}" + (    // List* l
                                if (s.tk_.str == "_ret_") "" else {
                                    (if (!s.type.containsRec()) "" else {
                                        " __attribute__ ((__cleanup__(${s.type.toce()}_free)))"
                                    }) + (if (s.init.e is Expr.Unk) "" else {
                                        " = " + s.init.pos(env,false)
                                    })
                                }
                                ) + ";\n"
                    }
        }
        /*is Stmt.User  -> {
            val ID = s.tk_.str
            if (ID == "Int") {
                return ""
            }

            // struct Bool;
            // typedef struct Bool Bool;
            val ret1 = """
                struct $ID;
                typedef struct $ID $ID;

            """.trimIndent()

            val (v,fn,ptr) = if (s.isrec) Triple("(*(*v))","_ptr","**") else Triple("v","","")
            val ret2 = """
                ${
                    if (!s.isrec) "" else {
                        "auto void ${ID}_free ($ID** p);\n"
                    }
                }
                auto void output_std_$ID${fn}_ ($ID$ptr v);

            """.trimIndent()

            if (s.subs.isEmpty()) {
                return ret1 + ret2
            }

            val ret3 = s.subs.map { it.second.pre() + "\n" }.joinToString("")

            // enum { Bool_False, Bool_True } _Bool_;
            val ret4 = """
                typedef enum {
                    ${ s.subs
                        .map { ID + "_" + it.first.str }
                        .joinToString(", ")
                    }
                } _${ID}_;

            """.trimIndent()

            // struct Bool { _Bool_ sub; union { ... } };
            val ret5 = """
                struct $ID {
                    _${ID}_ sub;
                    union {
                        ${ s.subs
                            .filter { (_,tp) -> tp !is Type.Unit }
                            .map { (sub,tp) -> tp.pos() + " _" + sub.str + ";\n" }
                            .joinToString("")
                        }
                    };
                };

            """.trimIndent()

            // void output_std_Bool_ (Bool v) { ... }
            val ret6 = """
                void output_std_$ID${fn}_ ($ID$ptr v) {
                    ${
                        if (s.isrec) {
                            """
                            if (*v == NULL) {
                                printf("Nil");
                                return;
                            }

                            """
                        } else { "" }
                    }
                    switch ($v.sub) {
                        ${s.subs
                            .map { (sub,tp) -> """
                                case ${ID}_${sub.str}:
                                    printf("${sub.str}");
                                    ${
                                        if (false) "" else {
                                            val (op2,fn2) = if (tp.ishasrec()) Pair("&","_ptr") else Pair("","")
                                            when (tp) {
                                                is Type.Nat -> "putchar('_');"
                                                is Type.Cons -> "putchar(' '); output_std_${tp.toce()}${fn2}_($op2$v._${sub.str});"
                                                is Type.User -> "putchar(' '); putchar('('); output_std_${tp.toce()}${fn2}_($op2$v._${sub.str}); putchar(')');"
                                                else -> ""
                                            }
                                        }
                                    }
                                    break;

                            """.trimIndent()
                            }
                            .joinToString("")
                        }
                    }
                }
                void output_std_$ID$fn ($ID$ptr v) {
                    output_std_$ID${fn}_(v);
                    puts("");
                }

            """.trimIndent()

            // void List_free (List** p) { ... }
            val ret7 = if (!s.isrec) "" else {
                """
                    void ${ID}_free ($ID** p) {
                        if (*p == NULL) return;
                        switch ((*p)->sub) {
                            ${ s.subs
                                .map { (id,tp) ->
                                    if (!tp.ishasrec()) "" else
                                        """
                                        case ${ID}_${id.str}:
                                            ${tp.toce()}_free(&(*p)->_${id.str});
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

                """.trimIndent()
            }

            return (ret1 + ret2 + ret3 + ret4 + ret5 + ret6 + ret7)
        } */
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
    this.visit(emptyList(),::code_fs,null)
    //println(CODE)
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