fun Type.toce (): String {
    return when (this) {
        is Type.Unit  -> "Unit"
        is Type.Nat   -> this.tk_.str.replace('*','_')
        is Type.User  -> this.tk_.str
        is Type.Tuple -> "TUPLE__" + this.vec.map { it.toce() }.joinToString("__")
        else -> { println(this) ; error("TODO") }
    }
}

fun Type.pre (): String {
    return when (this) {
        is Type.Tuple -> {
            val ce = this.toce()
            """
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
                            "output_std_${sub.toce()}_(" + (if (sub is Type.Unit) "" else "v._${i+1}") + ");\n"
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
        else -> ""
    }
}

fun Type.pos (): String {
    return when (this) {
        is Type.Nat   -> this.tk_.str
        is Type.User  -> this.tk_.str
        is Type.Tuple -> this.toce()
        else -> { println(this) ; error("TODO") }
    }
}

fun Expr.pre (): String {
    return when (this) {
        is Expr.Tuple -> this.totype().pre()
        else -> ""
    }
}

fun Expr.pos (): String {
    val TP = this.totype()
    if ((TP is Type.Unit) && (this !is Expr.Call)) {
        return ""
    }
    return when (this) {
        is Expr.Unit  -> ""
        is Expr.Nat   -> this.tk_.str
        is Expr.Int   -> this.tk_.num.toString()
        is Expr.Var   -> if (TP is Type.Unit) "" else this.tk_.str
        is Expr.Cons  -> {
            val user = this.id2stmt(this.sup.str)!! as Stmt.User
            val tp = user.subs.first { it.first.str==this.sub.str }.second
            val arg = if (tp is Type.Unit) "" else (", " + this.arg.pos())
            "((${this.sup.str}) { ${this.sup.str}_${this.sub.str}$arg })"
        }
        is Expr.Tuple -> {
            val vec = this.vec
                .filter { it.totype() !is Type.Unit }
                .map { it.pos() }
                .joinToString(", ")

            "((${TP.pos()}) { $vec })"
        }
        is Expr.Index -> this.pre.pos() + "._" + this.tk_.num
        is Expr.Call  -> this.pre.pos() + (
            if (this.pre is Expr.Var && this.pre.tk_.str=="output_std") {
                "_" + this.pos.totype().toce()
            } else {
                ""
            }
        ) + "(" + this.pos.pos() + ")"
        else -> { println(this) ; error("TODO") }
    }
}

fun Stmt.pos (): String {
    return when (this) {
        is Stmt.Pass  -> ""
        is Stmt.Nat   -> this.tk_.str + "\n"
        is Stmt.Seq   -> this.s1.pos() + this.s2.pos()
        is Stmt.Set   -> this.dst.pre() + this.src.pre() + (
            (if (this.dst.totype() is Type.Unit) "" else (this.dst.pos()+" = ")) +
            this.src.pos() + ";\n"
        )
        is Stmt.Call  -> this.call.pre() + this.call.pos() + ";\n"
        is Stmt.Block -> "{\n" + this.body.pos() + "}\n"
        is Stmt.Ret   -> "return" + if (this.e.totype() is Type.Unit) ";\n" else " _ret_;\n"
        is Stmt.Var  -> {
            this.init.pre() +
                if (this.type is Type.Unit) {
                    ""
                } else {
                    "${this.type.pos()} ${this.tk_.str}" + (
                        if (this.init is Expr.Unk) {
                            ";\n"
                        } else {
                            " = " + this.init.pos() + ";\n"
                        }
                    )
                }
        }
        is Stmt.User -> {
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

            val ret2 = "" //this.subs.map { it.second.pre() + "\n" }.joinToString()

            // enum { Bool_False, Bool_True } _Bool_;
            val ret3 = """
                typedef enum {
                    ${ this.subs
                        .map { ID + "_" + it.first.str }
                        .joinToString(", ")
                    }
                } _${ID}_;

            """.trimIndent()

            // struct Bool { _Bool_ sub; union { ... } };
            val ret4 = """
                struct $ID {
                    _${ID}_ sub;
                    union {
                        ${ this.subs
                            .filter { (_,tp) -> tp !is Type.Unit }
                            .map { (sub,tp) -> tp.pos() + " _" + sub + ";\n" }
                            .joinToString("")
                        }
                    };
                };
                
            """.trimIndent()

            val ret5 = """
                void output_std_${ID}_ ($ID v) {
                    switch (v.sub) {
                        ${this.subs
                            .map { (sub,tp) -> """
                                case ${ID}_${sub.str}:
                                    printf("${sub.str}");
                                    ${
                                        if (tp is Type.Unit) {
                                            ""
                                        } else {
                                            "output_std_${tp.toce()}_(v._$sub);"                                         
                                        }
                                    }
                                    break;

                            """.trimIndent()
                            }
                            .joinToString("")
                        }
                    }
                }
                void output_std_${ID} ($ID v) {
                    output_std_${ID}_(v);
                    puts("");
                }
                
            """.trimIndent()

            return (ret1 + ret2 + ret3 + ret4 + ret5)
        }
        is Stmt.Func -> {
            this.type.inp.pre() + this.type.out.pre() + when (this.tk_.str) {
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
        else -> { println(this) ; error("TODO") }
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