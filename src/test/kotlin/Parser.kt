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
        val e = parser_expr(all,false)
        assert(e is Expr.Unit)
    }
    @Test
    fun b02_parser_expr_var () {
        val all = All_new(PushbackReader(StringReader("x"), 2))
        lexer(all)
        val e = parser_expr(all,true)
        assert(e is Expr.Var && (e.tk.pay as TK_Str).v=="x")
    }
    @Test
    fun b08_parser_expr_nat () {
        val all = All_new(PushbackReader(StringReader("_x"), 2))
        lexer(all)
        val e = parser_expr(all,true)
        assert(e is Expr.Nat && (e.tk.pay as TK_Str).v=="x")
    }
    @Test
    fun b08_parser_expr_empty () {
        val all = All_new(PushbackReader(StringReader("\$Nat"), 2))
        lexer(all)
        val e = parser_expr(all,false)
        assert(e is Expr.Empty && (e.tk.pay as TK_Str).v=="Nat")
    }
    @Test
    fun b08_parser_expr_int () {
        val all = All_new(PushbackReader(StringReader("10"), 2))
        lexer(all)
        val e = parser_expr(all,false)
        assert(e is Expr.Int && (e.tk.pay as TK_Num).v==10)
    }

    // PARENS, TUPLE

    @Test
    fun b03_parser_expr_parens () {
        val all = All_new(PushbackReader(StringReader("( () )"), 2))
        lexer(all)
        val e = parser_expr(all,false)
        assert(e is Expr.Unit)
    }
    @Test
    fun b04_parser_expr_parens () {
        val all = All_new(PushbackReader(StringReader("("), 2))
        lexer(all)
        val e = parser_expr(all,true)
        assert(e==null && all.err=="(ln 1, col 2): expected expression : have end of file")
    }
    @Test
    fun b05_parser_expr_parens () {
        val all = All_new(PushbackReader(StringReader("(x"), 2))
        lexer(all)
        val e = parser_expr(all,false)
        assert(e==null && all.err=="(ln 1, col 3): expected `,´ : have end of file")
    }
    @Test
    fun b06_parser_expr_tuple () {
        val all = All_new(PushbackReader(StringReader("((),x,())"), 2))
        lexer(all)
        val e = parser_expr(all,true)
        assert(e is Expr.Tuple && e.vec.size==3 && e.vec[0] is Expr.Unit && e.vec[1] is Expr.Var && (e.vec[1].tk.pay as TK_Str).v=="x")
    }
    @Test
    fun b07_parser_expr_tuple_err () {
        val all = All_new(PushbackReader(StringReader("((),x,"), 2))
        lexer(all)
        val e = parser_expr(all,false)
        assert(e==null && all.err=="(ln 1, col 7): expected expression : have end of file")
    }

    // CALL

    @Test
    fun b09_parser_expr_call () {
        val all = All_new(PushbackReader(StringReader("xxx ()"), 2))
        lexer(all)
        val e = parser_expr(all,true)
        assert(e is Expr.Call && e.pre is Expr.Var && (e.pre.tk.pay as TK_Str).v=="xxx" && e.pos is Expr.Unit)
    }
    @Test
    fun b10_parser_expr_call () {
        val all = All_new(PushbackReader(StringReader("call xxx ()"), 2))
        lexer(all)
        val e = parser_expr(all,true)
        assert(e is Expr.Call && e.pre is Expr.Var && (e.pre.tk.pay as TK_Str).v=="xxx" && e.pos is Expr.Unit)
    }
    @Test
    fun b11_parser_expr_call () {
        val all = All_new(PushbackReader(StringReader("f()\n()\n()"), 2))
        lexer(all)
        val e1 = parser_expr(all,true)
        val e2 = parser_expr(all,true)
        val e3 = parser_expr(all,true)
        assert(e1 is Expr.Call && e2 is Expr.Unit && e3 is Expr.Unit)
        assert(e1 is Expr.Call && e1.pre is Expr.Var && (e1.pre.tk.pay as TK_Str).v=="f" && e1.pos is Expr.Unit)
    }
    @Test
    fun b12_parser_expr_call () {
        val all = All_new(PushbackReader(StringReader("f1 f2\nf3\n()"), 2)) // f1 (f2 (f3 ()))
        lexer(all)
        val e = parser_expr(all,true)
        assert(e is Expr.Call && e.pos is Expr.Call && (e.pos as Expr.Call).pos is Expr.Call && ((e.pos as Expr.Call).pos as Expr.Call).pos is Expr.Unit)
    }
    @Test
    fun b13_parser_expr_call () {
        val all = All_new(PushbackReader(StringReader("xxx ("), 2))
        lexer(all)
        val e = parser_expr(all,true)
        assert(e==null && all.err=="(ln 1, col 6): expected expression : have end of file")
    }

    // CONS

    @Test
    fun b14_parser_expr_cons () {
        val all = All_new(PushbackReader(StringReader("X ("), 2))
        lexer(all)
        val e = parser_expr(all,false)
        assert(e==null && all.err=="(ln 1, col 4): expected expression : have end of file")
    }
    @Test
    fun b15_parser_expr_cons () {
        val all = All_new(PushbackReader(StringReader("X ()"), 2))
        lexer(all)
        val e = parser_expr(all,false)
        assert(e is Expr.Cons && (e.tk.pay as TK_Str).v=="X" && e.pos is Expr.Unit)
    }
    @Test
    fun b16_parser_expr_cons () {
        val all = All_new(PushbackReader(StringReader("X"), 2))
        lexer(all)
        val e = parser_expr(all,false)
        assert(e is Expr.Cons && (e.tk.pay as TK_Str).v=="X" && e.pos is Expr.Unit)
    }
    @Test
    fun b17_parser_expr_cons () {
        val all = All_new(PushbackReader(StringReader("Aa1 Bb1 ((),())"), 2))
        lexer(all)
        val e = parser_expr(all,false)
        assert(e is Expr.Cons && (e.tk.pay as TK_Str).v=="Aa1" && e.pos is Expr.Cons && (e.pos as Expr.Cons).pos is Expr.Tuple)
    }

    // INDEX

    @Test
    fun b18_parser_expr_index () {
        val all = All_new(PushbackReader(StringReader("x.1"), 2))
        lexer(all)
        val e = parser_expr(all,false)
        assert(e is Expr.Index && (e.tk.pay as TK_Num).v==1 && e.pre is Expr.Var)
    }
    @Test
    fun b19_parser_expr_index () {
        val all = All_new(PushbackReader(StringReader("x () .10 ()"), 2))
        lexer(all)  // x [() .10] ~()~
        val e = parser_expr(all,false)
        assert(e is Expr.Call && e.pos is Expr.Index)
    }
    @Test
    fun b20_parser_expr_index () {
        val all = All_new(PushbackReader(StringReader("x().."), 2))
        lexer(all)  // x [() .10] ~()~
        val e = parser_expr(all,false)
        assert(e==null && all.err=="(ln 1, col 5): expected index or subtype : have `.´")
    }

    // UPREF, DNREF

    @Test
    fun b21_parser_expr_upref () {
        val all = All_new(PushbackReader(StringReader("\\x.1"), 2))
        lexer(all)
        val e = parser_expr(all,false)
        assert(e is Expr.Upref && e.pos is Expr.Index)
    }
    @Test
    fun b22_parser_expr_upref () {
        val all = All_new(PushbackReader(StringReader("\\()"), 2))
        lexer(all)
        val e = parser_expr(all,false)
        assert(e==null && all.err=="(ln 1, col 2): unexpected operand to `\\´")
    }
    @Test
    fun b23_parser_expr_dnref () {
        val all = All_new(PushbackReader(StringReader("x\\.1"), 2))
        lexer(all)
        val e = parser_expr(all,false)
        assert(e is Expr.Index && e.pre is Expr.Dnref && (e.pre as Expr.Dnref).pre is Expr.Var)
    }
    @Test
    fun b24_parser_expr_dnref () {
        val all = All_new(PushbackReader(StringReader("()\\"), 2))
        lexer(all)
        val e = parser_expr(all,false)
        assert(e==null && all.err=="(ln 1, col 3): unexpected operand to `\\´")
    }

    // PRED, DISC

    @Test
    fun b25_parser_expr_disc () {
        val all = All_new(PushbackReader(StringReader("x.Item!"), 2))
        lexer(all)
        val e = parser_expr(all,false)
        println(e)
        assert(e is Expr.Disc && (e.tk.pay as TK_Str).v=="Item" && e.pre is Expr.Var)
    }
    @Test
    fun b26_parser_expr_pred () {
        val all = All_new(PushbackReader(StringReader("x.Item?"), 2))
        lexer(all)
        val e = parser_expr(all,false)
        assert(e is Expr.Pred && (e.tk.pay as TK_Str).v=="Item" && e.pre is Expr.Var)
    }

    // STMT

    @Test
    fun c01_parser_stmt () {
        val all = All_new(PushbackReader(StringReader("var x: () = ()"), 2))
        lexer(all)
        val s = parser_stmt(all)
        assert(s is Stmt.Var && s.type is Type.Unit && s.init is Expr.Unit)
    }

}