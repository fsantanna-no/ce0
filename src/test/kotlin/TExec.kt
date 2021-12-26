import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.MethodOrderer.Alphanumeric
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.io.File

@TestMethodOrder(Alphanumeric::class)
class TExec {

    fun all (inp: String): String {
        val (ok1,out1) = All_inp2c(inp)
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
            set x = _10
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
            set x = _abs _(-1)
            output std x
        """.trimIndent())
        assert(out == "1\n") { out }
    }
    @Test
    fun a10_int_set () {
        val out = all("""
            var x: _int
            set x = _10
            set x = _(-20)
            output std x
        """.trimIndent())
        assert(out == "-20\n") { out }
    }
    @Test
    fun a11_unk () {
        val out = all("""
            var x: _int
            set x = _(-20)
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
            call _output_std_Unit y
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
            var n: _int ; set n = _1
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
            call _f
        """.trimIndent())
        assert(out == "a") { out }
    }
    @Test
    fun c02_nat () {
        val out = all("""
            var y: _(char*); set y = _("hello")
            var n: _int; set n = _10
            var x: [_int,_(char*)]; set x = [n,y]
            call _puts x.2
        """.trimIndent())
        assert(out == "hello\n")
    }
    @Test
    fun c03_nat () {
        val out = all("""
            var y: _(char*); set y = _("hello")
            var n: _int; set n = _10
            var x: [_int,_(char*)]; set x = [n,y]
            output std /x
        """.trimIndent())
        assert(out == "[10,\"hello\"]\n") { out }
    }

    // FUNC / CALL

    @Test
    fun d01_f_int () {
        val out = all("""
            var f: /(_int -> _int)
            set f = func (_int -> _int) { return arg }
            var x: _int
            set x = call f\ _10
            output std x
        """.trimIndent())
        assert(out == "10\n") { out }
    }
    @Test
    fun d02_f_unit () {
        val out = all("""
            var f: /(() -> ())
            set f = func ()->() { return }
            var x: ()
            set x = call f\
            output std x
        """.trimIndent())
        assert(out == "()\n") { out }
    }
    @Test
    fun d03_fg () {
        val out = all("""
            var f: /(() -> ())
            set f = func ()->() { var x: _int; set x = _10 ; output std x }
            var g: /(() -> ())
            set g = func ()->() { return f\ () }
            call g\ ()
        """.trimIndent())
        assert(out == "10\n") { out }
    }
    @Test
    fun d04_arg () {
        val out = all("""
        var f : /(_int -> _int)
        set f = func _int->_int {
           set arg = _(arg+1)
           return arg
        }
        output std f\ _1
        """.trimIndent())
        assert(out == "2\n")
    }
    @Test
    fun d05_func_var () {
        val out = all("""
        var f: /(_int->_int)
        set f = func _int->_int { return arg }
        var p: /(_int->_int)
        set p = f
        output std p\ _10
        """.trimIndent())
        assert(out == "10\n")
    }
    @Test
    fun d06_func_fg () {
        val out = all("""
            var f: /(_int->_int)
            set f = func _int->_int { return arg }
            var g: /([_int->_int, _int] -> _int)
            set g = func ([_int->_int, _int] -> _int) {
               var f: /(_int->_int)
               set f = arg.1
               var v: _int
               set v = call f\ arg.2
               return v
            }
            output std g [f,_10]
        """.trimIndent())
        assert(out == "(ln 5, col 8): invalid declaration : \"f\" is already declared (ln 1)") { out }
    }
    @Test
    fun d07_func_fg () {
        val out = all("""
            var f:/( _int->_int)
            set f = func _int->_int { return arg }
            var g: / ([@_1, /(_int->_int)@_1, _int] -> _int)
            set g = func ([@_1, /(_int->_int)@_1, _int]-> _int) {
               var fx: /(_int->_int)
               set fx = arg.2
               var v: _int
               set v = call fx\ arg.3
               return v
            }
            --var n: _int = _10
            output std g\ [@local, f,_10:_int]
        """.trimIndent())
        assert(out == "10\n") { out }
    }
    @Test
    fun d08_func_unit () {
        val out = all("""
            var f: /( ()->() ); set f = func ()->() { var x:() ; set x = arg ; return arg}
            var x:() ; set x = f\ ()
            output std x
        """.trimIndent())
        assert(out == "()\n") { out }
    }
    @Test
    fun d09_func_unit () {
        val out = all("""
            var f: /(()->()); set f = func ()->() { var x:() ; set x = arg ; return arg}
            output std f\ ()
        """.trimIndent())
        assert(out == "()\n") { out }
    }

    // OUTPUT

    @Test
    fun e01_out () {
        val out = all("""
            output () ()
        """.trimIndent())
        assert(out == "(ln 1, col 8): invalid `output` : expected identifier") { out }
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
            var output_f: /(_int -> ())
            set output_f = func _int -> () { output std arg }
            output f _10
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
        assert(out == "out.exe: out.c:72: main: Assertion `z.tag == 1' failed.\n") { out }
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
            if x?1 { } else { output std }
        """.trimIndent())
        assert(out == "()\n") { out }
    }
    @Test
    fun g02_if_pred () {
        val out = all("""
            var x: <(),()>
            set x = <.2>: <(),()>
            if x?2 { output std } else { }
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
        output std
        """.trimIndent())
        assert(out == "()\n")
    }

    // PTR

    @Test
    fun i01_ptr () {
        val out = all("""
            var y: _int
            set y = _10
            var x: /_int
            set x = /y
            output std x\
        """.trimIndent())
        assert(out == "10\n") { out }
    }
    @Test
    fun i02_ptr_func () {
        val out = all("""
        var f : /([@_1,/_int@_1] -> ())
        set f = func [@_1,/_int@_1] -> () {
           set arg.2\ = _(*arg._2+1)
           return
        }
        var x: _int
        set x = _1
        call f\ [@local,/x]
        output std x
        """.trimIndent())
        assert(out == "2\n") { out }
    }
    @Test
    fun i03_ptr_func () {
        val out = all("""
            var f: /([@_1,/_int@_1]->_int)
            set f = func [@_1,/_int@_1]->_int { return arg.2\ }
            var g: /([@_1,/_int@_1]->_int)
            set g = f
            var x: _int
            set x = _10
            output std g \ [@local,(/x)]
        """.trimIndent())
        assert(out == "10\n") { out }
    }
    @Test
    fun i05_ptr_block_err () {
        val out = all("""
            var p1: /_int @local
            var p2: /_int @local
            {
                var v: _int; set v = _10
                set p1 = /v  -- no
            }
            {
                var v: _int; set v = _20
                set p2 = /v
            }
            output std p1\
        """.trimIndent())
        assert(out == "(ln 5, col 12): invalid assignment : type mismatch") { out }
    }
    @Test
    fun i06_ptr_block_err () {
        val out = all("""
            var x: _int; set x = _10
            var p: /_int
            {
                var y: _int; set y = _10
                set p = /x
                set p = /y  -- no
            }
        """.trimIndent())
        assert(out == "(ln 6, col 11): invalid assignment : type mismatch") { out }
    }
    @Test
    fun i07_ptr_func_ok () {
        val out = all("""
            var f : /([@_1,/_int@_1] -> /_int@_1)
            set f = func [@_1,/_int@_1] -> /_int@_1 {
                return arg.2
            }
            var v: _int
            set v = _10
            var p: /_int@local
            set p = f\ [@local,/v]
            output std p\
        """.trimIndent())
        assert(out == "10\n") { out }
    }
    @Test
    fun i08_ptr_func_ok () {
        val out = all("""
            var v: _int
            set v = _10
            var f : /(@_1 -> /_int@_1)
            set f = func (@_1) -> /_int@_1 {
                return /v
            }
            var p: /_int @local
            set p = f\ @local
            output std p\
        """.trimIndent())
        assert(out == "10\n") { out }
    }
    @Test
    fun i09_ptr_func_err () {
        val out = all("""
            var f : (@_1) -> /_int@_1; set f = func (@_1) -> /_int@_1 {
                var v: _int; set v = _10
                return /v   -- err
            }
        """.trimIndent())
        assert(out == "(ln 3, col 5): invalid return : type mismatch") { out }
    }
    @Test
    fun i10_ptr_func_err () {
        val out = all("""
            var f : [@_1,/_int@_1] -> /_int@_1
            set f = func [@_1,/_int@_1] -> /_int@_1 {
                var ptr: /_int
                set ptr = arg.2
                return ptr  -- err
            }
        """.trimIndent())
        assert(out == "(ln 5, col 5): invalid return : type mismatch") { out }
    }
    @Test
    fun i11_ptr_func_ok () {
        val out = all("""
            var f : /([@_1,/_int@_1] -> /_int@_1)
            set f = func [@_1,/_int@_1] -> /_int@_1 {
                var ptr: /_int@_1
                set ptr = arg.2
                return ptr
            }
            var v: _int
            set v = _10
            var p: /_int@local
            set p = f\ [@local,/v]
            output std p\
        """.trimIndent())
        assert(out == "10\n") { out }
    }
    @Test
    fun i12_ptr_ptr_ok () {
        val out = all("""
            var p: //_int @local @local
            var z: _int; set z = _10
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
            var p: /_int @local
            set p = /v.1
            set p\ = _20
            output std /v
        """.trimIndent())
        assert(out == "[20,20]\n") { out }
    }
    @Test
    fun i14_ptr_type_err () {
        val out = all("""
            var v: <_int>
            set v = <.1 _10>: <_int>
            var p: /_int @local
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
            set p\ = _20
            output std /v
        """.trimIndent())
        assert(out == "[20]\n") { out }
    }
    @Test
    fun i15_ptr_tup () {
        val out = all("""
            var x: _int; set x = _10
            var p: [_int,/_int @local]; set p = [_10:_int,/x]
            var v: _int; set v = _20
            set p.2 = /v
            output std p.2\
        """.trimIndent())
        assert(out == "20\n") { out }
    }
    @Test
    fun i16_ptr_tup () {
        val out = all("""
            var x: _int; set x = _10
            var p: [_int,/_int @local]; set p = [_10:_int,/x]
            var v: _int; set v = _20
            set p = [_10:_int,/v]
            output std p.2\
        """.trimIndent())
        assert(out == "20\n") { out }
    }
    @Test
    fun i17_ptr_type () {
        val out = all("""
            var x: _int
            set x = _10
            var p: </_int>
            set p = <.1 /x>: </_int @local>
            var v: _int
            set v = _20
            set p!1 = /v
            output std p!1\
        """.trimIndent())
        assert(out == "20\n")
    }
    @Test
    fun i18_ptr_type () {
        val out = all("""
            var x: _int
            set x = _10
            var p: </_int @local>
            set p = <.1 /x>: </_int>
            var v: _int
            set v = _20
            set p = <.1 /v>: </_int @local>
            output std p!1\
        """.trimIndent())
        assert(out == "20\n")
    }
    @Test
    fun i19_ptr_tup () {
        val out = all("""
            var x1: [_int,/_int @local]
            var v: _int; set v = _20
            var x2: [_int,/_int]; set x2 = [_10:_int,/v]
            set x1 = x2
            output std x1.2\
        """.trimIndent())
        assert(out == "20\n") { out }
    }
    @Test
    fun f09_ptr_type_err () {
        val out = all("""
            var x1: </_int @local>
            var v: _int
            set v = _20
            var x2: </_int @local>
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
            set v = _10
            var f : /([@_1,/_int@_1] -> /_int@_1)
            set f = func ([@_1,/_int@_1] -> /_int@_1) {
                return /v
            }
            {
                var p: /_int @local
                set p = f\ [@global,/v]: @global
                output std p\
            }
        """.trimIndent())
        assert(out == "10\n") { out }
    }
    @Test
    fun f11_ptr_func () {
        val out = all("""
            var f: /(([@_1,/_int@_1] -> /_int@_1))
            set f = func ([@_1,/_int@_1] -> /_int@_1) {
                return arg.2
            }
            var v: _int
            set v = _10
            var p: /_int @local
            set p = f\ [@local,/v]: @local
            output std p\
        """.trimIndent())
        assert(out == "10\n") { out }
    }
    @Test
    fun i20_ptr_uni_ok () {
        val out = all("""
            var uni: <_(char*),_int>
                set uni = <.1 _("oi")>: <_(char*),_int>
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
                set uni = <.1 _("oi")>: <_(char*),_int>
            var ptr: /_(char*) @local
                set ptr = /uni!1
            set uni = <.2 _65>
            call _puts ptr\
        """.trimIndent())
        //assert(out == "(ln 5, col 9): invalid assignment of \"uni\" : borrowed in line 4")
        assert(out == "(ln 4, col 20): unexpected operand to `/´") { out }
    }

    // REC

    @Test
    fun j00_list_err () {
        val out = all("""
            var l: /</^ @local> @local
            set l = <.0>: /</^ @local>
            output std l
        """.trimIndent())
        //assert(out == "(ln 2, col 7): invalid assignment : type mismatch") { out }
        assert(out == "<.0>\n") { out }
    }
    @Test
    fun j01_list () {
        val out = all("""
            var l: /</^ @local> @local
            set l = <.0>: /</^ @local>
            output std l
        """.trimIndent())
        //assert(out == "(ln 1, col 5): invalid assignment : type mismatch") { out }
        assert(out == "<.0>\n") { out }
    }
    @Test
    fun j02_list_new_err_dst () {
        val out = all("""
            var l: /</^ @local> @local
            set l = new <.1 <.0>: /</^>>:</^>
            output std l
        """.trimIndent())
        //assert(out == "(ln 1, col 21): invalid assignment : type mismatch") { out }
        assert(out == "<.1 <.0>>\n") { out }
    }
    @Test
    fun j02_list_new_err_dst2 () {
        val out = all("""
            var l: /</^ @global> @global
            set l = new <.1 <.0>: /</^ @global> @global>:</^ @global>: @global
            output std l
        """.trimIndent())
        //assert(out == "(ln 1, col 21): invalid assignment : type mismatch") { out }
        assert(out == "<.1 <.0>>\n") { out }
    }
    @Test
    fun j02_list_new_err_src () {
        val out = all("""
            var l: /</^ @local> @local
            set l = new _1 @local
            output std l
        """.trimIndent())
        assert(out == "(ln 2, col 9): invalid `new` : expected constructor") { out }
    }
    @Test
    fun j02_list_new_ok () {
        val out = all("""
            var l: /</^ @local> @local
            set l = new <.1 <.0>: /</^ @local>>:</^ @local>
            output std l
        """.trimIndent())
        assert(out == "<.1 <.0>>\n") { out }
    }
    @Test
    fun j02_list_pln () {
        val out = all("""
            var l: </^ @local>
            set l = <.1 <.0>:/</^ @local>@local>: </^ @local>
            output std /l
        """.trimIndent())
        //assert(out == "(ln 1, col 25): invalid constructor : expected `new`") { out }
        assert(out == "<.1 <.0>>\n") { out }
    }
    @Test
    fun j03_list () {
        val out = all("""
            var l: /</^ @local> @local
            var z: /</^ @local> @local
            set z = <.0>: /</^ @local> @local
            var one: /</^ @local> @local
            set one = new <.1 z>:</^ @local>: @local
            set l = new <.1 one>:</^ @local>: @local
            output std l\!1
        """.trimIndent())
        assert(out == "<.1 <.0>>\n") { out }
    }
    @Test
    fun j05_list_disc_null_err () {
        val out = all("""
            var l: /</^ @local> @local
            set l = <.0>: /</^ @local> @local
            output std l\!1
        """.trimIndent())
        assert(out == "out.exe: out.c:72: main: Assertion `&(*l) != NULL' failed.\n") { out }
    }
    @Test
    fun j06_list_1 () {
        val out = all("""
            var l: /</^ @local> @local
            var z: /</^ @local> @local
            set z = <.0>: /</^ @local> @local
            var one: /</^ @local> @local
            set one = new <.1 z>:</^ @local>: @local
            set l = new <.1 one>:</^ @local>: @local
            var p: //</^ @local> @local @local
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
            var l: /</^ @local> @local
            var z: /</^ @local> @local
            set z = <.0>: /</^ @local> @local
            var one: /</^ @local> @local
            set one = new <.1 z>:</^ @local>: @local
            set l = new <.1 one>:</^ @local>: @local
            var p: /</^ @local> @local
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
            var l: /</^ @local> @local
            var z: /</^ @local> @local
            set z = <.0>: /</^ @local> @local
            var one: /</^ @local> @local
            set one = new <.1 z>:</^ @local>: @local
            set l = new <.1 one>:</^ @local>: @local
            var p: //</^ @local> @local @local
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
            var l1: /</^ @local> @local
            set l1 = new <.1 <.0>: /</^ @local> @local>:</^ @local>: @local
            var l2: /</^ @local> @local
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
            set l1 = new <.1 <.0>>: @local
            var l2: /</^>
            set l2 = l1
        """.trimIndent())
        //assert(out == "(ln 4, col 8): invalid assignment : type mismatch") { out }
        assert(out == "(ln 1, col 11): expected type : have `?´") { out }
    }
    @Test
    fun j08_list_move () {
        val out = all("""
            var l1: /</^ @local> @local
            set l1 = new <.1 <.0>: /</^ @local> @local>:</^ @local>: @local
            var l2: /</^ @local> @local
            set l2 = <.0>: /</^ @local> @local
            set l2 = new <.1 l1>:</^ @local>: @local
            output std l2
        """.trimIndent())
        assert(out == "<.1 <.1 <.0>>>\n") { out }
    }
    @Test
    fun j09_list_move () {
        val out = all("""
            var l1: /</^ @local> @local
            set l1 = new <.1 <.0>: /</^ @local> @local>:</^ @local>: @local
            var l2: [_int,/</^ @local> @local]
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
            var z: /</^ @local> @local
            set z = <.0>: /</^ @local> @local
            var one: /</^ @local> @local
            set one = new <.1 z>:</^ @local>

            var n: </</^ @local> @local>
            set n = <.1 one>:</</^ @local> @local>
            output std /n
        """.trimIndent())
        assert(out == "<.1 <.1 <.0>>>\n") { out }
    }
    @Test
    fun j11_borrow_1 () {
        val out = all("""
            var z: /</^ @local> @local
            set z = <.0>: /</^ @local> @local
            var one: /</^ @local> @local
            set one = new <.1 z>:</^ @local>

            var x: /</^ @local> @local
            set x = new <.1 one>:</^ @local>
            var y: //</^ @local> @local @local
            set y = /x
            output std y\
        """.trimIndent())
        assert(out == "<.1 <.1 <.0>>>\n") { out }
    }
    @Test
    fun j11_borrow_2 () {
        val out = all("""
            var z: /</^ @local> @local
            set z = <.0>: /</^ @local> @local
            var one: /</^ @local> @local
            set one = new <.1 z>:</^ @local>

            var x: /</^ @local> @local
            set x = new <.1 one>:</^ @local>
            var y: //</^ @local> @local @local
            set y = /x --!1
            output std y\
        """.trimIndent())
        assert(out == "<.1 <.1 <.0>>>\n") { out }
    }
    @Disabled // TODO: explicit type
    @Test
    fun j11_rec_double () {
        val out = all("""
            var n: /<</^^ @local>> @local
            set n = new <.1 <.1 new <.1 <.1 <.0>>>>>
            output std n
        """.trimIndent())
        assert(out == "<.1 <.1 <.1 <.1 <.0>>>>>\n") { out }
    }
    @Test
    fun j11_rec_double2 () {
        val out = all("""
            var n: /<</^^ @local>> @local
            set n = <.0>: /<</^^ @local>> @local
            output std n
        """.trimIndent())
        assert(out == "<.0>\n") { out }
    }
    @Test
    fun j09_tup_list_err () {
        val out = all("""
            var t: [_int,/</^ @local> @local]
            set t = [_10:_int, new <.1 <.0>:/</^ @local> @local>:</^ @local>]
            output std /t
        """.trimIndent())
        assert(out == "[10,<.1 <.0>>]\n") { out }
    }
    @Test
    fun j10_tup_copy_ok () {
        val out = all("""
            var l: /</^ @local> @local
            set l = <.0>: /</^ @local> @local
            var t1: [/</^ @local> @local]
            set t1 = [l]
            var t2: [/</^ @local> @local]
            set t2 = [t1.1]
            output std /t2
        """.trimIndent())
        assert(out == "[<.0>]\n") { out }
    }
    @Test
    fun j11_tup_move_ok () {
        val out = all("""
            var l: /</^ @local> @local
            set l = <.0>: /</^ @local> @local
            var t1: [/</^ @local> @local]
            set t1 = [l]
            var t2: [/</^ @local> @local]
            set t2 = [t1.1]
            output std /t2
        """.trimIndent())
        assert(out == "[<.0>]\n") { out }
    }
    @Test
    fun j11_tup_copy_ok () {
        val out = all("""
            var l: /</^ @local> @local
            set l = new <.1 <.0>: /</^ @local> @local>:</^ @local>
            var t1: [/</^ @local> @local]
            set t1 = [l]
            var t2: [/</^ @local> @local]
            set t2 = [t1.1]
            output std /t2
        """.trimIndent())
        assert(out == "[<.1 <.0>>]\n") { out }
    }
    @Disabled // TODO: explicit type
    @Test
    fun j12_tup_copy_ok () {
        val out = all("""
            var l: /<(),/^ @local> @local
            set l = new <.2 new <.1>: @local>
            var t1: [/<(),/^ @local> @local]
            set t1 = [l]
            var t2: [/<(),/^ @local> @local]
            set t2 = [t1.1]
            output std /t2
        """.trimIndent())
        assert(out == "[<.2 <.1>>]\n") { out }
    }
    @Disabled // TODO: explicit type
    @Test
    fun j13_tup_copy_ok () {
        val out = all("""
            var l: /<(),/^ @local> @local
            set l = new <.2 new <.1>: @local>: @local
            var t1: [(),/<(),/^ @local> @local]
            set t1 = [(), l]
            var t2: [(),/<(),/^ @local> @local]
            set t2 = [(), t1.2]
            output std /t2
        """.trimIndent())
        assert(out == "[(),<.2 <.1>>]\n") { out }
    }
    @Test
    fun j14_tup_copy_ok () {
        val out = all("""
            var l1: /</^ @local> @local
            set l1 = <.0>: /</^ @local> @local
            var l2: /</^ @local> @local
            set l2 = l1
            output std l2
        """.trimIndent())
        assert(out == "<.0>\n") { out }
    }
    @Test
    fun j15_tup_copy_ok () {
        val out = all("""
            var l1: /</^ @local> @local
            set l1 = new <.1 <.0>: /</^ @local> @local>:</^ @local>
            var l2: /</^ @local> @local
            set l2 = l1
            output std l2
        """.trimIndent())
        assert(out == "<.1 <.0>>\n") { out }
    }
    @Test
    fun j16_tup_move_copy_ok () {
        val out = all("""
            var l1: /</^ @local> @local
            set l1 = new <.1 <.0>: /</^ @local> @local>:</^ @local>: @local
            var l2: /</^ @local> @local
            set l2 = new <.1 l1>:</^ @local>
            var t3: [(),/</^ @local> @local]
            set t3 = [(), new <.1 l2\!1>:</^ @local>]
            output std l1
            output std /t3
        """.trimIndent())
        assert(out == "<.1 <.0>>\n[(),<.1 <.1 <.0>>>]\n") { out }
    }
    @Disabled // TODO: explicit type
    @Test
    fun j17_uni_rec () {
        val out = all("""
            var v1: /<(),/<[/^^ @local,/^ @local]> @local> @local
            set v1 = new <.2 <.0>>: @local
            var v2: /<(),/<[/^^ @local,/^ @local]> @local> @local
            set v2 = new <.2 new <.1 [new <.1>: @local,<.0>]>: @local>: @local
            output std v1
            output std v2
        """.trimIndent())
        assert(out == "<.2 <.0>>\n<.2 <.1 [<.1>,<.0>]>>\n") { out }
    }
    @Test
    fun j18_tup_copy_rec_ok () {
        val out = all("""
            var l1: [/</^ @local> @local]
            set l1 = [new <.1 <.0>:/</^ @local> @local>:</^ @local>: @local]
            var l2: [/</^ @local> @local]
            set l2 = l1
            output std /l2
        """.trimIndent())
        assert(out == "[<.1 <.0>>]\n") { out }
    }
    @Test
    fun j19_consume_ok () {
        val out = all("""
            var x: /</^ @local> @local
            set x = new <.1 <.0>: /</^ @local> @local>:</^ @local>: @local
            set x = x
            output std x
        """.trimIndent())
        assert(out == "<.1 <.0>>\n") { out }
    }
    @Test
    fun j20_consume_ok () {
        val out = all("""
            var x: /</^ @local> @local
            set x = new <.1 <.0>: /</^ @local> @local>:</^ @local>: @local
            var y: /</^ @local> @local
            if _1 {
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
            set xy.1 = _20
            var x: _int; set x = xy.1
            var y: _int; set y = xy.2
            var v: _int; set v = _(x+y)
            output std v
        """.trimIndent())
        assert(out == "30\n") { out }
    }
    @Test
    fun k02_var_union () {
        val out = all("""
            var c: _int; set c = arg.1\!1.1
        """.trimIndent())
        assert(out == "(ln 1, col 22): undeclared variable \"arg\"") { out }
    }

    // UNION SELF POINTER / HOLD

    @Disabled // TODO: explicit type
    @Test
    fun l01_hold_ok () {
        val out = all("""
            var x: /< [<(),//^^ @local @local>,/^ @local]> @local
            set x = new <.1 [<.1>,<.0>]>: @local
            var y: /< [<(),//^^ @local @local>,/^ @local]> @local
            set y = new <.1 [<.1>,x]>: @local
            set y\!1.2\!1.1 = <.1>
            output std y
        """.trimIndent())
        assert(out == "<.1 [<.1>,<.1 [<.1>,<.0>]>]>\n") { out }
        //assert(out == "(ln 1, col 16): invalid type declaration : unexpected `^´") { out }
    }
    @Test
    fun l02_hold_ok () {
        val out = all("""
            var x: /< [<(),//^^ @local @local>,_int,/^ @local]> @local
            var z: /< [<(),//^^ @local @local>,_int,/^ @local]> @local
            set z = <.0>: /< [<(),//^^ @local @local>,_int,/^ @local]> @local
            set x = new <.1 [z,_1,new <.1 [z,_2,z]>:< [<(),//^^ @local @local>,_int,/^ @local]>: @local]>:< [<(),//^^ @local @local>,_int,/^ @local]>: @local
            set x!1.3!1.1 = <.1 /x>: <(),//< [<(),//^^ @local @local>,_int,/^ @local]> @local @local>
            set x!1.1 = <.1 /x!1.3>: <(),//< [<(),//^^ @local @local>,_int,/^ @local]> @local @local> -- err: address of field inside union
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
            var x: /< [<(),/^^ @local>,_int,/^ @local]> @local
            var z: /< [<(),/^^ @local>,_int,/^ @local]> @local
            set z = <.0>: /< [<(),/^^ @local>,_int,/^ @local]> @local
            var o: <(),/< [<(),/^^ @local>,_int,/^ @local]> @local>
            set o = <.1>: <(),/< [<(),/^^ @local>,_int,/^ @local]> @local>
            set x = new <.1 [o,_1:_int,new <.1 [o,_2:_int,z]>:< [<(),/^^ @local>,_int,/^ @local]>]>:< [<(),/^^ @local>,_int,/^ @local]>
            set x\!1.3\!1.1 = <.2 x>: <(),/< [<(),/^^ @local>,_int,/^ @local]> @local>
            set x\!1.1 = <.2 x\!1.3>: <(),/< [<(),/^^ @local>,_int,/^ @local]> @local>
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
            var z: /< [<(),//^^ @local @local>,_int,/^ @local]> @local
            set z = <.0>: /< [<(),//^^ @local @local>,_int,/^ @local]> @local
            var o: <(),//< [<(),//^^ @local @local>,_int,/^ @local]> @local @local>
            set o = <.1>: <(),//< [<(),//^^ @local @local>,_int,/^ @local]> @local @local>

            var x: /< [<(),//^^ @local @local>,_int,/^ @local]> @local
            set x = new <.1 [o,_2:_int,z]>:< [<(),//^^ @local @local>,_int,/^ @local]>: @local
            output std x
        """.trimIndent())
        assert(out == "<.1 [<.1>,2,<.0>]>\n") { out }
        //assert(out == "(ln 1, col 14): invalid type declaration : unexpected `^´") { out }
    }
    @Disabled // TODO: explicit type
    @Test
    fun l03_hold_err () {
        val out = all("""
            var x: /< [<(),//^^ @local @local>,/^ @local]> @local
            set x = new <.1 [<.1>,new <.1 [<.1>,<.0>]> @local]> @local
            set x\!1.2 = <.0>
            output std x
        """.trimIndent())
        //assert(out == "(ln 1, col 14): invalid type declaration : unexpected `^´") { out }
        //assert(out == "out.exe: out.c:133: main: Assertion `(*(x))._1._2 == NULL' failed.\n") { out }
        assert(out == "<.1 [<.1>,<.0>]>\n") { out }
    }
    @Test // TODO: esse vai falhar enquanto nao voltar isnullptr
    fun l04_ptr_null () {
        val out = all("""
            var n: _int
            set n = _10
            var x: <(),/_int @local>
            set x = <.2 /n>: <(),/_int @local>
            output std x!2\
        """.trimIndent())
        assert(out == "10\n") { out }
    }

    // ALLOC / SCOPE / NEWS

    @Test
    fun m01_scope_a () {
        val out = all("""
            { @a
                var pa: /</^ @local> @local
                var f: /(()->() [@a])
                set f = func ()->() [@a] {
                    var pf: /</^ @a> @a
                    set pf = new <.1 <.0>: /</^ @a> @a>:</^ @a>: @a
                    set pa = pf
                }
                call f\
                output std pa
            }
        """.trimIndent())
        assert(out == "<.1 <.0>>\n") { out }
    }
    @Test
    fun m01_scope_f () {
        val out = all("""
            var f: /([@_1,/</^@_1>@_1]->())
            set f = func [@_1,/</^@_1>@_1]->() {
                var pf: /</^@_1> @_1
                set pf = arg.2
                output std pf
            }
            {
                var x: /</^ @local> @local
                set x = new <.1 <.0>: /</^ @local> @local>:</^ @local>
                call f\ [@local,x]
            }
        """.trimIndent())
        assert(out == "<.1 <.0>>\n") { out }
    }
    @Test
    fun m02_scope_f () {
        val out = all("""
            var f: /([@_1,/</^@_1>@_1]->())
            set f = func [@_1,/</^@_1>@_1]->() {
                set arg.2\!1 = new <.1 <.0>:/</^@_1>@_1>:</^@_1>: @_1
            }
            {
                var x: /</^ @local> @local
                set x = new <.1 <.0>: /</^ @local> @local>:</^ @local>
                call f\ [@local,x]
                output std x
            }
        """.trimIndent())
        assert(out == "<.1 <.1 <.0>>>\n") { out }
    }
    @Test
    fun m03_scope_f () {
        val out = all("""
            var f: /([@a_1,@a_2,/</^@a_1>@a_1,/</^@a_2>@a_2]->())
            set f = func [@a_1,@a_2,/</^@a_1>@a_1,/</^@a_2>@a_2]->() {
                set arg.3\!1 = new <.1 <.0>:/</^@a_1>@a_1>:</^@a_1>: @a_1
                set arg.4\!1 = new <.1 <.0>:/</^@a_2>@a_2>:</^@a_2>: @a_2
            }
            {
                var x: /</^ @local> @local
                set x = new <.1 <.0>: /</^ @local> @local>:</^ @local>
                call f\ [@local,@local,x,x]
                output std x
            }
        """.trimIndent())
        assert(out == "<.1 <.1 <.0>>>\n") { out }
    }
    @Test
    fun m04_scope_f () {
        val out = all("""
            var f: /([@_1,/</^@_1>@_1]->())
            set f = func [@_1,/</^@_1>@_1]->() {
                set arg.2\!1 = new <.1 <.0>:/</^@_1>@_1>:</^@_1>: @_1
            }
            {
                var x: /</^ @local> @local
                set x = new <.1 <.0>: /</^ @local> @local>:</^ @local>
                call f\ [@local,x]
                output std x
            }
        """.trimIndent())
        assert(out == "<.1 <.1 <.0>>>\n") { out }
    }

    // FUNC / POOL

    @Test
    fun n01_pool () {
        val out = all("""
            var f : [@a_1,/()@a_1] -> /()@a_1
            output std ()
        """.trimIndent()
        )
        assert(out == "()\n") { out }
    }
    @Test
    fun n02_pool () {
        val out = all("""
            var f : /([@a_1,/()@a_1] -> /()@a_1)
            set f = func [@a_1,/()@a_1] -> /()@a_1 {
                return arg.2
            }
            output std ()
        """.trimIndent()
        )
        assert(out == "()\n") { out }
    }
    @Test
    fun n03_pool () {
        val out = all("""
            var f :/( [@_1,/_int@_1] -> /()@_1)
            set f = func [@_1,/_int@_1] -> /()@_1 {
                return arg.2
            }
            var x: _int
            call f\ [@local,/x]
            output std ()
        """.trimIndent()
        )
        assert(out == "()\n") { out }
    }
    @Test
    fun n04_pool () {
        val out = all("""
            var f : /([@_1,/_int@_1] -> /()@_1)
            set f = func [@_1,/_int@_1] -> /()@_1 {
                return arg.2
            }
            var g : /([@_1,/_int@_1] -> /()@_1)
            set g = func [@_1,/_int@_1] -> /()@_1 {
                return f\ [@_1,arg.2]: @_1
            }
            var x: _int
            var px: /_int@local
            set px = call f\ [@local,/x]: @local
            output std _(px == &x):_int
        """.trimIndent()
        )
        assert(out == "1\n") { out }
    }
    @Test
    fun n05_pool_ff() {
        val out = all(
            """
            var f: /( () -> () )
            set f = func () -> () {
                return arg
            }
            var g: /([@_1, /(()->())@_1, ()] -> ())
            set g = func [@_1, /(()->())@_1, ()] -> () {
                return arg.2\ arg.3
            }
            output std g\ [@local,f,()]
        """.trimIndent()
        )
        assert(out == "()\n") { out }
    }
    @Test
    fun n06_pool_ff() {
        val out = all(
            """
            var f: /([@_1,/()@_1] -> /()@_1)
            set f = func [@_1,/()@_1] -> /()@_1 {
                return arg.2
            }
            var g: /([@_1, /([@_1,/()@_1]->/()@_1)@_1, /()@_1] -> /()@_1)
            set g = func [@_1, /([@_1,/()@_1]->/()@_1)@_1, /()@_1] -> /()@_1 {
                return arg.2\ [@_1,arg.3]: @_1
            }
            var x: ()
            output std g\ [@local,f,/_x]: @local
        """.trimIndent()
        )
        assert(out == "_\n") { out }
    }
    @Test
    fun n07_pool_closure() {
        val out = all(
            """
            var g: /(@a_1 -> /(@a_1->())@a_1)
            set g = func @a_1 -> /(@a_1->())@a_1 {
                var f: /(@a_1 -> ())@a_1
                set f = func @a_1 -> () {
                    output std ()
                }
               return f
            }
            var f: /(@a_1 -> ())
            set f = call g\ @local
            call f\ @local
        """.trimIndent()
        )
        assert(out == "()\n") { out }
    }
    @Test
    fun n08_pool_closure() {
        val out = all(
            """
            var g: /(@_1 -> /(@_1->())@_1)
            set g = func @_1 -> /(@_1->())@_1 {
                var f: /(@b_1 -> () [@b_1])
                var x: /</^@_1>@_1
                set x = new <.1 <.0>:/</^@_1>@_1>: </^@_1>: @_1
                set f = func @b_1 -> () [@b_1] {
                    output std x
                }
               return f
            }
            var f:/( @_1 -> () )
            set f = call g\ @local
            call f \ @local
        """.trimIndent()
        )
        assert(out == "<.1 <.0>>\n") { out }
    }
    @Test
    fun n09_pool_closure_err() {
        val out = all(
            """
            var g: /(@_1 -> (@_1->()))
            set g = func @_1 -> (@_1->()) {
                var f: /(@a_1 -> ())
                var x: /</^@local>@local
                set x = new <.1 <.0>:/</^@local>@local>: </^@local>
                set f = func @a_1 -> () {
                    output std x    -- f uses x in @local
                }
               return f             -- cannot return f which uses x in @local
            }
            var f:/( @_1 -> () )
            set f = call g\ @local
            call f\ @local
        """.trimIndent()
        )
        assert(out == "(ln 7, col 20): invalid access to \"x\" : invalid closure declaration (ln 6)") { out }
    }

    // ALL

    @Test
    fun z01 () {
        val out = all("""
        var inv : /(<(),()> -> <(),()>)
        set inv = func (<(),()> -> <(),()>) {
            if arg?1 {
                return <.2>:<(),()>
            } else {
                return <.1>:<(),()>
            }
        }
        var a: <(),()>
        set a = <.2>: <(),()>
        var b: <(),()>
        set b = inv\ a
        output std /b
        """.trimIndent())
        assert(out == "<.1>\n") { out }
    }
    @Test
    fun z02 () {
        val out = all("""
        var i: _int; set i = _1
        var n: _int; set n = _0
        loop {
            set n = _(n + i)
            set i = _(i + 1)
            if _(i > 5) {
                break
            }
        }
        output std n
        """.trimIndent())
        assert(out == "15\n")
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
        if _0 {
        } else {
            output std ()
        }
        if _1 {
            output std ()
        } else {
        }
        """.trimIndent())
        assert(out == "()\n()\n")
    }
    @Test
    fun z05_func_rec () {
        val out = all("""
        var i: _int; set i = _0
        var f: /(()->()); set f = func ()->() {
            if _(i == 10) {
                return
            } else {
                set i = _(i + 1)
                return f\ ()
            }
        }
        call f\ ()
        output std i
        """.trimIndent())
        assert(out == "10\n") { out }
    }
    @Test
    fun z06_type_complex () {
        val out = all("""
            var x: /<[(),/^ @local]> @local
            set x = new <.1 [(),<.0>: /<[(),/^ @local]>@local]>:<[(),/^ @local]>
            var y: [(),/<[(),/^ @local]> @local]
            set y = [(), new <.1 [(),<.0>: /<[(),/^ @local]>@local]>:<[(),/^ @local]>]
            var z: [(),//<[(),/^ @local]> @local @local]
            set z = [(), /x]
            output std z.2\\!1.2\!0
        """.trimIndent())
        assert(out == "()\n") { out }
    }
    @Test
    fun z07_type_complex_bug () {
        val out = all("""
            var x: /<[(),/^ @local]> @local
            set x = <.0>: /<[(),/^ @local]> @local
            var z: [(),//<[(),/^ @local]> @local @local]
            set z = [(), /x]
            output std z.2\\!0
        """.trimIndent())
        assert(out == "()\n") { out }
    }
    @Test
    fun z07_type_complex () {
        val out = all("""
            var x: /<[(),/^ @local]> @local
            set x = <.0>: /<[(),/^ @local]> @local
            var z: [(),//<[(),/^ @local]> @local @local]
            set z = [(), /x]
            output std z.2\\!0
        """.trimIndent())
        assert(out == "()\n") { out }
    }
    @Test
    fun zxx_type_complex () {
        val out = all("""
            var z: /</^ @local> @local
            set z = <.0>: /</^ @local> @local
            output std z\!0
        """.trimIndent())
        assert(out == "()\n") { out }
    }
    @Test
    fun z08_type_complex () {
        val out = all("""
            var y: [(),/<[(),/^ @local]> @local]
            set y = [(), new <.1 [(),<.0>:/<[(),/^ @local]> @local]>:<[(),/^ @local]>]
            output std /y
        """.trimIndent())
        assert(out == "[(),<.1 [(),<.0>]>]\n") { out }
    }
    @Test
    fun z08_func_arg () {
        val out = all("""
            var x1: /</^ @local> @local
            set x1 = <.0>: /</^ @local> @local
            var y1: _int
            set y1 = x1\?0
            var x2: //</^ @local> @local @local
            set x2 = /x1
            var y2: _int
            set y2 = x2\\?1
            set x2\ = new <.1 <.0>: /</^ @local> @local>:</^ @local>
            var f: /([@_1,//</^@_1>@_1@_1]->_int)
            set f = func [@_1,//</^@_1>@_1@_1]->_int {
                return arg.2\\?1
            }
            var y3: _int
            set y3 = f\ [@local,x2]
            var ret: _int
            set ret = _(y1 + y2 + y3)
            output std ret
        """.trimIndent())
        assert(out == "2\n") { out }
    }
    @Test
    fun z08_func_alt () {
        val out = all("""
            var x1: /</^ @local> @local
            set x1 = <.0>: /</^ @local> @local
            var x2: //</^ @local> @local @local
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
            var f: /( ()->() )
            set f = func ()->() {
                var s1: /<[_int,/^ @local]> @local
                set s1 = new <.1 [_1:_int,<.0>: /<[_int,/^ @local]> @local]>:<[_int,/^ @local]>: @local
                output std s1
            }
            call f\
        """.trimIndent())
        assert(out == "<.1 [1,<.0>]>\n") { out }
    }
    @Test
    fun z10_output_string () {
        val out = all("""
            var f: /(()->())
            set f = func ()->() {
                var s1: /<[_int,/^ @local]> @local
                set s1 = new <.1 [_1:_int,<.0>: /<[_int,/^ @local]> @local]>:<[_int,/^ @local]>: @local
                output std s1
            }
            call f\
        """.trimIndent())
        assert(out == "<.1 [1,<.0>]>\n") { out }
    }
    @Test
    fun z10_return_move () {
        val out = all("""
            var f: /((@_1)-><(),_int,/<[_int,/^@_1]>@_1>)
            set f = func (@_1)-><(),_int,/<[_int,/^@_1]>@_1> {
                var str: /<[_int,/^@_1]> @_1
                set str = <.0>: /<[_int,/^@_1]> @_1
                return <.3 str>:<(),_int,/<[_int,/^@_1]>@_1>
            }
            var x: <(),_int,/<[_int,/^@local]>@local>
            set x = call f\ (@local)
            output std x!3
        """.trimIndent())
        assert(out == "<.0>\n") { out }
    }
    @Test
    fun z11_func_err () {
        val out = all("""
            var f: ()->[()]; set f = func ()->() {
            }
        """.trimIndent())
        assert(out == "(ln 1, col 24): invalid assignment : type mismatch") { out }
    }
    @Test
    fun z12_union_tuple () {
        val out = all("""
            var tk2: <(),_int,/<[_int,/^ @local]> @local>
            set tk2 = <.3 <.0>:/<[_int,/^ @local]> @local>: <(),_int,/<[_int,/^ @local]> @local>
            var s21: /<(),_int,/<[_int,/^ @local]> @local> @local
            set s21 = /tk2
            output std s21\!3
        """.trimIndent())
        assert(out == "<.0>\n") { out }
    }
    @Disabled // TODO: explicit type
    @Test
    fun z13_union_rec () {
        val out = all("""
            var x: /<(),/^ @local> @local
            set x = new <.2 new <.1>: @local>: @local
            output std x
        """.trimIndent())
        assert(out == "<.2 <.1>>\n") { out }
    }
    @Disabled // TODO: explicit type
    @Test
    fun z14_acc_aft_move () {
        val out = all("""
            var x: /<(),/^ @local> @local
            set x = new <.2 new <.1>: @local>: @local
            var y: /<(),/^ @local> @local
            set y = x
            output std x
            output std y
        """.trimIndent())
        assert(out == "<.2 <.1>>\n<.2 <.1>>\n") { out }
    }
    @Test
    fun z15_acc_move_sub () {
        val out = all("""
            var x: /<(),/^ @local> @local
            set x = new <.2 new <.1>:<(),/^ @local>: @local>:<(),/^ @local>: @local
            var y: /<(),/^ @local> @local
            set y = x\!2
            output std x
            output std y
        """.trimIndent())
        assert(out == "<.2 <.1>>\n<.1>\n") { out }
    }
    @Disabled // TODO: explicit type
    @Test
    fun z16_acc_move_sub () {
        val out = all("""
            var x: /<(),[(),/^ @local]> @local
            set x = new <.2 [(),new <.1>: @local]>: @local
            var y: [(),/<(),[(),/^ @local]> @local]
            set y = [(), x\!2.2]
            output std x
            output std /y
        """.trimIndent())
        assert(out == "<.2 [(),<.1>]>\n[(),<.1>]\n") { out }
    }
}