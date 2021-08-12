import kotlin.math.absoluteValue

fun Type.toce (): String {
    return when (this) {
        is Type.Any   -> "Any"
        is Type.Unit  -> "Unit"
        is Type.Ptr   -> this.tp.toce() + "_ptr"
        is Type.Nat   -> this.tk_.str.replace('*','_')
        is Type.User  -> this.tk_.str
        is Type.Tuple -> "TUPLE__" + this.vec.map { it.toce() }.joinToString("__")
        is Type.Func  -> "FUNC__" + this.inp.toce() + "__" + this.out.toce()
    }
}

fun Type.pre (): String {
    return when (this) {
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
                    printf("(");
                    ${this.vec
                        .mapIndexed { i,sub ->
                            when (sub) {
                                is Type.Nat -> "putchar('_')"
                                else -> "output_std_${sub.toce()}_(" + (if (sub is Type.Unit) "" else "v._${i + 1}") + ")"
                            } + ";\n"
                        }
                        .joinToString("putchar(',');\n")
                    }
                    printf(")");
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
        is Type.Ptr   -> this.tp.pos() + "*"
        is Type.Nat   -> this.tk_.str
        is Type.User  -> (this.idToStmt(this.tk_.str) as Stmt.User).let {
            if (it.isrec) {
                "struct ${this.tk_.str}*"
            } else {
                this.tk_.str
            }
        }
        is Type.Tuple -> this.toce()
        is Type.Func  -> this.toce() + "*"
    }
}

fun Expr.pre (): String {
    return when (this) {
        is Expr.Unk, is Expr.Unit, is Expr.Int, is Expr.Var, is Expr.Nat -> ""
        is Expr.Tuple -> this.toType().pre() + this.vec.map { it.pre() }.joinToString("")
        is Expr.Cons  -> {
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
        }
        is Expr.Dnref -> this.e.pre()
        is Expr.Upref -> this.e.pre()
        is Expr.Index -> this.e.pre()
        is Expr.Pred  -> this.e.pre()
        is Expr.Disc  -> {
            val tpe = this.e.toType() as Type.User
            this.e.pre() + (
                if (this.tk_.str == "Nil") {
                    "assert(${this.e.pos(false)} == NULL);\n"
                } else {
                    (if (!tpe.ishasrec()) {
                        ""
                    } else {
                        "assert(${this.e.pos(false)} != NULL);\n"
                    }) + "assert(${this.e.pos(true)}.sub == ${tpe.tk_.str}_${this.tk_.str});\n"
                }
            )
        }
        is Expr.Call  -> this.f.pre() + this.arg.pre()
    }
}

fun Expr.pos (deref: Boolean): String {
    val TP = this.toType()
    if ((TP is Type.Unit) && (this !is Expr.Call)) {
        return ""
    }
    return when (this) {
        is Expr.Unit  -> ""
        is Expr.Nat   -> this.tk_.str
        is Expr.Int   -> this.tk_.num.toString()
        is Expr.Var   -> if (TP is Type.Unit) "" else this.tk_.str
        is Expr.Upref -> "&" + this.e.pos(false)
        is Expr.Dnref -> "*" + this.e.pos(false)
        is Expr.Index -> this.e.pos(true) + "._" + this.tk_.num
        is Expr.Disc  -> this.e.pos(true) + "._" + this.tk_.str
        is Expr.Pred  -> "((${this.e.pos(true)}.sub == ${(this.e.toType() as Type.User).tk_.str}_${this.tk_.str}) ? (Bool){Bool_True} : (Bool){Bool_False})"
        is Expr.Cons  -> if (this.sub.str=="Nil") "NULL" else "_tmp_${this.hashCode().absoluteValue}"
        is Expr.Tuple -> {
            val vec = this.vec
                .filter { it.toType() !is Type.Unit }
                .map { it.pos(false) }
                .joinToString(", ")

            "((${TP.pos()}) { $vec })"
        }
        is Expr.Call  -> {
            this.f.pos(true) + (
                if (this.f is Expr.Var && this.f.tk_.str=="output_std") {
                    "_" + this.arg.toType().toce()
                } else {
                    ""
                }
            ) + "(" + this.arg.pos(true) + ")"
        }
        else -> { println(this) ; error("TODO") }
    }.let {
        if (deref && TP.ishasrec()) "(*($it))" else it
    }
}

fun Stmt.pos (): String {
    return when (this) {
        is Stmt.Pass  -> ""
        is Stmt.Nat   -> this.tk_.str + "\n"
        is Stmt.Seq   -> this.s1.pos() + this.s2.pos()
        is Stmt.Set   -> {
            this.dst.pre() + this.src.pre() + (
                (if (this.dst.toType() is Type.Unit) "" else (this.dst.pos(false)+" = ")) +
                    this.src.pos(false) + ";\n"
            )
        }
        is Stmt.If    -> this.tst.pre() + """
            if (${this.tst.pos(true)}.sub) {
                ${this.true_.pos()}
            } else {
                ${this.false_.pos()}
            }
        """.trimIndent()
        is Stmt.Loop  -> """
            while (1) {
                ${this.block.pos()}
            }
        """.trimIndent()
        is Stmt.Break -> "break;\n"
        is Stmt.Call  -> this.call.pre() + this.call.pos(true) + ";\n"
        is Stmt.Block -> "{\n" + this.body.pos() + "}\n"
        is Stmt.Ret   -> "return" + if (this.e.toType() is Type.Unit) ";\n" else " _ret_;\n"
        is Stmt.Var   -> {
            this.init.pre() +
                if (this.type is Type.Unit) "" else {
                    "${this.type.pos()} ${this.tk_.str}" + (    // List* l
                        if (this.tk_.str == "_ret_") "" else {
                            (if (!this.type.ishasrec()) "" else {
                                " __attribute__ ((__cleanup__(${this.type.toce()}_free)))"
                            }) + (if (this.init is Expr.Unk) "" else {
                                " = " + this.init.pos(false)
                            })
                        }
                    ) + ";\n"
                }
        }
        is Stmt.User  -> {
            val ID = this.tk_.str
            if (ID == "Int") {
                return ""
            }

            // struct Bool;
            // typedef struct Bool Bool;
            val ret1 = """
                struct $ID;
                typedef struct $ID $ID;

            """.trimIndent()

            val (v,fn,ptr) = if (this.isrec) Triple("(*(*v))","_ptr","**") else Triple("v","","")
            val ret2 = """
                ${
                    if (!this.isrec) "" else {
                        "auto void ${ID}_free ($ID** p);\n"
                    }
                }
                auto void output_std_$ID${fn}_ ($ID$ptr v);
                
            """.trimIndent()

            if (this.subs.isEmpty()) {
                return ret1 + ret2
            }

            val ret3 = this.subs.map { it.second.pre() + "\n" }.joinToString("")

            // enum { Bool_False, Bool_True } _Bool_;
            val ret4 = """
                typedef enum {
                    ${ this.subs
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
                        ${ this.subs
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
                        if (this.isrec) {
                            """
                            if (*v == NULL) {
                                printf("Nil");
                                return;
                            }
    
                            """
                        } else { "" }
                    }
                    switch ($v.sub) {
                        ${this.subs
                            .map { (sub,tp) -> """
                                case ${ID}_${sub.str}:
                                    printf("${sub.str}");
                                    ${
                                        if (false) "" else {
                                            val (op2,fn2) = if (tp.ishasrec()) Pair("&","_ptr") else Pair("","")
                                            when (tp) {
                                                is Type.Nat -> "putchar('_');"
                                                is Type.Tuple -> "putchar(' '); output_std_${tp.toce()}${fn2}_($op2$v._${sub.str});"
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
            val ret7 = if (!this.isrec) "" else {
                """
                    void ${ID}_free ($ID** p) {
                        if (*p == NULL) return;
                        switch ((*p)->sub) {
                            ${ this.subs
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
        }
        is Stmt.Func  -> {
            this.type.pre() + when (this.tk_.str) {
                "output_std" -> ""
                else -> {
                    val out = this.type.out.let { if (it is Type.Unit) "void" else it.pos() }
                    val inp = this.type.inp.let { if (it is Type.Unit) "void" else (it.pos()+" _arg_") }
                    """
                        auto $out ${this.tk_.str} ($inp) {
                            ${this.block!!.pos()}
                        }
                        
                    """.trimIndent()
                }
            }
        }
    }
}

fun Stmt.code (): String {
    return ("""
        #include <assert.h>
        #include <stdio.h>
        #include <stdlib.h>
        typedef int Int;
        #define output_std_Unit_() printf("()")
        #define output_std_Unit()  (output_std_Unit_(), puts(""))
        #define output_std_Int_(x) printf("%d",x)
        #define output_std_Int(x)  (output_std_Int_(x), puts(""))
        int main (void) {
    """ + this.pos() + """
        }
    """).trimIndent()
}