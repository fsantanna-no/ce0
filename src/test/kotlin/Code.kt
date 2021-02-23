import org.junit.jupiter.api.MethodOrderer.Alphanumeric
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder

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
        assert(e.toc() == "")
    }
    @Test
    fun b02_expr_var () {
        val e = Expr.Var(Tk.Str(TK.XVAR,1,1,"xxx"))
        assert(e.toc() == "xxx")
    }
    @Test
    fun b03_expr_nat () {
        val e = Expr.Var(Tk.Str(TK.XNAT,1,1,"xxx"))
        assert(e.toc() == "xxx")
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
        assert(e.toc() == "((TUPLE__Unit__Unit) { })")
    }
    @Test
    fun b05_expr_index () {
        val e = Expr.Index (
            Tk.Num(TK.XNUM,1,1,2),
            Expr.Var(Tk.Str(TK.XVAR,1,1,"x"))
        )
        println(e.toc())
        assert(e.toc() == "x._2")
    }
}