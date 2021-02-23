import org.junit.jupiter.api.MethodOrderer.Alphanumeric
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.io.PushbackReader
import java.io.StringReader

@TestMethodOrder(Alphanumeric::class)
class Code {

    val tp_unit = Type.Unit(Tk(TK.UNIT,null,1,1))
    // TYPE

    @Test
    fun a01_type_unit () {
        assert(tp_unit.toce() == "Unit")
    }
    @Test
    fun a02_type_tuple () {
        val tp = Type.Tuple(Tk(TK.CHAR,TK_Chr('('),1,1), arrayOf(tp_unit,tp_unit))
        println(tp.toce())
        assert(tp.toce() == "TUPLE__Unit__Unit")
    }

    // EXPR

    @Test
    fun b01_expr_unit () {
        val e = Expr.Unit(Tk(TK.UNIT,null,1,1))
        val out = code_expr(e)
        assert(out == "")
    }
    @Test
    fun b02_expr_var () {
        val e = Expr.Var(Tk(TK.XVAR,TK_Str("xxx"),1,1))
        val out = code_expr(e)
        assert(out == "xxx")
    }
    @Test
    fun b03_expr_nat () {
        val e = Expr.Var(Tk(TK.XNAT,TK_Str("xxx"),1,1))
        val out = code_expr(e)
        assert(out == "xxx")
    }
    @Test
    fun b04_expr_tuple () {
        val e = Expr.Tuple (
            Tk(TK.ERR, null, 0, 0),
            arrayOf (
                Expr.Unit(Tk(TK.UNIT,null,1,1)),
                Expr.Unit(Tk(TK.UNIT,null,1,1)),
            )
        )
        val out = code_expr(e)
        println(out)
        assert(out == "((TUPLE__Unit__Unit) { })")
    }
}