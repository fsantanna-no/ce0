import org.junit.jupiter.api.MethodOrderer.Alphanumeric
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.io.PushbackReader
import java.io.StringReader

@TestMethodOrder(Alphanumeric::class)
class Parser {

    // TYPE

    @Test
    fun a01_parser_type () {
        val all = All_new(PushbackReader(StringReader("xxx"), 2))
        lexer(all)
        val tp = parser_type(all)
        assert(tp==null && all.err=="(ln 1, col 1): expected type : have \"xxx\"")
    }
    @Test
    fun a02_parser_type () {
        val all = All_new(PushbackReader(StringReader("()"), 2))
        lexer(all)
        val tp = parser_type(all)
        assert(tp is Type.Unit)
    }
    @Test
    fun a03_parser_type () {
        val all = All_new(PushbackReader(StringReader("_char"), 2))
        lexer(all)
        val tp = parser_type(all)
        assert(tp is Type.Nat && (tp.tk.pay as TK_Str).v=="char")
    }
    @Test
    fun a04_parser_type () {
        val all = All_new(PushbackReader(StringReader("Nat"), 2))
        lexer(all)
        val tp = parser_type(all)
        assert(tp is Type.User && (tp.tk.pay as TK_Str).v=="Nat")
    }
    @Test
    fun a05_parser_type () {
        val all = All_new(PushbackReader(StringReader("((),_char)"), 2))
        lexer(all)
        val tp = parser_type(all)
        assert(tp is Type.Tuple && tp.vec.size==2 && tp.vec[0] is Type.Unit && tp.vec[1] is Type.Nat && (tp.vec[1].tk.pay as TK_Str).v=="char")
    }
    @Test
    fun a05_parser_type_tuple_err () {
        val all = All_new(PushbackReader(StringReader("((),X,"), 2))
        lexer(all)
        val tp = parser_type(all)
        assert(tp==null && all.err=="(ln 1, col 7): expected type : have end of file")
    }
    @Test
    fun a06_parser_type_func () {
        val all = All_new(PushbackReader(StringReader("() -> ()"), 2))
        lexer(all)
        val tp = parser_type(all)
        assert(tp is Type.Func && tp.inp is Type.Unit && tp.out is Type.Unit)
    }
    @Test
    fun a07_parser_type_err () {
        val all = All_new(PushbackReader(StringReader("("), 2))
        lexer(all)
        val tp = parser_type(all)
        assert(tp==null && all.err=="(ln 1, col 2): expected type : have end of file")
    }
    @Test
    fun a08_parser_type_err () {
        val all = All_new(PushbackReader(StringReader("(X"), 2))
        lexer(all)
        val tp = parser_type(all)
        assert(tp==null && all.err=="(ln 1, col 3): expected `,´ : have end of file")
    }

    // EXPR

    @Test
    fun b01_parser_expr_unit () {
        val all = All_new(PushbackReader(StringReader("()"), 2))
        lexer(all)
        val e = parser_expr(all)
        assert(e is Expr.Unit)
    }
    @Test
    fun b02_parser_expr_var () {
        val all = All_new(PushbackReader(StringReader("x"), 2))
        lexer(all)
        val e = parser_expr(all)
        assert(e is Expr.Var && (e.tk.pay as TK_Str).v=="x")
    }
    @Test
    fun b03_parser_expr_parens () {
        val all = All_new(PushbackReader(StringReader("( () )"), 2))
        lexer(all)
        val e = parser_expr(all)
        assert(e is Expr.Unit)
    }
    @Test
    fun b04_parser_expr_parens () {
        val all = All_new(PushbackReader(StringReader("("), 2))
        lexer(all)
        val e = parser_expr(all)
        assert(e==null && all.err=="(ln 1, col 2): expected expression : have end of file")
    }
    @Test
    fun b05_parser_expr_parens () {
        val all = All_new(PushbackReader(StringReader("(x"), 2))
        lexer(all)
        val e = parser_expr(all)
        assert(e==null && all.err=="(ln 1, col 3): expected `,´ : have end of file")
    }
    @Test
    fun b06_parser_expr_tuple () {
        val all = All_new(PushbackReader(StringReader("((),x,())"), 2))
        lexer(all)
        val e = parser_expr(all)
        assert(e is Expr.Tuple && e.vec.size==3 && e.vec[0] is Expr.Unit && e.vec[1] is Expr.Var && (e.vec[1].tk.pay as TK_Str).v=="x")
    }
    @Test
    fun b07_parser_expr_tuple_err () {
        val all = All_new(PushbackReader(StringReader("((),x,"), 2))
        lexer(all)
        val e = parser_expr(all)
        assert(e==null && all.err=="(ln 1, col 7): expected expression : have end of file")
    }
    @Test
    fun b08_parser_expr_nat () {
        val all = All_new(PushbackReader(StringReader("_x"), 2))
        lexer(all)
        val e = parser_expr(all)
        assert(e is Expr.Nat && (e.tk.pay as TK_Str).v=="x")
    }
}