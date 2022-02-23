import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.MethodOrderer.Alphanumeric
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.io.PushbackReader
import java.io.StringReader

@Disabled
@TestMethodOrder(Alphanumeric::class)
class TClosure {

    fun inp2env (inp: String): String {
        All_new(PushbackReader(StringReader(inp), 2))
        Lexer.lex()
        try {
            val s = Parser().stmts()
            s.setUps(null)
            s.setScp1s()
            s.setEnvs(null)
            check_00_after_envs(s)
            check_01_before_tps(s)
            s.setTypes()
            s.setScp2s()
            check_02_after_tps(s)
            return "OK"
        } catch (e: Throwable) {
            if (THROW) {
                throw e
            }
            return e.message!!
        }
    }

    // MUTUTAL RECURSION

    @Test
    fun i04_uni_rec_err () {
        val out = inp2env("""
            var xxx: <(),</^^ @LOCAL,/^ @LOCAL>>
            set xxx  = <.1()>:<(),</^^ @LOCAL,/^ @LOCAL>>
        """.trimIndent())
        //assert(out == "(ln 2, col 14): invalid union constructor : expected `new`") { out }
        assert(out == "OK") { out }
    }
    @Test
    fun i05_uni_rec_ok () {
        val out = inp2env("""
            var xxx: /<(),</^^ @LOCAL,/^ @LOCAL>> @LOCAL
            set xxx = new <.1()>:<(),</^^ @LOCAL,/^ @LOCAL>>: @LOCAL
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun i06_uni_rec_ok1 () {
        val out = inp2env("""
            var xxx: /<(),/</^^ @LOCAL,/^ @LOCAL> @LOCAL> @LOCAL
            set xxx = new <.1()>:<(),/</^^ @LOCAL,/^ @LOCAL> @LOCAL>: @LOCAL
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun i07_list_err () {
        val out = inp2env("""
            var xxx: <[_int,</_int @LOCAL>]>
            set xxx = <.0>:</_int @LOCAL>
        """.trimIndent())
        //assert(out == "(ln 2, col 9): invalid assignment : type mismatch") { out }
        //assert(out == "(ln 2, col 13): invalid union constructor : type mismatch") { out }
        assert(out == "(ln 2, col 16): invalid type : expected pointer to union") { out }
    }
    @Test
    fun i08_mutual () {
        val out = inp2env("""
            var e: /<(), <(),/^^ @LOCAL>> @LOCAL
            set e = new <.2 <.1()>:<(),/<(), <(),/^^ @LOCAL>> @LOCAL>>:<(), <(),/^^ @LOCAL>>: @LOCAL
            var s: /<(), <(),/^^ @LOCAL>> @LOCAL
            set s = e\!2!2
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun i09_mutual () {
        val out = inp2env("""
            var e: /<</^^ @LOCAL,()>, ()> @LOCAL
            set e = new <.1 <.2()>:</<</^^ @LOCAL,()>, ()> @LOCAL,()>>:<</^^ @LOCAL,()>, ()>: @LOCAL
            var s: /<</^^ @LOCAL,()>, ()> @LOCAL
            set s = e\!1!1
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun i10_mutual () {
        val out = inp2env("""
            var e: /<</^^ @LOCAL,()>, ()> @LOCAL
            set e = new <.1 new <.2()>:</<</^^ @LOCAL,()>, ()> @LOCAL,()>: @LOCAL>:<</^^ @LOCAL,()>, ()>: @LOCAL
               -- err: ~new~ <.2>
        """.trimIndent())
        //assert(out == "(ln 2, col 7): invalid assignment : type mismatch") { out }
        assert(out.startsWith("(ln 2, col 15): invalid union constructor : type mismatch")) { out }
    }
    @Test
    fun j08_rec_xepr_double_rec_err () {
        val out = inp2env("""
            var x: <</^^^ @LOCAL>>
            --set x = <.1 <.1 <.0>>>
        """.trimIndent())
        assert(out == "(ln 1, col 11): invalid `^^^´ : missing enclosing recursive type") { out }
    }
    @Test
    fun j11_rec_xepr_double_rec () {
        val out = inp2env("""
            var x: /</<[/^^ @LOCAL,/^ @LOCAL]> @LOCAL> @LOCAL
            var z1: /</<[/^^ @LOCAL,/^ @LOCAL]> @LOCAL> @LOCAL
            var z2: /<[/</<[/^^ @LOCAL,/^ @LOCAL]> @LOCAL> @LOCAL,/^ @LOCAL]> @LOCAL
            set z1 = <.0>: /</<[/^^ @LOCAL,/^ @LOCAL]> @LOCAL> @LOCAL
            set z2 = <.0>: /<[/</<[/^^ @LOCAL,/^ @LOCAL]> @LOCAL> @LOCAL,/^ @LOCAL]> @LOCAL
            set x = new <.1 new <.1 [z1,z2]>:<[/</<[/^^ @LOCAL,/^ @LOCAL]> @LOCAL> @LOCAL,/^ @LOCAL]>: @LOCAL>:</<[/^^ @LOCAL,/^ @LOCAL]> @LOCAL>: @LOCAL
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun j29_rec_mutual () {
        val out = inp2env("""
            var e: /<(),<(),/^^ @LOCAL>> @LOCAL
            set e = new <.2 <.1()>:<(),/<(),<(),/^^ @LOCAL>> @LOCAL>>:<(),<(),/^^ @LOCAL>>: @LOCAL
        """.trimIndent())
        assert(out == "OK") { out }
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

    // CLOSURES

    @Test
    fun noclo_p17_pool_closure_ok() {
        val out = inp2env(
            """
            var g: (func@[a1]->() -> (func@[a1]->()->()))
            set g = func@[a1]->() -> (func@[a1]->()->()) {
                var f:(func@[b1]->() -> ())
                set f = func@[b1]->() -> () {
                    output std ()
                }           
               set ret = f
            }
            var f: (func@[a1]->() -> ())
            set f = g @[LOCAL] ()
            call f @[LOCAL] ()
        """.trimIndent()
        )
        assert(out == "OK") { out }
    }
    @Test // passou a falhar qd mudei env p/ upvals
    fun noclo_p22_pool_closure_err() {
        val out = inp2env(
            """
            var g : func @[a1]->() -> (func @[a1]->()->())
            set g = func @[a1]->() -> (func @[a1]->()->()) {
                var f: func @[b1]->() -> ()
                var x: /</_int@a1>@a1
                --set x = new <.1 <.0>:/</_int@a1>@a1>: </_int@a1>: @a1
                set f = func @[b1]->()  -> () {
                    output std x
                }
                set ret = f
            }
            var f : func @[a1]->() -> ()
            set f = g @[LOCAL] ()
            call f @[LOCAL] ()
        """.trimIndent()
        )
        //assert(out == "(ln 7, col 20): invalid access to \"x\" : invalid closure declaration (ln 6)") { out }
        assert(out == "(ln 7, col 20): undeclared variable \"x\"") { out }
    }
    @Test
    fun noclo_p30_closure_ok () {
        val out = inp2env("""
            var g: func @[a1]->() -> (func @a1->@[b1]->()->/</_int@b1>@b1)
        """.trimIndent())
        assert(out == "OK") { out }
    }
    @Test
    fun noclo_p31_closure_err () {
        val out = inp2env("""
            var g: func @[a1] -> () -> (func @a1->@[]->()->/</_int@b1>@b1)
        """.trimIndent())
        assert(out == "(ln 1, col 29): invalid function type : missing scope argument") { out }
    }
    @Test
    fun noclo_q18_curry () {
        val out = inp2env("""
            type Num @[s] = /<Num @[s]> @s
            var add: func @[a,b,r] -> [Num @[a],Num @[b]] -> Num @[r]
            var curry: func @[] -> func @[a,b,r] -> [Num @[a],Num @[b]] -> Num @[r] -> func @GLOBAL -> @[a] -> Num @[a] -> func @a -> @[b,r] -> Num @[b] -> Num @[r]
            var addc: func @GLOBAL -> @[a] -> Num @[a] -> func @a -> @[b,r] -> Num @[b] -> Num @[r]
            set addc = (curry @[] add: @GLOBAL)
        """.trimIndent())
        assert(out == "OK") { out }
    }

    @Test
    fun noclo_r03 () {
        val out = inp2env("""
            var f: func@[]->()->()
            { @A
                var pa: ()
                set pa = ()
                set f = func @A->@[]->()->() {
                    output std pa
                }
            }
            call f()
        """.trimIndent())
        //assert(out == "(ln 5, col 13): undeclared variable \"xxx\"") { out }
        assert(out.startsWith("(ln 5, col 11): invalid assignment : type mismatch :")) { out }
    }
    @Test
    fun noclo_r09 () {
        val out = inp2env("""
            var f: func () -> _int          -- 1. `f` is a reference to a function
            {
                var x: _int
                set x = _10: _int
                set f = func () -> _int {   -- 2. `f` is created
                    set ret = x             -- 3. `f` needs access to `x`
                    return
                }
            }                               -- 4. `x` goes out of scope
            call f ()                       -- 5. `f` still wants to access `x`
        """.trimIndent()
        )
        //assert(out == "(ln 6, col 19): invalid access to \"x\" : invalid closure declaration (ln 5)") { out }
        assert(out == "(ln 6, col 19): undeclared variable \"x\"") { out }
    }
    @Test
    fun noclo_r11 () {
        val out = inp2env(
            """
            var g: func @[a1]->() -> func @a1->@[]-> ()->()
            set g = func@[a1]->() -> func @a1->@[]-> ()->() {
                var x: ()
                var f: func @a1->@[]-> () -> ()
                set f = func @a1 ->() -> () {   -- ERR: x is between f and a1, so it will leak
                    output std x
                }
            }
        """.trimIndent()
        )
        assert(out == "(ln 6, col 20): undeclared variable \"x\"") { out }
    }
    @Test
    fun noclo_s06_pool_closure () {
        val out = inp2env(
            """
            var f: func@[]-> (func@[]->()->()) -> func @GLOBAL->()->()
            set f = func@[]-> func@[]->()->() -> (func @GLOBAL->()->()) {
                var ff: func@[]->()->()
                set ff = arg
                set ret = func @GLOBAL->@[]->()->() {   -- ERR: ff is between f and a1, so it will leak
                    call ff ()
                }
            }
            var u: func()->()
            set u = func()->() {
                output std ()
            }
            var ff: (func @GLOBAL->@[]->()->())
            set ff = f u
            call ff ()
        """.trimIndent()
        )
        //assert(out == "()\n") { out }
        assert(out == "(ln 6, col 14): undeclared variable \"ff\"") { out }
    }
}
