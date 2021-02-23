fun Type.toce (): String {
    return when (this) {
        is Type.Unit  -> "Unit"
        is Type.Tuple -> "TUPLE__" + this.vec.map { it.toce() }.joinToString("__")
        else -> error("TODO")
    }
}

fun Type.toc (): String {
    return when (this) {
        is Type.Tuple -> this.toce()
        else -> error("TODO")
    }
}

fun Expr.toc (): String {
    return when (this) {
        is Expr.Unit  -> ""
        is Expr.Var   -> this.tk_.str
        is Expr.Nat   -> this.tk_.str
        is Expr.Tuple -> "((${this.totype().toc()}) { })"
        is Expr.Index -> this.pre.toc() + "._" + this.tk_.num
        is Expr.Call  ->  {
            val (pre,pos) = when {
                (this.tk.enu != TK.OUT) -> Pair("","")
                (this.pre is Expr.Var && this.pre.tk_.str=="std") -> Pair("output_","_"+this.pos.totype().toce())
                else -> Pair("output_","")
            }
            pre + this.pre.toc() + pos + "(" + this.pos.toc() + ")"
        }
        else -> error("TODO")
    }
}

fun Stmt.toc (): String {
    return when (this) {
        is Stmt.Pass -> ""
        is Stmt.Seq  -> this.s1.toc() + this.s2.toc()
        is Stmt.Call -> this.call.toc() + ";\n"
        is Stmt.Var  -> {
            if (this.type is Type.Unit) {
                return ""
            }
            error("TODO")
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

            // enum { Bool_False, Bool_True } _Bool_;
            val ret2 = "typedef enum { " + this.subs.map { ID + "_" + it.first.str }.joinToString(", ") + " } _${ID}_;"

            return (ret1 + "\n" + ret2)
        }
        else -> error("TODO")
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
    """ + this.toc() + """
        }
    """).trimIndent()
}