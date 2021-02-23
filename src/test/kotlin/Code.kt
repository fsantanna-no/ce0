import org.junit.jupiter.api.MethodOrderer.Alphanumeric
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.io.PushbackReader
import java.io.StringReader

@TestMethodOrder(Alphanumeric::class)
class Code {

    val tp_unit = Type.Unit(Tk.Sym(TK.UNIT,1,1,"()"))
    // TYPE

    @Test
    fun a01_type_unit () {
        assert(tp_unit.toce() == "Unit")
    }
    @Test
    fun a02_type_tuple () {
        val tp = Type.Tuple(Tk.Chr(TK.CHAR,1,1,'('), arrayOf(tp_unit,tp_unit))
        println(tp.toce())
        assert(tp.toce() == "TUPLE__Unit__Unit")
    }

    // EXPR

    @Test
    fun b01_expr_unit () {
        val e = Expr.Unit(Tk.Sym(TK.UNIT,1,1,"()"))
        assert(e.toc(emptyList()) == "")
    }
    @Test
    fun b02_expr_var () {
        val e = Expr.Var(Tk.Str(TK.XVAR,1,1,"xxx"))
        assert(e.toc(emptyList()) == "xxx")
    }
    @Test
    fun b03_expr_nat () {
        val e = Expr.Var(Tk.Str(TK.XNAT,1,1,"xxx"))
        assert(e.toc(emptyList()) == "xxx")
    }
    @Test
    fun b04_expr_tuple () {
        val e = Expr.Tuple (
            Tk.Chr(TK.CHAR,0, 0, '('),
            arrayOf (
                Expr.Unit(Tk.Sym(TK.UNIT,1,1,"()")),
                Expr.Unit(Tk.Sym(TK.UNIT,1,1,"()")),
            )
        )
        assert(e.toc(emptyList()) == "((TUPLE__Unit__Unit) { })")
    }
    @Test
    fun b05_expr_index () {
        val e = Expr.Index (
            Tk.Num(TK.XNUM,1,1,2),
            Expr.Var(Tk.Str(TK.XVAR,1,1,"x"))
        )
        assert(e.toc(emptyList()) == "x._2")
    }

    // STMT

    @Test
    fun c01_stmt_pass () {
        val s = Stmt.Pass(Tk.Err(TK.ERR,1,1,""))
        assert(s.toc(emptyList()) == "")
    }

    // CODE

    @Test
    fun d01 () {
        val s = Stmt.Pass(Tk.Err(TK.ERR,1,1,""))
        val out = s.code(emptyList())
        println(out)
        assert(out == """
            #include <assert.h>
            #include <stdio.h>
            #include <stdlib.h>
            typedef int Int;
            #define stdout_Unit_() printf("()")
            #define stdout_Unit()  (stdout_Unit_(), puts(""))
            #define stdout_Int_(x) printf("%d",x)
            #define stdout_Int(x)  (stdout_Int_(x), puts(""))
            int main (void) {

            }
        """.trimIndent())
    }

    // STRING -> C

    fun toc (inp: String): Pair<Boolean,String> {
        val all = All_new(PushbackReader(StringReader(inp), 2))
        lexer(all)
        val s = parser_stmt(all)
        if (s == null) {
            return Pair(false, all.err)
        }
        return Pair(true, s.toc(emptyList()))
    }

    @Test
    fun e01_call () {
        val (ok, out) = toc("call _stdo a")
        assert(ok && out == "stdo(a);\n")
    }

}