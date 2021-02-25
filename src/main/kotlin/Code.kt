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
                        .mapIndexed { i,sub -> if (sub is Type.Unit) "output_std_Unit_();\n" else "output_std_${sub.toce()}_(v._$i);\n" }
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
    val tp = this.totype()
    if ((tp is Type.Unit) && (this !is Expr.Call)) {
        return ""
    }
    return when (this) {
        is Expr.Unit  -> ""
        is Expr.Nat   -> this.tk_.str
        is Expr.Int   -> this.tk_.num.toString()
        is Expr.Var   -> if (tp is Type.Unit) "" else this.tk_.str
        is Expr.Tuple -> {
            val vec = this.vec
                .filter { it.totype() !is Type.Unit }
                .map { it.pos() }
                .joinToString(", ")

            "((${tp.pos()}) { $vec })"
        }
        is Expr.Index -> this.pre.pos() + "._" + this.tk_.num
        is Expr.Call  ->  {
            val (pre,pos) = when {
                (this.tk.enu != TK.OUT) -> Pair("","")
                (this.pre is Expr.Var && this.pre.tk_.str=="std") -> Pair("output_","_"+this.pos.totype().toce())
                else -> Pair("output_","")
            }
            pre + this.pre.pos() + pos + "(" + this.pos.pos() + ")"
        }
        else -> { println(this) ; error("TODO") }
    }
}

fun Stmt.pos (): String {
    return when (this) {
        is Stmt.Pass -> ""
        is Stmt.Nat  -> this.tk_.str + "\n"
        is Stmt.Seq  -> this.s1.pos() + this.s2.pos()
        is Stmt.Call -> this.call.pre() + this.call.pos() + ";\n"
        is Stmt.Var  -> {
            this.init.pre() +
                if (this.type is Type.Unit) {
                    ""
                } else {
                    "${this.type.pos()} ${this.tk_.str} = ${this.init.pos()};\n"
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
            val ret3 = "typedef enum { " + this.subs.map { ID + "_" + it.first.str }.joinToString(", ") + " } _${ID}_;"

            return (ret1 + "\n" + ret2 + ret3 + "\n")
        }
        is Stmt.Func -> {
            this.type.inp.pre() + this.type.out.pre() +
                if (this.tk_.str == "std") {
                    ""
                } else {
                    println(this); error("TODO")
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