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
            aux(s)
            check_01(s)
            check_02(s)
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
        aux(s)
        return s
    }

    // UNDECLARED

    @Test
    fun a01_undeclared_var () {
        val out = inp2env("output std x")
        assert(out == "(ln 1, col 12): undeclared variable \"x\"") { out }
    }
    @Test
    fun a02_undeclared_func () {
        val out = inp2env("call f ()")
        assert(out == "(ln 1, col 6): undeclared variable \"f\"") { out }
    }
    @Test
    fun a03_redeclared_var () {
        val out = inp2env("var x:() ; { var x:() }")
        assert(out == "(ln 1, col 18): invalid declaration : \"x\" is already declared (ln 1)") { out }
    }
    @Test
    fun a04_redeclared_func () {
        val out = inp2env("var x:() ; var x:()->()")
        assert(out == "(ln 1, col 16): invalid declaration : \"x\" is already declared (ln 1)")
    }
    @Test
    fun a05_return_err () {
        val out = inp2env("return ()")
        //assert(out == "(ln 1, col 1): invalid return : no enclosing function") { out }
        assert(out == "(ln 1, col 1): undeclared variable \"_ret_\"") { out }
    }

    // CONS

    @Test
    fun b01_user_tuple_out () {
        val out = inp2env("""
            var x: [(),()]
            output std(x.3)
        """.trimIndent())
        assert(out == "(ln 2, col 14): invalid discriminator : out of bounds") { out }
    }
    @Test
    fun b02_user_sub_undeclared () {
        val out = inp2env("""
            var x: <(),()>
            output std(x.0)
        """.trimIndent())
        assert(out == "(ln 2, col 14): invalid discriminator : type mismatch") { out }
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
            var x: _int @a
        """.trimIndent())
        assert(out == "(ln 1, col 13): expected statement : have `@a´") { out }
    }
    @Test
    fun b07_user_rec_up () {
        val out = inp2env("""
            var x: \_int @a
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun b09_user_err () {
        val out = inp2env("""
            var x: <()>
            var y: <^>
            set y = x
        """.trimIndent())
        assert(out == "(ln 3, col 7): invalid assignment : type mismatch") { out }
    }
    @Test
    fun b10_user_empty_err () {
        val out = inp2env("""
            var l: <^>
            set l = <.1 ()>
        """.trimIndent())
        assert(out == "(ln 2, col 7): invalid assignment : type mismatch") { out }
    }
    @Test
    fun b11_user_empty_err () {
        val out = inp2env("""
            var l: <()>
            set l = <.1>
            output std l!2
        """.trimIndent())
        assert(out == "(ln 3, col 14): invalid discriminator : out of bounds") { out }
    }
    @Test
    fun b12_user_empty_err () {
        val out = inp2env("""
            var l: <?^>
            set l = <.1 <.0>>
            output std l!0
        """.trimIndent())
        //assert(out == "(ln 2, col 11): invalid expression : expected `new` operation modifier") { out }
        assert(out == "OK") { out }
    }
    @Test
    fun b13_user_empty_ok () {
        val out = inp2env("""
            var l: <?^>
            set l = new <.1 <.0>>
            output std \l!0
        """.trimIndent())
        assert(out == "(ln 2, col 7): invalid assignment : type mismatch") { out }
    }
    @Test
    fun b14_user_empty_ok () {
        val out = inp2env("""
            var l: \<?^>
            set l = new <.1 <.0>>
            output std (/l)!0
        """.trimIndent())
        assert(out == "OK")
    }

    // TYPE

    @Test
    fun c01_type_var () {
        val out = inp2env("""
            var x: [()]
            set x = ()
        """.trimIndent())
        assert(out == "(ln 2, col 7): invalid assignment : type mismatch")
    }
    @Test
    fun c02_type_set () {
        val out = inp2env("""
            var x: ()
            set x = [()]
        """.trimIndent())
        assert(out == "(ln 2, col 7): invalid assignment : type mismatch") { out }
    }
    @Test
    fun c03_type_func_ret () {
        val out = inp2env("""
            var f : () -> (); set f =
                func () -> () {
                    return [()]
                }
        """.trimIndent())
        assert(out == "(ln 3, col 9): invalid return : type mismatch") { out }
    }
    @Test
    fun c04_type_func_arg () {
        val out = inp2env("""
            var f : [(),()] -> (); set f = func ([(),()] -> ()) { }
            call f()
        """.trimIndent())
        assert(out == "(ln 2, col 6): invalid call : type mismatch")
    }
    @Test
    fun c05_type_idx () {
        val out = inp2env("""
            var x: (); set x = [[()],[()]].1
        """.trimIndent())
        //assert(out == "(ln 1, col 18): invalid assignment : type mismatch") { out }
        assert(out == "(ln 1, col 32): invalid discriminator : unexpected constructor") { out }
    }
    @Test
    fun c06_type_idx () {
        val out = inp2env("""
            var x: [(),()]
            set x.1 = [()]
        """.trimIndent())
        assert(out == "(ln 2, col 9): invalid assignment : type mismatch") { out }
    }
    @Test
    fun c07_type_upref () {
        val out = inp2env("""
            var x: \()
            set x = ()
        """.trimIndent())
        assert(out == "(ln 2, col 7): invalid assignment : type mismatch") { out }
    }
    @Test
    fun c08_type_upref () {
        val out = inp2env("""
            var y: ()
            var x: ()
            set x = \y
        """.trimIndent())
        assert(out == "(ln 3, col 7): invalid assignment : type mismatch")
    }
    @Test
    fun c09_type_upref () {
        val out = inp2env("""
            var y: ()
            var x: \()
            set x = \y
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun c10_type_upref () {
        val out = inp2env("""
            var y: [()]
            var x: \()
            set x = \y
        """.trimIndent())
        assert(out == "(ln 3, col 7): invalid assignment : type mismatch")
    }
    @Test
    fun c11_type_upref () {
        val out = inp2env("""
            var y: ()
            var x: \(); set x = \y
            var z: _x; set z = \x
        """.trimIndent())
        assert(out == "OK")
    }
    @Test
    fun c12_type_dnref () {
        val out = inp2env("""
            var x: ()
            output std /x
        """.trimIndent())
        assert(out == "(ln 2, col 12): unexpected `/´ : argument is not a pointer") { out }
    }
    @Test
    fun c13_type_dnref () {
        val out = inp2env("""
            var x: ()
            var y: \(); set y = \x
            var z: \()
            set z = /y
        """.trimIndent())
        assert(out == "(ln 4, col 7): invalid assignment : type mismatch")
    }
    @Test
    fun c14_type_func_err () {
        val out = inp2env("""
            var x: ()->[(()->())]
        """.trimIndent())
        //assert(out == "(ln 1, col 12): invalid type : cannot return function type : currently not supported")
        assert(out == "OK")
    }
    @Test
    fun c15_type_func_tup () {
        val out = inp2env("""
            var f: [()->()]
            call f.1
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun c16_type_func_unit () {
        val out = inp2env("""
            call ()
        """.trimIndent())
        assert(out == "(ln 1, col 6): invalid call : not a function") { out }
    }
    @Test
    fun c17_type_func_err () {
        val out = inp2env("""
        var f: ()->(); set f = func ()->() {
            call arg.2
        }
        """.trimIndent())
        assert(out == "(ln 2, col 14): invalid discriminator : type mismatch") { out }
    }
    @Test
    fun c18_type_func_err () {
        val out = inp2env("""
        var f: [(),<(),()->()>]->()
        set f = func <(),()->()>->() {
        }
        """.trimIndent())
        assert(out == "(ln 2, col 7): invalid assignment : type mismatch") { out }
    }

    // TUPLE / UNION DISCRIMINATOR

    @Test
    fun c14_tup_disc_err () {
        val out = inp2env("""
            var x: [()]
            output std x!2
        """.trimIndent())
        assert(out == "(ln 2, col 14): invalid discriminator : type mismatch")
    }
    @Test
    fun c15_tup_disc_err () {
        val out = inp2env("""
            var x: [()]
            output std x.2
        """.trimIndent())
        assert(out == "(ln 2, col 14): invalid discriminator : out of bounds")
    }
    @Test
    fun c16_tup_disc_err () {
        val out = inp2env("""
            output std [()].2
        """.trimIndent())
        assert(out == "(ln 1, col 17): invalid discriminator : unexpected constructor") { out }
    }
    @Test
    fun c17_uni_disc_err () {
        val out = inp2env("""
            var x: <()>
            output std x.2
        """.trimIndent())
        assert(out == "(ln 2, col 14): invalid discriminator : type mismatch")
    }
    @Test
    fun c18_uni_disc_err () {
        val out = inp2env("""
            var x: <()>
            output std x!2
        """.trimIndent())
        assert(out == "(ln 2, col 14): invalid discriminator : out of bounds")
    }
    @Test
    fun c19_uni_disc_err () {
        val out = inp2env("""
            output std <.1>!2
        """.trimIndent())
        assert(out == "(ln 1, col 17): invalid discriminator : unexpected constructor") { out }
    }
    @Test
    fun c19_uni_pred_err () {
        val out = inp2env("""
            output std <.1>?1
        """.trimIndent())
        assert(out == "(ln 1, col 17): invalid discriminator : unexpected constructor") { out }
    }
    @Test
    fun c20_uni_disc_err () {
        val out = inp2env("""
            output std <.2>!2
        """.trimIndent())
        assert(out == "(ln 1, col 17): invalid discriminator : unexpected constructor") { out }
    }
    @Test
    fun c21_uni_disc_err () {
        val out = inp2env("""
            var x: <()>
            set x = <.2>
        """.trimIndent())
        assert(out == "(ln 2, col 7): invalid assignment : type mismatch") { out }
    }
    @Test
    fun c22_tup_cons_err () {
        val out = inp2env("""
            var t: [(),()]
            set t = [(),(),()]
        """.trimIndent())
        assert(out == "(ln 2, col 7): invalid assignment : type mismatch") { out }
    }

    // DEPTH
/*
    @Test
    fun d01_block () {
        val s = pre("var x: ()->() ; { call x }")
        fun fe (e: Expr) {
            if (e is Expr.Var && e.tk_.str=="x") {
                assert(0 == e.getDepth(0,true).first)
            }
        }
        fun fs (s: Stmt) {
            if (s is Stmt.Block) {
                assert(s.getDepth() == 0)
            }
        }
        s.visit(::fs, null, ::fe, null)
    }
    @Test
    fun d02_func () {
        val s = pre("var x: ()->() ; var f: ()->() = func ()->() { var y: ()->() = x ; call y ; set x }")
        fun fe (e: Expr) {
            if (e is Expr.Var) {
                if (e.tk_.str == "x") {
                    assert(0 == e.getDepth(0, true).first)
                }
                if (e.tk_.str == "y") {
                    //assert(2 == s.getDepth(false))
                }
            }
        }
        fun fs (s: Stmt) {
            if (s is Stmt.Var) {
                if (s.tk_.str == "x") {
                    assert(0 == s.getDepth())
                }
                if (s.tk_.str == "y") {
                    assert(2 == s.getDepth())
                }
            }
            if (s is Stmt.Set) {
                assert(0 == s.dst.getDepth(s.getDepth(), true).first)
            }
        }
        s.visit(::fs, null, ::fe, null)
    }
*/
    // POINTERS

    @Test
    fun e01_ptr_block_err () {
        val out = inp2env("""
            var p1: \()
            var p2: \()
            {
                var v: ()
                set p1 = \v     -- ERRO p1=0 < v=1
            }
            {
                var v: ()
                --set p2 = \v
            }
            output std /p1
        """.trimIndent())
        assert(out == "(ln 5, col 12): invalid assignment : type mismatch") { out }
    }
    @Test
    fun e02_ptr_block_err () {
        val out = inp2env("""
            var x: ()
            var p: \()
            {
                var y: ()
                set p = \x  -- ok
                set p = \y  -- no
            }
        """.trimIndent())
        assert(out == "(ln 6, col 11): invalid assignment : type mismatch") { out }
    }
    @Test
    fun e03_ptr_err () {
        val out = inp2env("""
            var pout: \_int
            {
                var pin: \_int
                set pout = pin  -- no
            }
        """.trimIndent())
        assert(out == "(ln 4, col 14): invalid assignment : type mismatch") { out }
    }
    @Test
    fun e03_ptr_ok () {
        val out = inp2env("""
            var pout: \_int
            {
                var pin: \_int @@
                set pout = pin
            }
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun e04_ptr_ok () {
        val out = inp2env("""
            var pout: \_int
            {
                var pin: \_int
                set pin = pout
            }
        """.trimIndent())
        assert(out == "OK")
    }

    // POINTERS - DOUBLE

    @Test
    fun f01_ptr_ptr_err () {
        val out = inp2env("""
            var p: \\_int
            {
                var y: \_int
                set p = \y
            }
        """.trimIndent())
        assert(out == "(ln 4, col 11): invalid assignment : type mismatch") { out }
    }
    @Test
    fun f02_ptr_ptr_ok () {
        val out = inp2env("""
            var p: \\_int
            var z: _int; set z = _10
            var y: \_int; set y = \z
            set p = \y
            output std //p
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun f03_ptr_ptr_err () {
        val out = inp2env("""
            var p: \\_int
            {
                var z: _int; set z = _10
                var y: \_int; set y = \z
                set p = \y
            }
        """.trimIndent())
        assert(out == "(ln 5, col 11): invalid assignment : type mismatch") { out }
    }
    @Test
    fun f04_ptr_ptr_err () {
        val out = inp2env("""
            var p: \_int
            {
                var x: _int; set x = _10
                var y: \_int; set y = \x
                var z: \\_int; set z = \y
                set p = /z
            }
        """.trimIndent())
        assert(out == "(ln 6, col 11): invalid assignment : type mismatch") { out }
    }

    // POINTERS - FUNC - CALL

    @Test
    fun g01_ptr_func_ok () {
        val out = inp2env("""
            var f : (\_int -> \_int); set f = func (\_int -> \_int) {
                return arg
            }
            var v: _int; set v = _10
            var p: \_int; set p = f \v
            output std /p
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun g02_ptr_func_err () {
        val out = inp2env("""
            var v: _int; set v = _10
            var f : () -> \_int; set f = func () -> \_int {
                return \v
            }
            var p: \_int; set p = f ()
            output std /p
        """.trimIndent())
        //assert(out == "(ln 3, col 13): undeclared variable \"v\"") { out }
        assert(out == "OK") { out }
    }
    @Test
    fun g02_ptr_func_ok () {
        val out = inp2env("""
            var v: _int; set v = _10
            var f : () -> \_int; set f = func () -> \_int {
                return \v
            }
            var p: \_int; set p = f ()
            output std /p
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun g03_ptr_func_err () {
        val out = inp2env("""
            var f : () -> \_int; set f = func () -> \_int {
                var v: _int; set v = _10
                return \v
            }
            var v: _int; set v = _10
            var p: \_int; set p = f ()
            output std /p
        """.trimIndent())
        assert(out == "(ln 3, col 5): invalid return : type mismatch") { out }
    }
    @Test
    fun g04_ptr_func_err () {
        val out = inp2env("""
            var f : \_int -> \_int; set f = func (\_int -> \_int) {
                var ptr: \_int; set ptr = arg
                return ptr
            }
            var v: _int; set v = _10
            var p: \_int; set p = f \v
            output std /p
        """.trimIndent())
        assert(out == "(ln 3, col 5): invalid return : type mismatch") { out }
    }
    @Test
    fun g05_ptr_caret_ok () {
        val out = inp2env("""
            var f : \_int -> \_int; set f = func \_int -> \_int {
                var ptr: \_int@a; set ptr = arg
                return ptr
            }
            var v: _int; set v = _10
            var p: \_int; set p = f \v
            output std /p
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun g06_ptr_caret_err () {
        val out = inp2env("""
            var f : \_int -> \_int; set f = func \_int -> \_int {
                var x: _int; set x = _10
                var ptr: \_int @a; set ptr = \x
                return ptr
            }
            var v: _int; set v = _10
            var p: \_int; set p = f \v
            output std /p
        """.trimIndent())
        assert(out == "(ln 3, col 9): invalid assignment : cannot hold local pointer \"x\" (ln 2)") { out }
    }

    @Test
    fun g07_ptr_caret_err () {
        val out = inp2env("""
            var ptr: \_int @a
        """.trimIndent())
        assert(out == "OK") { out }
    }

    @Test
    fun g08_ptr_arg_err () {
        val out = inp2env("""
            var f: _int -> \_int; set f = func _int -> \_int
            {
                return \arg
            }
        """.trimIndent())
        assert(out == "(ln 3, col 5): invalid assignment : cannot hold local pointer \"arg\" (ln 2)") { out }
    }
    @Test
    fun g09_ptr_arg_err () {
        val out = inp2env("""
            var f: _int -> \_int @a; set f = func _int -> \_int @a
            {
                var ptr: \_int @a; set ptr = \arg
                return ptr
            }
        """.trimIndent())
        assert(out == "(ln 3, col 9): invalid assignment : cannot hold local pointer \"arg\" (ln 2)") { out }
    }
    @Test
    fun g10_ptr_out_err () {
        val out = inp2env("""
            var f: \_int -> \\_int; set f = func \_int -> \\_int
            {
                var ptr: ^\_int; set ptr = arg
                return \ptr
            }
        """.trimIndent())
        assert(out == "(ln 4, col 5): invalid assignment : cannot hold local pointer \"ptr\" (ln 3)") { out }
    }
    @Test
    fun g11_ptr_func () {
        val out = inp2env("""
            var v: _int; set v = _10
            var f : () -> \_int; set f = func () -> \_int {
                return \v
            }
            var p: \_int
            {
                set p = f ()
            }
        """.trimIndent())
        assert(out == "(ln 7, col 11): invalid assignment : cannot hold local pointer \"f\" (ln 2)") { out }
    }
    @Test
    fun g12_ptr_func () {
        val out = inp2env("""
            var v: _int; set v = _10
            var f : \_int -> \_int; set f = func \_int -> \_int {
                return \v
            }
            var p: \_int
            {
                set p = f (\v)
            }
        """.trimIndent())
        assert(out == "(ln 7, col 11): invalid assignment : cannot hold local pointer \"f\" (ln 2)") { out }
    }
    @Test
    fun g13_ptr_func () {
        val out = inp2env("""
            var v: \_int
            var f : \_int -> (); set f = func \_int -> () {
                set v = arg
            }
        """.trimIndent())
        assert(out == "(ln 3, col 11): invalid assignment : cannot hold local pointer \"arg\" (ln 2)") { out }
    }

    // POINTERS - TUPLE - TYPE

    @Test
    fun h01_ptr_tuple_err () {
        val out = inp2env("""
            var p: \[_int,_int]
            {
                var y: [_int,_int]; set y = [_10,_20]
                set p = \y
            }
        """.trimIndent())
        assert(out == "(ln 4, col 11): invalid assignment : type mismatch") { out }
    }
    @Test
    fun h02_ptr_user_err1 () {
        val out = inp2env("""
            var p: \()
            {
                var y: <()>; set y = <.1>
                set p = \y!1
            }
        """.trimIndent())
        assert(out == "(ln 4, col 11): invalid assignment : type mismatch") { out }
    }
    @Test
    fun h02_ptr_user_err2 () {
        val out = inp2env("""
            var p: \()
            {
                var y: <()>; set y = <.1>
                set p = \y!1
            }
        """.trimIndent())
        assert(out == "(ln 4, col 11): invalid assignment : cannot hold local pointer \"y\" (ln 3)") { out }
    }
    @Test
    fun h03_ptr_tup () {
        val out = inp2env("""
            var v: [_int,_int]; set v = [_10,_20]
            var p: \_int; set p = \v.1
            set /p = _20
            output std v
        """.trimIndent())
        assert(out == "OK")
    }
    @Test
    fun h04_ptr_tup_err () {
        val out = inp2env("""
            var p: \_int
            {
                var v: [_int,_int]; set v = [_10,_20]
                set p = \v.1
            }
        """.trimIndent())
        assert(out == "(ln 4, col 11): invalid assignment : cannot hold local pointer \"v\" (ln 3)") { out }
    }
    @Test
    fun h05_ptr_type_err () {
        val out = inp2env("""
            var p: \()
            {
                var v: <()> = <.1 ()>
                set p = \v!1
            }
        """.trimIndent())
        assert(out == "(ln 4, col 11): invalid assignment : cannot hold local pointer \"v\" (ln 3)") { out }
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
        assert(out == "(ln 4, col 13): invalid assignment : cannot hold local pointer \"v\" (ln 3)") { out }
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
            var x1: [_int,\_int]
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
            var x1: <\_int>
            {
                var v: _int = _20
                var x2: <\_int> = <.1 \v>
                set x1 = x2
            }
        """.trimIndent())
        assert(out == "(ln 5, col 12): invalid assignment : cannot hold local pointer \"x2\" (ln 4)")
    }

    // TYPE - REC - REPLACE - CLONE - BORROW

    @Test
    fun i01_list () {
        val out = inp2env("""
            var p: \<?^>
            {
                var l: <?^> = new <.1 (new <.1 <.0>>)>
                set p = \l
            }
            output std p
        """.trimIndent())
        assert(out == "(ln 4, col 11): invalid assignment : cannot hold local pointer \"l\" (ln 3)")
    }
    @Test
    fun i01_list_2 () {
        val out = inp2env("""
            var p: \<?^>
            {
                var l: <?^> = new <.1 (new <.1 <.0>>)>
                set p = \l!1
            }
            output std p
        """.trimIndent())
        assert(out == "(ln 4, col 13): invalid expression : expected `borrow` operation modifier")
    }
    @Test
    fun i02_list () {
        val out = inp2env("""
            var p: \<?^>
            {
                var l: <?^> = new <.1 (new <.1 <.0>>)>
                set p = \l!1
            }
            output std p
        """.trimIndent())
        assert(out == "(ln 4, col 11): invalid assignment : cannot hold local pointer \"l\" (ln 3)")
    }
    @Test
    fun i03_list () {
        val out = inp2env("""
            var ret: <[_int,<^>]> = <.1>
        """.trimIndent())
        assert(out == "(ln 1, col 5): invalid assignment : type mismatch")
    }
    @Test
    fun i04_uni_rec_err () {
        val out = inp2env("""
            var ret: <(),<?^^,^>> = <.1>
        """.trimIndent())
        assert(out == "(ln 1, col 27): invalid expression : expected `new` operation modifier")
    }
    @Test
    fun i05_uni_rec_ok () {
        val out = inp2env("""
            var ret: <(),<?^^,^>> = new <.1>
        """.trimIndent())
        assert(out == "OK")
    }
    @Test
    fun i06_uni_rec_err () {
        val out = inp2env("""
            var ret: <(),<?^^,^>> = new <.2 <.1 <.1>>>
        """.trimIndent())
        assert(out == "(ln 1, col 39): invalid expression : expected `new` operation modifier")
    }
    @Test
    fun i07_list_err () {
        val out = inp2env("""
            var ret: <[_int,<^>]> = <.0>
        """.trimIndent())
        assert(out == "(ln 1, col 27): invalid constructor : out of bounds") { out }
    }
    @Test
    fun i08_mutual () {
        val out = inp2env("""
            var e: <(), <(),^^>> = new <.2 new <.1>>
            var s: <(), <(),^^>> = e!2
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun i09_mutual () {
        val out = inp2env("""
            var e: <<^^,()>, ()> = new <.1 new <.2>>
            var s: <<^^,()>, ()> = e!1
        """.trimIndent())
        assert(out == "OK") { out }
    }

    // XEXPR

    @Test
    fun j01_rec_xepr_null_err () {
        val out = inp2env("""
            var x: <^>
            var y: <^> = x
        """.trimIndent())
        assert(out == "(ln 2, col 14): invalid expression : expected operation modifier")
    }
    @Test
    fun j02_rec_xepr_move_ok () {
        val out = inp2env("""
            var x: <^>
            var y: <^> = x
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun j02_rec_xepr_move_err () {
        val out = inp2env("""
            var x: <^>
            var y: \<^> = \x
        """.trimIndent())
        assert(out == "(ln 2, col 23): expected expression : have `\\´")
    }
    @Test
    fun j03_rec_xepr_borrow_err () {
        val out = inp2env("""
            var x: <^>
            var y: <^> = x
        """.trimIndent())
        assert(out == "(ln 2, col 21): invalid `borrow` : expected pointer to recursive variable")
    }
    @Test
    fun j04_rec_xepr_copy_err () {
        val out = inp2env("""
            var x: _int = _10
        """.trimIndent())
        assert(out == "(ln 1, col 20): invalid `copy` : expected recursive variable")
    }
    @Test
    fun j05_rec_xepr_borrow_ok () {
        val out = inp2env("""
            var x: <^>
            var y: \<^> = \x
        """.trimIndent())
        assert(out == "OK")
    }
    @Test
    fun j06_rec_xepr_borrow_err () {
        val out = inp2env("""
            var x: _int
            var y: \_int = \x
        """.trimIndent())
        assert(out == "(ln 2, col 23): invalid `borrow` : expected pointer to recursive variable")
    }
    @Test
    fun j07_rec_xepr_copy_err () {
        val out = inp2env("""
            var x: <?^> = <.1 <.0>>
        """.trimIndent())
        assert(out == "(ln 1, col 20): expected expression : have `<´")
        //assert(out == "(ln 1, col 20): invalid `copy` : expected recursive variable")
        //assert(out == "(ln 1, col 5): invalid assignment : expected `new` operation modifier")
    }
    @Test
    fun j08_rec_xepr_double_rec_err () {
        val out = inp2env("""
            var x: <?<^^>> = new <.1 <.1 <.0>>>
        """.trimIndent())
        assert(out == "(ln 1, col 28): invalid expression : expected `new` operation modifier") { out }
    }
    @Test
    fun j08_rec_xepr_double_rec_ok () {
        val out = inp2env("""
            var x: <?<^^>> = new <.1 new <.1 <.0>>>
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun j09_rec_xepr_double_rec () {
        val out = inp2env("""
            var x: <?<^^>> = new <.1 new <.1 new <.1 new <.1 <.0>>>>>
        """.trimIndent())
        assert(out == "OK")
    }
    @Test
    fun j10_rec_tup () {
        val out = inp2env("""
            var x: <(),[^^]> = new <.2 [new <.1>]>
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun j10_rec_xepr_double_rec () {
        val out = inp2env("""
            var x: <?<?[^^^,^^]>> = new <.1 new <.1 [<.0>,<.0>]>>
        """.trimIndent())
        assert(out == "OK")
    }
    @Test
    fun j11_rec_xepr_double_rec () {
        val out = inp2env("""
            var x: <?<?[^^^,^^]>> = new <.1 new <.1 [<.0>,<.0>]>>
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun j11_rec_xepr_double_rec2 () {
        val out = inp2env("""
            var x: <?[^^]> = new <.1 [<.0>]>
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun j11_rec_xepr_double_rec3 () {
        val out = inp2env("""
            var x: <?[^^,^^]> = new <.1 [<.0>,<.0>]>
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun j11_rec_xepr_double_rec4 () {
        val out = inp2env("""
            var x: <?<?[^^^,^^]>> = new <.1 new <.1 [<.0>,new <.1 [<.0>,<.0>]>]>>
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun j11_rec_xepr_double_rec5 () {
        val out = inp2env("""
            var x: <?<?[^^^,^^]>> = new <.1 new <.1 [<.0>,<.0>]>>
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun j11_rec_xepr_double_rec6 () {
        val out = inp2env("""
            var x: <?<?[^^^,^^]>> = new <.1 new <.1 [<.0>,new <.1 [?,<.0>]>]>>
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun j12_rec_xepr_double_rec_err () {
        val out = inp2env("""
            var x: <?<?[^^^,^^]>> = new <.1 <.1 [<.0>,<.0>]>>
        """.trimIndent())
        assert(out == "(ln 1, col 35): invalid expression : expected `new` operation modifier") { out }
    }
    @Test
    fun j13_tup_move_no () {
        val out = inp2env("""
            var t1: [<?^>] = [<.0>]
            var t2: [<?^>] = t1
        """.trimIndent())
        assert(out == "(ln 2, col 26): invalid `replace` : expected recursive variable")
    }
    @Test
    fun j13_tup_move_ok () {
        val out = inp2env("""
            var l: <?^> = <.0>
            var t1: [<?^>] = [l]
            var t2: [<?^>] = [t1.1]
        """.trimIndent())
        //assert(out == "(ln 3, col 17): invalid `replace` : expected recursive variable")
        assert(out == "OK") { out }
    }
    @Test
    fun j14_new_no () {
        val out = inp2env("""
            var l: <?^> = new <.0>
        """.trimIndent())
        assert(out == "(ln 1, col 21): invalid `new` : expected variant constructor")
    }
    @Test
    fun j15_rec_xepr_borrow_err () {
        val out = inp2env("""
            var x: [()]
            var y: \_int = \x
        """.trimIndent())
        assert(out == "(ln 2, col 23): invalid `borrow` : expected pointer to recursive variable")
    }
    @Test
    fun j16_rec_xepr_borrow_err () {
        val out = inp2env("""
            var x: <()>
            var y: \_int = \x
        """.trimIndent())
        //assert(out == "(ln 2, col 16): invalid `borrow` : expected pointer to recursive variable")
        assert(out == "OK")
    }
    @Test
    fun j17_rec_xepr_borrow_ok () {
        val out = inp2env("""
            var x: <(),^>
            var y: \_int = \x
        """.trimIndent())
        assert(out == "OK")
    }
    @Test
    fun j18_rec_xepr_borrow_ok () {
        val out = inp2env("""
            var x: [<^>]
            var y: \_int = \x
        """.trimIndent())
        assert(out == "OK")
    }
    @Test
    fun j19_rec_xepr_borrow_ok () {
        val out = inp2env("""
            var x: [<^>]
            var y: \_int = \x
        """.trimIndent())
        assert(out == "OK")
    }
    @Test
    fun j20_rec_xepr_borrow_err () {
        val out = inp2env("""
            var x: [<^>]
            var f: \<^> -> () = func \<^> -> ()
            {
                output std arg
            }
            call f \x.1!1
        """.trimIndent())
        assert(out == "(ln 6, col 8): invalid expression : expected `borrow` operation modifier") { out }
    }
    @Test
    fun j21_rec_xepr_borrow_err () {
        val out = inp2env("""
            var x: [<^>]
            var y: \<^> = \x.1!1
        """.trimIndent())
        assert(out == "(ln 2, col 15): invalid expression : expected `borrow` operation modifier")
    }
    @Test
    fun j23_rec_xexpr_move_err () {
        val out = inp2env("""
            var x: <^>
            var y: \<^> = \x!1
            var z: <^> = /y
        """.trimIndent())
        assert(out == "OK")
        //assert(out == "(ln 3, col 22): invalid `consume` : expected recursive variable")
    }
    @Test
    fun j23_rec_xexpr_move_err2 () {
        val out = inp2env("""
            var x: <^>
            var y: \<^> = \x!1
            var z: <^> = /y
        """.trimIndent())
        assert(out == "(ln 3, col 25): expected expression : have end of file")
    }
    @Test
    fun j23_rec_xexpr_move_err3 () {
        val out = inp2env("""
            var x: <^>
            var y: \<^> = \x!1
            var z: <^> = /y
        """.trimIndent())
        assert(out == "(ln 3, col 24): expected `=´ : have end of file")
    }
    @Test
    fun j24_rec_xepr_borrow_ok () {
        val out = inp2env("""
            var x: [<^>]
            var f: \<^> -> () = func \<^> -> ()
            {
                output std arg
            }
            call f \x.1!1
        """.trimIndent())
        assert(out == "OK")
    }
    @Test
    fun j25_new_no () {
        val out = inp2env("""
            var l: <^> = new <.1 ?>
        """.trimIndent())
        assert(out == "OK")
    }
    @Test
    fun j26_func_move () {
        val out = inp2env("""
            var f: ()-><?^> = func ()-><?^> {
                return new <.1 <.0>>
            }
            var v: <?^> = f ()
        """.trimIndent())
        //assert(out == "(ln 4, col 20): invalid `replace` : expected recursive variable")
        assert(out == "(ln 4, col 25): expected `=´ : have `()´")
    }
    @Test
    fun j27_f_rec () {
        val out = inp2env("""
            var f: ()-><?^> = func ()-><?^> {
                return new <.1 <.0>>
            }
            var v: <?^> = call f ()
        """.trimIndent())
        assert(out == "OK")
    }
    @Test
    fun j28_nonrec_nullable () {
        val out = inp2env("""
            var l: <?()>
        """.trimIndent())
        assert(out == "(ln 1, col 8): invalid type declaration : unexpected `?´") { out }
    }
    @Test
    fun j29_rec_mutual () {
        val out = inp2env("""
            var e: <(),<(),^^>> = new <.2 new <.1>>
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun j30_rec_mutual_err () {
        val out = inp2env("""
            var e: <(),<(),^>> = new <.2 new <.1>>
        """.trimIndent())
        assert(out == "(ln 1, col 28): invalid `new` : expected variant constructor") { out }
    }
    @Test
    fun j31_rec_mutual () {
        val out = inp2env("""
            var e: <(),<(),^^>> = new <.2 new <.2 new <.1>>>
            var s: <(),<(),<(),^^>>> = <.2 e>
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun j32_nonrec_hold () {
        val out = inp2env("""
            var l: <(),\^>
        """.trimIndent())
        assert(out == "(ln 1, col 8): invalid type declaration : unexpected recursive pointer") { out }
    }
    @Test
    fun j32_rec_hold () {
        val out = inp2env("""
            var x: <? [<(),\^^^>,^^]> = new <.1 [<.1>,<.0>]>
        """.trimIndent())
        assert(out == "OK") { out }
    }

    // IF / FUNC

    @Test
    fun k01_if () {
        val out = inp2env("""
            if () {} else {}
        """.trimIndent())
        assert(out == "(ln 1, col 1): invalid condition : type mismatch")
    }
    @Test
    fun k02_func_arg () {
        val out = inp2env("""
            var f1: ()->() = func ()->() {
                var f2: ()->() = func ()->() {
                }
            }
        """.trimIndent())
        assert(out == "OK")
    }

    // BORROW / CONSUME

    @Test
    fun l01_borrow_err () {
        val out = inp2env("""
            var x: <?^>
            var y: \<?^> = \x!1
            set x = <.0>
        """.trimIndent())
        assert(out == "(ln 3, col 7): invalid assignment of \"x\" : borrowed in line 2") { out }
    }
    @Test
    fun l02_borrow_err () {
        val out = inp2env("""
            var x: <^>
            var y: \<^> = \x!1
            var z: <^> = x
        """.trimIndent())
        assert(out == "(ln 3, col 22): invalid operation on \"x\" : borrowed in line 2")
    }
    @Test
    fun l02_borrow_func_err () {
        val out = inp2env("""
            var f: ()->() = func ()->() {
                var x: <^>
                var y: \<^> = \x!1
                var z: <^> = x
            }
        """.trimIndent())
        assert(out == "(ln 4, col 26): invalid operation on \"x\" : borrowed in line 3")
    }
    @Test
    fun l02_borrow_ok () {
        val out = inp2env("""
            var x: <?^>
            {
                var y: \<?^> = \x!1
            }
            var z: <?^> = x
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun l03_borrow_err () {
        val out = inp2env("""
            var x: <?^>
            var f: \<?^> -> () = func \<?^> -> ()
            {
                set x = <.0>
            }
            call f \x!1
        """.trimIndent())
        assert(out == "(ln 4, col 9): undeclared variable \"x\"") { out }
        //assert(out == "(ln 4, col 11): invalid assignment of \"x\" : borrowed in line 3") { out }
    }
    @Test
    fun l03_borrow_err2 () {
        val out = inp2env("""
            var x: <?^>
            var f: \<?^> -> () = func \<?^> -> ()
            {
                set x = <.0>
            }
            var y: \<?^> = \x!1
            call f y
        """.trimIndent())
        assert(out == "(ln 4, col 9): undeclared variable \"x\"") { out }
        //assert(out == "(ln 4, col 11): invalid assignment of \"x\" : borrowed in line 6") { out }
    }
    @Test
    fun l04_borrow_err () {
        val out = inp2env("""
            var x: <?^>
            var f: () -> () = func () -> () {
                set x = <.0>
            }
            var y: \<?^> = \x!1
            var g: () -> () = f
            call g ()
        """.trimIndent())
        assert(out == "(ln 3, col 9): undeclared variable \"x\"") { out }
        //assert(out == "(ln 3, col 11): invalid assignment of \"x\" : borrowed in line 5") { out }
    }
    @Test
    fun l05_borrow_err () {
        val out = inp2env("""
            var x: <?^>
            var y: \<?^> = \x!1
            set x = <.0>
        """.trimIndent())
        assert(out == "(ln 3, col 7): invalid assignment of \"x\" : borrowed in line 2")
    }
    @Test
    fun l06_borrow_err () {
        val out = inp2env("""
            var x: <?^>
            var y: [(),\<?^>] = [(), \x!1]
            set x = <.0>
        """.trimIndent())
        assert(out == "(ln 3, col 7): invalid assignment of \"x\" : borrowed in line 2") { out }
    }
    @Test
    fun l07_borrow_err () {
        val out = inp2env("""
            var x: <?^>
            var y: <\<?^>> = <.1 \x!1>
            set x = <.0>
        """.trimIndent())
        assert(out == "(ln 3, col 7): invalid assignment of \"x\" : borrowed in line 2")
    }
    @Test
    fun l08_borrow_err () {
        val out = inp2env("""
            var x: [\<?^>,<?^>] = [?,<.0>]
            set x.1 = \x.2!1
            var y: <?^> = x.2
        """.trimIndent())
        assert(out == "(ln 2, col 9): invalid assignment of \"x\" : borrowed in line 2") { out }
    }
    @Test
    fun l08_borrow_rec_err () {
        val out = inp2env("""
        var x: <?^>
        var f: ()->() = func ()->() {
            set x = <.0>
            var y: \<?^> = \x
            return f ()
        }
        call f ()
        """.trimIndent())
        assert(out == "(ln 3, col 9): undeclared variable \"x\"")
        //assert(out == "(ln 3, col 11): invalid assignment of \"x\" : borrowed in line 4") { out }
    }
    @Test
    fun l09_consume_err () {
        val out = inp2env("""
            var x: <?^> = <.0>
            var y: <?^> = x
            set x = <.0>
        """.trimIndent())
        assert(out == "OK") { out }
        // TODO: could accept b/c x is being reassigned
        //assert(out == "(ln 3, col 5): invalid access to \"x\" : consumed in line 2") { out }
    }
    @Test
    fun l10_consume_err () {
        val out = inp2env("""
            var x: <?^> = <.0>
            var y: <?^> = x
            output std \x
        """.trimIndent())
        assert(out == "(ln 3, col 13): invalid access to \"x\" : consumed in line 2") { out }
    }
    @Test
    fun l11_consume_err () {
        val out = inp2env("""
            var x: <?^> = <.0>
            var y: \<?^> = \x
            var z: <?^> = /y
            output std \x
        """.trimIndent())
        assert(out == "(ln 4, col 13): invalid access to \"x\" : consumed in line 3")
    }
    @Test
    fun l12_replace_ok () {
        val out = inp2env("""
            var x: <?^> = <.0>
            var y: \<?^> = \x
            var z: <?^> = /y
            output std \x
        """.trimIndent())
        assert(out == "OK")
    }
    @Test
    fun l13_consume_okr () {
        val out = inp2env("""
            var x: <?^> = <.0>
            set x = x
            output std \x
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun l13_consume_ok2 () {
        val out = inp2env("""
            var f: ()-><?^> = func ()-><?^> {
                var x: <?^> = <.0>
                return x
            }
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun l13_consume_err () {
        val out = inp2env("""
            var x: <?^> = <.0>
            set x!1 = x
        """.trimIndent())
        assert(out == "(ln 2, col 9): invalid assignment of \"x\" : consumed in line 2") { out }
    }
    @Test
    fun l14_consume_ok () {
        val out = inp2env("""
            var string_c2ce: _(char*)-><?[_int,^^]> = func _(char*)-><?[_int,^^]> {
                var ret: <?^> = <.0>
                loop {
                    set ret = ret
                }
                var zzz: <?^> = ret
                --return ret
            }
            call string_c2ce _x
        """.trimIndent())
        assert(out == "OK") { out }
    }

    // UNION SELF POINTER

    @Test
    fun m01_hold_ok () {
        val out = inp2env("""
            var x: <(),^> = new <.2 new <.1>>
            output std \x
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun m02_borrow_err () {
        val out = inp2env("""
            var x: <()> = <.1>
            var y: \() = \x!1
            set x = <.1>
        """.trimIndent())
        assert(out == "(ln 3, col 7): invalid assignment of \"x\" : borrowed in line 2") { out }
    }

    // UNION SELF POINTER / HOLD

    @Test
    fun n01_hold_ok () {
        val out = inp2env("""
            var x: <? [<(),\^^^>,^^]> = new <.1 [<.1>,<.0>]>
            var y: <? [<(),\^^^>,^^]> = new <.1 [<.1>,x]>
            set y!1.2!1.1 = <.1>
            -- <.1 [<.1>,<.1 [<.1>,<.0>]>]>
            output std \y
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun n02_hold_err () {
        val out = inp2env("""
            var x: <? [<(),\^^^>,^^]> = new <.1 [<.1>,<.0>]>
            var y: <? [<(),\^^^>,^^]> = new <.1 [<.1>, x]>
            set y!1.2!1.1 = <.2 \y>
            output std \y
        """.trimIndent())
        assert(out == "(ln 3, col 21): invalid expression : expected `hold` operation modifier") { out }
    }
    @Test
    fun n03_hold_ok () {
        val out = inp2env("""
            var x: <? [<(),\^^^>,^^]> = new <.1 [<.1>,<.0>]>
            var y: <? [<(),\^^^>,^^]> = new <.1 [<.1>, x]>
                -- can receive x
                -- can be consumed by + (but not -)
                -- can hold itself (or borrows)
                -- cannot set its parts
            set y!1.2!1.1 = <.2 \y>
            output std \y
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun n04_hold_err () {
        val out = inp2env("""
            var x: <? [<(),\^^^>,^^]> = new <.1 [<.1>,<.0>]>
            var y: <? [<(),\^^^>,^^]> = new <.1 [<.1>, x]>
            set y!1.2!1.1 = <.2 \y>
            output std \y
        """.trimIndent())
        assert(out == "(ln 3, col 28): invalid `borrow` : expected pointer to recursive variable") { out }
    }
    @Test
    fun n05_borrow_ok () {
        val out = inp2env("""
            var x: <?[(),^^]> = new <.1 [(),<.0>]>
            var y: [(),<?[(),^^]>] = [(), new <.1 [(),<.0>]>]
            var z: [(),\<?[(),^^]>] = [(), \x]
            output std (/z.2)!1.2!0
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun n06_hold_ok () {
        val out = inp2env("""
            var x: <? [\^^,^^]> = new <.1 [?,<.0>]>
            set x!1.1 = \x
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun n07_hold_ok () {
        val out = inp2env("""
            var x: <? [<(),\^^^>,^^]> = new <.1 [<.1>,<.0>]>
            set x!1.1 = <.2 \x>  -- ok
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun n08_hold_ok () {
        val out = inp2env("""
            var x: <? [<?\^^^>,^^]> = new <.1 [<.0>,<.0>]>
            set x!1.1 = <.1 \x>  -- ok
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun n09_hold_err () {
        val out = inp2env("""
            var x: <? [<?\^^^>,^^]> = new <.1 [<.0>,<.0>]>
            var y: <? [<?\^^^>,^^]> = x
            output std y
        """.trimIndent())
        assert(out == "(ln 2, col 35): invalid `consume` : expected recursive variable") { out }
    }
}
