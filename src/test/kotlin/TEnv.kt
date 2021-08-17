import org.junit.jupiter.api.MethodOrderer.Alphanumeric
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.io.PushbackReader
import java.io.StringReader

@TestMethodOrder(Alphanumeric::class)
class TEnv {

    fun inp2env (inp: String): String {
        val all = All_new(PushbackReader(StringReader(inp), 2))
        lexer(all)
        try {
            var s = parser_stmts(all, Pair(TK.EOF,null))
            s = env_prelude(s)
            check_dcls(s)
            check_types(s)
            check_xexprs(s)
            check_pointers(s)
            return "OK"
        } catch (e: Throwable) {
            //throw e
            return e.message!!
        }
    }

    fun pre (inp: String): Stmt {
        val all = All_new(PushbackReader(StringReader(inp), 2))
        lexer(all)
        var s = parser_stmts(all, Pair(TK.EOF,null))
        return s
    }

    // UNDECLARED

    @Test
    fun a01_undeclared_var () {
        val out = inp2env("output std x")
        assert(out == "(ln 1, col 12): undeclared variable \"x\"")
    }
    @Test
    fun a02_undeclared_func () {
        val out = inp2env("call f ()")
        assert(out == "(ln 1, col 6): undeclared variable \"f\"")
    }
    @Test
    fun a03_redeclared_var () {
        val out = inp2env("var x:()=() ; { var x:()=() }")
        assert(out == "(ln 1, col 21): invalid declaration : \"x\" is already declared (ln 1)")
    }
    @Test
    fun a04_redeclared_func () {
        val out = inp2env("var x:()=() ; func x:()->() {}")
        assert(out == "(ln 1, col 20): invalid declaration : \"x\" is already declared (ln 1)")
    }

    // CONS

    @Test
    fun b01_user_tuple_out () {
        val out = inp2env("""
            var x: [(),()] = ?
            output std(x.3)
        """.trimIndent())
        assert(out == "(ln 2, col 14): invalid discriminator : out of bounds")
    }
    @Test
    fun b02_user_sub_undeclared () {
        val out = inp2env("""
            var x: <(),()> = ?
            output std(x.0)
        """.trimIndent())
        assert(out == "(ln 2, col 14): invalid discriminator : type mismatch")
    }
    @Test
    fun b04_user_disc_cons_err () {
        val out = inp2env("""
            output std ()!1
        """.trimIndent())
        assert(out == "(ln 1, col 15): invalid discriminator : type mismatch")
    }
    @Test
    fun b07_user_out_err1 () {
        val out = inp2env("""
            var x: ^
        """.trimIndent())
        assert(out == "(ln 1, col 9): expected type : have end of file")
    }
    @Test
    fun b07_user_rec_up () {
        val out = inp2env("""
            var x: (^) = ?
        """.trimIndent())
        assert(out == "OK")
    }
    @Test
    fun b09_user_err () {
        val out = inp2env("""
            var x: <()> = ?
            var y: <^> = x
        """.trimIndent())
        assert(out == "(ln 2, col 5): invalid assignment : type mismatch")
    }
    @Test
    fun b10_user_empty_err () {
        val out = inp2env("""
            var l: <^> = <.1 ()>
        """.trimIndent())
        assert(out == "(ln 1, col 5): invalid assignment : type mismatch")
    }
    @Test
    fun b11_user_empty_err () {
        val out = inp2env("""
            var l: <()> = <.1>
            output std \l!2
        """.trimIndent())
        assert(out == "(ln 2, col 15): invalid discriminator : out of bounds")
    }
    @Test
    fun b12_user_empty_err () {
        val out = inp2env("""
            var l: <^> = <.1 <.0>>
            output std \l!0
        """.trimIndent())
        println(out)
        assert(out == "(ln 1, col 16): invalid expression : expected `new` operation modifier")
    }
    @Test
    fun b13_user_empty_ok () {
        val out = inp2env("""
            var l: <^> = new <.1 <.0>>
            output std \l!0
        """.trimIndent())
        assert(out == "OK")
    }

    // TYPE

    @Test
    fun c01_type_var () {
        val out = inp2env("""
            var x: [()] = ()
        """.trimIndent())
        assert(out == "(ln 1, col 5): invalid assignment : type mismatch")
    }
    @Test
    fun c02_type_set () {
        val out = inp2env("""
            var x: () = ()
            set x = [()]
        """.trimIndent())
        assert(out == "(ln 2, col 7): invalid assignment : type mismatch")
    }
    @Test
    fun c03_type_func_ret () {
        val out = inp2env("""
            func f : () -> () { return [()] }
        """.trimIndent())
        assert(out == "(ln 1, col 21): invalid return : type mismatch")
    }
    @Test
    fun c04_type_func_arg () {
        val out = inp2env("""
            func f : [(),()] -> () { }
            call f()
        """.trimIndent())
        assert(out == "(ln 2, col 6): invalid call : type mismatch")
    }
    @Test
    fun c05_type_idx () {
        val out = inp2env("""
            var x: () = [[()],[()]].1
        """.trimIndent())
        assert(out == "(ln 1, col 5): invalid assignment : type mismatch")
    }
    @Test
    fun c06_type_idx () {
        val out = inp2env("""
            var x: [(),()] = [(),()]
            set x.1 = [()]
        """.trimIndent())
        assert(out == "(ln 2, col 9): invalid assignment : type mismatch")
    }
    @Test
    fun c07_type_upref () {
        val out = inp2env("""
            var x: \() = ()
        """.trimIndent())
        assert(out == "(ln 1, col 5): invalid assignment : type mismatch")
    }
    @Test
    fun c08_type_upref () {
        val out = inp2env("""
            var y: () = ()
            var x: () = \y
        """.trimIndent())
        assert(out == "(ln 2, col 5): invalid assignment : type mismatch")
    }
    @Test
    fun c09_type_upref () {
        val out = inp2env("""
            var y: () = ()
            var x: \() = \y
        """.trimIndent())
        assert(out == "OK")
    }
    @Test
    fun c10_type_upref () {
        val out = inp2env("""
            var y: [()] = [()]
            var x: \() = \y
        """.trimIndent())
        assert(out == "(ln 2, col 5): invalid assignment : type mismatch")
    }
    @Test
    fun c11_type_upref () {
        val out = inp2env("""
            var y: () = ()
            var x: \() = \y
            var z: _x = \x
        """.trimIndent())
        assert(out == "OK")
    }
    @Test
    fun c12_type_dnref () {
        val out = inp2env("""
            var x: () = ()
            output std /x
        """.trimIndent())
        assert(out == "(ln 2, col 12): invalid `/` : expected pointer type")
    }
    @Test
    fun c13_type_dnref () {
        val out = inp2env("""
            var x: () = ()
            var y: \() = \x
            var z: \() = /y
        """.trimIndent())
        assert(out == "(ln 3, col 5): invalid assignment : type mismatch")
    }

    // DEPTH

    @Test
    fun d01_block () {
        val s = pre("var x: () = () ; { output std x }")
        fun fe (env: Env, e: Expr) {
            if (e is Expr.Var && e.tk_.str=="x") {
                assert(0 == e.getDepth(env,0,true).first)
            }
        }
        fun fs (env: Env, s: Stmt) {
            if (s is Stmt.Block) {
                assert(s.getDepth(env,false) == 0)
            }
        }
        s.visit(emptyList(), ::fs, ::fe)
    }
    @Test
    fun d02_func () {
        val s = pre("var x: () = () ; func f: ()->() { var y: () = x ; output std y ; set x = () }")
        fun fe (env: Env, e: Expr) {
            if (e is Expr.Var) {
                if (e.tk_.str == "x") {
                    assert(0 == e.getDepth(env, 0, true).first)
                }
                if (e.tk_.str == "y") {
                    assert(2 == s.getDepth(env,false))
                }
            }
        }
        fun fs (env: Env, s: Stmt) {
            if (s is Stmt.Var) {
                if (s.tk_.str == "x") {
                    assert(0 == s.getDepth(env,false))
                }
                if (s.tk_.str == "y") {
                    assert(2 == s.getDepth(env,false))
                }
            }
            if (s is Stmt.Set) {
                assert(0 == s.dst.toExpr().getDepth(env, s.getDepth(env,true), true).first)
            }
        }
        s.visit(emptyList(), ::fs, ::fe)
    }

    // POINTERS

    @Test
    fun e01_ptr_block_err () {
        val out = inp2env("""
            var p1: \() = ?
            var p2: \() = ?
            {
                var v: () = ()
                set p1 = \v
            }
            {
                var v: () = ()
                --set p2 = \v
            }
            output std /p1
        """.trimIndent())
        assert(out == "(ln 5, col 12): invalid assignment : cannot hold local pointer \"v\" (ln 4)")
    }
    @Test
    fun e02_ptr_block_err () {
        val out = inp2env("""
            var x: () = ()
            var p: \() = ?
            {
                var y: () = ()
                set p = \x
                set p = \y
            }
        """.trimIndent())
        assert(out == "(ln 6, col 11): invalid assignment : cannot hold local pointer \"y\" (ln 4)")
    }
    @Test
    fun e03_ptr_err () {
        val out = inp2env("""
            var pout: \_int = ?
            {
                var pin: \_int = ?
                set pout = pin
            }
        """.trimIndent())
        assert(out == "(ln 4, col 14): invalid assignment : cannot hold local pointer \"pin\" (ln 3)")
    }
    @Test
    fun e04_ptr_ok () {
        val out = inp2env("""
            var pout: \_int = ?
            {
                var pin: \_int = ?
                set pin = pout
            }
        """.trimIndent())
        assert(out == "OK")
    }

    // POINTERS - DOUBLE

    @Test
    fun f01_ptr_ptr_err () {
        val out = inp2env("""
            var p: \\_int = ?
            {
                var y: \_int = ?
                set p = \y
            }
        """.trimIndent())
        assert(out == "(ln 4, col 11): invalid assignment : cannot hold local pointer \"y\" (ln 3)")
    }
    @Test
    fun f02_ptr_ptr_ok () {
        val out = inp2env("""
            var p: \\_int = ?
            var z: _int = _10
            var y: \_int = \z
            set p = \y
            output std //p
        """.trimIndent())
        assert(out == "OK")
    }
    @Test
    fun f03_ptr_ptr_err () {
        val out = inp2env("""
            var p: \\_int = ?
            {
                var z: _int = _10
                var y: \_int = \z
                set p = \y
            }
        """.trimIndent())
        assert(out == "(ln 5, col 11): invalid assignment : cannot hold local pointer \"y\" (ln 4)")
    }
    @Test
    fun f04_ptr_ptr_err () {
        val out = inp2env("""
            var p: \_int = ?
            {
                var x: _int = _10
                var y: \_int = \x
                var z: \\_int = \y
                set p = /z
            }
        """.trimIndent())
        assert(out == "(ln 6, col 11): invalid assignment : cannot hold local pointer \"z\" (ln 5)")
    }

    // POINTERS - FUNC - CALL

    @Test
    fun g01_ptr_func_ok () {
        val out = inp2env("""
            func f : \_int -> \_int {
                return arg
            }
            var v: _int = _10
            var p: \_int = f \v
            output std /p
        """.trimIndent())
        assert(out == "OK")
    }
    @Test
    fun g02_ptr_func_ok () {
        val out = inp2env("""
            var v: _int = _10
            func f : () -> \_int {
                return \v
            }
            var p: \_int = f ()
            output std /p
        """.trimIndent())
        assert(out == "OK")
    }
    @Test
    fun g03_ptr_func_err () {
        val out = inp2env("""
            func f : () -> \_int {
                var v: _int = _10
                return \v
            }
            var v: _int = _10
            var p: \_int = f ()
            output std /p
        """.trimIndent())
        assert(out == "(ln 3, col 5): invalid assignment : cannot hold local pointer \"v\" (ln 2)")
    }
    @Test
    fun g04_ptr_func_err () {
        val out = inp2env("""
            func f : \_int -> \_int {
                var ptr: \_int = arg
                return ptr
            }
            var v: _int = _10
            var p: \_int = f \v
            output std /p
        """.trimIndent())
        assert(out == "(ln 3, col 5): invalid assignment : cannot hold local pointer \"ptr\" (ln 2)")
    }
    @Test
    fun g05_ptr_caret_ok () {
        val out = inp2env("""
            func f : \_int -> \_int {
                var ptr: ^\_int = arg
                return ptr
            }
            var v: _int = _10
            var p: \_int = f \v
            output std /p
        """.trimIndent())
        assert(out == "OK")
    }
    @Test
    fun g06_ptr_caret_err () {
        val out = inp2env("""
            func f : \_int -> \_int {
                var x: _int = _10
                var ptr: ^\_int = \x
                return ptr
            }
            var v: _int = _10
            var p: \_int = f \v
            output std /p
        """.trimIndent())
        assert(out == "(ln 3, col 9): invalid assignment : cannot hold local pointer \"x\" (ln 2)")
    }

    // TODO: caret outside function in global scope
    @Test
    fun g07_ptr_caret_err () {
        val out = inp2env("""
            var ptr: ^\_int = ?
        """.trimIndent())
        assert(out == "OK")
    }

    @Test
    fun g08_ptr_arg_err () {
        val out = inp2env("""
            func f: _int -> \_int
            {
                return \arg
            }
        """.trimIndent())
        assert(out == "(ln 3, col 5): invalid assignment : cannot hold local pointer \"arg\" (ln 2)")
    }
    @Test
    fun g09_ptr_arg_err () {
        val out = inp2env("""
            func f: _int -> \_int
            {
                var ptr: ^\_int = \arg
                return ptr
            }
        """.trimIndent())
        assert(out == "(ln 3, col 9): invalid assignment : cannot hold local pointer \"arg\" (ln 2)")
    }
    @Test
    fun g10_ptr_out_err () {
        val out = inp2env("""
            func f: \_int -> \\_int
            {
                var ptr: ^\_int = arg
                return \ptr
            }
        """.trimIndent())
        assert(out == "(ln 4, col 5): invalid assignment : cannot hold local pointer \"ptr\" (ln 3)")
    }
    @Test
    fun g11_ptr_func () {
        val out = inp2env("""
            var v: _int = _10
            func f : () -> \_int {
                return \v
            }
            var p: \_int = ?
            {
                set p = f ()
            }
        """.trimIndent())
        assert(out == "(ln 7, col 11): invalid assignment : cannot hold local pointer \"f\" (ln 2)")
    }
    @Test
    fun g12_ptr_func () {
        val out = inp2env("""
            var v: _int = _10
            func f : \_int -> \_int {
                return \v
            }
            var p: \_int = ?
            {
                set p = f (\v)
            }
        """.trimIndent())
        assert(out == "(ln 7, col 11): invalid assignment : cannot hold local pointer \"f\" (ln 2)")
    }
    @Test
    fun g13_ptr_func () {
        val out = inp2env("""
            var v: \_int = ?
            func f : \_int -> () {
                set v = arg
            }
        """.trimIndent())
        assert(out == "(ln 3, col 11): invalid assignment : cannot hold local pointer \"arg\" (ln 2)")
    }

    // POINTERS - TUPLE - TYPE

    @Test
    fun h01_ptr_tuple_err () {
        val out = inp2env("""
            var p: \[_int,_int] = ?
            {
                var y: [_int,_int] = [_10,_20]
                set p = \y
            }
        """.trimIndent())
        assert(out == "(ln 4, col 11): invalid assignment : cannot hold local pointer \"y\" (ln 3)")
    }
    @Test
    fun h02_ptr_user_err () {
        val out = inp2env("""
            var p: \<()> = ?
            {
                var y: <()> = <.1>
                set p = \y
            }
        """.trimIndent())
        assert(out == "(ln 4, col 11): invalid assignment : cannot hold local pointer \"y\" (ln 3)")
    }
    @Test
    fun h03_ptr_tup () {
        val out = inp2env("""
            var v: [_int,_int] = [_10,_20]
            var p: \_int = \v.1
            set /p = _20
            output std v
        """.trimIndent())
        assert(out == "OK")
    }
    @Test
    fun h04_ptr_tup_err () {
        val out = inp2env("""
            var p: \_int = ?
            {
                var v: [_int,_int] = [_10,_20]
                set p = \v.1
            }
        """.trimIndent())
        assert(out == "(ln 4, col 11): invalid assignment : cannot hold local pointer \"v\" (ln 3)")
    }
    @Test
    fun h05_ptr_type_err () {
        val out = inp2env("""
            var p: \() = ?
            {
                var v: <()> = <.1 ()>
                set p = \v!1
            }
        """.trimIndent())
        assert(out == "(ln 4, col 11): invalid assignment : cannot hold local pointer \"v\" (ln 3)")
    }
    @Test
    fun h06_ptr_tup_err () {
        val out = inp2env("""
            var p: [_int,\_int] = [_10,?]
            {
                var v: _int = _20
                set p.2 = \v
            }
        """.trimIndent())
        assert(out == "(ln 4, col 13): invalid assignment : cannot hold local pointer \"v\" (ln 3)")
    }
    @Test
    fun h07_ptr_tup_err () {
        val out = inp2env("""
            var p: [_int,\_int] = [_10,?]
            {
                var v: _int = _20
                set p = [_10,\v]
            }
        """.trimIndent())
        assert(out == "(ln 4, col 11): invalid assignment : cannot hold local pointer \"v\" (ln 3)")
    }
    @Test
    fun h08_ptr_type_err () {
        val out = inp2env("""
            var p: <\_int> = <.1 ?>
            {
                var v: _int = _20
                set p!1 = \v
            }
        """.trimIndent())
        assert(out == "(ln 4, col 13): invalid assignment : cannot hold local pointer \"v\" (ln 3)")
    }
    @Test
    fun h09_ptr_type_err () {
        val out = inp2env("""
            var p: <\_int> = <.1 ?>
            {
                var v: _int = _20
                set p = <.1 \v>
            }
        """.trimIndent())
        assert(out == "(ln 4, col 11): invalid assignment : cannot hold local pointer \"v\" (ln 3)")
    }
    @Test
    fun h10_ptr_tup_err () {
        val out = inp2env("""
            var x1: [_int,\_int] = ?
            {
                var v: _int = _20
                var x2: [_int,\_int] = [_10,\v]
                set x1 = x2
            }
        """.trimIndent())
        assert(out == "(ln 5, col 12): invalid assignment : cannot hold local pointer \"x2\" (ln 4)")
    }
    @Test
    fun h11_ptr_type_err () {
        val out = inp2env("""
            var x1: <\_int> = ?
            {
                var v: _int = _20
                var x2: <\_int> = <.1 \v>
                set x1 = x2
            }
        """.trimIndent())
        assert(out == "(ln 5, col 12): invalid assignment : cannot hold local pointer \"x2\" (ln 4)")
    }

    // TYPE - REC - MOVE - CLONE - BORROW

    @Test
    fun i01_list () {
        val out = inp2env("""
            var p: \<^> = ?
            {
                var l: <^> = new <.1 (new <.1 <.0>>)>
                set p = \l
            }
            output std p
        """.trimIndent())
        assert(out == "(ln 4, col 11): invalid assignment : cannot hold local pointer \"l\" (ln 3)")
    }

    // XEPR

    @Test
    fun j01_rec_xepr_null_err () {
        val out = inp2env("""
            var x: <^> = ?
            var y: <^> = x
        """.trimIndent())
        assert(out == "(ln 2, col 14): invalid expression : expected operation modifier")
    }
    @Test
    fun j02_rec_xepr_move_ok () {
        val out = inp2env("""
            var x: <^> = ?
            var y: <^> = move x
        """.trimIndent())
        assert(out == "OK")
    }
    @Test
    fun j03_rec_xepr_borrow_err () {
        val out = inp2env("""
            var x: <^> = ?
            var y: <^> = borrow x
        """.trimIndent())
        assert(out == "(ln 2, col 14): invalid `borrow` : expected pointer to recursive variable")
    }
    @Test
    fun j04_rec_xepr_copy_err () {
        val out = inp2env("""
            var x: _int = copy _10
        """.trimIndent())
        assert(out == "(ln 1, col 15): invalid `copy` : expected recursive variable")
    }
    @Test
    fun j05_rec_xepr_borrow_ok () {
        val out = inp2env("""
            var x: <^> = ?
            var y: \<^> = borrow \x
        """.trimIndent())
        assert(out == "OK")
    }
    @Test
    fun j06_rec_xepr_borrow_err () {
        val out = inp2env("""
            var x: _int = ?
            var y: \_int = borrow \x
        """.trimIndent())
        assert(out == "(ln 2, col 16): invalid `borrow` : expected pointer to recursive variable")
    }
    @Test
    fun j07_rec_xepr_copy_err () {
        val out = inp2env("""
            var x: <^> = copy <.1 <.0>>
        """.trimIndent())
        assert(out == "(ln 1, col 14): invalid `copy` : expected recursive variable")
        //assert(out == "(ln 1, col 5): invalid assignment : expected `new` operation modifier")
    }
    @Test
    fun j08_rec_xepr_double_rec () {
        val out = inp2env("""
            var x: <<^^>> = new <.1 <.1 new <.1 <.1 .0>>>
        """.trimIndent())
        assert(out == "(ln 1, col 14): invalid `copy` : expected recursive variable")
        //assert(out == "(ln 1, col 5): invalid assignment : expected `new` operation modifier")
    }

    // IF

    @Test
    fun k01_if () {
        val out = inp2env("""
            if () {} else {}
        """.trimIndent())
        assert(out == "(ln 1, col 1): invalid condition : type mismatch")
    }

}