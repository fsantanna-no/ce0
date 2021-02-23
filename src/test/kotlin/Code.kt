import org.junit.jupiter.api.MethodOrderer.Alphanumeric
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.io.PushbackReader
import java.io.StringReader

@TestMethodOrder(Alphanumeric::class)
class Code {

    // EXPR

    @Test
    fun a01_expr_unit () {
        val e = Expr.Unit(Tk(TK.UNIT,null,1,1))
        val out = code_expr(e)
        assert(out == "")
    }
    @Test
    fun a02_expr_var () {
        val e = Expr.Var(Tk(TK.XVAR,TK_Str("xxx"),1,1))
        val out = code_expr(e)
        assert(out == "xxx")
    }
    @Test
    fun a03_expr_nat () {
        val e = Expr.Var(Tk(TK.XNAT,TK_Str("xxx"),1,1))
        val out = code_expr(e)
        assert(out == "xxx")
    }
}