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
        assert(tp is Type.Nat && tp.tk_.str=="char")
    }
    @Test
    fun a05_parser_type () {
        val all = All_new(PushbackReader(StringReader("[(),_char]"), 2))
        lexer(all)
        val tp = parser_type(all)
        assert(tp is Type.Tuple && tp.vec.size==2 && tp.vec[0] is Type.Unit && tp.vec[1] is Type.Nat && (tp.vec[1].tk as Tk.Str).str=="char")
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
        val all = All_new(PushbackReader(StringReader("() -> ()"), 2))
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
        //val tp = parser_type(all)
        try {
            parser_type(all)
            error("impossible case")
        } catch (e: Throwable) {
            assert(e.message == "(ln 1, col 4): expected `@´ : have end of file") { e.message!! }
        }
    }
    @Test
    fun a09_parser_type_ptr_err2 () {
        val all = All_new(PushbackReader(StringReader("/()@"), 2))
        lexer(all)
        //val tp = parser_type(all)
        try {
            parser_type(all)
            error("impossible case")
        } catch (e: Throwable) {
            assert(e.message == "(ln 1, col 4): expected `@´ : have \"@\"") { e.message!! }
        }
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
        val all = All_new(PushbackReader(StringReader("/<[^]>@global"), 2))
        lexer(all)
        val tp = parser_type(all)
        assert(tp is Type.Ptr)      // error on check
    }
    @Test
    fun a10_parser_type_ptr2 () {
        val all = All_new(PushbackReader(StringReader("/<[/^@global]>@local"), 2))
        lexer(all)
        val tp = parser_type(all)
        assert(tp is Type.Ptr)
    }
    @Test
    fun a11_parser_type_issupof () {
        val all = All_new(PushbackReader(StringReader("<(),<(),^^>>"), 2))
        lexer(all)
        val tp1 = parser_type(all)
        tp1.visit { AUX.ups[it] = Any() }
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
        assert(tp is Type.Ptr && tp.scope!!.scp=="@b" && tp.pln is Type.Ptr && (tp.pln as Type.Ptr).scope!!.scp=="@a")
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
        assert(e is Expr.Var && e.tk_.str=="x")
    }
    @Test
    fun b08_parser_expr_nat () {
        val all = All_new(PushbackReader(StringReader("_x"), 2))
        lexer(all)
        val e = parser_expr(all,true)
        assert(e is Expr.Nat && e.tk_.str=="x")
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
        try {
            parser_expr(all, true)
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
            parser_expr(all, false)
            error("impossible case")
        } catch (e: Throwable) {
            assert(e.message == "(ln 1, col 3): expected `)´ : have end of file")
        }
    }
    @Test
    fun b06_parser_expr_tuple () {
        val all = All_new(PushbackReader(StringReader("[(),x,()]"), 2))
        lexer(all)
        val e = parser_expr(all,true)
        assert(e is Expr.TCons && e.arg.size==3 && e.arg[0] is Expr.Unit && e.arg[1] is Expr.Var && (e.arg[1].tk as Tk.Str).str=="x")
    }
    @Test
    fun b07_parser_expr_tuple_err () {
        val all = All_new(PushbackReader(StringReader("[(),x,"), 2))
        lexer(all)
        try {
            parser_expr(all, false)
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
            parser_expr(all, false)
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
            parser_expr(all, false)
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
        val e = parser_expr(all,true)
        assert(e is Expr.Call && e.f is Expr.Var && (e.f.tk as Tk.Str).str=="xxx" && e.arg is Expr.Unit)
    }
    @Test
    fun b10_parser_expr_call () {
        val all = All_new(PushbackReader(StringReader("call xxx ()"), 2))
        lexer(all)
        val e = parser_expr(all,true)
        assert(e is Expr.Call && e.f is Expr.Var && (e.f.tk as Tk.Str).str=="xxx" && e.arg is Expr.Unit)
    }
    @Test
    fun b10_parser_expr_call_err () {
        val all = All_new(PushbackReader(StringReader("call () ()"), 2))
        lexer(all)
        val e = parser_expr(all, true)
        assert(e is Expr.Call && e.f is Expr.Unit && e.arg is Expr.Unit)
    }
    @Test
    fun b11_parser_expr_call () {
        val all = All_new(PushbackReader(StringReader("f()\n()\n()"), 2))
        lexer(all)
        val e = parser_expr(all, true)
        assert(e is Expr.Call && e.f is Expr.Var && e.arg is Expr.Call && (e.arg as Expr.Call).f is Expr.Unit)
    }
    @Test
    fun b12_parser_expr_call () {
        val all = All_new(PushbackReader(StringReader("f1 f2\nf3\n()"), 2)) // f1 (f2 (f3 ()))
        lexer(all)
        val e = parser_expr(all,true)
        assert(e is Expr.Call && e.arg is Expr.Call && (e.arg as Expr.Call).arg is Expr.Call && ((e.arg as Expr.Call).arg as Expr.Call).arg is Expr.Unit)
    }
    @Test
    fun b13_parser_expr_call () {
        val all = All_new(PushbackReader(StringReader("xxx ("), 2))
        lexer(all)
        try {
            parser_expr(all, true)
            error("impossible case")
        } catch (e: Throwable) {
            assert(e.message == "(ln 1, col 6): expected expression : have end of file")
        }
    }

    // CONS

    @Test
    fun b14_parser_expr_cons_err_1 () {
        val all = All_new(PushbackReader(StringReader("<.0 ("), 2))
        lexer(all)
        try {
            parser_expr(all, false)
            error("impossible case")
        } catch (e: Throwable) {
            assert(e.message == "(ln 1, col 6): expected expression : have end of file")
        }
    }
    @Test
    fun b15_parser_expr_cons () {
        val all = All_new(PushbackReader(StringReader("<.0 ()>"), 2))
        lexer(all)
        val e = parser_expr(all,false)
        assert(e is Expr.UCons && e.tk_.num==0 && e.arg is Expr.Unit)
    }
    @Test
    fun b16_parser_expr_cons () {
        val all = All_new(PushbackReader(StringReader("<.1>"), 2))
        lexer(all)
        val e = parser_expr(all,false)
        assert(e is Expr.UCons && e.tk_.num==1 && e.arg is Expr.Unit)
    }
    @Test
    fun b17_parser_expr_cons () {
        val all = All_new(PushbackReader(StringReader("<.2 <.1 [(),()]>>"), 2))
        lexer(all)
        val e = parser_expr(all,false)
        assert(e is Expr.UCons && e.tk_.num==2 && e.arg is Expr.UCons && (e.arg as Expr.UCons).arg is Expr.TCons)
    }

    // INDEX

    @Test
    fun b18_parser_expr_index () {
        val all = All_new(PushbackReader(StringReader("x.1"), 2))
        lexer(all)
        val e = parser_expr(all,false)
        assert(e is Expr.TDisc && e.tk_.num==1 && e.tup is Expr.Var)
    }
    @Test
    fun b19_parser_expr_index () {
        val all = All_new(PushbackReader(StringReader("x () .10"), 2))
        lexer(all)  // x [() .10]
        val e = parser_expr(all,false)
        assert(e is Expr.Call && e.arg is Expr.TDisc)
    }

    // UPREF, DNREF

    @Test
    fun b21_parser_expr_upref () {
        val all = All_new(PushbackReader(StringReader("/x.1 @a"), 2))
        lexer(all)
        val e = parser_expr(all,false)
        assert(e is Expr.Upref && e.pln is Expr.TDisc)
    }
    @Test
    fun b22_parser_expr_upref () {
        val all = All_new(PushbackReader(StringReader("/()"), 2))
        lexer(all)
        try {
            parser_expr(all, false)
            error("impossible case")
        } catch (e: Throwable) {
            assert(e.message == "(ln 1, col 2): unexpected operand to `/´") { e.message!! }
        }
    }
    @Test
    fun b23_parser_expr_dnref () {
        val all = All_new(PushbackReader(StringReader("x\\.1"), 2))
        lexer(all)
        val e = parser_expr(all,false)
        assert(e is Expr.TDisc && e.tup is Expr.Dnref && (e.tup as Expr.Dnref).ptr is Expr.Var)
    }
    @Test
    fun b24_parser_expr_dnref () {
        val all = All_new(PushbackReader(StringReader("()\\"), 2))
        lexer(all)
        try {
            parser_expr(all, false)
            error("impossible case")
        } catch (e: Throwable) {
            assert(e.message == "(ln 1, col 3): unexpected operand to `\\´") { e.message!! }
        }
    }
    @Test
    fun b25_parser_expr_dnref () {
        val all = All_new(PushbackReader(StringReader("x\\\\"), 2))
        lexer(all)
        val e = parser_expr(all,false)
        assert(e is Expr.Dnref && e.ptr is Expr.Dnref)
    }

    // PRED, DISC

    @Test
    fun b25_parser_expr_disc () {
        val all = All_new(PushbackReader(StringReader("x!1"), 2))
        lexer(all)
        val e = parser_expr(all,false)
        assert(e is Expr.UDisc && e.tk_.num==1 && e.uni is Expr.Var)
    }
    @Test
    fun b26_parser_expr_pred () {
        val all = All_new(PushbackReader(StringReader("x?1"), 2))
        lexer(all)
        val e = parser_expr(all,false)
        assert(e is Expr.UPred && e.tk_.num==1 && e.uni is Expr.Var)
    }
    @Test
    fun b27_parser_expr_idx () {
        val all = All_new(PushbackReader(StringReader("x.10"), 2))
        lexer(all)
        val e = parser_expr(all,false)
        assert(e is Expr.TDisc && e.tk_.num==10 && e.tup is Expr.Var)
    }
    @Test
    fun b28_parser_expr_disc () {
        val all = All_new(PushbackReader(StringReader("arg.1\\!1.1"), 2))
        lexer(all)
        val e = parser_expr(all,false)
        assert(e is Expr.TDisc && e.tk_.num==1 && e.tup is Expr.UDisc)
    }

    // STMT

    @Test
    fun c01_parser_stmt_var () {
        val all = All_new(PushbackReader(StringReader("var x: ()"), 2))
        lexer(all)
        val s = parser_stmt(all)
        assert(s is Stmt.Var && s.type is Type.Unit)
    }
    @Test
    fun c03_parser_stmt_var_tuple () {
        val all = All_new(PushbackReader(StringReader("var x: [(),()]"), 2))
        lexer(all)
        val s = parser_stmt(all)
        assert(s is Stmt.Var && s.type is Type.Tuple)
    }
    @Test
    fun c04_parser_stmt_var_caret () {
        val all = All_new(PushbackReader(StringReader("var x: () @a"), 2))
        lexer(all)
        try {
            parser_stmts(all, Pair(TK.EOF,null))
            error("impossible case")
        } catch (e: Throwable) {
            assert(e.message == "(ln 1, col 11): expected statement : have `@a´") { e.message!! }
        }
    }
    @Test
    fun c05_parser_stmt_var_global () {
        val all = All_new(PushbackReader(StringReader("var x: /()@1"), 2))
        lexer(all)
        val s = parser_stmt(all)
        assert(s is Stmt.Var && s.type is Type.Ptr)
    }
    @Test
    fun c06_parser_stmt_block_scope () {
        val all = All_new(PushbackReader(StringReader("{ @a }"), 2))
        lexer(all)
        val s = parser_stmt(all)
        assert(s is Stmt.Block && s.scope!!.scp=="@a")
    }

    // STMT_CALL

    @Test
    fun c03_parser_stmt_call () {
        val all = All_new(PushbackReader(StringReader("call ()"), 2))
        lexer(all)
        val s = parser_stmt(all)
        assert(s is Stmt.Call)
    }
    @Test
    fun c04_parser_stmt_call () {
        val all = All_new(PushbackReader(StringReader("call () ()"), 2))
        lexer(all)
        val s = parser_stmt(all)
        assert(s is Stmt.Call)
    }
    @Test
    fun c05_parser_stmt_call () {
        val all = All_new(PushbackReader(StringReader("call f ()"), 2))
        lexer(all)
        val s = parser_stmt(all)
        assert(s is Stmt.Call && s.call.f is Expr.Var && s.call.arg is Expr.Unit)
    }
    @Test
    fun c06_parser_stmt_call () {
        val all = All_new(PushbackReader(StringReader("call f"), 2))
        lexer(all)
        val s = parser_stmt(all)
        assert(s is Stmt.Call && s.call.f is Expr.Var && s.call.arg is Expr.Unit)
    }
    @Test
    fun c07_parser_stmt_call () {
        val all = All_new(PushbackReader(StringReader("call _printf ()"), 2))
        lexer(all)
        val s = parser_stmt(all)
        assert(s is Stmt.Call && s.call.f is Expr.Nat && s.call.arg is Expr.Unit)
    }
    @Test
    fun c07_parser_stmt_output () {
        val all = All_new(PushbackReader(StringReader("output std ()"), 2))
        lexer(all)
        val s = parser_stmt(all)
        assert(s is Stmt.Call && s.call.f is Expr.Var && (s.call.f as Expr.Var).tk_.str=="output_std")
    }

    // STMT_SEQ

    @Test
    fun c08_parser_stmt_seq () {
        val all = All_new(PushbackReader(StringReader("call f() ; call _printf() call g"), 2))
        lexer(all)
        val s = parser_stmts(all, Pair(TK.EOF,null))
        assert (
            s is Stmt.Seq && s.s1 is Stmt.Seq && s.s2 is Stmt.Call && ((s.s2 as Stmt.Call).call.f.tk as Tk.Str).str=="g" &&
            (s.s1 as Stmt.Seq).let {
                it.s1 is Stmt.Call && (it.s1 as Stmt.Call).let {
                    it.call.f is Expr.Var && (it.call.f.tk as Tk.Str).str=="f"
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
        assert(s is Stmt.Block && s.body is Stmt.Call)
    }

    // STMT_IF

    @Test
    fun c10_parser_stmt_if () {
        val all = All_new(PushbackReader(StringReader("if () {} else { call f() }"), 2))
        lexer(all)
        val s = parser_stmt(all)
        assert (
            s is Stmt.If && s.tst is Expr.Unit &&
            s.true_.body is Stmt.Pass && s.false_.body is Stmt.Call
        )
    }
    @Test
    fun c11_parser_stmt_if () {
        val all = All_new(PushbackReader(StringReader("if <.2> {}"), 2))
        lexer(all)
        val s = parser_stmt(all)
        assert (
            s is Stmt.If && s.tst is Expr.UCons &&
            s.true_.body is Stmt.Pass && s.false_.body is Stmt.Pass
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
            assert(e.message == "(ln 1, col 14): expected function type") { e.message!! }
        }
    }
    @Test
    fun c13_parser_func () {
        val all = All_new(PushbackReader(StringReader("set f = func () -> () { return }"), 2))
        lexer(all)
        val s = parser_stmt(all)
        assert (
            (s is Stmt.Set) && ((s.dst as Expr.Var).tk_.str=="f") &&
            s.src.let {
                (it is Expr.Func) && (it.type.inp is Type.Unit) && it.block.body.let {
                    it is Stmt.Seq && it.s1 is Stmt.Var && it.s2 is Stmt.Block
                }
            }
        )
    }
    @Test
    fun c13_parser_ret () {
        val all = All_new(PushbackReader(StringReader("return"), 2))
        lexer(all)
        val s = parser_stmt(all)
        assert (
            s is Stmt.Seq && s.s1 is Stmt.Set && s.s2.let {
                it is Stmt.Ret //&& it.e.e is Expr.Unit
            }
        )
    }

    // STMT_NAT

    @Test
    fun c14_parser_nat () {
        val all = All_new(PushbackReader(StringReader("native _{xxx}"), 2))
        lexer(all)
        val s = parser_stmt(all)
        assert(s is Stmt.Nat && s.tk_.str=="xxx")
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
        val e = parser_expr(all,true)
        //assert(s is Stmt.Var && s.src.e is Expr.TDisc)
    }
}