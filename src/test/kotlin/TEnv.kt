import org.junit.jupiter.api.Disabled
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
            aux_clear()
            s.aux_01_upsenvs(null, null)
            check_01_before_tps(s)
            Aux_02_tps(s)
            check_02_after_tps(s)
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
        aux_clear()
        s.aux_01_upsenvs(null,null)
        Aux_02_tps(s)
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
        val out = inp2env("var x:() ; var x:{}()->()")
        assert(out == "(ln 1, col 16): invalid declaration : \"x\" is already declared (ln 1)") { out }
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
        assert(out == "(ln 1, col 15): invalid discriminator : not an union") { out }
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
            var x: /_int @a
        """.trimIndent())
        assert(out == "(ln 1, col 14): undeclared scope \"@a\"") { out }
    }
    @Test
    fun b09_user_err () {
        val out = inp2env("""
            var y: <^>
        """.trimIndent())
        assert(out == "(ln 1, col 9): invalid `^´ : expected pointer type") { out }
    }
    @Test
    fun b10_user_err () {
        val out = inp2env("""
            var x: <()>
            var y: </^ @local>
            set y = x
        """.trimIndent())
        assert(out == "(ln 3, col 7): invalid assignment : type mismatch") { out }
    }
    @Test
    fun b10_user_empty_err () {
        val out = inp2env("""
            var l: </^ @global>
            set l = <.1 ()>:<()>
        """.trimIndent())
        assert(out == "(ln 2, col 7): invalid assignment : type mismatch") { out }
        //assert(out == "(ln 2, col 11): invalid constructor : expected `new`") { out }
    }
    @Test
    fun b10_user_empty_err2 () {
        val out = inp2env("""
            var l: </^ @global>
            set l = <.1 ()>:</^ @global>
        """.trimIndent())
        //assert(out == "(ln 2, col 7): invalid assignment : type mismatch") { out }
        assert(out == "(ln 2, col 11): invalid constructor : type mismatch") { out }
        //assert(out == "(ln 2, col 11): invalid constructor : expected `new`") { out }
    }
    @Test
    fun b11_user_empty_err () {
        val out = inp2env("""
            var l: <()>
            set l = <.1>:<()>
            output std l!2
        """.trimIndent())
        assert(out == "(ln 3, col 14): invalid discriminator : out of bounds") { out }
    }
    @Test
    fun b12_user_empty_err () {
        val out = inp2env("""
            var l: </^>
            set l = <.1 <.0>:/</^>>:</^>
            --output std l!0
        """.trimIndent())
        //assert(out == "(ln 2, col 11): invalid expression : expected `new` operation modifier") { out }
        //assert(out == "(ln 2, col 11): invalid constructor : expected `new`") { out }
        assert(out == "OK") { out }
    }
    @Test
    fun b13_user_empty_ok () {
        val out = inp2env("""
            var l: </^ @local>
            set l = new <.1 <.0>:/</^>>:</^>
        """.trimIndent())
        assert(out == "(ln 2, col 7): invalid assignment : type mismatch") { out }
    }
    @Test
    fun b14_user_empty_ok () {
        val out = inp2env("""
            var l: /</^>
            set l = new <.1 <.0>:/</^>>:</^>
            output std l\!0
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun b14_pool_err () {
        val out = inp2env("""
            var l: /</^ @local> @local
            set l = new <.1 <.0>:/</^ @local>@local>:</^ @local>: @aaa
            output std l\!0
        """.trimIndent())
        assert(out == "(ln 2, col 55): undeclared scope \"@aaa\"") { out }
    }
    @Test
    fun b15_pool_err () {
        val out = inp2env("""
            var l: @aaa
        """.trimIndent())
        assert(out == "(ln 1, col 8): undeclared scope \"@aaa\"") { out }
    }
    @Test
    fun b16_pool_err () {
        val out = inp2env("""
            call _f @aaa
        """.trimIndent())
        assert(out == "(ln 1, col 9): undeclared scope \"@aaa\"") { out }
    }
    @Test
    fun b17_pool_err () {
        val out = inp2env("""
            var f: {} () -> ()
            call f: @aaa
        """.trimIndent())
        assert(out == "(ln 2, col 9): undeclared scope \"@aaa\"") { out }
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
            var f : {} () -> (); set f =
                func {} () -> () {
                    return [()]
                }
        """.trimIndent())
        assert(out == "(ln 3, col 9): invalid return : type mismatch") { out }
    }
    @Test
    fun c04_type_func_arg () {
        val out = inp2env("""
            var f : / ({}[(),()] -> ()) @local
            set f = func ({}[(),()] -> ()) { }
            call f\ ()
        """.trimIndent())
        assert(out == "(ln 3, col 7): invalid call : type mismatch") { out }
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
            var x: /() @local
            set x = ()
        """.trimIndent())
        assert(out == "(ln 2, col 7): invalid assignment : type mismatch") { out }
    }
    @Test
    fun c08_type_upref () {
        val out = inp2env("""
            var y: ()
            var x: ()
            set x = /y
        """.trimIndent())
        assert(out == "(ln 3, col 7): invalid assignment : type mismatch")
    }
    @Test
    fun c09_type_upref () {
        val out = inp2env("""
            var y: ()
            var x: /() @local
            set x = /y
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun c10_type_upref () {
        val out = inp2env("""
            var y: [()]
            var x: /()
            set x = /y
        """.trimIndent())
        assert(out == "(ln 3, col 7): invalid assignment : type mismatch") { out }
    }
    @Test
    fun c11_type_upref () {
        val out = inp2env("""
            var y: ()
            var x: /() @local
            set x = /y
            var z: _x
            set z = /x
        """.trimIndent())
        assert(out == "OK")
    }
    @Test
    fun c12_type_dnref () {
        val out = inp2env("""
            var x: ()
            output std x\
        """.trimIndent())
        assert(out == "(ln 2, col 13): invalid operand to `\\´ : not a pointer") { out }
    }
    @Test
    fun c12_type_dnref2 () {
        val out = inp2env("""
            var x: ()
            set x = x\
        """.trimIndent())
        assert(out == "(ln 2, col 10): invalid operand to `\\´ : not a pointer") { out }
    }
    @Test
    fun c13_type_dnref () {
        val out = inp2env("""
            var x: ()
            var y: /()
            set y = /x
            var z: /() @local
            set z = y\
        """.trimIndent())
        assert(out == "(ln 5, col 7): invalid assignment : type mismatch") { out }
    }
    @Test
    fun c14_type_func_err () {
        val out = inp2env("""
            var x: {}()->[({}()->())]
        """.trimIndent())
        //assert(out == "(ln 1, col 12): invalid type : cannot return function type : currently not supported")
        assert(out == "OK")
    }
    @Test
    fun c15_type_func_tup () {
        val out = inp2env("""
            var f: [{}()->()]
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
        var f: {}()->(); set f = func {}()->() {
            call arg.2
        }
        """.trimIndent())
        assert(out == "(ln 2, col 14): invalid discriminator : type mismatch") { out }
    }
    @Test
    fun c18_type_func_err () {
        val out = inp2env("""
        var f: {}[(),<(),{}()->()>]->()
        set f = func {}<(),{}()->()>->() {
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
        assert(out == "(ln 2, col 14): invalid discriminator : not an union") { out }
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
            output std <.1>:<()>!2
        """.trimIndent())
        assert(out == "(ln 1, col 22): invalid discriminator : unexpected constructor") { out }
    }
    @Test
    fun c19_uni_pred_err () {
        val out = inp2env("""
            output std <.1>:<()>?1
        """.trimIndent())
        assert(out == "(ln 1, col 22): invalid discriminator : unexpected constructor") { out }
    }
    @Test
    fun c20_uni_disc_err () {
        val out = inp2env("""
            output std <.2>:<(),()>!2
        """.trimIndent())
        assert(out == "(ln 1, col 25): invalid discriminator : unexpected constructor") { out }
    }
    @Test
    fun c21_uni_disc_err () {
        val out = inp2env("""
            var x: <()>
            set x = <.2>:<(),()>
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
    @Test
    fun c23_list_zero_err () {
        val out = inp2env("""
            var x: _int
            set x = <.0 [()]>:<()>
        """.trimIndent())
        //assert(out == "(ln 2, col 7): invalid assignment : type mismatch") { out }
        assert(out == "(ln 2, col 11): invalid constructor : type mismatch") { out }
    }
    @Test
    fun c23_list_zero_err2 () {
        val out = inp2env("""
            var x: < /^>
            set x = <.0 [()]>:<()>
        """.trimIndent())
        //assert(out == "(ln 2, col 7): invalid assignment : type mismatch") { out }
        assert(out == "(ln 2, col 11): invalid constructor : type mismatch") { out }
    }

    // POINTERS / SCOPE / @global

    @Test
    fun e01_ptr_block_err () {
        val out = inp2env("""
            var p1: /() @local
            var p2: /()
            {
                var v: ()
                set p1 = /v -- ERRO p1=0 < v=1
            }
            {
                var v: ()
                --set p2 = /v
            }
        """.trimIndent())
        assert(out == "(ln 5, col 12): invalid assignment : type mismatch") { out }
    }
    @Test
    fun e02_ptr_block_err () {
        val out = inp2env("""
            var x: ()
            var p: /() @local
            {
                var y: ()
                set p = /x   -- ok
                set p = /y   -- no
            }
        """.trimIndent())
        assert(out == "(ln 6, col 11): invalid assignment : type mismatch") { out }
    }
    @Test
    fun e03_ptr_err () {
        val out = inp2env("""
            var pout: /_int
            {
                var pin: /_int @local
                set pout = pin  -- no
            }
        """.trimIndent())
        assert(out == "(ln 4, col 14): invalid assignment : type mismatch") { out }
    }
    @Test
    fun e03_ptr_ok () {
        val out = inp2env("""
            var pout: /_int
            {
                var pin: /_int @global
                set pout = pin
            }
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun e04_ptr_ok () {
        val out = inp2env("""
            var pout: /_int @local
            {
                var pin: /_int
                set pin = pout
            }
        """.trimIndent())
        assert(out == "OK")
    }
    @Test
    fun e05_ptr_ok () {
        val out = inp2env("""
            { @a
                var pout: /_int @local
                {
                    var pin: /_int @a
                    set pout = pin  -- ok
                }
            }
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun e06_ptr_scope_err () {
        val out = inp2env("""
            var pin: /_int @a
        """.trimIndent())
        assert(out == "(ln 1, col 16): undeclared scope \"@a\"") { out }
    }
    @Test
    fun e07_ptr_ok () {
        val out = inp2env("""
            { @a
                var pa: /_int
                var f: / ({@a}()->()) @local
                set f = func {@a}()->() {
                    var pf: /_int @a
                    set pa = pf
                }
            }
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun e07_ptr_err () {
        val out = inp2env("""
            { @a
                var pa: /_int @local
                var f: /({}()->())
                set f = func {}()->() {
                    var pf: /_int @a
                    set pa = pf
                }
            }
        """.trimIndent())
        assert(out == "(ln 6, col 13): invalid access to \"pa\" : invalid closure declaration (ln 4)") { out }
    }
    @Test
    fun e07_ptr_err2 () {
        val out = inp2env("""
            var f: {}()->()
            { @a
                var pa: ()
                set pa = ()
                set f = func {@a}()->() {  -- set [] vs [@a]
                    output std pa
                }
            }
            call f()
        """.trimIndent())
        assert(out == "(ln 5, col 11): invalid assignment : type mismatch") { out }
    }
    @Test
    fun e07_ptr_err3 () {
        val out = inp2env("""
            var f: {@a}()->()
        """.trimIndent())
        assert(out == "(ln 1, col 9): undeclared scope \"@a\"") { out }
    }
    @Test
    fun e08_ptr_ok () {
        val out = inp2env("""
            var f: / ({}[@a_1,/()@a_1]->())
            set f = func {}[@a_1,/()@a_1]->() {
                var pf: /_int @a_1
                set pf = arg.2
            }
            {
                var x: ()
                call f\ [@local,/x]
            }
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun e09_ptr_err () {
        val out = inp2env("""
            { @a
                var pa: /_int @local
                var f: {@a}[@_1,/()@_1]->()
                set f = func {@a}[@_1,/()@_1]->() {
                    var pf: /_int @_1
                    set pa = arg.2
                }
                {
                    var x: ()
                    call f [@local,/x]
                }
            }
        """.trimIndent())
        assert(out == "(ln 6, col 16): invalid assignment : type mismatch") { out }
    }
    @Test
    fun e10_func_err () {
        val out = inp2env("""
            var f: {}[@a_1,/()@a_1]->()
            set f = func {}[@b_1,/()@b_1]->() {}
        """.trimIndent())
        assert(out == "(ln 2, col 7): invalid assignment : type mismatch") { out }
        //assert(out == "OK") { out }
    }
    @Test
    fun e11_func_err () {
        val out = inp2env("""
            var f: {}[@a_1,/()@a_1]->()
            set f = func {}[@a_2,/()@a_2]->() {}
        """.trimIndent())
        //assert(out == "(ln 2, col 7): invalid assignment : type mismatch") { out }
        assert(out == "(ln 2, col 30): invalid function type : pool arguments are not continuous") { out }
        //assert(out == "OK") { out }
    }
    @Test
    fun e12_func_ok () {
        val out = inp2env("""
            var f: / ({}[@a_1,@a_2,/()@a_1,/()@a_2]->())
            set f = func {}[@a_1,@a_2,/()@a_1,/()@a_2]->() {}
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun e12_call_err () {
        val out = inp2env("""
            var f: / ({}[@a_1,@a_2,/()@a_1,/()@a_2]->())
            set f = func {}[@a_1,@a_2,/()@a_1,/()@a_2]->() {}
            { @a
                var x: ()
                {
                    var y: ()
                    call f\ [@local,@a,/y,/x]  -- err
                }
            }
        """.trimIndent())
        //assert(out == "(ln 7, col 14): invalid call : type mismatch") { out }
        assert(out == "(ln 7, col 25): invalid call : incompatible scopes") { out }

    }
    @Test
    fun e13_tuple_ok () {
        val out = inp2env("""
            { @a
                var x: ()
                { @b
                    var y: ()
                    var ps: [/()@a,/()@b]
                    set ps = [/x,/y]
                }
            }
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun e13_tuple_err () {
        val out = inp2env("""
            { @a
                var x: ()
                { @b
                    var y: ()
                    var ps: [/()@a,/()@b]
                    set ps = [/y,/x]
                }
            }
        """.trimIndent())
        assert(out == "(ln 6, col 16): invalid assignment : type mismatch") { out }
    }
    @Test
    fun e13_call_ok () {
        val out = inp2env("""
            var f: / ({}[@_1,/()@_1]->/()@_1)
            set f = func {}[@_1,/()@_1]->/()@_1 {}
            { @aaa
                var x: /()
                {
                    var y: /() @local
                    set x = call f\ [@aaa,x]: @aaa -- ok
                }
            }
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun e14_call_err () {
        val out = inp2env("""
            var f: /({}[@_1,/()@_1]->/()@_1)
            set f = func {}[@_1,/()@_1]->/()@_1 {}
            { @aaa
                var x: /() @local
                {
                    var y: /()
                    set x = call f\ [@local,y]: @aaa -- err
                }
            }
        """.trimIndent())
        //assert(out == "(ln 7, col 22): invalid call : type mismatch") { out }
        assert(out == "(ln 7, col 17): invalid call : incompatible scopes") { out }
    }
    @Test
    fun e15_call_err () {
        val out = inp2env("""
            var f: /({}[@a_1,@a_2,/()@a_1]->/()@a_2)
            set f = func {}[@a_1,@a_2,/()@a_1]->/()@a_2 {}
            { @aaa
                var x: /() @local
                {
                    var y: /()
                    set x = call f\ [@local,@aaa,y]: @aaa  -- err
                }
            }
        """.trimIndent())
        //assert(out == "(ln 7, col 15): invalid assignment : type mismatch") { out }
        //assert(out == "(ln 7, col 22): invalid call : type mismatch") { out }
        assert(out == "(ln 7, col 33): invalid call : incompatible scopes") { out }
    }
    @Test
    fun e16_call_ok () {
        val out = inp2env("""
            var f:     /({}[@_2,@_1,/()@_2]->/()@_1)
            set f = func {}[@_2,@_1,/()@_2]->/()@_1 {}
            { @aaa
                var x: /() @local
                {
                    var y: /() @local
                    set x = call f\ [@local,@aaa,y]: @aaa  -- ok
                }
            }
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun e17_call_ok () {
        val out = inp2env("""
            var f: /(    {}[@a_1,@a_2,/()@a_1,/()@a_2]->() )
            set f = func {}[@a_1,@a_2,/()@a_1,/()@a_2]->() { }
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun e18_call_err () {
        val out = inp2env("""
            var f:       {}[@a_1,@a_2,/()@a_2,/()@a_1]->()
            set f = func {}[@a_1,@a_2,/()@a_1,/()@a_2]->() { }   -- err
        """.trimIndent())
        assert(out == "(ln 2, col 7): invalid assignment : type mismatch") { out }
    }
    @Test
    fun e19_call_ok () {
        val out = inp2env("""
            var f: / ({}[@a_2,@a_1,/()@a_2]->/()@a_1)
            set f = func {}[@a_2,@a_1,/()@a_2]->/()@a_1 {}
            { @a
                var x: /()
                {
                    var y: /() @local
                    set y = call f\ [@local,@a,y]: @a  -- ok
                }
            }
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun e19_call_err () {
        val out = inp2env("""
            var f: /({}[@a_2,@a_1,/()@a_2]->/()@a_1)
            set f = func {}[@a_2,@a_1,/()@a_2]->/()@a_1 {}
            { @a
                var x: /() @local
                {
                    var y: /()
                    set y = call f\ [@local,@a,y]: @local -- no @local=@2
                }
            }
        """.trimIndent())
        assert(out == "(ln 7, col 17): invalid call : incompatible scopes") { out }
    }
    @Test
    fun e19_call_err2 () {
        val out = inp2env("""
            var f: /({}[@a_2,@a_1,/()@a_2]->/()@a_1)
            set f = func {}[@a_2,@a_1,/()@a_2]->/()@a_1 {}
            {
                var x: /() @local
                {
                    var y: /() @local
                    set y = call f\ [@local,@local,y]  -- no
                }
            }
        """.trimIndent())
        //assert(out == "(ln 7, col 22): invalid call : type mismatch") { out }
        assert(out == "OK") { out }
    }
    @Test
    fun e19_call_ok2 () {
        val out = inp2env("""
            var f: /({}[@a_1,/()@a_1,()]->/()@a_1)
            set f = func {}[@a_1,/()@a_1,()]->/()@a_1 {}
            var y: /()
            set y = call f\ [@local,y,()]: @local  -- ok
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun e20_arg_ok () {
        val out = inp2env("""
            var f: /({}[@_1,/()@_1]->/()@_1)
            set f = func {}[@_1,/()@_1]->/()@_1 { return arg.2 }
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun e20_arg_ok2 () {
        val out = inp2env("""
            var f: /({}[@a_1,@a_2,/()@a_1]->/()@a_2)
            set f = func {}[@a_1,@a_2,/()@a_1]->/()@a_2 { return arg.3 }
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun e20_arg_err () {
        val out = inp2env("""
            var f: /({}[@a_1,@a_2,/()@a_2]->/()@a_1)
            set f = func {}[@a_1,@a_2,/()@a_2]->/()@a_1 {
                return arg.3
            }
        """.trimIndent())
        assert(out == "(ln 3, col 5): invalid return : type mismatch") { out }
    }
    @Test
    fun e21_local_err () {
        val out = inp2env("""
            var f: /({}[@_1,/()@_1]->/()@_1)
            set f = func {}[@_1,/()@_1]->/()@_1 {
                {
                    var x: /() @local
                    set x = arg.2
                    return x    -- err
                }
            }
        """.trimIndent())
        assert(out == "(ln 6, col 9): invalid return : type mismatch") { out }
    }
    @Test
    fun e22_local_ok () {
        val out = inp2env("""
            var f:/( {}[@_1,/()@_1]->/()@_1)
            set f = func {}[@_1,/()@_1]->/()@_1 {
                {
                    var x: /() @local
                    set x = arg.2
                    return arg.2   -- ok
                }
            }
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun e23_union_ok () {
        val out = inp2env("""
            {
                var x: /</^ @local>
                var y: /</^> @local
                set x\!1 = y
            }
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun e24_err () {
        val out = inp2env("""
            var x: /</^ @local> @local
            output std [x,x]
        """.trimIndent())
        assert(out == "OK") { out }
    }

    // POINTERS - DOUBLE

    @Test
    fun f01_ptr_ptr_err () {
        val out = inp2env("""
            var p: //_int
            {
                var y: /_int @local
                set p = /y
            }
        """.trimIndent())
        assert(out == "(ln 4, col 11): invalid assignment : type mismatch") { out }
    }
    @Test
    fun f02_ptr_ptr_ok () {
        val out = inp2env("""
            var p: //_int @local @local
            var z: _int
            set z = _10
            var y: /_int @local
            set y = /z
            set p = /y
            output std p\\
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun f03_ptr_ptr_err () {
        val out = inp2env("""
            var p: //_int @local @local
            {
                var z: _int; set z = _10
                var y: /_int @local; set y = /z
                set p = /y
            }
        """.trimIndent())
        assert(out == "(ln 5, col 11): invalid assignment : type mismatch") { out }
    }
    @Test
    fun f04_ptr_ptr_err () {
        val out = inp2env("""
            var p: /_int @local
            {
                var x: _int; set x = _10
                var y: /_int @local; set y = /x
                var z: //_int @local @local; set z = /y
                set p = z\
            }
        """.trimIndent())
        assert(out == "(ln 6, col 11): invalid assignment : type mismatch") { out }
    }

    // POINTERS - FUNC - CALL

    @Test
    fun g01_ptr_func_ok () {
        val out = inp2env("""
            var f : / (({}[@_1,/_int@_1] -> /_int@_1))
            set f = func ({}[@_1,/_int@_1] -> /_int@_1) {
                return arg.2
            }
            var v: _int
            set v = _10
            var p: /_int @local
            set p = f\ [@local,/v]
            output std p\
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun g02_ptr_func_err () {
        val out = inp2env("""
            var v: _int
            set v = _10
            var f : / ({}@_1 -> /_int@_1)
            set f = func {}@_1 -> /_int@_1 {
                return /v
            }
            var p: /_int @local
            set p = f\ @local: @local
            output std p\
        """.trimIndent())
        //assert(out == "(ln 3, col 13): undeclared variable \"v\"") { out }
        assert(out == "OK") { out }
    }
    @Test
    fun g02_ptr_func_ok () {
        val out = inp2env("""
            var v: _int
            set v = _10
            var f : / ({} (@_1) -> /_int@_1 )
            set f = func {}(@_1) -> /_int@_1 {
                return /v
            }
            var p: /_int @local
            set p = f\ (@local)
            output std p\
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun g03_ptr_func_err () {
        val out = inp2env("""
            var f : {}@_1 -> /_int@_1; set f = func {}@_1 -> /_int@_1 {
                var v: _int; set v = _10
                return /v
            }
        """.trimIndent())
        assert(out == "(ln 3, col 5): invalid return : type mismatch") { out }
    }
    @Test
    fun g04_ptr_func_err () {
        val out = inp2env("""
            var f : {}[@_1,/_int@_1] -> /_int@_1; set f = func ({}[@_1,/_int@_1] -> /_int@_1) {
                var ptr: /_int@local; set ptr = arg.2
                return ptr
            }
        """.trimIndent())
        assert(out == "(ln 3, col 5): invalid return : type mismatch") { out }
    }
    @Test
    fun g05_ptr_caret_ok () {
        val out = inp2env("""
            var f : / ({}[@_1,/_int@_1] -> /_int@_1)
            set f = func {}[@_1,/_int@_1] -> /_int@_1 {
                var ptr: /_int@_1
                set ptr = arg.2
                return ptr
            }
            var v: _int
            set v = _10
            var p: /_int @local
            set p = f\ [@local,/v]
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun g06_ptr_caret_err () {
        val out = inp2env("""
            var f : {}[@_1,/_int@_1] -> /_int@_1
            set f = func {}[@_1,/_int@_1] -> /_int@_1 {
                var x: _int
                set x = _10
                var ptr: /_int @_1
                set ptr = /x
                return ptr
            }
        """.trimIndent())
        assert(out == "(ln 6, col 13): invalid assignment : type mismatch") { out }
    }

    @Test
    fun g08_ptr_arg_err () {
        val out = inp2env("""
            var f: {}[@_1,_int] -> /_int@_1; set f = func {}[@_1,_int] -> /_int@_1
            {
                return /arg.2
            }
        """.trimIndent())
        assert(out == "(ln 3, col 5): invalid return : type mismatch") { out }
    }
    @Test
    fun g08_ptr_arg_err2 () {
        val out = inp2env("""
            var f: {}[@_1,_int] -> /_int@_1; set f = func{} [@_1,_int] -> /_int@_1
            {
                return /arg.2
            }
        """.trimIndent())
        assert(out == "(ln 3, col 5): invalid return : type mismatch") { out }
    }
    @Test
    fun g09_ptr_arg_err () {
        val out = inp2env("""
            var f: {}(@_1) -> /() @_1
            set f = func {}(@_1) -> /() @_1 {
                var ptr: /() @_1
                set ptr = arg   -- err: type mismatch
                return ptr
            }
        """.trimIndent())
        assert(out == "(ln 4, col 13): invalid assignment : type mismatch") { out }
    }
    @Test
    fun g10_ptr_out_err () {
        val out = inp2env("""
            var f: {}[@_1,/_int@_1] -> //_int@_1@_1
            set f = func {}[@_1,/_int@_1] -> _int {
                var ptr: /_int@global
                set ptr = arg
                return /ptr
            }
        """.trimIndent())
        assert(out == "(ln 4, col 13): invalid assignment : type mismatch") { out }
    }
    @Test
    fun g11_ptr_func_err () {
        val out = inp2env("""
            var p: /() @global
            { @a
                var v: ()
                var f : {@a}(@_1) -> /()@_1
                set f = func {@a}(@_1) -> /()@_1 {
                    return /v      -- err: /v may not be at expected @
                }
                {
                    --set p = f ()    -- p=@ is smaller than /v
                }
            }
        """.trimIndent())
        assert(out == "(ln 6, col 9): invalid return : type mismatch") { out }
    }
    @Test
    fun g11_ptr_func_ok () {
        val out = inp2env("""
            { @a
                var v: _int
                set v = _10
                var f : / ({@a} (@a_1) -> /_int@a_1 )
                set f = func {@a}(@a_1) -> /_int@a_1 {
                    return /v
                }
                var p: /_int @local
                {
                    set p = f\ (@a): @a
                }
            }
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun g13_ptr_func () {
        val out = inp2env("""
            var v: /_int @local
            var f : {}[@_1,/_int@_1] -> ()
            set f = func{} [@_1,/_int@_1] -> () {
                set v = arg.2
            }
        """.trimIndent())
        assert(out == "(ln 4, col 11): invalid assignment : type mismatch") { out }
    }
    @Test
    fun g14_ptr_func_err () {
        val out = inp2env("""
            var f : /({}[@_1,/()@_1] -> /()@_1)
            set f = func{} [@_1,/()@_1] -> /()@_1 {
                return arg.2
            }
            var p: /() @local
            {
                var x: /() @local
                set p = f\ [@local,x]: @global    -- err: call p/x have diff scopes (@ will be x which is greater)
            }
        """.trimIndent())
        assert(out == "(ln 8, col 11): invalid call : incompatible scopes") { out }
        //assert(out == "(ln 8, col 13): invalid call : type mismatch") { out }
        //assert(out == "(ln 8, col 11): invalid assignment : type mismatch") { out }
    }
    @Test
    fun g15_ptr_func_ok () {
        val out = inp2env("""
            var f : /({}[@_1,/()@_1] -> /()@_1)
            set f = func {}[@_1,/()@_1] -> /()@_1 {
                return arg.2
            }
            var p: /() @local
            {
                var x: /() @local
                set x = f\ [@global,p]: @global -- ok: call p/x have diff scopes (@ will be x which is greater)
            }
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun g16_ptr_func_ok () {
        val out = inp2env("""
            var f : / ({}[@a_1,@a_2,/()@a_1] -> /()@a_2)
            set f = func {}[@a_1,@a_2,/()@a_1] -> /()@a_2 {
                return arg.3
            }
            var p: /() @local
            {
                var x: /() @local
                set x = f\ [@global,@local,p]: @local -- ok: call p/x have diff scopes (@1 will be x which is greater)
            }
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun g17_ptr_func_err () {
        val out = inp2env("""
            var f :{} [@a_1,@a_2,/()@a_2] -> /()@a_1
            set f = func {}[@a_1,@a_2,/()@a_2] -> /()@a_1 {
                return arg.3     -- err
            }
            var p: /() @local
            {
                var x: /() @local
                set x = f [@local,@global,p]
            }
        """.trimIndent())
        assert(out == "(ln 3, col 5): invalid return : type mismatch") { out }
    }
    @Test
    fun g18_ptr_func_err () {
        val out = inp2env("""
            var f : /({}[@a_1,@a_2,/()@a_1] -> /()@a_2)
            set f = func {}[@a_1,@a_2,/()@a_1] -> /()@a_2 {
                return arg.3
            }
            var p: /() @local
            {
                var x: /() @local
                set p = f\ [@local,@global,x]     -- err: @2=p <= @1=x (false) 
            }
        """.trimIndent())
        assert(out == "(ln 8, col 24): invalid call : incompatible scopes") { out }
        //assert(out == "(ln 8, col 13): invalid call : type mismatch") { out }
        //assert(out == "(ln 8, col 11): invalid assignment : type mismatch") { out }
    }

    // POINTERS - TUPLE - TYPE

    @Test
    fun h01_ptr_tuple_err () {
        val out = inp2env("""
            var p: /[_int,_int] @local
            {
                var y: [_int,_int]; set y = [_10,_20]
                set p = /y
            }
        """.trimIndent())
        assert(out == "(ln 4, col 11): invalid assignment : type mismatch") { out }
    }
    @Test
    fun h02_ptr_user_err1 () {
        val out = inp2env("""
            var p: /() @local
            {
                var y: [()]
                set p = /y.1
            }
        """.trimIndent())
        assert(out == "(ln 4, col 11): invalid assignment : type mismatch") { out }
    }
    @Test
    fun h02_ptr_user_err2 () {
        val out = inp2env("""
            var p: /() @local
            {
                var y: [()]
                set p = /y.1
            }
        """.trimIndent())
        assert(out == "(ln 4, col 11): invalid assignment : type mismatch") { out }
    }
    @Test
    fun h03_ptr_tup () {
        val out = inp2env("""
            var v: [_int,_int]; set v = [_10,_20]
            var p: /_int @local
            set p = /v.1
            set p\ = _20
            output std v
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun h04_ptr_tup_err () {
        val out = inp2env("""
            var p: /_int @local
            {
                var v: [_int,_int]; set v = [_10,_20]
                set p = /v.1
            }
        """.trimIndent())
        assert(out == "(ln 4, col 11): invalid assignment : type mismatch") { out }
    }
    @Test
    fun h05_ptr_type_err () {
        val out = inp2env("""
            var p: /() @local
            {
                var v: [()]
                set p = /v.1
            }
        """.trimIndent())
        assert(out == "(ln 4, col 11): invalid assignment : type mismatch") { out }
    }
    @Test
    fun h06_ptr_tup_err () {
        val out = inp2env("""
            var p: [_int,/_int @local]
            {
                var v: _int
                set p.2 = /v     -- err
            }
        """.trimIndent())
        assert(out == "(ln 4, col 13): invalid assignment : type mismatch") { out }
    }
    @Test
    fun h07_ptr_tup_err () {
        val out = inp2env("""
            var p: [_int,/_int @local]
            {
                var v: _int
                set p = [_10,/v]
            }
        """.trimIndent())
        assert(out == "(ln 4, col 11): invalid assignment : type mismatch") { out }
    }
    @Test
    fun h08_ptr_type_err () {
        val out = inp2env("""
            var p: </_int @local>
            {
                var v: _int
                set p!1 = /v
            }
        """.trimIndent())
        assert(out == "(ln 4, col 13): invalid assignment : type mismatch") { out }
    }
    @Test
    fun h09_ptr_type_err1 () {
        val out = inp2env("""
            var p: </_int @local>
            {
                var v: _int
                set p = <.1 /v>: </_int @local>
            }
        """.trimIndent())
        assert(out == "(ln 4, col 11): invalid assignment : type mismatch") { out }
    }
    @Test
    fun h09_ptr_type_err2 () {
        val out = inp2env("""
            var p: </_int @local>
            {
                var v: _int
                set p = <.1 /v>: </_int @global>
            }
        """.trimIndent())
        assert(out == "(ln 4, col 15): invalid constructor : type mismatch") { out }
    }
    @Test
    fun h09_ptr_type_err3 () {
        val out = inp2env("""
            var p: </_int @local>
            {
                var v: _int
                { @aaa
                    set p = <.1 /v>: </_int @aaa>
                }
            }
        """.trimIndent())
        assert(out == "(ln 5, col 15): invalid assignment : type mismatch") { out }
    }
    @Test
    fun h10_ptr_tup_err () {
        val out = inp2env("""
            var x1: [_int,/_int @local]
            {
                var v: _int
                var x2: [_int,/_int @local]
                set x1 = x2
            }
        """.trimIndent())
        assert(out == "(ln 5, col 12): invalid assignment : type mismatch") { out }
    }
    @Test
    fun h11_ptr_type_err () {
        val out = inp2env("""
            var x1: </_int @local>
            {
                var v: _int
                var x2: </_int @local>
                set x1 = x2
            }
        """.trimIndent())
        assert(out == "(ln 5, col 12): invalid assignment : type mismatch") { out }
    }

    // TYPE - REC - REPLACE - CLONE - BORROW

    @Test
    fun i01_list_err () {
        val out = inp2env("""
            var p: /</^ @local> @local
            {
                var l: /</^ @local> @local
                set l = new <.1 (new <.1 <.0>:/</^@local> @local>:</^@local>)>:</^@local>
                set p = l   -- err: p<l
            }
            output std p
        """.trimIndent())
        assert(out == "(ln 5, col 11): invalid assignment : type mismatch") { out }
    }
    @Test
    fun i01_list_2 () {
        val out = inp2env("""
            var p: /</^ @local> @local
            {
                var l: [/</^ @local> @local]
                set l = [new <.1 (new <.1 <.0>:/</^@local>@local>:</^@local>: @local)>:</^@local>: @local]
                set p = /l.1
            }
            output std p
        """.trimIndent())
        assert(out == "(ln 5, col 11): invalid assignment : type mismatch") { out }
    }
    @Test
    fun i02_list () {
        val out = inp2env("""
            var p: /</^ @local> @local
            {
                var l: [/</^ @local> @local]
                set l = [new <.1 (new <.1 <.0>:/</^@local>@local>:</^@local>)>:</^@local>: @local]
                set p = l.1     -- err: p<l
            }
            output std p
        """.trimIndent())
        assert(out == "(ln 5, col 11): invalid assignment : type mismatch") { out }
    }
    @Test
    fun i03_list () {
        val out = inp2env("""
            var ret: <[_int,</^ @local>]>
            set ret = <.1>:<()>
        """.trimIndent())
        assert(out == "(ln 2, col 9): invalid assignment : type mismatch") { out }
    }
    @Test
    fun i04_uni_rec_err () {
        val out = inp2env("""
            var ret: <(),</^^ @local,/^ @local>>
            set ret  = <.1>:<(),</^^ @local,/^ @local>>
        """.trimIndent())
        //assert(out == "(ln 2, col 14): invalid constructor : expected `new`") { out }
        assert(out == "OK") { out }
    }
    @Test
    fun i05_uni_rec_ok () {
        val out = inp2env("""
            var ret: /<(),</^^ @local,/^ @local>> @local
            set ret = new <.1>:<(),</^^ @local,/^ @local>>: @local
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun i06_uni_rec_ok1 () {
        val out = inp2env("""
            var ret: /<(),/</^^ @local,/^ @local> @local> @local
            set ret = new <.1>:<(),/</^^ @local,/^ @local> @local>
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Disabled // TODO: explicit type
    @Test
    fun i06_uni_rec_ok2 () {
        val out = inp2env("""
            var ret: /<(),/</^^ @local,/^ @local> @local> @local
            set ret =
                new <.2
                    new <.1
                        new <.1>
                    >
                >
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Disabled // TODO: explicit type
    @Test
    fun i06_uni_rec_ok3 () {
        val out = inp2env("""
            var ret: /<(),/</^^ @local,/^ @local> @local> @local
            set ret = new <.2 new <.2 new <.2 <.0>>: @local>>
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Disabled // TODO: explicit type
    @Test
    fun i06_uni_rec_ok4 () {
        val out = inp2env("""
            var ret: /<(),/</^^ @local,/^ @local> @local> @local
            set ret = new <.2 new <.1 new <.1>: @local>: @local>
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Disabled // TODO: explicit type
    @Test
    fun i06_uni_rec_ok5 () {
        val out = inp2env("""
            var ret: /<(),/</^^ @local,/^ @local> @local> @local
            set ret = new <.2 new <.2 new <.1 new <.1>>>>
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Disabled // TODO: explicit type
    @Test
    fun i06_uni_rec_ok6 () {
        val out = inp2env("""
            var ret: /<(),/</^^ @local,/^ @local> @local> @local
            set ret = new <.2 new <.2 new <.1 new <.1>: @local>: @local>: @local> :@local
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Disabled // TODO: explicit type
    @Test
    fun i06_uni_rec_err2 () {
        val out = inp2env("""
            var ret: /<(),/</^^ @local,/^ @local> @local> @local
            set ret = new <.2 new <.1 <.1>>: @local>: @local
        """.trimIndent())
        assert(out == "(ln 2, col 9): invalid assignment : type mismatch") { out }
    }
    @Test
    fun i07_list_err () {
        val out = inp2env("""
            var ret: <[_int,</^ @local>]>
            set ret = <.0>:</^ @local>
        """.trimIndent())
        //assert(out == "(ln 2, col 9): invalid assignment : type mismatch") { out }
        assert(out == "(ln 2, col 13): invalid constructor : type mismatch") { out }
    }
    @Test
    fun i08_mutual () {
        val out = inp2env("""
            var e: /<(), <(),/^^ @local>> @local
            set e = new <.2 <.1>:<(),/<(), <(),/^^ @local>> @local>>:<(), <(),/^^ @local>>
            var s: /<(), <(),/^^ @local>> @local
            set s = e\!2!2
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun i09_mutual () {
        val out = inp2env("""
            var e: /<</^^ @local,()>, ()> @local
            set e = new <.1 <.2>:</<</^^ @local,()>, ()> @local,()>>:<</^^ @local,()>, ()>: @local
            var s: /<</^^ @local,()>, ()> @local
            set s = e\!1!1
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun i10_mutual () {
        val out = inp2env("""
            var e: /<</^^ @local,()>, ()> @local
            set e = new <.1 new <.2>:</<</^^ @local,()>, ()> @local,()>>:<</^^ @local,()>, ()>
               -- err: ~new~ <.2>
        """.trimIndent())
        //assert(out == "(ln 2, col 7): invalid assignment : type mismatch") { out }
        assert(out == "(ln 2, col 15): invalid constructor : type mismatch") { out }
    }

    // XEXPR

    @Test
    fun j01_rec_xepr_null_err () {
        val out = inp2env("""
            var x: </^ @local>
            var y: </^ @local>; set y = x
        """.trimIndent())
        //assert(out == "(ln 2, col 14): invalid expression : expected operation modifier") { out }
        assert(out == "OK") { out }
    }
    @Test
    fun j02_rec_xepr_move_ok () {
        val out = inp2env("""
            var x: </^ @local>
            var y: </^ @local>; set y = x
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun j02_rec_xepr_move_err () {
        val out = inp2env("""
            var x: </^ @local>
            var y: /</^ @local> @local
            set y = /x
        """.trimIndent())
        //assert(out == "(ln 2, col 23): expected expression : have `\\´") { out }
        assert(out == "OK") { out }
    }
    @Test
    fun j03_rec_xepr_borrow_err () {
        val out = inp2env("""
            var x: </^ @local>
            var y: </^ @local>
            set y = x
        """.trimIndent())
        //assert(out == "(ln 2, col 21): invalid `borrow` : expected pointer to recursive variable") { out }
        assert(out == "OK") { out }
    }
    @Test
    fun j04_rec_xepr_copy_err () {
        val out = inp2env("""
            var x: _int; set x = _10
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun j05_rec_xepr_borrow_ok () {
        val out = inp2env("""
            var x: </^ @local>
            var y: /</^ @local> @local
            set y = /x
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun j06_rec_xepr_borrow_err () {
        val out = inp2env("""
            var x: _int
            var y: /_int @local
            set y = /x
        """.trimIndent())
        //assert(out == "(ln 2, col 23): invalid `borrow` : expected pointer to recursive variable") { out }
        assert(out == "OK") { out }
    }
    @Test
    fun j07_rec_xepr_copy_err () {
        val out = inp2env("""
            var x: </^ @local>
            set x = <.1 <.0>:/</^ @local>@local>:</^ @local>
        """.trimIndent())
        //assert(out == "(ln 1, col 20): expected expression : have `<´") { out }
        //assert(out == "(ln 2, col 11): invalid constructor : expected `new`") { out }
        //assert(out == "(ln 1, col 20): invalid `copy` : expected recursive variable")
        //assert(out == "(ln 1, col 5): invalid assignment : expected `new` operation modifier")
        assert(out == "OK") { out }
    }
    @Test
    fun j08_rec_xepr_double_rec_err () {
        val out = inp2env("""
            var x: <</^^^ @local>>
            --set x = <.1 <.1 <.0>>>
        """.trimIndent())
        assert(out == "(ln 1, col 11): invalid `^^^´ : missing enclosing recursive type") { out }
    }
    @Test
    fun j10_rec_tup () {
        val out = inp2env("""
            var x: /<(),[/^ @local]> @local
            set x = new <.2 [new <.1>:<(),[/^ @local]> ]>:<(),[/^ @local]>
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Disabled // TODO: explicit type
    @Test
    fun j10_rec_xepr_double_rec () {
        val out = inp2env("""
            var x: /</<[/^^ @local,/^ @local]> @local> @local
            set x = new <.1 new <.1 [<.0>,<.0>]>: @local>: @local
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun j11_rec_xepr_double_rec () {
        val out = inp2env("""
            var x: /</<[/^^ @local,/^ @local]> @local> @local
            var z1: /</<[/^^ @local,/^ @local]> @local> @local
            var z2: /<[/</<[/^^ @local,/^ @local]> @local> @local,/^ @local]> @local
            set z1 = <.0>: /</<[/^^ @local,/^ @local]> @local> @local
            set z2 = <.0>: /<[/</<[/^^ @local,/^ @local]> @local> @local,/^ @local]> @local
            set x = new <.1 new <.1 [z1,z2]>:<[/</<[/^^ @local,/^ @local]> @local> @local,/^ @local]>: @local>:</<[/^^ @local,/^ @local]> @local>: @local
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun j11_rec_xepr_double_rec2 () {
        val out = inp2env("""
            var x: /<[/^ @local]> @local
            set x = new <.1 [<.0>:/<[/^ @local]> @local]>:<[/^ @local]>: @local
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun j11_rec_xepr_double_rec3 () {
        val out = inp2env("""
            var x: /<[/^ @local,/^ @local]> @local
            set x = new <.1 [<.0>:/<[/^ @local,/^ @local]> @local,<.0>:/<[/^ @local,/^ @local]> @local]>:<[/^ @local,/^ @local]>: @local
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Disabled // TODO: explicit type
    @Test
    fun j11_rec_xepr_double_rec4 () {
        val out = inp2env("""
            var x: /</<[/^^ @local,/^ @local]> @local> @local
            set x = new <.1 new <.1 [<.0>,new <.1 [<.0>,<.0>]>: @local]>: @local>: @local
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Disabled // TODO: explicit type
    @Test
    fun j11_rec_xepr_double_rec5 () {
        val out = inp2env("""
            var x: /</<[/^^ @local,/^ @local]> @local> @local
            set x = new <.1 new <.1 [<.0>,<.0>]>: @local>: @local
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Disabled // TODO: explicit type
    @Test
    fun j11_rec_xepr_double_rec6 () {
        val out = inp2env("""
            var x: /</<[/^^ @local,/^ @local]> @local> @local
            set x = new <.1 new <.1 [<.0>,new <.1 [_,<.0>]> @local]> @local> @local
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Disabled // TODO: explicit type
    @Test
    fun j12_rec_xepr_double_rec_err () {
        val out = inp2env("""
            var x: /</<[/^^ @local,/^ @local]> @local> @local
            set x = new <.1 <.1 [<.0>,<.0>]>> @local
        """.trimIndent())
        assert(out == "(ln 2, col 7): invalid assignment : type mismatch") { out }
    }
    @Test
    fun j13_tup_move_no () {
        val out = inp2env("""
            var t1: [/</^ @local> @local]
            set t1 = [<.0>:/</^ @local> @local]
            var t2: [/</^ @local> @local]
            set t2 = t1
        """.trimIndent())
        //assert(out == "(ln 2, col 26): invalid `replace` : expected recursive variable") { out }
        assert(out == "OK") { out }
    }
    @Test
    fun j13_tup_move_ok () {
        val out = inp2env("""
            var l: /</^ @local> @local
            set l = <.0>:/</^ @local> @local
            var t1: [/</^ @local> @local]
            set t1 = [l]
            var t2: [/</^ @local> @local]
            set t2 = [t1.1]
        """.trimIndent())
        //assert(out == "(ln 3, col 17): invalid `replace` : expected recursive variable")
        assert(out == "OK") { out }
    }
    @Test
    fun j14_new_no () {
        val out = inp2env("""
            var l: /</^ @local> @local
            set l = new <.0>:/</^ @local> @local
        """.trimIndent())
        assert(out == "(ln 2, col 9): invalid `new` : expected constructor") { out }
        //assert(out == "(ln 2, col 9): invalid `new` : unexpected <.0>") { out }
        //assert(out == "(ln 2, col 7): invalid assignment : type mismatch") { out }
    }
    @Test
    fun j15_rec_xepr_borrow_err () {
        val out = inp2env("""
            var x: [()]
            var y: /_int @local
            set y = /x
        """.trimIndent())
        //assert(out == "(ln 2, col 23): invalid `borrow` : expected pointer to recursive variable") { out }
        assert(out == "OK") { out }
    }
    @Test
    fun j16_rec_xepr_borrow_err () {
        val out = inp2env("""
            var x: <()>
            var y: /_int @local
            set y = /x
        """.trimIndent())
        //assert(out == "(ln 2, col 16): invalid `borrow` : expected pointer to recursive variable")
        assert(out == "OK") { out }
    }
    @Test
    fun j17_rec_xepr_borrow_ok () {
        val out = inp2env("""
            var x: /<(),/^ @local> @local
            var y: /_int @local
            set y = /x
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun j18_rec_xepr_borrow_ok () {
        val out = inp2env("""
            var x: [/</^ @local> @local]
            var y: /_int @local
            set y = /x
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun j19_rec_xepr_borrow_ok () {
        val out = inp2env("""
            var x: [/</^ @local> @local]
            var y: /_int @local
            set y = /x
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun j20_rec_xepr_borrow_err () {
        val out = inp2env("""
            var x: [/</^ @local> @local]
            var f: / ({}[@_1, //</^@_1>@_1@_1] -> ())
            set f = func {}[@_1, //</^@_1>@_1@_1] -> ()
            {
                output std arg.2
            }
            call f\ [@local,/x.1]
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun j21_rec_xepr_borrow_err () {
        val out = inp2env("""
            var x: [/</^ @local> @local]
            var y: //</^ @local> @local @local
            set y = /x.1
        """.trimIndent())
        //assert(out == "(ln 2, col 15): invalid expression : expected `borrow` operation modifier") { out }
        assert(out == "OK") { out }
    }
    @Test
    fun j23_rec_xexpr_move_err () {
        val out = inp2env("""
            var x: /</^ @local> @local
            var y: //</^ @local> @local @local
            set y = /x --!1
            var z: /</^ @local> @local
            set z = y\
        """.trimIndent())
        assert(out == "OK") { out }
        //assert(out == "(ln 3, col 22): invalid `consume` : expected recursive variable")
    }
    @Test
    fun j23_rec_xexpr_move_err2 () {
        val out = inp2env("""
            var x: /</^ @local> @local
            var y: //</^ @local> @local @local
            set y = /x --!1
            var z: /</^ @local> @local
            set z = y\
        """.trimIndent())
        //assert(out == "(ln 3, col 25): expected expression : have end of file") { out }
        assert(out == "OK") { out }
    }
    @Test
    fun j23_rec_xexpr_move_err3 () {
        val out = inp2env("""
            var x: /</^ @local> @local
            var y: //</^ @local> @local @local
            set y = /x --!1
            var z: /</^ @local> @local
            set z = y\
        """.trimIndent())
        //assert(out == "(ln 3, col 24): expected `=´ : have end of file") { out }
        assert(out == "OK") { out }
    }
    @Test
    fun j24_rec_xepr_borrow_ok () {
        val out = inp2env("""
            var x: [/</^ @local> @local]
            var f: /({}[@_1, //</^@_1>@_1@_1] -> ())
            set f = func {}[@_1, //</^@_1>@_1@_1] -> ()
            {
                output std arg.2
            }
            call f\ [@local,/x.1]
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun j25_new_no () {
        val out = inp2env("""
            var l: /</^ @local> @local
            set l = new <.1 _>:</^ @local>: @local
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun j26_func_move () {
        val out = inp2env("""
            var f: /({}(@_1)->/</^@_1>@_1)
            set f = func {}(@_1)->/</^@_1>@_1 {
                return new <.1 <.0>:/</^@_1>@_1>:</^@_1>: @_1
            }
            var v: /</^ @local> @local
            set v = f \ (@local)
        """.trimIndent())
        //assert(out == "(ln 4, col 20): invalid `replace` : expected recursive variable")
        //assert(out == "(ln 4, col 25): expected `=´ : have `()´") { out }
        assert(out == "OK") { out }
    }
    @Test
    fun j27_f_rec () {
        val out = inp2env("""
            var f: /({} (@_1)->/</^@_1>@_1 )
            set f = func {}(@_1)->/</^@_1>@_1 {
                return new <.1 <.0>:/</^@_1>@_1>:</^@_1>: @_1
            }
            var v: /</^ @local> @local
            set v = call f\ (@local)
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun j28_nonrec_nullable () {
        val out = inp2env("""
            var l: <()>
        """.trimIndent())
        //assert(out == "(ln 1, col 8): invalid type declaration : unexpected `?´") { out }
        assert(out == "OK") { out }
    }
    @Test
    fun j29_rec_mutual () {
        val out = inp2env("""
            var e: /<(),<(),/^^ @local>> @local
            set e = new <.2 <.1>:<(),/<(),<(),/^^ @local>> @local>>:<(),<(),/^^ @local>>: @local
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun j30_rec_mutual_err () {
        val out = inp2env("""
            var e: <(),/<(),/^ @local> @local>
            set e = new <.2 new <.1>:<(),/^ @local>: @local>:<(),/<(),/^ @local> @local>: @local
        """.trimIndent())
        //assert(out == "(ln 1, col 29): unexpected `new` : expected recursive type") { out }
        assert(out == "(ln 2, col 7): invalid assignment : type mismatch") { out }
    }
    @Disabled // TODO: explicit type
    @Test
    fun j31_rec_mutual () {
        val out = inp2env("""
            var e: /<(),<(),/^^ @local>> @local
            set e = new <.2 <.2 new <.1> @local>> @local
            var s: <(),/<(),<(),/^^ @local>> @local>
            set s = <.2 e>
        """.trimIndent())
        assert(out == "OK") { out }
    }

    // IF / FUNC

    @Test
    fun k01_if () {
        val out = inp2env("""
            if () {} else {}
        """.trimIndent())
        assert(out == "(ln 1, col 1): invalid condition : type mismatch") { out }
    }
    @Test
    fun k02_func_arg () {
        val out = inp2env("""
            var f1: /({}()->())
            set f1 = func {}()->() {
                var f2: /({}()->())
                set f2 = func {}()->() {
                }
            }
        """.trimIndent())
        assert(out == "OK") { out }
    }

    // BORROW / CONSUME

    @Test
    fun l01_borrow_err () {
        val out = inp2env("""
            var x: /</^ @local> @local
            var y: //</^ @local> @local @local
            set y = /x --!1
            set x = <.0>:/</^ @local> @local
        """.trimIndent())
        //assert(out == "(ln 3, col 7): invalid assignment of \"x\" : borrowed in line 2") { out }
        assert(out == "OK") { out }
    }
    @Test
    fun l02_borrow_err () {
        val out = inp2env("""
            var x: /</^ @local> @local
            var y: //</^ @local> @local @local
            set y = /x --!1
            var z: /</^ @local> @local
            set z = x
        """.trimIndent())
        //assert(out == "(ln 3, col 22): invalid operation on \"x\" : borrowed in line 2") { out }
        assert(out == "OK") { out }
    }
    @Test
    fun l02_borrow_func_err () {
        val out = inp2env("""
            var f: /({}()->())
            set f = func {}()->() {
                var x: /</^ @local> @local
                var y: //</^ @local> @local @local
                --set y = /x --!1
                --var z: <^>
                --set z = x
            }
        """.trimIndent())
        //assert(out == "(ln 4, col 26): invalid operation on \"x\" : borrowed in line 3") { out }
        assert(out == "OK") { out }
    }
    @Test
    fun l02_borrow_ok () {
        val out = inp2env("""
            var x: /</^ @local> @local
            {
                var y: //</^ @local> @local @local
                set y = /x --!1
            }
            var z: /</^ @local> @local
            set z = x
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun l03_borrow_err () {
        val out = inp2env("""
            var x: /</^ @local> @local
            var f: /({}[@_1, //</^@_1>@_1@_1] -> ())
            set f = func {}[@_1,//</^@_1>@_1@_1] -> ()
            {
                set x = <.0>:/</^@global>@global
            }
            call f\ [@local,/x] --!1
        """.trimIndent())
        //assert(out == "(ln 4, col 9): undeclared variable \"x\"") { out }
        //assert(out == "(ln 4, col 11): invalid assignment of \"x\" : borrowed in line 3") { out }
        assert(out == "OK") { out }
    }
    @Test
    fun l03_borrow_err2 () {
        val out = inp2env("""
            var x: /</^ @local> @local
            var f: /({}[@_1,//</^@_1>@_1@_1] -> ())
            set f = func {}[@_1,//</^@_1>@_1@_1] -> ()
            {
                set x = <.0>:/</^@global>@global
            }
            var y: //</^ @local> @local @local
            set y = /x --!1
            call f \[@local,y]
        """.trimIndent())
        assert(out == "OK") { out }
        //assert(out == "(ln 4, col 9): undeclared variable \"x\"") { out }
        //assert(out == "(ln 4, col 11): invalid assignment of \"x\" : borrowed in line 6") { out }
    }
    @Test
    fun l04_borrow_err () {
        val out = inp2env("""
            var x: /</^ @local> @local
            var f: /({}() -> ())
            set f = func {}() -> () {
                set x = <.0>:/</^@global>@global
            }
            var y: //</^ @local> @local @local
            set y = /x --!1
            var g: /({}() -> ())
            set g = f
            call g \ ()
        """.trimIndent())
        assert(out == "OK") { out }
        //assert(out == "(ln 3, col 9): undeclared variable \"x\"") { out }
        //assert(out == "(ln 3, col 11): invalid assignment of \"x\" : borrowed in line 5") { out }
    }
    @Test
    fun l05_borrow_err () {
        val out = inp2env("""
            var x: /</^ @local> @local
            var y: //</^ @local> @local @local
            set y = /x --!1
            set x = <.0>:/</^ @local> @local
        """.trimIndent())
        //assert(out == "(ln 3, col 7): invalid assignment of \"x\" : borrowed in line 2") { out }
        assert(out == "OK") { out }
    }
    @Test
    fun l05_borrow_err2 () {
        val out = inp2env("""
            var x: /</^ @local> @local
            var y: /</^ @local> @local
            set y = x --!1
            set x = <.0>:/</^ @local> @local
        """.trimIndent())
        //assert(out == "(ln 3, col 7): invalid assignment of \"x\" : borrowed in line 2") { out }
        assert(out == "OK") { out }
    }
    @Test
    fun l06_borrow_err () {
        val out = inp2env("""
            var x: /</^ @local> @local
            var y: [(),//</^ @local> @local @local]
            set y = [(), /x] --/x!1]
            set x = <.0>: /</^ @local> @local
        """.trimIndent())
        //assert(out == "(ln 3, col 7): invalid assignment of \"x\" : borrowed in line 2") { out }
        assert(out == "OK") { out }
    }
    @Test
    fun l06_borrow_err2 () {
        val out = inp2env("""
            var x: /</^ @local> @local
            var y: [(),/</^ @local> @local]
            set y = [(), x] --/x!1]
            set x = <.0>: /</^ @local> @local
        """.trimIndent())
        //assert(out == "(ln 3, col 7): invalid assignment of \"x\" : borrowed in line 2") { out }
        assert(out == "OK") { out }
    }
    @Disabled // TODO: explicit type
    @Test
    fun l07_borrow_err () {
        val out = inp2env("""
            var x: /</^ @local> @local
            var y: <//</^ @local> @local @local>
            set y = <.1 /x> --/x!1>
            set x = <.0>: /</^ @local> @local
        """.trimIndent())
        //assert(out == "(ln 3, col 7): invalid assignment of \"x\" : borrowed in line 2") { out }
        assert(out == "OK") { out }
    }
    @Test
    fun l08_borrow_err () {
        val out = inp2env("""
            var x: [//</^ @local> @local @local,/</^ @local> @local]
            set x = [_,<.0>:/</^ @local> @local]
            set x.1 = /x.2 --!1
            var y: /</^ @local> @local
            set y = x.2
        """.trimIndent())
        assert(out == "OK") { out }
        //assert(out == "(ln 2, col 9): invalid assignment of \"x\" : borrowed in line 2") { out }
    }
    @Test
    fun l08_borrow_rec_err () {
        val out = inp2env("""
        var x: /</^ @local> @local
        var f:/( {}()->())
        set f = func {}()->() {
            set x = <.0>:/</^ @global> @global
            var y: //</^ @local> @local @local
            set y = /x
            return f\ ()
        }
        call f\ ()
        """.trimIndent())
        assert(out == "OK") { out }
        //assert(out == "(ln 3, col 9): undeclared variable \"x\"") { out }
        //assert(out == "(ln 3, col 11): invalid assignment of \"x\" : borrowed in line 4") { out }
    }
    @Test
    fun l09_consume_err () {
        val out = inp2env("""
            var x: /</^ @local> @local
            set x = <.0>:/</^ @local> @local
            var y: /</^ @local> @local
            set y = x
            set x = <.0>:/</^ @local> @local
        """.trimIndent())
        assert(out == "OK") { out }
        // TODO: could accept b/c x is being reassigned
        //assert(out == "(ln 3, col 5): invalid access to \"x\" : consumed in line 2") { out }
    }
    @Test
    fun l10_consume_err () {
        val out = inp2env("""
            var x: /</^ @local> @local
            set x = <.0>: /</^ @local> @local
            var y: /</^ @local> @local
            set y = x
            output std /x
        """.trimIndent())
        assert(out == "OK") { out }
        //assert(out == "(ln 3, col 13): invalid access to \"x\" : consumed in line 2") { out }
    }
    @Test
    fun l11_consume_err () {
        val out = inp2env("""
            var x: /</^ @local> @local
            set x = <.0>: /</^ @local> @local
            var y: //</^ @local> @local @local
            set y = /x
            var z: /</^ @local> @local
            set z = y\
            output std /x
        """.trimIndent())
        assert(out == "OK") { out }
        //assert(out == "(ln 4, col 13): invalid access to \"x\" : consumed in line 3") { out }
    }
    @Test
    fun l12_replace_ok () {
        val out = inp2env("""
            var x: /</^ @local> @local
            set x = <.0>: /</^ @local> @local
            var y: //</^ @local> @local @local
            set y = /x
            var z: /</^ @local> @local
            set z = y\
            output std /x
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun l13_consume_okr () {
        val out = inp2env("""
            var x: /</^ @local> @local
            set x = <.0>: /</^ @local> @local
            set x = x
            output std /x
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun l13_consume_ok2 () {
        val out = inp2env("""
            var f: {}(@_1)->/</^ @_1> @_1
            set f = func {}(@_1)->/</^ @_1> @_1 {
                var x: /</^ @local> @local
                set x = <.0>: /</^ @local> @local
                return x    -- err
            }
        """.trimIndent())
        assert(out == "(ln 5, col 5): invalid return : type mismatch") { out }
    }
    @Test
    fun l13_consume_err () {
        val out = inp2env("""
            var x: /</^ @local> @local
            set x = <.0>: /</^ @local> @local
            set x\!1 = x
        """.trimIndent())
        assert(out == "OK") { out }
        //assert(out == "(ln 2, col 9): invalid assignment of \"x\" : consumed in line 2") { out }
    }
    @Test
    fun l14_consume_ok () {
        val out = inp2env("""
            var string_c2ce: /({}[@_1,_(char*)]->/<[_int,/^ @_1]> @_1)
            set string_c2ce = func {}[@_1,_(char*)]->/<[_int,/^ @_1]> @_1 {
                var ret: /</^ @local> @local
                set ret = <.0>:/</^ @local> @local
                loop {
                    set ret = ret
                }
                var zzz: /</^ @local> @local
                set zzz = ret
                --return ret
            }
            call string_c2ce\ _x
        """.trimIndent())
        assert(out == "OK") { out }
    }

    // UNION SELF POINTER

    @Disabled // TODO: explicit type
    @Test
    fun m01_hold_ok () {
        val out = inp2env("""
            var x: /<(),/^ @local> @local
            set x = new <.2 new <.1>: @local>: @local
            output std /x
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun m02_borrow_err () {
        val out = inp2env("""
            var x: <()>
            set x = <.1>:<()>
            var y: /<()> @local
            set y = /x --!1
            set x = <.1>:<()>
        """.trimIndent())
        assert(out == "OK") { out }
        //assert(out == "(ln 3, col 7): invalid assignment of \"x\" : borrowed in line 2") { out }
    }

    @Test
    fun m03 () {
        val out = inp2env("""
            var l: </^ @local>
            set l = <.0>:</^ @local>    -- ERR: l is not a pointer, cannot accept NULL
            output std /l
        """.trimIndent())
        //assert(out == "(ln 2, col 11): unexpected <.0> : not a pointer") { out }
        assert(out == "(ln 2, col 11): invalid constructor : type mismatch") { out }
    }
    @Test
    fun m06_pred_notunion () {
        val out = inp2env("""
            var l: ()
            call _f l?1
        """.trimIndent())
        assert(out == "(ln 2, col 11): invalid discriminator : not an union") { out }
    }
    @Test
    fun m06_disc_notunion () {
        val out = inp2env("""
            var l: ()
            call _f l!1
        """.trimIndent())
        assert(out == "(ln 2, col 11): invalid discriminator : not an union") { out }
    }

    // UNION SELF POINTER / HOLD

    @Disabled // TODO: explicit type
    @Test
    fun n01_hold_ok () {
        val out = inp2env("""
            var x: /< [<(),//^^ @local @local>,/^ @local]> @local
            set x = new <.1 [<.1>,<.0>]> @local
            var y: /< [<(),//^^ @local @local>,/^ @local]> @local
            set y = new <.1 [<.1>,x]> @local
            set y\!1.2\!1.1 = <.1>
            -- <.1 [<.1>,<.1 [<.1>,<.0>]>]>
            output std /y
        """.trimIndent())
        //assert(out == "OK") { out }
        //assert(out == "(ln 1, col 16): invalid type declaration : unexpected `^´") { out }
        assert(out == "OK") { out }
    }
    @Disabled // TODO: explicit type
    @Test
    fun n02_hold_err () {
        val out = inp2env("""
            var x: /< [<(),//^^ @local @local>,/^ @local]> @local
            set x = new <.1 [<.1>,<.0>]> @local
            var y: /< [<(),//^^ @local @local>,/^ @local]> @local
            set y = new <.1 [<.1>, x]> @local
            set y\!1.2\!1.1 = <.2 /y>
            output std /y
        """.trimIndent())
        //assert(out == "(ln 3, col 21): invalid expression : expected `hold` operation modifier") { out }
        //assert(out == "(ln 1, col 16): invalid type declaration : unexpected `^´") { out }
        assert(out == "OK") { out }
    }
    @Disabled // TODO: explicit type
    @Test
    fun n03_hold_ok () {
        val out = inp2env("""
            var x: /< [<(),//^^ @local @local>,/^ @local]> @local
            set x = new <.1 [<.1>,<.0>]> @local
            var y: /< [<(),//^^ @local @local>,/^ @local]> @local
            set y = new <.1 [<.1>, x]> @local
                -- can receive x
                -- can be consumed by + (but not -)
                -- can hold itself (or borrows)
                -- cannot set its parts
            set y\!1.2\!1.1 = <.2 /y>
            output std /y
        """.trimIndent())
        assert(out == "OK") { out }
        //assert(out == "(ln 1, col 16): invalid type declaration : unexpected `^´") { out }
    }
    @Disabled // TODO: explicit type
    @Test
    fun n04_hold_err () {
        val out = inp2env("""
            var x: /< [<(),//^^ @local @local>,/^ @local]> @local
            set x = new <.1 [<.1>,<.0>]> @local
            var y: /< [<(),//^^ @local @local>,/^ @local]> @local
            set y = new <.1 [<.1>, x]> @local
            set y\!1.2\!1.1 = <.2 /y>
            output std /y
        """.trimIndent())
        assert(out == "OK") { out }
        //assert(out == "(ln 3, col 28): invalid `borrow` : expected pointer to recursive variable") { out }
        //assert(out == "(ln 1, col 16): invalid type declaration : unexpected `^´") { out }
    }
    @Disabled // TODO: explicit type
    @Test
    fun n05_borrow_ok () {
        val out = inp2env("""
            var x: /<[(),/^ @local]> @local
            set x = new <.1 [(),<.0>]> @local
            var y: [(),/<[(),/^ @local]> @local]
            set y = [(), new <.1 [(),<.0>]> @local]
            var z: [(),//<[(),/^ @local]> @local @local]
            set z = [(), /x]
            output std z.2\\!1.2\!0
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Disabled // TODO: explicit type
    @Test
    fun n06_hold_ok () {
        val out = inp2env("""
            var x: /< [//^ @local @local,/^ @local]> @local
            set x = new <.1 [_,<.0>]> @local
            set x\!1.1 = /x
        """.trimIndent())
        assert(out == "OK") { out }
        //assert(out == "(ln 1, col 12): invalid type declaration : unexpected `^´") { out }
    }
    @Disabled // TODO: explicit type
    @Test
    fun n07_hold_ok () {
        val out = inp2env("""
            var x: /< [<(),//^^ @local @local>,/^ @local]> @local
            set x = new <.1 [<.1>,<.0>]> @local
            set x\!1.1 = <.2 /x>  -- ok
        """.trimIndent())
        assert(out == "OK") { out }
        //assert(out == "(ln 1, col 16): invalid type declaration : unexpected `^´") { out }
    }
    @Test
    fun n08_hold_err () {
        val out = inp2env("""
            var x: /<? <?//^^>>  -- err: <? ...>
        """.trimIndent())
        //assert(out == "(ln 1, col 12): invalid type declaration : unexpected `?´") { out }
        //assert(out == "(ln 1, col 14): invalid type declaration : unexpected `^´") { out }
        assert(out == "(ln 1, col 10): expected type : have `?´") { out }
    }
    @Disabled // TODO: explicit type
    @Test
    fun n08_hold_ok1 () {
        val out = inp2env("""
            var x: /< <(),//^^ @local @local>> @local
            set x = new <.1 <.1>> @local
        """.trimIndent())
        assert(out == "OK") { out }
        //assert(out == "(ln 1, col 14): invalid type declaration : unexpected `^´") { out }
    }
    @Test
    fun n08_hold_ok2 () {
        val out = inp2env("""
            var x: <?/_int>
            set x = <.0>
        """.trimIndent())
        //assert(out == "OK") { out }
        //assert(out == "(ln 2, col 11): unexpected <.0> : not a pointer") { out }
        //assert(out == "(ln 1, col 8): invalid type declaration : unexpected `?´") { out }
        assert(out == "(ln 1, col 9): expected type : have `?´") { out }
    }
    @Test
    fun n08_hold_ok3 () {
        val out = inp2env("""
            var x: /<? [<?//^^>,/^]>
            set x = new <.1 [<.0>,<.0>]>
            set x\!1.1 = <.1 /x>  -- ok
        """.trimIndent())
        //assert(out == "OK") { out }
        //assert(out == "(ln 2, col 20): unexpected <.0> : not a pointer") { out }
        //assert(out == "(ln 1, col 14): invalid type declaration : unexpected `^´") { out }
        //assert(out == "(ln 1, col 13): invalid type declaration : unexpected `?´") { out }
        assert(out == "(ln 1, col 10): expected type : have `?´") { out }
    }
    @Test
    fun n09_hold_err () {
        val out = inp2env("""
            var x: /<? [<?//^^>,/^]>
            set x = new <.1 [<.0>,<.0>]>
            var y: /<? [<?//^^>,/^]>
            set y = x
            output std y
        """.trimIndent())
        //assert(out == "(ln 2, col 35): invalid `consume` : expected recursive variable") { out }
        //assert(out == "(ln 1, col 14): invalid type declaration : unexpected `^´") { out }
        //assert(out == "OK") { out }
        //assert(out == "(ln 2, col 20): unexpected <.0> : not a pointer") { out }
        //assert(out == "(ln 1, col 13): invalid type declaration : unexpected `?´") { out }
        assert(out == "(ln 1, col 10): expected type : have `?´") { out }
    }

    // POINTER CROSSING UNION

    @Test
    fun o01_pointer_union_err () {
        val out = inp2env("""
            var x: <[()]>
            var y: /() @local
            set y = /x!1.1  -- can't point to .1 inside union (union might change)
            output std y
        """.trimIndent())
        assert(out == "(ln 3, col 9): invalid operand to `/´ : union discriminator") { out }
    }
    @Test
    fun o02_pointer_union_err () {
        val out = inp2env("""
            var x: /</^ @local> @local
            set x = new <.1 new <.1 <.0>: /</^ @local> @local>:</^@local>: @local>:</^@local>: @local
            var y: //</^ @local> @local @local
            set y = /x!1    -- udisc
            output std y
        """.trimIndent())
        assert(out == "(ln 4, col 12): unexpected operand to `/´") { out }
    }
    @Test
    fun o03_pointer_union_err () {
        val out = inp2env("""
            var x: /<[/^ @local]> @local
            set x = new <.1 [new <.1 [<.0>:/<[/^ @local]> @local]>:<[/^ @local]>: @local]>:<[/^ @local]>: @local
            var y: //<[/^ @local]> @local @local
            set y = /x\!1.1  -- crossing udisc
            output std y
        """.trimIndent())
        assert(out == "(ln 4, col 9): invalid operand to `/´ : union discriminator") { out }
    }
    @Test
    fun o04_pointer_union_err () {
        val out = inp2env("""
            var x: <(),[()]>
            set x = <.2 [()]>:<(),[()]>
            var y: /() @local
            set y = /x!2.1  -- crossing udisc
            output std y
        """.trimIndent())
        assert(out == "(ln 4, col 9): invalid operand to `/´ : union discriminator") { out }
    }
    @Test
    fun o05_pointer_union_ok () {
        val out = inp2env("""
            var x: </() @local>
            var y: /() @local
            set y = /x!1\   -- ok: crosses udisc but dnrefs a pointer before the upref
            output std y
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun o06_pointer_union_err () {
        val out = inp2env("""
            var x: </() @local>
            var y: //() @local @local
            set y = //x!1\   -- no: upref after dnref fires the problem again
            output std y
        """.trimIndent())
        assert(out == "(ln 3, col 9): invalid operand to `/´ : union discriminator") { out }
    }
    @Test
    fun o07_bug () {
        val out = inp2env("""
            var z: //<()> @local @local
            output std z\!1
        """.trimIndent())
        assert(out == "(ln 2, col 15): invalid discriminator : not an union") { out }
    }

    // FUNC / POOL

    @Test
    fun p01_pool_err () {
        val out = inp2env("""
            var f : {}/()@local -> /()@local
        """.trimIndent()
        )
        assert(out == "(ln 1, col 21): invalid function type : missing pool argument") { out }
    }
    @Test
    fun p02_pool_ok () {
        val out = inp2env("""
            var f : {}[@a_1,/()@a_1] -> /()@a_1
        """.trimIndent()
        )
        assert(out == "OK") { out }
    }
    @Test
    fun p03_pool_err () {
        val out = inp2env("""
            var f : {}[@b_1,/()@a_1] -> ()
        """.trimIndent()
        )
        assert(out == "(ln 1, col 26): invalid function type : missing pool argument") { out }
    }
    @Test
    fun p04_pool_err () {
        val out = inp2env("""
            var f :{} [@a,/()@a_1] -> ()
        """.trimIndent()
        )
        //assert(out == "(ln 1, col 22): invalid function type : missing pool argument") { out }
        assert(out == "(ln 1, col 24): invalid pool : expected `_N´ depth") { out }
    }
    @Test
    fun p06_pool_ok () {
        val out = inp2env("""
            var f :{} [@a_1,@a_2,/()@a_1] -> /()@a_2
        """.trimIndent()
        )
        assert(out == "OK") { out }
    }
    @Test
    fun p07_pool_err () {
        val out = inp2env("""
            var f : {}[@a_1,@a_3,/()@a_1] -> /()@a_3
        """.trimIndent()
        )
        assert(out == "(ln 1, col 31): invalid function type : pool arguments are not continuous") { out }
    }
    @Test
    fun p08_pool_err () {
        val out = inp2env("""
            var f : {}[@a,/()@a] -> /()@a
        """.trimIndent()
        )
        assert(out == "(ln 1, col 22): invalid pool : expected `_N´ depth") { out }
    }
    @Test
    fun p09_pool_err () {
        val out = inp2env("""
            var x : /() @local_1
        """.trimIndent()
        )
        //assert(out == "(ln 1, col 10): invalid pool : expected `_N´ depth") { out }
        assert(out == "OK") { out }
    }
    @Test
    fun p10_pool_err () {
        val out = inp2env("""
            { @_1 }
        """.trimIndent()
        )
        //assert(out == "(ln 1, col 10): invalid pool : expected `_N´ depth") { out }
        assert(out == "(ln 1, col 3): invalid pool : unexpected `_1´ depth") { out }
    }
    @Test
    fun p11_pool_err () {
        val out = inp2env("""
            { @a
                var x: @a_1
            }
        """.trimIndent()
        )
        //assert(out == "(ln 1, col 8): invalid pool : unexpected `_1´ depth") { out }
        assert(out == "(ln 2, col 12): undeclared scope \"@a_1\"") { out }
    }
    @Test
    fun p11_pool_err2 () {
        val out = inp2env("""
            { @a_1
                var x: @a_1
            }
        """.trimIndent()
        )
        assert(out == "(ln 1, col 3): invalid pool : unexpected `_1´ depth") { out }
        //assert(out == "(ln 2, col 12): undeclared scope \"@a_1\"") { out }
    }
    @Test
    fun p12_pool_ff() {
        val out = inp2env(
            """
            var f: /({}@_1 -> ())
            set f = func {}@_1 -> () {}
            var g: /({}[@_1, {}@_1->()] -> ())
            set g = func {}[@_1, {}@_1->()] -> () {}
        """.trimIndent()
        )
        assert(out == "OK") { out }
    }
    @Test
    fun p13_pool_ff() {
        val out = inp2env(
            """
            var f: /({}@_1 -> /()@_1)
            set f = func {}@_1 -> /()@_1 {}
            var g: /({}[@_1, {}@_1->/()@_1] -> ())
            set g = func {}[@_1, {}@_1->/()@_1] -> () {}
        """.trimIndent()
        )
        assert(out == "OK") { out }
    }
    @Test
    fun p14_pool_ff() {
        val out = inp2env(
            """
            var f:/({} @_1 -> ())
            set f = func {}@_1 -> () {}
            var g: /  ({}[@_1, /({}@_1->())@_1] -> ())
            set g = func {}[@_1, /({}@_1->())@_1] -> () {}
            call g\ [@local,f]
        """.trimIndent()
        )
        assert(out == "OK") { out }
    }
    @Test
    fun p15_pool_ff() {
        val out = inp2env(
            """
            var f:/({} @_1 -> /()@_1)
            set f = func{} @_1 -> /()@_1 {}
            var g: /({}[@_1, /({}@_1->/()@_1)@_1] -> ())
            set g = func {}[@_1, /({}@_1->/()@_1)@_1] -> () {}
            call g\ [@local,f]
        """.trimIndent()
        )
        assert(out == "OK") { out }
    }
    @Test
    fun p16_strcpy() {
        val out = inp2env(
            """
            var f:     /({}[@a_1,@b_1,/()@a_1,/()@b_1] -> /()@a_1)
            set f = func {}[@a_1,@b_1,/()@a_1,/()@b_1] -> /()@a_1 {}
            var s1: ()
            call f\ [@local,@local,/s1,/s1]
        """.trimIndent()
        )
        assert(out == "OK") { out }
    }
    @Test
    fun p17_pool_closure_err() {
        val out = inp2env(
            """
            var g: /({}@a_1 -> /({}@a_1->())@a_1)
            set g = func{} @a_1 -> /({}@a_1->())@a_1 {
                var f:/({} @b_1 -> ())     -- this is @local, cant return it
                set f = func {}@b_1 -> () {
                    output std ()
                }           
               return f                 -- can't return pointer @local
            }
            var f: /({}@a_1 -> ())
            set f = call g\ @local
            call f\ @local
        """.trimIndent()
        )
        assert(out == "(ln 7, col 6): invalid return : type mismatch") { out }
    }
    @Test
    fun p17_pool_closure_ok() {
        val out = inp2env(
            """
            var g: /({}@a_1 -> /({}@a_1->())@a_1)
            set g = func {}@a_1 -> /({}@a_1->())@a_1 {
                var f:/({} @b_1 -> ())@b_1
                set f = func {}@b_1 -> () {
                    output std ()
                }           
               return f
            }
            var f: /({}@a_1 -> ())
            set f = call g\ @local
            call f\ @local
        """.trimIndent()
        )
        assert(out == "OK") { out }
    }
    @Test
    fun p18_pool_closure_err() {
        val out = inp2env(
            """
            var f: {@local}() -> ()
            {
                var x: /</^@local>@local
                set f = func {@local}() -> () {
                    output std x
                }
            }
        """.trimIndent()
        )
        assert(out == "(ln 4, col 11): invalid assignment : type mismatch") { out }
    }
    @Test
    fun p19_pool_closure_err() {
        val out = inp2env(
            """
            var f: {}() -> ()
            {
                var x: /</^@local>@local
                set f = func {}() -> () { -- OK?: x escapes but no enclosing func
                    output std x
                }
            }
        """.trimIndent()
        )
        //assert(out == "OK") { out }
        assert(out == "(ln 5, col 20): invalid access to \"x\" : invalid closure declaration (ln 4)") { out }
    }
    @Test
    fun p20_pool_closure_err() {
        val out = inp2env(
            """
            var g: {}() -> ()
            set g = func {}() -> () {
                var x: ()
                var f: {}() -> ()
                set f = func {}() -> () {
                    output std x    -- x escapes f
                }
            }
        """.trimIndent()
        )
        assert(out == "(ln 6, col 20): invalid access to \"x\" : invalid closure declaration (ln 5)") { out }
    }
    @Test
    fun p21_pool_closure_err() {
        val out = inp2env(
            """
            var f: {@local}() -> ()
            set f = func {@local}() -> () {
                var x: ()
                output std x
            }
        """.trimIndent()
        )
        assert(out == "(ln 2, col 9): invalid function : unexpected closure declaration") { out }
    }
    @Test
    fun p22_pool_closure_err() {
        val out = inp2env(
            """
            var g: {}@a_1 -> ({}@a_1->())
            set g = func {}@a_1 -> ({}@a_1->()) {
                var f: {}@b_1 -> ()
                var x: /</^@a_1>@a_1
                set x = new <.1 <.0>:/</^@a_1>@a_1>: </^@a_1>: @a_1
                set f = func{} @b_1 -> () {
                    output std x
                }
                return f
            }
            var f: {}@a_1 -> ()
            set f = call g @local
            call f @local
        """.trimIndent()
        )
        assert(out == "(ln 7, col 20): invalid access to \"x\" : invalid closure declaration (ln 6)") { out }
    }
    @Test
    fun p23_pool_closure_err() {
        val out = inp2env(
            """
            var g: {}@a_1 -> ({}@a_1->())
            set g = func {}@a_1 -> ({}@a_1->()) {
                var f: {@_a1}@b_1 -> ()
                var x: /</^@a_1>@a_1
                set x = new <.1 <.0>:/</^@a_1>@a_1>: </^@a_1>: @a_1
                set f = func {@a_1}@b_1 -> () {
                    output std x
                }
               return f
            }
            var f: {}@a_1 -> ()        -- still requires [@a_1]
            set f = call g @local
            call f @local
        """.trimIndent()
        )
        assert(out == "ERR") { out }
    }
    @Test
    fun p24_pool_closure_err() {
        val out = inp2env(
            """
            var h: {@a_1}@a_1 -> ()
            {
                var g: {}@a_1 -> ({}@a_1->())
                set g = func {}@a_1 -> ({}@a_1->()) {
                    var f: {@b_1}@b_1 -> ()
                    var x: /</^@a_1>@a_1
                    set x = new <.1 <.0>:/</^@a_1>@a_1>: </^@a_1>: @a_1
                    set f = func {@b_1}@b_1 -> () {
                        output std x
                    }
                   return f
                }
                var f: {@a_1} @a_1 -> ()
                set f = call g @local
                set h = f
                set h = call g @local
            }
            call h @local
        """.trimIndent()
        )
        assert(out == "ERR") { out }
    }
    @Test
    fun p25_pool_closure_err() {
        val out = inp2env(
            """
            var f:{} () -> /()@global
        """.trimIndent()
        )
        assert(out == "OK") { out }
    }
    @Test
    fun p26_pool_err() {
        val out = inp2env(
            """
            { @a
                var x:()
                { @a
                }
            }
        """.trimIndent()
        )
        assert(out == "(ln 3, col 7): invalid pool : \"@a\" is already declared (ln 1)") { out }
    }
    @Test
    fun p27_pool_err() {
        val out = inp2env(
            """
            var f: /({} @a_1->())
            set f = func {} @a_1 -> () {
                var g: /({} @a_1->())
                set g = func {} @a_1 -> () {
                }
            }
        """.trimIndent()
        )
        assert(out == "(ln 4, col 13): invalid pool : \"@a\" is already declared (ln 2)") { out }
    }
}
