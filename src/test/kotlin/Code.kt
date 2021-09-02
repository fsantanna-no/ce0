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
        val tp = Type.Tuple(Tk.Chr(TK.CHAR,1,1,'['), arrayOf(tp_unit,tp_unit))
        assert(tp.toce() == "TUPLE__Unit__Unit")
    }

    // EXPR

    @Test
    fun b01_expr_unit () {
        val e = Expr.Unit(Tk.Sym(TK.UNIT,1,1,"()"))
        code_fe(emptyList(), e, tp_unit)
        assert(EXPRS.removeFirst().second == "")
    }
    @Test
    fun b02_expr_var () {
        val e = Expr.Var(Tk.Str(TK.XVAR,1,1,"xxx"))
        val env = listOf (
            Stmt.Var (
                Tk.Str(TK.XVAR,1,1,"xxx"),
                false,
                Type.Nat(Tk.Str(TK.XNAT,1,1,"int")),
                XExpr(null, Expr.Nat(Tk.Str(TK.XNAT,1,1,"0")))
            )
        )
        code_fe(env, e, tp_unit)
        assert(EXPRS.removeFirst().second == "xxx")
    }
    @Test
    fun b03_expr_nat () {
        val e = Expr.Var(Tk.Str(TK.XNAT,1,1,"xxx"))
        val env = listOf (
            Stmt.Var (
                Tk.Str(TK.XVAR,1,1,"xxx"),
                false,
                Type.Nat(Tk.Str(TK.XNAT,1,1,"int")),
                XExpr(null, Expr.Nat(Tk.Str(TK.XNAT,1,1,"0")))
            )
        )
        code_fe(env, e, tp_unit)
        assert(EXPRS.removeFirst().second == "xxx")
    }
    @Test
    fun b04_expr_tuple () {
        val e = Expr.TCons (
            Tk.Chr(TK.CHAR,0, 0, '('),
            arrayOf (
                XExpr(null, Expr.Unit(Tk.Sym(TK.UNIT,1,1,"()"))),
                XExpr(null, Expr.Unit(Tk.Sym(TK.UNIT,1,1,"()"))),
            )
        )
        e.visitXP(emptyList(), ::code_fx, ::code_fe,
            Type.Tuple(Tk.Chr(TK.CHAR,1,1,'['), listOf(tp_unit,tp_unit).toTypedArray()))
        EXPRS.removeFirst().second.let {
            //println(it)
            assert(it == "((struct TUPLE__Unit__Unit) {  })")
        }
    }
    @Test
    fun b05_expr_index () {
        val e = Expr.TDisc (
            Tk.Num(TK.XNUM,1,1,1),
            Expr.Var(Tk.Str(TK.XVAR,1,1,"x"))
        )
        val env = listOf (
            Stmt.Var (
                Tk.Str(TK.XVAR,1,1,"x"),
                false,
                Type.Tuple(Tk.Chr(TK.CHAR,1,1,'('), arrayOf(Type.Nat(Tk.Str(TK.XNAT,1,1,"int")))),
                XExpr(null, Expr.Nat(Tk.Str(TK.XNAT,1,1,"0")))
            )
        )
        e.visitXP(env, ::code_fx, ::code_fe, tp_unit)
        EXPRS.removeFirst().second.let {
            //println(it)
            assert(it == "x._1")
        }
    }

    // STMT

    @Test
    fun c01_stmt_pass () {
        val s = Stmt.Pass(Tk.Err(TK.ERR,1,1,""))
        s.visit(emptyList(), ::code_fs, null, null)
        assert(CODE.removeFirst() == "")
        assert(CODE.size == 0)
    }

    // CODE

    @Test
    fun d01 () {
        val s = Stmt.Pass(Tk.Err(TK.ERR,1,1,""))
        val out = s.code()
        println(out)
        assert(out == """
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


            int main (void) {
                
            }
        """.trimIndent())
    }

    // STRING -> C

    fun toc (inp: String): String {
        val all = All_new(PushbackReader(StringReader(inp), 2))
        lexer(all)
        var s = parser_stmts(all, Pair(TK.EOF,null))
        s = env_prelude(s)
        s.visit(emptyList(), ::code_fs, null, null)
        return CODE.removeFirst()
    }
}