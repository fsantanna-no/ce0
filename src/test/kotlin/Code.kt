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
        assert(tp.toce() == "TUPLE__Unit__Unit")
    }

    // EXPR

    @Test
    fun b01_expr_unit () {
        val e = Expr.Unit(Tk.Sym(TK.UNIT,1,1,"()"))
        assert(e.pos() == "")
    }
    @Test
    fun b02_expr_var () {
        val e = Expr.Var(Tk.Str(TK.XVAR,1,1,"xxx"))
        env_PRV[e] = Stmt.Var (
            Tk.Str(TK.XVAR,1,1,"xxx"),
            Type.Nat(Tk.Str(TK.XNAT,1,1,"int")),
            Expr.Nat(Tk.Str(TK.XNAT,1,1,"0"))
        )
        assert(e.pos() == "xxx")
    }
    @Test
    fun b03_expr_nat () {
        val e = Expr.Var(Tk.Str(TK.XNAT,1,1,"xxx"))
        env_PRV[e] = Stmt.Var (
            Tk.Str(TK.XVAR,1,1,"xxx"),
            Type.Nat(Tk.Str(TK.XNAT,1,1,"int")),
            Expr.Nat(Tk.Str(TK.XNAT,1,1,"0"))
        )
        assert(e.pos() == "xxx")
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
        assert(e.pos() == "((TUPLE__Unit__Unit) {  })")
    }
    @Test
    fun b05_expr_index () {
        val e = Expr.Index (
            Tk.Num(TK.XNUM,1,1,1),
            Expr.Var(Tk.Str(TK.XVAR,1,1,"x"))
        )
        env_PRV[e.pre] = Stmt.Var (
            Tk.Str(TK.XVAR,1,1,"x"),
            Type.Tuple(Tk.Chr(TK.CHAR,1,1,'('), arrayOf(Type.Nat(Tk.Str(TK.XNAT,1,1,"int")))),
            Expr.Nat(Tk.Str(TK.XNAT,1,1,"0"))
        )
        assert(e.pos() == "x._1")
    }

    // STMT

    @Test
    fun c01_stmt_pass () {
        val s = Stmt.Pass(Tk.Err(TK.ERR,1,1,""))
        assert(s.pos() == "")
    }

    // CODE

    @Test
    fun d01 () {
        val s = Stmt.Pass(Tk.Err(TK.ERR,1,1,""))
        val out = s.code()
        assert(out == """
            #include <assert.h>
            #include <stdio.h>
            #include <stdlib.h>
            typedef int Int;
            #define output_std_Unit_() printf("()")
            #define output_std_Unit()  (output_std_Unit_(), puts(""))
            #define output_std_Int_(x) printf("%d",x)
            #define output_std_Int(x)  (output_std_Int_(x), puts(""))
            int main (void) {

            }
        """.trimIndent())
    }

    // STRING -> C

    fun toc (inp: String): Pair<Boolean,String> {
        val all = All_new(PushbackReader(StringReader(inp), 2))
        lexer(all)
        var s = parser_stmts(all, Pair(TK.EOF,null))
        if (s == null) {
            return Pair(false, all.err)
        }
        s = env_prelude(s)
        env_PRV_set(s, null)
        //println(s)
        return Pair(true, s.pos())
    }

    @Test
    fun e01_call () {
        val (ok, out) = toc("call _stdo _a")
        assert(ok && out == "stdo(a);\n")
    }
    @Test
    fun e02_seq () {
        val (ok, out) = toc("var a : () = () ; call _stdo a")
        assert(ok && out == "stdo();\n")
    }
    @Test
    fun e04_output () {
        val (ok, out) = toc("output std ()")
        assert(ok && out == "output_std_Unit();\n")
    }
    @Test
    fun e05_var_nat () {
        val (ok, out) = toc("var x: _int = 10")
        assert(ok && out == "int x = 10;\n")
    }
    @Test
    fun e06_var_int () {
        val (ok, out) = toc("var x: Int = 10")
        assert(ok && out == "Int x = 10;\n")
    }
}