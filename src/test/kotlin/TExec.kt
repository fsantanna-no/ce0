import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.MethodOrderer.Alphanumeric
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.io.File

@TestMethodOrder(Alphanumeric::class)
class TExec {

    fun all (inp: String): String {
        val (ok1,out1) = ce2c(inp)
        if (!ok1) {
            return out1
        }
        File("out.c").writeText(out1)
        val (ok2,out2) = exec("gcc -Werror out.c -o out.exe")
        if (!ok2) {
            return out2
        }
        val (_,out3) = exec("./out.exe")
        //val (_,out3) = exec("valgrind ./out.exe")
            // search in tests output for
            //  - "definitely lost"
            //  - "Invalid read of size"
        //println(out3)
        return out3
    }

    @Test
    fun a01_output () {
        val out = all("output std ()")
        assert(out == "()\n") { out }
    }
    @Test
    fun a02_var () {
        val out = all("""
            var x: ()
            set x = ()
            output std x
        """.trimIndent())
        assert(out == "()\n") { out }
    }
    @Test
    fun a03_error () {
        val out = all("//output std ()")
        assert(out == "(ln 1, col 1): expected statement : have `/´")
    }
    @Test
    fun a05_int () {
        val out = all("""
            var x: _int
            set x = _10: _int
            output std x
        """.trimIndent())
        assert(out == "10\n")
    }
    @Test
    fun a06_int () {
        val out = all("""
            var x: _int
            output std _10:_int
        """.trimIndent())
        assert(out == "10\n") { out }
    }
    @Test
    fun a07_syntax_error () {
        val out = all("""
            native {
                putchar('A');
            }
        """.trimIndent())
        assert(out == "(ln 1, col 8): expected `_´ : have `{´")
    }
    @Test
    fun a08_int () {
        val out = all("""
            native _{
                putchar('A');
            }
        """.trimIndent())
        assert(out == "A")
    }
    @Test
    fun a09_int_abs () {
        val out = all("""
            var x: _int
            set x = call _abs:_ _(-1): _int
            output std x
        """.trimIndent())
        assert(out == "1\n") { out }
    }
    @Test
    fun a10_int_set () {
        val out = all("""
            var x: _int
            set x = _10: _int
            set x = _(-20): _int
            output std x
        """.trimIndent())
        assert(out == "-20\n") { out }
    }
    @Test
    fun a11_unk () {
        val out = all("""
            var x: _int
            set x = _(-20): _int
            output std x
        """.trimIndent())
        assert(out == "-20\n")
    }
    @Test
    fun a12_set () {
        val out = all("""
            var x: ()
            set x = ()
            set x = ()
            output std x
        """.trimIndent())
        assert(out == "()\n")
    }

    // TUPLES

    @Test
    fun b01_tuple_units () {
        val out = all("""
            var x: [(),()]
            set x = [(),()]
            var y: (); set y = x.1
            call _output_std_Unit:func{}->{}->()->() y
        """.trimIndent())
        assert(out == "()\n")
    }
    @Test
    fun b02_tuple_idx () {
        val out = all("""
            output std ([(),()].1)
        """.trimIndent())
        assert(out == "(ln 1, col 21): invalid discriminator : unexpected constructor") { out }
    }
    @Test
    fun b03_tuple_tuples () {
        val out = all("""
            var v: [(),()] ; set v = [(),()]
            var x: [(),[(),()]] ; set x = [(),v]
            var y: [(),()] ; set y = x.2
            var z: () ; set z = y.2
            output std z
        """.trimIndent())
        assert(out == "()\n")
    }
    @Test
    fun b04_tuple_pp () {
        val out = all("""
            var n: _int ; set n = _1: _int
            var x: [[_int,_int],[_int,_int]] ; set x = [[n,n],[n,n]]
            output std /x
        """.trimIndent())
        assert(out == "[[1,1],[1,1]]\n") { out }
    }

    // NATIVE

    @Test
    fun c01_nat () {
        val out = all("""
            native _{
                void f (void) { putchar('a'); }
            }
            call _f:_ ()
        """.trimIndent())
        assert(out == "a") { out }
    }
    @Test
    fun c02_nat () {
        val out = all("""
            var y: _(char*); set y = _("hello"): _(char*)
            var n: _int
            set n = _10: _int
            var x: [_int,_(char*)]
            set x = [n,y]
            call _puts:_ x.2
        """.trimIndent())
        assert(out == "hello\n") { out }
    }
    @Test
    fun c03_nat () {
        val out = all("""
            var y: _(char*); set y = _("hello"): _(char*)
            var n: _int; set n = _10: _int
            var x: [_int,_(char*)]; set x = [n,y]
            output std /x
        """.trimIndent())
        assert(out == "[10,\"hello\"]\n") { out }
    }

    // FUNC / CALL

    @Test
    fun d01_f_int () {
        val out = all("""
            var f: (func {} ->{}-> _int -> _int)
            set f = func {}->{}-> _int -> _int {
                set ret = arg
                return
            }
            var x: _int
            set x = call f _10: _int
            output std x
        """.trimIndent())
        assert(out == "10\n") { out }
    }
    @Test
    fun d02_f_unit () {
        val out = all("""
            var f: (func {} ->{}-> () -> ())
            set f = func {}->{}-> ()->() { return }
            var x: ()
            set x = call f ()
            output std x
        """.trimIndent())
        assert(out == "()\n") { out }
    }
    @Test
    fun d03_fg () {
        val out = all("""
            var f: (func {}->{}-> () -> ())
            set f = func {}->{}-> ()->() { var x: _int; set x = _10: _int ; output std x }
            var g: (func {}->{}-> () -> ())
            set g = func {}->{}-> ()->() { call f () ; return }
            call g ()
        """.trimIndent())
        assert(out == "10\n") { out }
    }
    @Test
    fun d04_arg () {
        val out = all("""
        var f : (func {}->{}-> _int -> _int)
        set f = func {}->{}-> _int->_int {
           set arg = _(arg+1): _int
           set ret = arg
           return
        }
        output std call f _1: _int
        """.trimIndent())
        assert(out == "2\n") { out }
    }
    @Test
    fun d05_func_var () {
        val out = all("""
        var f: (func {}->{}-> _int->_int)
        set f = func {}->{}-> _int->_int { set ret=arg ; return }
        var p: (func {}->{}-> _int->_int)
        set p = f
        output std call p _10: _int
        """.trimIndent())
        assert(out == "10\n")
    }
    @Test
    fun d06_func_fg () {
        val out = all("""
            var f: (func {}->{}-> _int->_int)
            set f = func {}->{}-> _int->_int { set ret=arg ; return }
            var g: (func {}->{}-> [func {}->{}-> _int->_int, _int] -> _int)
            set g = func {}->{}-> [func {}->{}-> _int->_int, _int] -> _int {
               var f: (func {}->{}-> _int->_int)
               set f = arg.1
               var v: _int
               set v = call f arg.2
               set ret = v
               return
            }
            output std call g [f,_10: _int]
        """.trimIndent())
        assert(out == "(ln 5, col 8): invalid declaration : \"f\" is already declared (ln 1)") { out }
    }
    @Test
    fun d07_func_fg () {
        val out = all("""
            var f:(func {}->{}->  _int->_int)
            set f = func {}->{}-> _int->_int { set ret=arg return }
            var g:  (func {}->{@i1}-> [(func {}->{}-> _int->_int), _int] -> _int)
            set g = func {}->{@i1}-> [(func {}->{}-> _int->_int), _int]-> _int {
               var fx: (func {}->{}-> _int->_int)
               set fx = arg.1
               var v: _int
               set v = call fx arg.2
               set ret = v
               return
            }
            --var n: _int = _10: _int
            output std call g {@LOCAL} [f,_10:_int]
        """.trimIndent())
        assert(out == "10\n") { out }
    }
    @Test
    fun d08_func_unit () {
        val out = all("""
            var f: (func {}->{}->  ()->() )
            set f = func {}->{}-> ()->() { var x:() ; set x = arg ; set ret=arg ; return }
            var x:() ; set x = call f ()
            output std x
        """.trimIndent())
        assert(out == "()\n") { out }
    }
    @Test
    fun d09_func_unit () {
        val out = all("""
            var f: (func {}->{}-> ()->())
            set f = func {}->{}-> ()->() { var x:() ; set x = arg ; set ret=arg ; return }
            output std call f ()
        """.trimIndent())
        assert(out == "()\n") { out }
    }

    // INPUT / OUTPUT

    @Test
    fun e01_out () {
        val out = all("""
            output () ()
        """.trimIndent())
        //assert(out == "(ln 1, col 8): invalid `output` : expected identifier") { out }
        assert(out == "(ln 1, col 8): expected variable identifier : have `()´") { out }
    }
    @Test
    fun e02_out () {
        val out = all("""
            var x: [(),()]; set x = [(),()]
            output std /x
        """.trimIndent())
        assert(out == "[(),()]\n")
    }
    @Test
    fun e03_out () {
        val out = all("""
            var output_f: (func {}->{}-> _int -> ())
            set output_f = func{}->{}-> _int -> () { output std arg }
            output f _10: _int
        """.trimIndent())
        assert(out == "10\n") { out }
    }
    @Disabled   // needs user input
    @Test
    fun e04_inp () {
        val out = all("""
            var x: _int
            set x = input std: _int
            output std x
        """.trimIndent())
        assert(out == "10\n") { out }
    }

    // USER

    @Test
    fun f01_bool () {
        val out = all("""
            var b : <(),()>
            set b = <.1>:<(),()>
            output std /b
        """.trimIndent())
        assert(out == "<.1>\n") { out }
    }
    @Test
    fun f02_xyz () {
        val out = all("""
            var z : <()>
            set z = <.1()>:<()>
            var y : <<()>>
            set y = <.1 z>:<<()>>
            var x : <<<()>>>
            set x = <.1 y>:<<<()>>>
            var yy: <<()>>
            set yy = x!1
            var zz: <()>
            set zz = yy!1
            output std /zz
        """.trimIndent())
        assert(out == "<.1>\n") { out }
    }
    @Test
    fun f05_user_big () {
        val out = all("""
            var s: <[_int,_int,_int,_int],_int,_int>
            set s = <.1 [_1:_int,_2:_int,_3:_int,_4:_int]>:<[_int,_int,_int,_int],_int,_int>
            output std /s
        """.trimIndent())
        assert(out == "<.1 [1,2,3,4]>\n") { out }
    }
    @Test
    fun f06_user_big () {
        val out = all("""
            var x: <[<()>,<()>]>
            set x = <.1 [<.1>:<()>,<.1>:<()>]>:<[<()>,<()>]>
            output std /x
        """.trimIndent())
        assert(out == "<.1 [<.1>,<.1>]>\n") { out }
    }
    @Test
    fun f07_user_pred () {
        val out = all("""
            var z: <()>
            set z = <.1>: <()>
            output std z?1
        """.trimIndent())
        assert(out == "1\n") { out }
    }
    @Test
    fun f10_user_disc () {
        val out = all("""
            var z: <(),()>
            set z = <.2 ()>:<(),()>
            output std z!2
        """.trimIndent())
        assert(out == "()\n") { out }
    }
    @Test
    fun f11_user_disc_err () {
        val out = all("""
            var z: <(),()>
            set z = <.2>: <(),()>
            output std z!1
        """.trimIndent())
        assert(out == "out.exe: out.c:82: main: Assertion `z.tag == 1' failed.\n") { out }
    }
    @Test
    fun f12_user_disc_pred_idx () {
        val out = all("""
            var v: <[<()>,()]>
            set v = <.1 [<.1>:<()>,()]>: <[<()>,()]>
            output std v!1.1?1
        """.trimIndent())
        assert(out == "1\n") { out }
    }
    @Test
    fun f13_user_disc_pred_err () {
        val out = all("""
            output std ()?1
        """.trimIndent())
        assert(out == "(ln 1, col 15): invalid discriminator : not an union") { out }
    }
    @Test
    fun f14_user_dots_err () {
        val out = all("""
            var x: <<<()>>>; set x = <.1 <.1 <.1>:<()>>:<<()>>>:<<<()>>>
            output std x!1!2
        """.trimIndent())
        assert(out == "(ln 2, col 16): invalid discriminator : out of bounds")
    }
    @Test
    fun f15_user_dots () {
        val out = all("""
            var x: <<<()>>>
            set x = <.1 <.1 <.1>:<()>>:<<()>>>:<<<()>>>
            output std x!1!1!1
        """.trimIndent())
        assert(out == "()\n") { out }
    }

    // IF

    @Test
    fun g01_if () {
        val out = all("""
            var x: <(),()>
            set x = <.2>: <(),()>
            if x?1 { } else { output std () }
        """.trimIndent())
        assert(out == "()\n") { out }
    }
    @Test
    fun g02_if_pred () {
        val out = all("""
            var x: <(),()>
            set x = <.2>: <(),()>
            if x?2 { output std () } else { }
        """.trimIndent())
        assert(out == "()\n") { out }
    }

    // LOOP

    @Test
    fun h01_loop () {
        val out = all("""
        loop {
           break
        }
        output std ()
        """.trimIndent())
        assert(out == "()\n")
    }

    // PTR

    @Test
    fun i01_ptr () {
        val out = all("""
            var y: _int
            set y = _10: _int
            var x: /_int
            set x = /y
            output std x\
        """.trimIndent())
        assert(out == "10\n") { out }
    }
    @Test
    fun i02_ptr_func () {
        val out = all("""
        var f : func{}->{@i1}-> /_int@i1 -> ()
        set f = func {}->{@i1}-> /_int@i1 -> () {
           set arg\ = _(*arg+1): _int
           return
        }
        var x: _int
        set x = _1: _int
        call f {@LOCAL} /x
        output std x
        """.trimIndent())
        assert(out == "2\n") { out }
    }
    @Test
    fun i03_ptr_func () {
        val out = all("""
            var f: func{}->{@i1}-> /_int@i1->_int
            set f = func{}-> {@i1}->/_int@i1->_int { set ret = arg\ }
            var g: func{}->{@i1}-> /_int@i1->_int
            set g = f
            var x: _int
            set x = _10: _int
            output std call g {@LOCAL}(/x)
        """.trimIndent())
        assert(out == "10\n") { out }
    }
    @Test
    fun i05_ptr_block_err () {
        val out = all("""
            var p1: /_int @LOCAL
            var p2: /_int @LOCAL
            {
                var v: _int
                set v = _10: _int
                set p1 = /v  -- no
            }
            {
                var v: _int; set v = _20: _int
                set p2 = /v
            }
            output std p1\
        """.trimIndent())
        assert(out == "(ln 6, col 12): invalid assignment : type mismatch") { out }
    }
    @Test
    fun i06_ptr_block_err () {
        val out = all("""
            var x: _int; set x = _10: _int
            var p: /_int
            {
                var y: _int; set y = _10: _int
                set p = /x
                set p = /y  -- no
            }
        """.trimIndent())
        assert(out == "(ln 6, col 11): invalid assignment : type mismatch") { out }
    }
    @Test
    fun i07_ptr_func_ok () {
        val out = all("""
            var f : func{}->{@i1}-> /_int@i1 -> /_int@i1
            set f = func {}->{@i1}-> /_int@i1 -> /_int@i1 {
                set ret = arg
            }
            var v: _int
            set v = _10: _int
            var p: /_int@LOCAL
            set p = call f{@LOCAL} /v
            output std p\
        """.trimIndent())
        assert(out == "10\n") { out }
    }
    @Test
    fun i08_ptr_func_ok () {
        val out = all("""
            var v: _int
            set v = _10: _int
            var f : func{}->{@i1}-> () -> /_int@i1
            set f = func {}->{@i1}-> () -> /_int@i1 {
                set ret = /v
            }
            var p: /_int @LOCAL
            set p = call f {@LOCAL} ()
            output std p\
        """.trimIndent())
        assert(out == "10\n") { out }
    }
    @Test
    fun i09_ptr_func_err () {
        val out = all("""
            var f : func{}->{@i1}-> () -> /_int@i1
            set f = func {}->{@i1}-> () -> /_int@i1 {
                var v: _int; set v = _10: _int
                set ret = /v   -- err
            }
            output std ()
        """.trimIndent())
        assert(out == "(ln 4, col 13): invalid return : type mismatch") { out }
    }
    @Test
    fun i10_ptr_func_err () {
        val out = all("""
            var f : func{}->{@i1}-> /_int@i1 -> /_int@i1
            set f = func {}->{@i1}-> /_int@i1 -> /_int@i1 {
                var ptr: /_int
                set ptr = arg
                set ret = ptr  -- err
            }
            output std ()
        """.trimIndent())
        assert(out == "(ln 5, col 13): invalid return : type mismatch") { out }
    }
    @Test
    fun i11_ptr_func_ok () {
        val out = all("""
            var f : func{}->{@i1}-> /_int@i1 -> /_int@i1
            set f = func {}->{@i1}-> /_int@i1 -> /_int@i1 {
                var ptr: /_int@i1
                set ptr = arg
                set ret = ptr
            }
            var v: _int
            set v = _10: _int
            var p: /_int@LOCAL
            set p = call f {@LOCAL} /v
            output std p\
        """.trimIndent())
        assert(out == "10\n") { out }
    }
    @Test
    fun i12_ptr_ptr_ok () {
        val out = all("""
            var p: //_int @LOCAL @LOCAL
            var z: _int; set z = _10: _int
            var y: /_int; set y = /z
            set p = /y
            output std p\\
        """.trimIndent())
        assert(out == "10\n") { out }
    }
    @Test
    fun i13_ptr_tup () {
        val out = all("""
            var v: [_int,_int]
            set v = [_10:_int,_20:_int]
            var p: /_int @LOCAL
            set p = /v.1
            set p\ = _20: _int
            output std /v
        """.trimIndent())
        assert(out == "[20,20]\n") { out }
    }
    @Test
    fun i14_ptr_type_err () {
        val out = all("""
            var v: <_int>
            set v = <.1 _10: _int>: <_int>
            var p: /_int @LOCAL
            set p = /v!1
            output std ()
        """.trimIndent())
        //assert(out == "(ln 2, col 16): invalid expression : expected `borrow` operation modifier")
        assert(out == "(ln 4, col 12): unexpected operand to `/´") { out }
    }
    @Test
    fun i14_ptr_type () {
        val out = all("""
            var v: [_int]; set v = [_10:_int]
            var p: /_int; set p = /v.1
            set p\ = _20: _int
            output std /v
        """.trimIndent())
        assert(out == "[20]\n") { out }
    }
    @Test
    fun i15_ptr_tup () {
        val out = all("""
            var x: _int; set x = _10: _int
            var p: [_int,/_int @LOCAL]; set p = [_10:_int,/x]
            var v: _int; set v = _20: _int
            set p.2 = /v
            output std p.2\
        """.trimIndent())
        assert(out == "20\n") { out }
    }
    @Test
    fun i16_ptr_tup () {
        val out = all("""
            var x: _int; set x = _10: _int
            var p: [_int,/_int @LOCAL]; set p = [_10:_int,/x]
            var v: _int; set v = _20: _int
            set p = [_10:_int,/v]
            output std p.2\
        """.trimIndent())
        assert(out == "20\n") { out }
    }
    @Test
    fun i17_ptr_type () {
        val out = all("""
            var x: _int
            set x = _10: _int
            var p: </_int>
            set p = <.1 /x>: </_int @LOCAL>
            var v: _int
            set v = _20: _int
            set p!1 = /v
            output std p!1\
        """.trimIndent())
        assert(out == "20\n") { out }
    }
    @Test
    fun i18_ptr_type () {
        val out = all("""
            var x: _int
            set x = _10: _int
            var p: </_int @LOCAL>
            set p = <.1 /x>: </_int>
            var v: _int
            set v = _20: _int
            set p = <.1 /v>: </_int @LOCAL>
            output std p!1\
        """.trimIndent())
        assert(out == "20\n") { out }
    }
    @Test
    fun i19_ptr_tup () {
        val out = all("""
            var x1: [_int,/_int @LOCAL]
            var v: _int; set v = _20: _int
            var x2: [_int,/_int]; set x2 = [_10:_int,/v]
            set x1 = x2
            output std x1.2\
        """.trimIndent())
        assert(out == "20\n") { out }
    }
    @Test
    fun f09_ptr_type_err () {
        val out = all("""
            var x1: </_int @LOCAL>
            var v: _int
            set v = _20: _int
            var x2: </_int @LOCAL>
            set x2 = <.1 /v>:</_int>
            set x1 = x2
            output std x1!1\
        """.trimIndent())
        assert(out == "20\n") { out }
    }
    @Test
    fun f10_ptr_func () {
        val out = all("""
            var v: _int
            set v = _10: _int
            var f : func {}->{@i1}-> /_int@i1 -> /_int@i1
            set f = func {}->{@i1}-> /_int@i1 -> /_int@i1 {            
                set ret = /v
                return
            }
            {
                var p: /_int @LOCAL
                set p = call f {@GLOBAL} /v: @GLOBAL
                output std p\
            }
        """.trimIndent())
        assert(out == "10\n") { out }
    }
    @Test
    fun f11_ptr_func () {
        val out = all("""
            var f: (func{}->{@i1}-> /_int@i1 -> /_int@i1)
            set f = func {}->{@i1}-> /_int@i1 -> /_int@i1 {
                set ret = arg
            }
            var v: _int
            set v = _10: _int
            var p: /_int @LOCAL
            set p = call f {@LOCAL} /v: @LOCAL
            output std p\
        """.trimIndent())
        assert(out == "10\n") { out }
    }
    @Test
    fun i20_ptr_uni_ok () {
        val out = all("""
            var uni: <_(char*),_int>
                set uni = <.1 _("oi"): _(char*)>: <_(char*),_int>
            var ptr: /_(char*)
                set ptr = /uni!1
            call _puts ptr\
        """.trimIndent())
        assert(out == "(ln 4, col 20): unexpected operand to `/´") { out }
    }
    @Test
    fun i21_ptr_uni_err () {
        val out = all("""
            var uni: <_(char*),_int>
                set uni = <.1 _("oi"): _(char*)>: <_(char*),_int>
            var ptr: /_(char*) @LOCAL
                set ptr = /uni!1
            set uni = <.2 _65:_int>
            call _puts ptr\
        """.trimIndent())
        //assert(out == "(ln 5, col 9): invalid assignment of \"uni\" : borrowed in line 4")
        assert(out == "(ln 4, col 20): unexpected operand to `/´") { out }
    }

    // REC

    @Test
    fun j00_list_err () {
        val out = all("""
            var l: /</^ @LOCAL> @LOCAL
            set l = <.0>: /</^ @LOCAL>
            output std l
        """.trimIndent())
        //assert(out == "(ln 2, col 7): invalid assignment : type mismatch") { out }
        assert(out == "<.0>\n") { out }
    }
    @Test
    fun j01_list () {
        val out = all("""
            var l: /</^ @LOCAL> @LOCAL
            set l = <.0>: /</^ @LOCAL>
            output std l
        """.trimIndent())
        //assert(out == "(ln 1, col 5): invalid assignment : type mismatch") { out }
        assert(out == "<.0>\n") { out }
    }
    @Test
    fun j02_list_new_err_dst () {
        val out = all("""
            var l: /</^ @LOCAL> @LOCAL
            set l = new <.1 <.0>: /</^>>:</^>
            output std l
        """.trimIndent())
        //assert(out == "(ln 1, col 21): invalid assignment : type mismatch") { out }
        assert(out == "<.1 <.0>>\n") { out }
    }
    @Test
    fun j02_list_new_err_dst2 () {
        val out = all("""
            var l: /</^ @GLOBAL> @GLOBAL
            set l = new <.1 <.0>: /</^ @GLOBAL> @GLOBAL>:</^ @GLOBAL>: @GLOBAL
            output std l
        """.trimIndent())
        //assert(out == "(ln 1, col 21): invalid assignment : type mismatch") { out }
        assert(out == "<.1 <.0>>\n") { out }
    }
    @Test
    fun j02_list_new_err_src () {
        val out = all("""
            var l: /</^ @LOCAL> @LOCAL
            set l = new _1: _int @LOCAL
            output std l
        """.trimIndent())
        assert(out == "(ln 2, col 9): invalid `new` : expected constructor") { out }
    }
    @Test
    fun j02_list_new_ok () {
        val out = all("""
            var l: /</^ @LOCAL> @LOCAL
            set l = new <.1 <.0>: /</^ @LOCAL>>:</^ @LOCAL>
            output std l
        """.trimIndent())
        assert(out == "<.1 <.0>>\n") { out }
    }
    @Test
    fun j02_list_pln () {
        val out = all("""
            var l: </^ @LOCAL>
            set l = <.1 <.0>:/</^ @LOCAL>@LOCAL>: </^ @LOCAL>
            output std /l
        """.trimIndent())
        //assert(out == "(ln 1, col 25): invalid constructor : expected `new`") { out }
        assert(out == "<.1 <.0>>\n") { out }
    }
    @Test
    fun j03_list () {
        val out = all("""
            var l: /</^ @LOCAL> @LOCAL
            var z: /</^ @LOCAL> @LOCAL
            set z = <.0>: /</^ @LOCAL> @LOCAL
            var one: /</^ @LOCAL> @LOCAL
            set one = new <.1 z>:</^ @LOCAL>: @LOCAL
            set l = new <.1 one>:</^ @LOCAL>: @LOCAL
            output std l\!1
        """.trimIndent())
        assert(out == "<.1 <.0>>\n") { out }
    }
    @Test
    fun j05_list_disc_null_err () {
        val out = all("""
            var l: /</^ @LOCAL> @LOCAL
            set l = <.0>: /</^ @LOCAL> @LOCAL
            output std l\!1
        """.trimIndent())
        assert(out == "out.exe: out.c:82: main: Assertion `&(*l) != NULL' failed.\n") { out }
    }
    @Test
    fun j06_list_1 () {
        val out = all("""
            var l: /</^ @LOCAL> @LOCAL
            var z: /</^ @LOCAL> @LOCAL
            set z = <.0>: /</^ @LOCAL> @LOCAL
            var one: /</^ @LOCAL> @LOCAL
            set one = new <.1 z>:</^ @LOCAL>: @LOCAL
            set l = new <.1 one>:</^ @LOCAL>: @LOCAL
            var p: //</^ @LOCAL> @LOCAL @LOCAL
            {
                set p = /l --!1
            }
            output std p\
        """.trimIndent())
        assert(out == "<.1 <.1 <.0>>>\n") { out }
    }
    @Test
    fun j06_list_2 () {
        val out = all("""
            var l: /</^ @LOCAL> @LOCAL
            var z: /</^ @LOCAL> @LOCAL
            set z = <.0>: /</^ @LOCAL> @LOCAL
            var one: /</^ @LOCAL> @LOCAL
            set one = new <.1 z>:</^ @LOCAL>: @LOCAL
            set l = new <.1 one>:</^ @LOCAL>: @LOCAL
            var p: /</^ @LOCAL> @LOCAL
            {
                set p = l --!1
            }
            output std p
        """.trimIndent())
        assert(out == "<.1 <.1 <.0>>>\n") { out }
    }
    @Test
    fun j06_list_ptr () {
        val out = all("""
            var l: /</^ @LOCAL> @LOCAL
            var z: /</^ @LOCAL> @LOCAL
            set z = <.0>: /</^ @LOCAL> @LOCAL
            var one: /</^ @LOCAL> @LOCAL
            set one = new <.1 z>:</^ @LOCAL>: @LOCAL
            set l = new <.1 one>:</^ @LOCAL>: @LOCAL
            var p: //</^ @LOCAL> @LOCAL @LOCAL
            {
                set p = /l --!1
            }
            output std p
        """.trimIndent())
        assert(out == "_\n") { out }
    }
    @Test
    fun j07_list_move () {
        val out = all("""
            var l1: /</^ @LOCAL> @LOCAL
            set l1 = new <.1 <.0>: /</^ @LOCAL> @LOCAL>:</^ @LOCAL>: @LOCAL
            var l2: /</^ @LOCAL> @LOCAL
            set l2 = l1
            output std /l1
            output std /l2
        """.trimIndent())
        //assert(out == "(ln 3, col 13): invalid access to \"l1\" : consumed in line 2") { out }
        assert(out == "_\n_\n") { out }
    }
    @Test
    fun j07_list_move_err () {
        val out = all("""
            var l1: /<?/^>
            set l1 = new <.1 <.0>>: @LOCAL
            var l2: /</^>
            set l2 = l1
        """.trimIndent())
        //assert(out == "(ln 4, col 8): invalid assignment : type mismatch") { out }
        assert(out == "(ln 1, col 11): expected type : have `?´") { out }
    }
    @Test
    fun j08_list_move () {
        val out = all("""
            var l1: /</^ @LOCAL> @LOCAL
            set l1 = new <.1 <.0>: /</^ @LOCAL> @LOCAL>:</^ @LOCAL>: @LOCAL
            var l2: /</^ @LOCAL> @LOCAL
            set l2 = <.0>: /</^ @LOCAL> @LOCAL
            set l2 = new <.1 l1>:</^ @LOCAL>: @LOCAL
            output std l2
        """.trimIndent())
        assert(out == "<.1 <.1 <.0>>>\n") { out }
    }
    @Test
    fun j09_list_move () {
        val out = all("""
            var l1: /</^ @LOCAL> @LOCAL
            set l1 = new <.1 <.0>: /</^ @LOCAL> @LOCAL>:</^ @LOCAL>: @LOCAL
            var l2: [_int,/</^ @LOCAL> @LOCAL]
            set l2 = [_10:_int, l1]
            output std l1
            output std l2.2
        """.trimIndent())
        //assert(out == "<.0>\n<.1 <.0>>\n") { out }
        assert(out == "<.1 <.0>>\n<.1 <.0>>\n") { out }
    }
    @Test
    fun j10_rec () {
        val out = all("""
            var z: /</^ @LOCAL> @LOCAL
            set z = <.0>: /</^ @LOCAL> @LOCAL
            var one: /</^ @LOCAL> @LOCAL
            set one = new <.1 z>:</^ @LOCAL>

            var n: </</^ @LOCAL> @LOCAL>
            set n = <.1 one>:</</^ @LOCAL> @LOCAL>
            output std /n
        """.trimIndent())
        assert(out == "<.1 <.1 <.0>>>\n") { out }
    }
    @Test
    fun j11_borrow_1 () {
        val out = all("""
            var z: /</^ @LOCAL> @LOCAL
            set z = <.0>: /</^ @LOCAL> @LOCAL
            var one: /</^ @LOCAL> @LOCAL
            set one = new <.1 z>:</^ @LOCAL>

            var x: /</^ @LOCAL> @LOCAL
            set x = new <.1 one>:</^ @LOCAL>
            var y: //</^ @LOCAL> @LOCAL @LOCAL
            set y = /x
            output std y\
        """.trimIndent())
        assert(out == "<.1 <.1 <.0>>>\n") { out }
    }
    @Test
    fun j11_borrow_2 () {
        val out = all("""
            var z: /</^ @LOCAL> @LOCAL
            set z = <.0>: /</^ @LOCAL> @LOCAL
            var one: /</^ @LOCAL> @LOCAL
            set one = new <.1 z>:</^ @LOCAL>

            var x: /</^ @LOCAL> @LOCAL
            set x = new <.1 one>:</^ @LOCAL>
            var y: //</^ @LOCAL> @LOCAL @LOCAL
            set y = /x --!1
            output std y\
        """.trimIndent())
        assert(out == "<.1 <.1 <.0>>>\n") { out }
    }
    @Test
    fun j11_rec_double2 () {
        val out = all("""
            var n: /<</^^ @LOCAL>> @LOCAL
            set n = <.0>: /<</^^ @LOCAL>> @LOCAL
            output std n
        """.trimIndent())
        assert(out == "<.0>\n") { out }
    }
    @Test
    fun j09_tup_list_err () {
        val out = all("""
            var t: [_int,/</^ @LOCAL> @LOCAL]
            set t = [_10:_int, new <.1 <.0>:/</^ @LOCAL> @LOCAL>:</^ @LOCAL>]
            output std /t
        """.trimIndent())
        assert(out == "[10,<.1 <.0>>]\n") { out }
    }
    @Test
    fun j10_tup_copy_ok () {
        val out = all("""
            var l: /</^ @LOCAL> @LOCAL
            set l = <.0>: /</^ @LOCAL> @LOCAL
            var t1: [/</^ @LOCAL> @LOCAL]
            set t1 = [l]
            var t2: [/</^ @LOCAL> @LOCAL]
            set t2 = [t1.1]
            output std /t2
        """.trimIndent())
        assert(out == "[<.0>]\n") { out }
    }
    @Test
    fun j11_tup_move_ok () {
        val out = all("""
            var l: /</^ @LOCAL> @LOCAL
            set l = <.0>: /</^ @LOCAL> @LOCAL
            var t1: [/</^ @LOCAL> @LOCAL]
            set t1 = [l]
            var t2: [/</^ @LOCAL> @LOCAL]
            set t2 = [t1.1]
            output std /t2
        """.trimIndent())
        assert(out == "[<.0>]\n") { out }
    }
    @Test
    fun j11_tup_copy_ok () {
        val out = all("""
            var l: /</^ @LOCAL> @LOCAL
            set l = new <.1 <.0>: /</^ @LOCAL> @LOCAL>:</^ @LOCAL>
            var t1: [/</^ @LOCAL> @LOCAL]
            set t1 = [l]
            var t2: [/</^ @LOCAL> @LOCAL]
            set t2 = [t1.1]
            output std /t2
        """.trimIndent())
        assert(out == "[<.1 <.0>>]\n") { out }
    }
    @Test
    fun j14_tup_copy_ok () {
        val out = all("""
            var l1: /</^ @LOCAL> @LOCAL
            set l1 = <.0>: /</^ @LOCAL> @LOCAL
            var l2: /</^ @LOCAL> @LOCAL
            set l2 = l1
            output std l2
        """.trimIndent())
        assert(out == "<.0>\n") { out }
    }
    @Test
    fun j15_tup_copy_ok () {
        val out = all("""
            var l1: /</^ @LOCAL> @LOCAL
            set l1 = new <.1 <.0>: /</^ @LOCAL> @LOCAL>:</^ @LOCAL>
            var l2: /</^ @LOCAL> @LOCAL
            set l2 = l1
            output std l2
        """.trimIndent())
        assert(out == "<.1 <.0>>\n") { out }
    }
    @Test
    fun j16_tup_move_copy_ok () {
        val out = all("""
            var l1: /</^ @LOCAL> @LOCAL
            set l1 = new <.1 <.0>: /</^ @LOCAL> @LOCAL>:</^ @LOCAL>: @LOCAL
            var l2: /</^ @LOCAL> @LOCAL
            set l2 = new <.1 l1>:</^ @LOCAL>
            var t3: [(),/</^ @LOCAL> @LOCAL]
            set t3 = [(), new <.1 l2\!1>:</^ @LOCAL>]
            output std l1
            output std /t3
        """.trimIndent())
        assert(out == "<.1 <.0>>\n[(),<.1 <.1 <.0>>>]\n") { out }
    }
    @Test
    fun j18_tup_copy_rec_ok () {
        val out = all("""
            var l1: [/</^ @LOCAL> @LOCAL]
            set l1 = [new <.1 <.0>:/</^ @LOCAL> @LOCAL>:</^ @LOCAL>: @LOCAL]
            var l2: [/</^ @LOCAL> @LOCAL]
            set l2 = l1
            output std /l2
        """.trimIndent())
        assert(out == "[<.1 <.0>>]\n") { out }
    }
    @Test
    fun j19_consume_ok () {
        val out = all("""
            var x: /</^ @LOCAL> @LOCAL
            set x = new <.1 <.0>: /</^ @LOCAL> @LOCAL>:</^ @LOCAL>: @LOCAL
            set x = x
            output std x
        """.trimIndent())
        assert(out == "<.1 <.0>>\n") { out }
    }
    @Test
    fun j20_consume_ok () {
        val out = all("""
            var x: /</^ @LOCAL> @LOCAL
            set x = new <.1 <.0>: /</^ @LOCAL> @LOCAL>:</^ @LOCAL>: @LOCAL
            var y: /</^ @LOCAL> @LOCAL
            if _1: _int {
                set y = x
            } else {
                set y = x
            }
            output std y
        """.trimIndent())
        assert(out == "<.1 <.0>>\n") { out }
    }

    // SET - TUPLE - UNION

    @Test
    fun k01_set_tuple () {
        val out = all("""
            var xy: [_int,_int]; set xy = [_10:_int,_10:_int]
            set xy.1 = _20: _int
            var x: _int; set x = xy.1
            var y: _int; set y = xy.2
            var v: _int; set v = _(x+y): _int
            output std v
        """.trimIndent())
        assert(out == "30\n") { out }
    }

    // UNION SELF POINTER / HOLD

    @Test
    fun l02_hold_ok () {
        val out = all("""
            var x: /< [<(),//^^ @LOCAL @LOCAL>,_int,/^ @LOCAL]> @LOCAL
            var z: /< [<(),//^^ @LOCAL @LOCAL>,_int,/^ @LOCAL]> @LOCAL
            set z = <.0>: /< [<(),//^^ @LOCAL @LOCAL>,_int,/^ @LOCAL]> @LOCAL
            set x = new <.1 [z,_1: _int,new <.1 [z,_2: _int,z]>:< [<(),//^^ @LOCAL @LOCAL>,_int,/^ @LOCAL]>: @LOCAL]>:< [<(),//^^ @LOCAL @LOCAL>,_int,/^ @LOCAL]>: @LOCAL
            set x!1.3!1.1 = <.1 /x>: <(),//< [<(),//^^ @LOCAL @LOCAL>,_int,/^ @LOCAL]> @LOCAL @LOCAL>
            set x!1.1 = <.1 /x!1.3>: <(),//< [<(),//^^ @LOCAL @LOCAL>,_int,/^ @LOCAL]> @LOCAL @LOCAL> -- err: address of field inside union
            output std x!1.3!1.2
            output std x!1.1!1\!1.1!1\!1.2
        """.trimIndent())
        //assert(out == "2\n1\n") { out }
        //assert(out == "(ln 1, col 14): invalid type declaration : unexpected `^´") { out }
        assert(out == "(ln 6, col 17): invalid operand to `/´ : union discriminator") { out }
    }
    @Test
    fun l02_hold_ok1 () {
        val out = all("""
            var x: /< [<(),/^^ @LOCAL>,_int,/^ @LOCAL]> @LOCAL
            var z: /< [<(),/^^ @LOCAL>,_int,/^ @LOCAL]> @LOCAL
            set z = <.0>: /< [<(),/^^ @LOCAL>,_int,/^ @LOCAL]> @LOCAL
            var o: <(),/< [<(),/^^ @LOCAL>,_int,/^ @LOCAL]> @LOCAL>
            set o = <.1>: <(),/< [<(),/^^ @LOCAL>,_int,/^ @LOCAL]> @LOCAL>
            set x = new <.1 [o,_1:_int,new <.1 [o,_2:_int,z]>:< [<(),/^^ @LOCAL>,_int,/^ @LOCAL]>]>:< [<(),/^^ @LOCAL>,_int,/^ @LOCAL]>
            set x\!1.3\!1.1 = <.2 x>: <(),/< [<(),/^^ @LOCAL>,_int,/^ @LOCAL]> @LOCAL>
            set x\!1.1 = <.2 x\!1.3>: <(),/< [<(),/^^ @LOCAL>,_int,/^ @LOCAL]> @LOCAL>
            output std x\!1.3\!1.2
            output std x\!1.2
        """.trimIndent())
        assert(out == "2\n1\n") { out }
        //assert(out == "(ln 1, col 14): invalid type declaration : unexpected `^´") { out }
        //assert(out == "(ln 4, col 17): invalid operand to `/´ : union discriminator") { out }
    }
    @Test
    fun l02_hold_ok2 () {
        val out = all("""
            var z: /< [<(),//^^ @LOCAL @LOCAL>,_int,/^ @LOCAL]> @LOCAL
            set z = <.0>: /< [<(),//^^ @LOCAL @LOCAL>,_int,/^ @LOCAL]> @LOCAL
            var o: <(),//< [<(),//^^ @LOCAL @LOCAL>,_int,/^ @LOCAL]> @LOCAL @LOCAL>
            set o = <.1>: <(),//< [<(),//^^ @LOCAL @LOCAL>,_int,/^ @LOCAL]> @LOCAL @LOCAL>

            var x: /< [<(),//^^ @LOCAL @LOCAL>,_int,/^ @LOCAL]> @LOCAL
            set x = new <.1 [o,_2:_int,z]>:< [<(),//^^ @LOCAL @LOCAL>,_int,/^ @LOCAL]>: @LOCAL
            output std x
        """.trimIndent())
        assert(out == "<.1 [<.1>,2,<.0>]>\n") { out }
        //assert(out == "(ln 1, col 14): invalid type declaration : unexpected `^´") { out }
    }
    @Test
    fun l04_ptr_null () {
        val out = all("""
            var n: _int
            set n = _10: _int
            var x: <(),/_int @LOCAL>
            set x = <.2 /n>: <(),/_int @LOCAL>
            output std x!2\
        """.trimIndent())
        assert(out == "10\n") { out }
    }

    // ALLOC / SCOPE / NEWS

    @Test
    fun m01_scope_a () {
        val out = all("""
            { @A
                var pa: /</^ @LOCAL> @LOCAL
                set pa = new <.1 <.0>: /</^ @A> @A>:</^ @A>: @A
                var f: func {@A}->{}-> ()->()
                set f = func{@A}-> {}-> ()->()[pa]{
                    var pf: /</^ @A> @A
                    set pf = new <.1 <.0>: /</^ @A> @A>:</^ @A>: @A
                    --var p2: /</^ @A> @A
                    --set p2 = new <.1 <.0>: /</^ @A> @A>:</^ @A>: @A
                    --set p2\!1 = pf
                    --output std pa
                    set pa\!1 = pf
                }
                call f ()
                output std pa
            }
        """.trimIndent())
        assert(out == "<.1 <.1 <.0>>>\n") { out }
    }
    @Test
    fun m01_scope_f () {
        val out = all("""
            var f: func {}->{@i1}-> /</^@i1>@i1->()
            set f = func {}->{@i1}-> /</^@i1>@i1->() {
                var pf: /</^@i1> @i1
                set pf = arg
                output std pf
            }
            {
                var x: /</^ @LOCAL> @LOCAL
                set x = new <.1 <.0>: /</^ @LOCAL> @LOCAL>:</^ @LOCAL>
                call f {@LOCAL} x
            }
        """.trimIndent())
        assert(out == "<.1 <.0>>\n") { out }
    }
    @Test
    fun m02_scope_f () {
        val out = all("""
            var f: func {} ->{@i1}->/</^@i1>@i1->()
            set f = func {}->{@i1}-> /</^@i1>@i1->() {
                set arg\!1 = new <.1 <.0>:/</^@i1>@i1>:</^@i1>: @i1
            }
            {
                var x: /</^ @LOCAL> @LOCAL
                set x = new <.1 <.0>: /</^ @LOCAL> @LOCAL>:</^ @LOCAL>
                call f {@LOCAL} x
                output std x
            }
        """.trimIndent())
        assert(out == "<.1 <.1 <.0>>>\n") { out }
    }
    @Test
    fun m03_scope_f () {
        val out = all("""
            var f: func {}->{@a1,@a2}-> [/</^@a1>@a1,/</^@a2>@a2]->()
            set f = func {} ->{@a1,@a2}->[/</^@a1>@a1,/</^@a2>@a2]->() {
                set arg.1\!1 = new <.1 <.0>:/</^@a1>@a1>:</^@a1>: @a1
                set arg.2\!1 = new <.1 <.0>:/</^@a2>@a2>:</^@a2>: @a2
            }
            {
                var x: /</^ @LOCAL> @LOCAL
                set x = new <.1 <.0>: /</^ @LOCAL> @LOCAL>:</^ @LOCAL>
                call f {@LOCAL,@LOCAL} [x,x]
                output std x
            }
        """.trimIndent())
        assert(out == "<.1 <.1 <.0>>>\n") { out }
    }
    @Test
    fun m04_scope_f () {
        val out = all("""
            var f: func {} ->{@i1}->/</^@i1>@i1->()
            set f = func {} ->{@i1}->/</^@i1>@i1->() {
                set arg\!1 = new <.1 <.0>:/</^@i1>@i1>:</^@i1>: @i1
            }
            {
                var x: /</^ @LOCAL> @LOCAL
                set x = new <.1 <.0>: /</^ @LOCAL> @LOCAL>:</^ @LOCAL>
                call f {@LOCAL} x
                output std x
            }
        """.trimIndent())
        assert(out == "<.1 <.1 <.0>>>\n") { out }
    }

    // FUNC / POOL

    @Test
    fun n01_pool () {
        val out = all("""
            var f : func {} -> {@a1} -> /()@a1 -> /()@a1
            output std ()
        """.trimIndent()
        )
        assert(out == "()\n") { out }
    }
    @Test
    fun n02_pool () {
        val out = all("""
            var f : func {} ->{@a1}->/()@a1 -> /()@a1
            set f = func {} ->{@a1}->/()@a1 -> /()@a1 {
                set ret = arg
            }
            output std ()
        """.trimIndent()
        )
        assert(out == "()\n") { out }
    }
    @Test
    fun n03_pool () {
        val out = all("""
            var f :func {}->{@i1}-> /_int@i1 -> /()@i1
            set f = func {}->{@i1}-> /_int@i1 -> /()@i1 {
                set ret = arg
            }
            var x: _int
            call f {@LOCAL} /x
            output std ()
        """.trimIndent()
        )
        assert(out == "()\n") { out }
    }
    @Test
    fun n04_pool () {
        val out = all("""
            var f : func {}->{@i1}-> /_int@i1 -> /()@i1
            set f = func {}->{@i1}-> /_int@i1 -> /()@i1 {
                set ret = arg
            }
            var g : func {}->{@i1}-> /_int@i1 -> /()@i1
            set g = func {}->{@i1}-> /_int@i1 -> /()@i1 {
                set ret = call f {@i1} arg: @i1
            }
            var x: _int
            var px: /_int@LOCAL
            set px = call f {@LOCAL} /x: @LOCAL
            output std _(px == &x):_int
        """.trimIndent()
        )
        assert(out == "1\n") { out }
    }
    @Test
    fun n05_pool_ff() {
        val out = all(
            """
            var f: ( func {}->{}-> () -> () )
            set f = func {}->{}-> () -> () {
                set ret = arg
            }
            var g: func {}->{@i1}-> [func {}->{}-> ()->(), ()] -> ()
            set g = func {}->{@i1}-> [(func {}->{}-> ()->()), ()] -> () {
                set ret = call arg.1 arg.2
            }
            output std call g {@LOCAL} [f,()]
        """.trimIndent()
        )
        assert(out == "()\n") { out }
    }
    @Test
    fun n06_pool_ff() {
        val out = all(
            """
            var f: func {} ->{@i1}->/()@i1 -> /()@i1
            set f = func {}->{@i1}-> /()@i1 -> /()@i1 {
                set ret = arg
            }
            var g: func {}->{@i1}-> [ func {}->{@i1}-> /()@i1->/()@i1, /()@i1] -> /()@i1
            set g = func {} ->{@i1}->[func {}->{@i1}-> /()@i1->/()@i1, /()@i1] -> /()@i1 {
                set ret = call arg.1 {@i1} arg.2: @i1
            }
            var x: ()
            output std call g {@LOCAL} [f,/x]: @LOCAL
        """.trimIndent()
        )
        assert(out == "_\n") { out }
    }
    @Test
    fun n07_pool_closure() {
        val out = all(
            """
            var g: func {}->{@a1}-> () -> func {} ->{@a1}->()->()
            set g = func {} ->{@a1}->() -> func {}->{@a1}->()->() {
                var f: func {}->{@b1}-> () -> ()
                set f = func {}->{@b1}-> () -> () {
                    output std ()
                }
               set ret = f
            }
            var f: func {}->{@a1}->() -> ()
            set f = call g {@LOCAL} ()
            call f {@LOCAL} ()
        """.trimIndent()
        )
        assert(out == "()\n") { out }
    }

    @Test
    fun n08_pool_closure() {
        val out = all(
            """
            var g: func {} ->{@a1}->() -> func {@a1}->{}-> ()->()
            set g = func {}->{@a1}->() -> func {@a1}->{}-> ()->() {
                var x: /</^@a1>@a1
                set x = new <.1 <.0>:/</^@a1>@a1>: </^@a1>: @a1
                var f: func {@a1}->{}-> () -> ()
                set f = func {@a1} ->{}->() -> () [x] {
                    output std x
                }
               set ret = f
            }
            var f: func {@LOCAL} -> {} -> () -> ()
            set f = call g {@LOCAL} ()
            call f {}()
        """.trimIndent()
        )
        assert(out == "<.1 <.0>>\n") { out }
    }
    @Test
    fun n09_pool_closure_err() {
        val out = all(
            """
            var g: /func {} ->{@i1}->() -> func {}->{@i1}-> ()->()
            set g = func {}->{@i1}-> () -> func {}->{@i1}-> ()->() {
                var f: /func {}->{@a1}-> () -> ()
                var x: /</^@LOCAL>@LOCAL
                set x = new <.1 <.0>:/</^@LOCAL>@LOCAL>: </^@LOCAL>
                set f = func {}->{@a1}-> () -> () {
                    output std x    -- f uses x in @LOCAL
                }
               set ret = f             -- cannot return f which uses x in @LOCAL
            }
            var f:/(func  {}->{@i1}-> () -> () )
            set f = call g\ {@LOCAL} ()
            call f\ {@LOCAL} ()
        """.trimIndent()
        )
        assert(out == "(ln 7, col 20): invalid access to \"x\" : invalid closure declaration (ln 6)") { out }
    }
    @Test
    fun n10_pool_closure () {
        val out = all(
            """
            var cnst: func {}->{@i1}-> /_int@i1 -> (func {@i1}->{}-> () -> /_int@i1)
            set cnst = func {}->{@i1}-> /_int@i1 -> (func {@i1}->{}-> () -> /_int@i1) {
                var x: /_int@i1
                set x = arg
                set ret = func{@i1}->{}-> () -> /_int@i1 [x] {
                    set ret = x
                }
            }
            { @AAA
            var five: _int
            set five = _5: _int
            var f: func {@LOCAL} -> {} -> () -> /_int@LOCAL
            set f = call cnst {@LOCAL} /five
            var v: /_int@LOCAL
            set v = call f ()
            output std v\ --@LOCAL
            }
        """.trimIndent()
        )
        assert(out == "5\n") { out }
    }
    @Test
    fun n11_pool_closure () {
        val out = all(
            """
            var cnst:  func {}->{@i1}-> /_int@i1 -> (func {@i1}->{}-> () -> /_int@i1)
            set cnst = func {}->{@i1}-> /_int@i1 -> (func {@i1}->{}-> () -> /_int@i1) {
                var x: /_int@i1
                set x = arg
                set ret = func {@i1}->{}-> () -> /_int@i1 [x]{
                    set ret = x
                }
            }
            var five: _int
            set five = _5: _int
            var f: func {@LOCAL} -> {} -> () -> /_int@LOCAL
            set f = call cnst {@LOCAL} /five
            var v: /_int@LOCAL
            set v = call f ()
            output std v\ --@LOCAL
        """.trimIndent()
        )
        assert(out == "5\n") { out }
    }
    @Test
    fun n12_pool_closure () {
        val out = all(
            """
            var f: func {}->{}-> (func {}->{}->()->()) -> func {@GLOBAL}->{}->()->()
            set f = func {}->{}-> func {}->{}->()->() -> (func {@GLOBAL}->{}->()->()) {
                var ff: func {}->{}->()->()
                set ff = arg
                set ret = func {@GLOBAL}->{}->()->()[ff] {
                    call ff ()
                }
            }
            var u: func {}->{}->()->()
            set u = func {}->{}->()->() {
                output std ()
            }
            var ff: (func {@GLOBAL}->{}->()->())
            set ff = call f u
            call ff ()
        """.trimIndent()
        )
        assert(out == "()\n") { out }
    }

    // ALL

    @Test
    fun z01 () {
        val out = all("""
        var inv : (func {}->{}-> <(),()> -> <(),()>)
        set inv = func {}->{}-> <(),()> -> <(),()> {
            if arg?1 {
                set ret = <.2>:<(),()>
            } else {
                set ret = <.1>:<(),()>
            }
        }
        var a: <(),()>
        set a = <.2>: <(),()>
        var b: <(),()>
        set b = call inv a
        output std /b
        """.trimIndent())
        assert(out == "<.1>\n") { out }
    }
    @Test
    fun z02 () {
        val out = all("""
        var i: _int; set i = _1: _int
        var n: _int; set n = _0: _int
        loop {
            set n = _(n + i): _int
            set i = _(i + 1): _int
            if _(i > 5): _int {
                break
            }
        }
        output std n
        """.trimIndent())
        assert(out == "15\n") { out }
    }
    @Test
    fun z03 () {
        val out = all("""
        native _{}
        output std ()
        """.trimIndent())
        assert(out == "()\n")
    }
    @Test
    fun z04_if_bool () {
        val out = all("""
        if _0: _int {
        } else {
            output std ()
        }
        if _1: _int {
            output std ()
        } else {
        }
        """.trimIndent())
        assert(out == "()\n()\n")
    }
    @Test
    fun z05_func_rec () {
        val out = all("""
        var i: _int; set i = _0: _int
        var f: func {}->{}-> ()->()
        set f = func {}->{}-> ()->() {
            if _(i == 10): _int {
                
            } else {
                set i = _(i + 1): _int
                set ret = call f ()
            }
        }
        call f ()
        output std i
        """.trimIndent())
        assert(out == "10\n") { out }
    }
    @Test
    fun z06_type_complex () {
        val out = all("""
            var x: /<[(),/^ @LOCAL]> @LOCAL
            set x = new <.1 [(),<.0>: /<[(),/^ @LOCAL]>@LOCAL]>:<[(),/^ @LOCAL]>
            var y: [(),/<[(),/^ @LOCAL]> @LOCAL]
            set y = [(), new <.1 [(),<.0>: /<[(),/^ @LOCAL]>@LOCAL]>:<[(),/^ @LOCAL]>]
            var z: [(),//<[(),/^ @LOCAL]> @LOCAL @LOCAL]
            set z = [(), /x]
            output std z.2\\!1.2\!0
        """.trimIndent())
        assert(out == "()\n") { out }
    }
    @Test
    fun z07_type_complex_bug () {
        val out = all("""
            var x: /<[(),/^ @LOCAL]> @LOCAL
            set x = <.0>: /<[(),/^ @LOCAL]> @LOCAL
            var z: [(),//<[(),/^ @LOCAL]> @LOCAL @LOCAL]
            set z = [(), /x]
            output std z.2\\!0
        """.trimIndent())
        assert(out == "()\n") { out }
    }
    @Test
    fun z07_type_complex () {
        val out = all("""
            var x: /<[(),/^ @LOCAL]> @LOCAL
            set x = <.0>: /<[(),/^ @LOCAL]> @LOCAL
            var z: [(),//<[(),/^ @LOCAL]> @LOCAL @LOCAL]
            set z = [(), /x]
            output std z.2\\!0
        """.trimIndent())
        assert(out == "()\n") { out }
    }
    @Test
    fun zxx_type_complex () {
        val out = all("""
            var z: /</^ @LOCAL> @LOCAL
            set z = <.0>: /</^ @LOCAL> @LOCAL
            output std z\!0
        """.trimIndent())
        assert(out == "()\n") { out }
    }
    @Test
    fun z08_type_complex () {
        val out = all("""
            var y: [(),/<[(),/^ @LOCAL]> @LOCAL]
            set y = [(), new <.1 [(),<.0>:/<[(),/^ @LOCAL]> @LOCAL]>:<[(),/^ @LOCAL]>]
            output std /y
        """.trimIndent())
        assert(out == "[(),<.1 [(),<.0>]>]\n") { out }
    }
    @Test
    fun z08_func_arg () {
        val out = all("""
            var x1: /</^ @LOCAL> @LOCAL
            set x1 = <.0>: /</^ @LOCAL> @LOCAL
            var z1: _int
            set z1 = x1\?0
            var x2: //</^ @LOCAL> @LOCAL @LOCAL
            set x2 = /x1
            var z2: _int
            set z2 = x2\\?1
            set x2\ = new <.1 <.0>: /</^ @LOCAL> @LOCAL>:</^ @LOCAL>
            var f: func {}->{@i1}-> //</^@i1>@i1@i1->_int
            set f = func {}->{@i1}-> //</^@i1>@i1@i1->_int {
                set ret = arg\\?1
            }
            var z3: _int
            set z3 = call f {@LOCAL} x2
            var xxx: _int
            set xxx = _(z1 + z2 + z3): _int
            output std xxx
        """.trimIndent())
        assert(out == "2\n") { out }
    }
    @Test
    fun z08_func_alt () {
        val out = all("""
            var x1: /</^ @LOCAL> @LOCAL
            set x1 = <.0>: /</^ @LOCAL> @LOCAL
            var x2: //</^ @LOCAL> @LOCAL @LOCAL
            set x2 = /x1
            var y2: _int
            set y2 = x2\\?1
            output std y2
        """.trimIndent())
        assert(out == "0\n") { out }
    }
    @Test
    fun z09_output_string () {
        val out = all("""
            var f: (func  {}->{}-> ()->() )
            set f = func {}->{}-> ()->() {
                var s1: /<[_int,/^ @LOCAL]> @LOCAL
                set s1 = new <.1 [_1:_int,<.0>: /<[_int,/^ @LOCAL]> @LOCAL]>:<[_int,/^ @LOCAL]>: @LOCAL
                output std s1
            }
            call f ()
        """.trimIndent())
        assert(out == "<.1 [1,<.0>]>\n") { out }
    }
    @Test
    fun z10_output_string () {
        val out = all("""
            var f: func {}->{}-> ()->()
            set f = func {}->{}-> ()->() {
                var s1: /<[_int,/^ @LOCAL]> @LOCAL
                set s1 = new <.1 [_1:_int,<.0>: /<[_int,/^ @LOCAL]> @LOCAL]>:<[_int,/^ @LOCAL]>: @LOCAL
                output std s1
            }
            call f ()
        """.trimIndent())
        assert(out == "<.1 [1,<.0>]>\n") { out }
    }
    @Test
    fun z10_return_move () {
        val out = all("""
            var f: func {}->{@i1}-> ()-><(),_int,/<[_int,/^@i1]>@i1>
            set f = func {}->{@i1}-> ()-><(),_int,/<[_int,/^@i1]>@i1> {
                var str: /<[_int,/^@i1]> @i1
                set str = <.0>: /<[_int,/^@i1]> @i1
                set ret = <.3 str>:<(),_int,/<[_int,/^@i1]>@i1>
            }
            var x: <(),_int,/<[_int,/^@LOCAL]>@LOCAL>
            set x = call f {@LOCAL} ()
            output std x!3
        """.trimIndent())
        assert(out == "<.0>\n") { out }
    }
    @Test
    fun z11_func_err () {
        val out = all("""
            var f: func {} ->{}->()->[()]
            set f = func {} ->{}->()->() {
            }
        """.trimIndent())
        assert(out == "(ln 2, col 7): invalid assignment : type mismatch") { out }
    }
    @Test
    fun z12_union_tuple () {
        val out = all("""
            var tk2: <(),_int,/<[_int,/^ @LOCAL]> @LOCAL>
            set tk2 = <.3 <.0>:/<[_int,/^ @LOCAL]> @LOCAL>: <(),_int,/<[_int,/^ @LOCAL]> @LOCAL>
            var s21: /<(),_int,/<[_int,/^ @LOCAL]> @LOCAL> @LOCAL
            set s21 = /tk2
            output std s21\!3
        """.trimIndent())
        assert(out == "<.0>\n") { out }
    }
    @Test
    fun z15_acc_move_sub () {
        val out = all("""
            var x: /<(),/^ @LOCAL> @LOCAL
            set x = new <.2 new <.1>:<(),/^ @LOCAL>: @LOCAL>:<(),/^ @LOCAL>: @LOCAL
            var y: /<(),/^ @LOCAL> @LOCAL
            set y = x\!2
            output std x
            output std y
        """.trimIndent())
        assert(out == "<.2 <.1>>\n<.1>\n") { out }
    }
}