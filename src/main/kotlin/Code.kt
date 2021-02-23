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

fun Expr.toc (envs: Envs): String {
    return when (this) {
        is Expr.Unit  -> ""
        is Expr.Var   -> this.tk_.str
        is Expr.Nat   -> this.tk_.str
        is Expr.Tuple -> "((${this.totype(envs).toc()}) { })"
        is Expr.Index -> this.pre.toc(envs) + "._" + this.tk_.num
        is Expr.Call  -> this.pre.toc(envs) + "(" + this.pos.toc(envs) + ")"
        else -> error("TODO")
    }
}

fun Stmt.toc (envs: Envs): String {
    return when (this) {
        is Stmt.Pass -> ""
        is Stmt.Call -> this.call.toc(envs) + ";\n"
        else -> error("TODO")
    }
}

fun Stmt.code (envs: Envs): String {
    return ("""
        #include <assert.h>
        #include <stdio.h>
        #include <stdlib.h>
        typedef int Int;
        #define stdout_Unit_() printf("()")
        #define stdout_Unit()  (stdout_Unit_(), puts(""))
        #define stdout_Int_(x) printf("%d",x)
        #define stdout_Int(x)  (stdout_Int_(x), puts(""))
        int main (void) {
    """ + this.toc(envs) + """
        }
    """).trimIndent()
}