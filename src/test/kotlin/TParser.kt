import org.junit.jupiter.api.MethodOrderer.Alphanumeric
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.io.PushbackReader
import java.io.StringReader

@TestMethodOrder(Alphanumeric::class)
class TParser {

    // TYPE

    @Test
    fun a01_parser_type () {
        val all = All_new(PushbackReader(StringReader("xxx"), 2))
        lexer(all)
        try {
            parser_type(all)
            error("impossible case")
        } catch (e: Throwable) {
            assert(e.message == "(ln 1, col 1): expected type : have \"xxx\"")
        }
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
        assert(tp is Type.Nat && tp.tk_.src=="char")
    }
    @Test
    fun a05_parser_type () {
        val all = All_new(PushbackReader(StringReader("[(),_char]"), 2))
        lexer(all)
        val tp = parser_type(all)
        assert(tp is Type.Tuple && tp.vec.size==2 && tp.vec[0] is Type.Unit && tp.vec[1] is Type.Nat && (tp.vec[1].tk as Tk.Nat).src=="char")
    }
    @Test
    fun a05_parser_type_tuple_err () {
        val all = All_new(PushbackReader(StringReader("[(),(),"), 2))
        lexer(all)
        try {
            parser_type(all)
            error("impossible case")
        } catch (e: Throwable) {
            assert(e.message == "(ln 1, col 8): expected type : have end of file")
        }
    }
    @Test
    fun a06_parser_type_func () {
        val all = All_new(PushbackReader(StringReader("func @[] -> () -> ()"), 2))
        lexer(all)
        val tp = parser_type(all)
        assert(tp is Type.Func && tp.inp is Type.Unit && tp.out is Type.Unit)
    }
    @Test
    fun a07_parser_type_err () {
        val all = All_new(PushbackReader(StringReader("("), 2))
        lexer(all)
        try {
            parser_type(all)
            error("impossible case")
        } catch (e: Throwable) {
            assert(e.message == "(ln 1, col 2): expected type : have end of file")
        }
    }
    @Test
    fun a08_parser_type_err () {
        val all = All_new(PushbackReader(StringReader("[()"), 2))
        lexer(all)
        try {
            parser_type(all)
            error("impossible case")
        } catch (e: Throwable) {
            assert(e.message == "(ln 1, col 4): expected `]´ : have end of file")
        }
    }
    @Test
    fun a08_parser_type_tuple () {
        val all = All_new(PushbackReader(StringReader("[(),()]"), 2))
        lexer(all)
        val tp = parser_type(all)
        assert(tp is Type.Tuple && tp.vec.size==2 && tp.vec[0] is Type.Unit && tp.vec[1] is Type.Unit)
    }
    @Test
    fun a09_parser_type_ptr () {
        val all = All_new(PushbackReader(StringReader("/()@a"), 2))
        lexer(all)
        val tp = parser_type(all)
        assert(tp is Type.Ptr && tp.pln is Type.Unit)
    }
    @Test
    fun a09_parser_type_ptr_err () {
        val all = All_new(PushbackReader(StringReader("/()"), 2))
        lexer(all)
        try {
            parser_type(all)
            error("impossible case")
        } catch (e: Throwable) {
            assert(e.message == "(ln 1, col 4): expected `@´ : have end of file")
        }
    }
    @Test
    fun a09_parser_type_ptr_ok () {
        val all = All_new(PushbackReader(StringReader("/()@x"), 2))
        lexer(all)
        val tp = parser_type(all)
        assert(tp is Type.Ptr && tp.xscp1.lbl=="x")
    }
    @Test
    fun a09_parser_type_ptr_err2 () {
        val all = All_new(PushbackReader(StringReader("/()@LOCAL"), 2))
        lexer(all)
        val tp = parser_type(all)
        assert(tp is Type.Ptr && tp.xscp1.lbl=="LOCAL")
        /*
        try {
            val s = parser_type(all)
            println(s)
            error("impossible case")
        } catch (e: Throwable) {
            assert(e.message == "(ln 1, col 3): expected type : have `?´") { e.message!! }
        }
         */
    }
    @Test
    fun a10_parser_type_ptr0 () {
        val all = All_new(PushbackReader(StringReader("/<?[^]>"), 2))
        lexer(all)
        //val tp = parser_type(all)
        try {
            parser_type(all)
            error("impossible case")
        } catch (e: Throwable) {
            //assert(e.message == "(ln 1, col 2): invalid type declaration : unexpected `?´") { e.message!! }
            assert(e.message == "(ln 1, col 3): expected type : have `?´") { e.message!! }
        }
    }
    @Test
    fun a10_parser_type_ptr1 () {
        val all = All_new(PushbackReader(StringReader("/<[^]>@GLOBAL"), 2))
        lexer(all)
        val tp = parser_type(all)
        assert(tp is Type.Ptr)      // error on check
    }
    @Test
    fun a10_parser_type_ptr2 () {
        val all = All_new(PushbackReader(StringReader("/<[/^@GLOBAL]>@LOCAL"), 2))
        lexer(all)
        val tp = parser_type(all)
        assert(tp is Type.Ptr)
    }
    @Test
    fun a11_parser_type_issupof () {
        val all = All_new(PushbackReader(StringReader("<(),<(),^^>>"), 2))
        lexer(all)
        val tp1 = parser_type(all)
        tp1.visit(false, { it.wup = Any() })
        val tp2 = (tp1 as Type.Union).expand()[1]
        // <(),<(),^^>> = <(),<(),<(),^^>>>
        val ok1 = tp1.isSupOf(tp2)
        val ok2 = tp2.isSupOf(tp1)
        assert(ok1 && ok2)
    }
    @Test
    fun a12_parser_type_ptr_null () {
        val all = All_new(PushbackReader(StringReader("<? /()>"), 2))
        lexer(all)
        //val tp = parser_type(all)
        //assert(tp is Type.Union && tp.isnull)
        try {
            parser_type(all)
            error("impossible case")
        } catch (e: Throwable) {
            //assert(e.message == "(ln 1, col 1): invalid type declaration : unexpected `?´") { e.message!! }
            assert(e.message == "(ln 1, col 2): expected type : have `?´") { e.message!! }
        }
    }
    @Test
    fun a13_parser_type_ptr_null () {
        val all = All_new(PushbackReader(StringReader("<? /(), ()>"), 2))
        lexer(all)
        try {
            parser_type(all)
            error("impossible case")
        } catch (e: Throwable) {
            //assert(e.message == "(ln 1, col 1): invalid type declaration : unexpected `?´") { e.message!! }
            assert(e.message == "(ln 1, col 2): expected type : have `?´") { e.message!! }
        }
    }
    @Test
    fun a13_parser_type_pointer_scope () {
        val all = All_new(PushbackReader(StringReader("//() @a @b"), 2))
        lexer(all)
        val tp = parser_type(all)
        assert(tp is Type.Ptr && tp.xscp1.lbl=="b" && tp.pln is Type.Ptr && (tp.pln as Type.Ptr).xscp1.lbl=="a")
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
        assert(e is Expr.Var && e.tk_.str=="x")
    }
    @Test
    fun b08_parser_expr_nat () {
        val all = All_new(PushbackReader(StringReader("_x:_int"), 2))
        lexer(all)
        val e = parser_expr(all)
        assert(e is Expr.Nat && e.tk_.src=="x" && e.wtype is Type.Nat)
    }
    @Test
    fun b09_parser_expr_nat () {
        val all = All_new(PushbackReader(StringReader("_x:_int"), 2))
        lexer(all)
        val e = parser_expr(all)
        assert(e is Expr.Nat && e.tk_.src=="x" && e.wtype is Type.Nat)
    }

    // PARENS, TUPLE

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
        try {
            parser_expr(all)
            error("impossible case")
        } catch (e: Throwable) {
            assert(e.message == "(ln 1, col 2): expected expression : have end of file")
        }
    }
    @Test
    fun b05_parser_expr_parens () {
        val all = All_new(PushbackReader(StringReader("(x"), 2))
        lexer(all)
        try {
            parser_expr(all)
            error("impossible case")
        } catch (e: Throwable) {
            assert(e.message == "(ln 1, col 3): expected `)´ : have end of file")
        }
    }
    @Test
    fun b06_parser_expr_tuple () {
        val all = All_new(PushbackReader(StringReader("[(),x,()]"), 2))
        lexer(all)
        val e = parser_expr(all)
        assert(e is Expr.TCons && e.arg.size==3 && e.arg[0] is Expr.Unit && e.arg[1] is Expr.Var && (e.arg[1].tk as Tk.Str).str=="x")
    }
    @Test
    fun b07_parser_expr_tuple_err () {
        val all = All_new(PushbackReader(StringReader("[(),x,"), 2))
        lexer(all)
        try {
            parser_expr(all)
            error("impossible case")
        } catch (e: Throwable) {
            assert(e.message == "(ln 1, col 7): expected expression : have end of file")
        }
    }
    @Test
    fun b08_parser_expr_disc_err () {
        val all = All_new(PushbackReader(StringReader("l!0"), 2))
        lexer(all)
        try {
            parser_expr(all)
            error("impossible case")
        } catch (e: Throwable) {
            assert(e.message == "(ln 1, col 3): invalid discriminator : union cannot be <.0>") { e.message!! }
        }
    }
    @Test
    fun b08_parser_expr_pred_err () {
        val all = All_new(PushbackReader(StringReader("l?0"), 2))
        lexer(all)
        try {
            parser_expr(all)
            error("impossible case")
        } catch (e: Throwable) {
            assert(e.message == "(ln 1, col 3): invalid discriminator : union cannot be <.0>") { e.message!! }
        }
    }

    // CALL

    @Test
    fun b09_parser_expr_call () {
        val all = All_new(PushbackReader(StringReader("xxx ()"), 2))
        lexer(all)
        val e = parser_expr(all)
        assert(e is Expr.Call && e.f is Expr.Var && (e.f.tk as Tk.Str).str=="xxx" && e.arg is Expr.Unit)
    }
    @Test
    fun b10_parser_expr_call () {
        val all = All_new(PushbackReader(StringReader("xxx ()"), 2))
        lexer(all)
        val e = parser_expr(all)
        assert(e is Expr.Call && e.f is Expr.Var && (e.f.tk as Tk.Str).str=="xxx" && e.arg is Expr.Unit)
    }
    @Test
    fun b10_parser_expr_call_err () {
        val all = All_new(PushbackReader(StringReader("() ()"), 2))
        lexer(all)
        val e = parser_expr(all)
        assert(e is Expr.Call && e.f is Expr.Unit && e.arg is Expr.Unit)
    }
    @Test
    fun b11_parser_expr_call () {
        val all = All_new(PushbackReader(StringReader("f ()\n()\n()"), 2))
        lexer(all)
        val e = parser_expr(all)
        assert(e is Expr.Call && e.f is Expr.Var && e.arg is Expr.Call && (e.arg as Expr.Call).f is Expr.Unit)
    }
    @Test
    fun b12_parser_expr_call () {
        val all = All_new(PushbackReader(StringReader("f1 f2\nf3\n()"), 2)) // f1 (f2 (f3 ()))
        lexer(all)
        val e = parser_expr(all)
        assert(e is Expr.Call && e.arg is Expr.Call && (e.arg as Expr.Call).arg is Expr.Call && ((e.arg as Expr.Call).arg as Expr.Call).arg is Expr.Unit)
    }
    @Test
    fun b13_parser_expr_call () {
        val all = All_new(PushbackReader(StringReader("xxx ("), 2))
        lexer(all)
        try {
            parser_expr(all)
            error("impossible case")
        } catch (e: Throwable) {
            assert(e.message == "(ln 1, col 6): expected expression : have end of file")
        }
    }

    // CONS

    @Test
    fun b14_parser_expr_cons_err_1 () {
        val all = All_new(PushbackReader(StringReader("<.1 ("), 2))
        lexer(all)
        try {
            parser_expr(all)
            error("impossible case")
        } catch (e: Throwable) {
            assert(e.message == "(ln 1, col 6): expected expression : have end of file")
        }
    }
    @Test
    fun b15_parser_expr_cons_err () {
        val all = All_new(PushbackReader(StringReader("<.0>"), 2))
        lexer(all)
        try {
            parser_expr(all)
            error("impossible case")
        } catch (e: Throwable) {
            assert(e.message == "(ln 1, col 5): expected `:´ : have end of file") { e.message!! }
        }
    }
    @Test
    fun b15_parser_expr_cons_err2 () {
        val all = All_new(PushbackReader(StringReader("<.1 ()>:()"), 2))
        lexer(all)
        try {
            parser_expr(all)
            //error("impossible case")
        } catch (e: Throwable) {
            assert(e.message == "(ln 1, col 9): invalid type : expected union") { e.message!! }
        }
    }
    @Test
    fun b16_parser_expr_cons () {
        val all = All_new(PushbackReader(StringReader("<.1 ()>:<()>"), 2))
        lexer(all)
        val e = parser_expr(all)
        assert(e is Expr.UCons && e.tk_.num==1 && e.arg is Expr.Unit)
    }
    @Test
    fun b17_parser_expr_cons () {
        val all = All_new(PushbackReader(StringReader("<.2 <.1 [(),()]>:<()>>:<()>"), 2))
        lexer(all)
        val e = parser_expr(all)
        assert(e is Expr.UCons && e.tk_.num==2 && e.arg is Expr.UCons && (e.arg as Expr.UCons).arg is Expr.TCons)
    }

    // INDEX

    @Test
    fun b18_parser_expr_index () {
        val all = All_new(PushbackReader(StringReader("x.1"), 2))
        lexer(all)
        val e = parser_expr(all)
        assert(e is Expr.TDisc && e.tk_.num==1 && e.tup is Expr.Var)
    }
    @Test
    fun b19_parser_expr_index () {
        val all = All_new(PushbackReader(StringReader("x () .10"), 2))
        lexer(all)  // x [() .10]
        val e = parser_expr(all)
        assert(e is Expr.Call && e.arg is Expr.TDisc)
    }

    // UPREF, DNREF

    @Test
    fun b21_parser_expr_upref () {
        val all = All_new(PushbackReader(StringReader("/x.1"), 2))
        lexer(all)
        val e = parser_expr(all)
        assert(e is Expr.Upref && e.pln is Expr.TDisc)
    }
    @Test
    fun b22_parser_expr_upref () {
        val all = All_new(PushbackReader(StringReader("/()"), 2))
        lexer(all)
        try {
            parser_expr(all)
            error("impossible case")
        } catch (e: Throwable) {
            assert(e.message == "(ln 1, col 2): unexpected operand to `/´") { e.message!! }
        }
    }
    @Test
    fun b23_parser_expr_dnref () {
        val all = All_new(PushbackReader(StringReader("x\\.1"), 2))
        lexer(all)
        val e = parser_expr(all)
        assert(e is Expr.TDisc && e.tup is Expr.Dnref && (e.tup as Expr.Dnref).ptr is Expr.Var)
    }
    @Test
    fun b24_parser_expr_dnref () {
        val all = All_new(PushbackReader(StringReader("()\\"), 2))
        lexer(all)
        try {
            parser_expr(all)
            error("impossible case")
        } catch (e: Throwable) {
            assert(e.message == "(ln 1, col 3): unexpected operand to `\\´") { e.message!! }
        }
    }
    @Test
    fun b25_parser_expr_dnref () {
        val all = All_new(PushbackReader(StringReader("x\\\\"), 2))
        lexer(all)
        val e = parser_expr(all)
        assert(e is Expr.Dnref && e.ptr is Expr.Dnref)
    }

    // PRED, DISC

    @Test
    fun b25_parser_expr_disc () {
        val all = All_new(PushbackReader(StringReader("x!1"), 2))
        lexer(all)
        val e = parser_expr(all)
        assert(e is Expr.UDisc && e.tk_.num==1 && e.uni is Expr.Var)
    }
    @Test
    fun b26_parser_expr_pred () {
        val all = All_new(PushbackReader(StringReader("x?1"), 2))
        lexer(all)
        val e = parser_expr(all)
        assert(e is Expr.UPred && e.tk_.num==1 && e.uni is Expr.Var)
    }
    @Test
    fun b27_parser_expr_idx () {
        val all = All_new(PushbackReader(StringReader("x.10"), 2))
        lexer(all)
        val e = parser_expr(all)
        assert(e is Expr.TDisc && e.tk_.num==10 && e.tup is Expr.Var)
    }
    @Test
    fun b28_parser_expr_disc () {
        val all = All_new(PushbackReader(StringReader("arg.1\\!1.1"), 2))
        lexer(all)
        val e = parser_expr(all)
        assert(e is Expr.TDisc && e.tk_.num==1 && e.tup is Expr.UDisc)
    }

    // ATTR

    @Test
    fun b29 () {
        val all = All_new(PushbackReader(StringReader("y\\!1.2\\!1.1"), 2))
        lexer(all)
        val e = parser_attr(all)
        //println(e)
        assert(e is Attr.TDisc && e.tk_.num==1)
    }
    @Test
    fun b30 () {
        val all = All_new(PushbackReader(StringReader("((((((y\\)!1).2)\\)!1).1)"), 2))
        lexer(all)
        val e = parser_attr(all)
        //println(e)
        assert(e is Attr.TDisc && e.tk_.num==1)
    }

    // STMT

    @Test
    fun c01_parser_stmt_var () {
        val all = All_new(PushbackReader(StringReader("var x: ()"), 2))
        lexer(all)
        val s = parser_stmt(all)
        assert(s is Stmt.Var && s.xtype is Type.Unit)
    }
    @Test
    fun c03_parser_stmt_var_tuple () {
        val all = All_new(PushbackReader(StringReader("var x: [(),()]"), 2))
        lexer(all)
        val s = parser_stmt(all)
        assert(s is Stmt.Var && s.xtype is Type.Tuple)
    }
    @Test
    fun c04_parser_stmt_var_caret () {
        val all = All_new(PushbackReader(StringReader("var x: () @a"), 2))
        lexer(all)
        try {
            parser_stmts(all)
            error("impossible case")
        } catch (e: Throwable) {
            assert(e.message == "(ln 1, col 11): expected statement : have `@a´") { e.message!! }
        }
    }
    @Test
    fun c05_parser_stmt_var_global () {
        val all = All_new(PushbackReader(StringReader("var x: /()@1"), 2))
        lexer(all)
        try {
            parser_stmts(all)
            error("impossible case")
        } catch (e: Throwable) {
            assert(e.message == "(ln 1, col 11): expected `@´ : have \"@\"") { e.message!! }
        }
    }
    @Test
    fun c06_parser_stmt_block_scope () {
        val all = All_new(PushbackReader(StringReader("{ @A }"), 2))
        lexer(all)
        val s = parser_stmt(all)
        assert(s is Stmt.Block && s.xscp1!!.lbl=="A")
    }

    // STMT_CALL

    @Test
    fun c03_parser_stmt_call () {
        val all = All_new(PushbackReader(StringReader("call () ()"), 2))
        lexer(all)
        val s = parser_stmt(all)
        assert(s is Stmt.SCall)
    }
    @Test
    fun c04_parser_stmt_call () {
        val all = All_new(PushbackReader(StringReader("call () ()"), 2))
        lexer(all)
        val s = parser_stmt(all)
        assert(s is Stmt.SCall)
    }
    @Test
    fun c05_parser_stmt_call () {
        val all = All_new(PushbackReader(StringReader("call f ()"), 2))
        lexer(all)
        val s = parser_stmt(all)
        assert(s is Stmt.SCall && (s.e as Expr.Call).f is Expr.Var && (s.e as Expr.Call).arg is Expr.Unit)
    }
    @Test
    fun c06_parser_stmt_call () {
        val all = All_new(PushbackReader(StringReader("call f ()"), 2))
        lexer(all)
        val s = parser_stmt(all)
        assert(s is Stmt.SCall && (s.e as Expr.Call).f is Expr.Var && (s.e as Expr.Call).arg is Expr.Unit)
    }
    @Test
    fun c07_parser_stmt_call () {
        val all = All_new(PushbackReader(StringReader("call _printf:func@[]->()->() ()"), 2))
        lexer(all)
        val s = parser_stmt(all)
        assert(s is Stmt.SCall && (s.e as Expr.Call).f is Expr.Nat && (s.e as Expr.Call).arg is Expr.Unit)
    }
    @Test
    fun c07_parser_stmt_output () {
        val all = All_new(PushbackReader(StringReader("output std ()"), 2))
        lexer(all)
        val s = parser_stmt(all)
        //assert(s is Stmt.Call && s.call.f is Expr.Dnref && ((s.call.f as Expr.Dnref).ptr is Expr.Var) && ((s.call.f as Expr.Dnref).ptr as Expr.Var).tk_.str=="output_std")
        assert(s is Stmt.Output && s.lib.str=="std")
    }
    @Test
    fun c08_parser_stmt_input () {
        val all = All_new(PushbackReader(StringReader("set x = input std (): _int"), 2))
        lexer(all)
        val s = parser_stmt(all)
        //assert(s is Stmt.Call && s.call.f is Expr.Dnref && ((s.call.f as Expr.Dnref).ptr is Expr.Var) && ((s.call.f as Expr.Dnref).ptr as Expr.Var).tk_.str=="output_std")
        assert(s is Stmt.Input && s.lib.str=="std" && s.xtype is Type.Nat && s.dst is Expr.Var && s.arg is Expr.Unit)
    }

    // STMT_SEQ

    @Test
    fun c08_parser_stmt_seq () {
        val all = All_new(PushbackReader(StringReader("call f() ; call _printf:func@[]->()->() () call g()"), 2))
        lexer(all)
        val s = parser_stmts(all)
        assert (
            s is Stmt.Seq && s.s1 is Stmt.Seq && s.s2 is Stmt.SCall && (((s.s2 as Stmt.SCall).e as Expr.Call).f.tk as Tk.Str).str=="g" &&
            (s.s1 as Stmt.Seq).let {
                it.s1 is Stmt.SCall && (it.s1 as Stmt.SCall).let {
                    (it.e as Expr.Call).f is Expr.Var && ((it.e as Expr.Call).f.tk as Tk.Str).str=="f"
                }
            }
        )
    }

    // STMT_BLOCK

    @Test
    fun c09_parser_stmt_block () {
        val all = All_new(PushbackReader(StringReader("{ call f() }"), 2))
        lexer(all)
        val s = parser_stmt(all)
        assert(s is Stmt.Block && s.body is Stmt.SCall)
    }

    // STMT_IF

    @Test
    fun c10_parser_stmt_if () {
        val all = All_new(PushbackReader(StringReader("if () {} else { call f() }"), 2))
        lexer(all)
        val s = parser_stmt(all)
        assert (
            s is Stmt.If && s.tst is Expr.Unit &&
            s.true_.body is Stmt.Nop && s.false_.body is Stmt.SCall
        )
    }
    @Test
    fun c11_parser_stmt_if_err () {
        val all = All_new(PushbackReader(StringReader("if <.2()>:<()> {}"), 2))
        lexer(all)
        try {
            parser_stmts(all)
            error("impossible case")
        } catch (e: Throwable) {
            assert(e.message == "(ln 1, col 18): expected `else` : have end of file") { e.message!! }
        }
    }
    @Test
    fun c11_parser_stmt_if () {
        val all = All_new(PushbackReader(StringReader("if <.2()>:<()> {} else {}"), 2))
        lexer(all)
        val s = parser_stmt(all)
        assert (
            s is Stmt.If && s.tst is Expr.UCons &&
                    s.true_.body is Stmt.Nop && s.false_.body is Stmt.Nop
        )
    }

    // STMT_FUNC, STMT_RET

    @Test
    fun c12_parser_func () {
        val all = All_new(PushbackReader(StringReader("set f = func () { }"), 2))
        lexer(all)
        try {
            parser_stmt(all)
            error("impossible case")
        } catch (e: Throwable) {
            //assert(e.message == "(ln 1, col 14): expected function type") { e.message!! }
            assert(e.message == "(ln 1, col 14): expected `@[´ : have `()´") { e.message!! }
        }
    }
    @Test
    fun c13_parser_func () {
        val all = All_new(PushbackReader(StringReader("set f = func @[] -> () -> () { return }"), 2))
        lexer(all)
        val s = parser_stmt(all)
        assert (
            (s is Stmt.Set) && ((s.dst as Expr.Var).tk_.str=="f") &&
            s.src.let {
                (it is Expr.Func) && (it.type.inp is Type.Unit) && it.block.body is Stmt.Return
            }
        )
    }
    @Test
    fun c14_parser_ret () {
        val all = All_new(PushbackReader(StringReader("return"), 2))
        lexer(all)
        val s = parser_stmt(all)
        assert(s is Stmt.Return)
    }
    @Test
    fun c15_parser_func () {
        val all = All_new(PushbackReader(StringReader("set f = func @[] -> () -> () [a,b] { return }"), 2))
        lexer(all)
        val s = parser_stmt(all)
        assert (
            (s is Stmt.Set) && ((s.dst as Expr.Var).tk_.str=="f") &&
                    s.src.let { (it is Expr.Func) && (it.ups.size==2) }
        )
    }

    // STMT_NAT

    @Test
    fun c14_parser_nat () {
        val all = All_new(PushbackReader(StringReader("native _{xxx}"), 2))
        lexer(all)
        val s = parser_stmt(all)
        assert(s is Stmt.Native && s.tk_.src=="xxx")
    }
    @Test
    fun c15_parser_nat () {
        val all = All_new(PushbackReader(StringReader("_("), 2))
        lexer(all)
        try {
            parser_stmt(all)
            error("impossible case")
        } catch (e: Throwable) {
            assert(e.message == "(ln 1, col 1): expected statement : have \"unterminated token\"")
        }
    }
    @Test
    fun c16_parser_nat () {
        val all = All_new(PushbackReader(StringReader("native _{${xxx}}"), 2))
        lexer(all)
        val s = parser_stmt(all)
        assert(s is Stmt.Native && s.tk_.src=="${xxx}")
    }

    // STMT_LOOP

    @Test
    fun c15_parser_loop () {
        val all = All_new(PushbackReader(StringReader("loop { break }"), 2))
        lexer(all)
        val s = parser_stmt(all)
        assert(s is Stmt.Loop && s.block.body is Stmt.Break)
    }

    // STMT_SET / STMT_VAR

    @Test
    fun c16_parser_set () {
        val all = All_new(PushbackReader(StringReader("set s = ()"), 2))
        lexer(all)
        val s = parser_stmt(all)
        assert(s is Stmt.Set && s.dst is Expr.Var && s.src is Expr.Unit)
    }
    @Test
    fun c17_parser_var () {
        val all = All_new(PushbackReader(StringReader("set c = arg.1\\!1.1"), 2))
        lexer(all)
        val s = parser_stmt(all)
        assert(s is Stmt.Set && s.src is Expr.TDisc)
    }
    @Test
    fun c18_parser_var () {
        val all = All_new(PushbackReader(StringReader("arg.1\\!1.1"), 2))
        lexer(all)
        parser_expr(all)
        //assert(s is Stmt.Var && s.src.e is Expr.TDisc)
    }

    // TASKS / PUB / POOL

    @Test
    fun d01_type_task () {
        val all = All_new(PushbackReader(StringReader("task @LOCAL->@[]->()->()->() {}"), 2))
        lexer(all)
        val tp = parser_type(all)
        assert(tp is Type.Func && tp.tk.enu==TK.TASK)
    }
    @Test
    fun d02_stmt_spawn () {
        val all = All_new(PushbackReader(StringReader("set x = spawn f ()"), 2))
        lexer(all)
        val s = parser_stmt(all)
        assert(s is Stmt.SSpawn && s.call.f is Expr.Var && s.dst is Expr.Var)
    }

    // LOOP

    @Test
    fun d03_stmt_loop () {
        val all = All_new(PushbackReader(StringReader("loop tk in () {}"), 2))
        lexer(all)
        val s = parser_stmt(all)
        assert(s is Stmt.DLoop && s.i.tk_.str=="tk" && s.tsks is Expr.Unit)
    }
    @Test
    fun d04_loop () {
        val all = All_new(PushbackReader(StringReader("loop () in @LOCAL {}"), 2))
        lexer(all)
        try {
            parser_stmt(all)
            error("impossible case")
        } catch (e: Throwable) {
            assert(e.message == "(ln 1, col 6): expected variable expression") { e.message!! }
        }
    }
    @Test
    fun d05_loop () {
        val all = All_new(PushbackReader(StringReader("loop e {}"), 2))
        lexer(all)
        try {
            parser_stmt(all)
            error("impossible case")
        } catch (e: Throwable) {
            assert(e.message == "(ln 1, col 8): expected `in` : have `{´") { e.message!! }
        }
    }
    @Test
    fun d06_loop () {
        val all = All_new(PushbackReader(StringReader("loop e in {}"), 2))
        lexer(all)
        try {
            parser_stmt(all)
            error("impossible case")
        } catch (e: Throwable) {
            assert(e.message == "(ln 1, col 11): expected expression : have `{´") { e.message!! }
        }
    }

    // PUB

    @Test
    fun d07_error () {
        val all = All_new(PushbackReader(StringReader("x.y"), 2))
        lexer(all)
        try {
            parser_expr(all)
            error("impossible case")
        } catch (e: Throwable) {
            assert(e.message == "(ln 1, col 3): unexpected \"y\"") { e.message!! }
        }
    }
    @Test
    fun d08_ok () {
        val all = All_new(PushbackReader(StringReader("set x.pub = ()"), 2))
        lexer(all)
        val s = parser_stmt(all)
        assert(s is Stmt.Set && s.dst is Expr.Pub)
    }

    // TASKS TYPE

    @Test
    fun d09_tasks () { // task @LOCAL->@[]->()->()->() {}
        val all = All_new(PushbackReader(StringReader("active tasks @[]->()->()->()"), 2))
        lexer(all)
        try {
            parser_type(all)
            error("impossible case")
        } catch (e: Throwable) {
            assert(e.message == "(ln 1, col 14): expected `@´ : have `@[´") { e.message!! }
        }
    }
    @Test
    fun d10_tassk () {
        val all = All_new(PushbackReader(StringReader("active tasks @LOCAL->@[]->()->()->()"), 2))
        lexer(all)
        val tp = parser_type(all)
        assert(tp is Type.Spawns && tp.tsk is Type.Func && tp.tsk.tk.enu==TK.TASKS && tp.tsk.xscp1s.first!!.lbl=="LOCAL")
    }
}