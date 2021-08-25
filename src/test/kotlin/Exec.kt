import org.junit.jupiter.api.MethodOrderer.Alphanumeric
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.io.File

fun exec (cmd: String): Pair<Boolean,String> {
    val p = ProcessBuilder(cmd.split(' '))
        //.redirectOutput(ProcessBuilder.Redirect.PIPE)
        .redirectErrorStream(true)
        .start()
    val ret = p.waitFor()
    val str = p.inputStream.bufferedReader().readText()
    return Pair(ret==0, str)
}

@TestMethodOrder(Alphanumeric::class)
class Exec {

    fun all (inp: String): String {
        val (ok1,out1) = All_inp2c(inp)
        if (!ok1) {
            return out1
        }
        File("out.c").writeText(out1)
        val (ok2,out2) = exec("gcc out.c -o out.exe")
        if (!ok2) {
            return out2
        }
        val (_,out3) = exec("./out.exe")
        //val (_,out3) = exec("valgrind ./out.exe")
        return out3
    }

    @Test
    fun a01_output () {
        val out = all("output std ()")
        assert(out == "()\n")
    }
    @Test
    fun a02_var () {
        val out = all("""
            var x: () = ()
            output std x
        """.trimIndent())
        assert(out == "()\n")
    }
    @Test
    fun a03_error () {
        val out = all("//output std ()")
        assert(out == "(ln 1, col 1): expected statement : have `/´")
    }
    @Test
    fun a05_int () {
        val out = all("""
            var x: _int = _10
            output std x
        """.trimIndent())
        println(out)
        assert(out == "10\n")
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
            var x: _int = _abs _(-1)
            output std x
        """.trimIndent())
        assert(out == "1\n")
    }
    @Test
    fun a10_int_set () {
        val out = all("""
            var x: _int = _10
            set x = _(-20)
            output std x
        """.trimIndent())
        assert(out == "-20\n")
    }
    @Test
    fun a11_unk () {
        val out = all("""
            var x: _int = ?
            set x = _(-20)
            output std x
        """.trimIndent())
        assert(out == "-20\n")
    }
    @Test
    fun a12_set () {
        val out = all("""
            var x: () = ()
            set x = ()
            output std x
        """.trimIndent())
        assert(out == "()\n")
    }

    // TUPLES

    @Test
    fun b01_tuple_units () {
        val out = all("""
            var x: [(),()] = [(),()]
            var y: () = x.1
            call _output_std_Unit y
        """.trimIndent())
        assert(out == "()\n")
    }
    @Test
    fun b02_tuple_idx () {
        val out = all("""
            output std ([(),()].1)
        """.trimIndent())
        assert(out == "()\n")
    }
    @Test
    fun b03_tuple_tuples () {
        val out = all("""
            var v: [(),()] = [(),()]
            var x: [(),[(),()]] = [(),v]
            var y: [(),()] = x.2
            var z: () = y.2
            output std z
        """.trimIndent())
        assert(out == "()\n")
    }
    @Test
    fun b04_tuple_pp () {
        val out = all("""
            var n: _int = _1
            var x: [[_int,_int],[_int,_int]] = [[n,n],[n,n]]
            output std x
        """.trimIndent())
        println(out)
        assert(out == "[[1,1],[1,1]]\n")
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
        assert(out == "a")
    }
    @Test
    fun c02_nat () {
        val out = all("""
            var y: _(char*) = _("hello")
            var n: _int = _10
            var x: [_int,_(char*)] = [n,y]
            call _puts x.2
        """.trimIndent())
        assert(out == "hello\n")
    }
    @Test
    fun c03_nat () {
        val out = all("""
            var y: _(char*) = _("hello")
            var n: _int = _10
            var x: [_int,_(char*)] = [n,y]
            output std x
        """.trimIndent())
        assert(out == "[10,\"hello\"]\n")
    }

    // FUNC / CALL

    @Test
    fun d01_f_int () {
        val out = all("""
            func f: _int -> _int { return arg }
            var x: _int = call f _10
            output std x
        """.trimIndent())
        assert(out == "10\n")
    }
    @Test
    fun d02_f_unit () {
        val out = all("""
            func f: () -> () { return }
            var x: () = call f
            output std x
        """.trimIndent())
        assert(out == "()\n")
    }
    @Test
    fun d03_fg () {
        val out = all("""
            func f: () -> () { var x: _int = _10 ; output std x }
            func g: () -> () { return f () }
            call g ()
        """.trimIndent())
        assert(out == "10\n")
    }
    @Test
    fun d04_arg () {
        val out = all("""
        func f : _int -> _int {
           set arg = _(arg+1)
           return arg
        }
        output std f _1
        """.trimIndent())
        assert(out == "2\n")
    }
    @Test
    fun d05_func_var () {
        val out = all("""
        func f: _int->_int { return arg }
        var p: _int->_int = f
        output std p _10
        """.trimIndent())
        assert(out == "10\n")
    }
    @Test
    fun d06_func_fg () {
        val out = all("""
            func f: _int->_int { return arg }
            func g: [_int->_int, _int] -> _int {
               var f: _int->_int = arg.1
               var v: _int = call f arg.2
               return v
            }
            output std g [f,_10]
        """.trimIndent())
        assert(out == "(ln 3, col 8): invalid declaration : \"f\" is already declared (ln 1)")
    }
    @Test
    fun d07_func_fg () {
        val out = all("""
            func f: _int->_int { return arg }
            func g: [_int->_int, _int] -> _int {
               var fx: _int->_int = arg.1
               var v: _int = call fx arg.2
               return v
            }
            --var n: _int = _10
            output std g [f,_10]
        """.trimIndent())
        println(out)
        assert(out == "10\n")
    }

    // OUTPUT

    @Test
    fun e01_out () {
        val out = all("""
            output () ()
        """.trimIndent())
        assert(out == "(ln 1, col 8): expected function")
    }
    @Test
    fun e02_out () {
        val out = all("""
            var x: [(),()] = [(),()]
            output std x
        """.trimIndent())
        assert(out == "[(),()]\n")
    }
    @Test
    fun e03_out () {
        val out = all("""
            func output_f: _int -> () { output std arg }
            output f _10
        """.trimIndent())
        assert(out == "10\n")
    }

    // USER

    @Test
    fun f01_bool () {
        val out = all("""
            var b : <(),()> = <.1>
            output std b
        """.trimIndent())
        println(out)
        assert(out == "<.1>\n")
    }
    @Test
    fun f02_xyz () {
        val out = all("""
            var z : <()> = <.1()>
            var y : <<()>> = <.1 z>
            var x : <<<()>>> = <.1 y>
            var yy: <<()>> = x!1
            var zz: <()> = yy!1
            output std zz
        """.trimIndent())
        println(out)
        assert(out == "<.1>\n")
    }
    @Test
    fun f05_user_big () {
        val out = all("""
            var s: <[_int,_int,_int,_int],_int,_int> = <.1 [_1,_2,_3,_4]>
            output std s
        """.trimIndent())
        assert(out == "<.1 [1,2,3,4]>\n")
    }
    @Test
    fun f06_user_big () {
        val out = all("""
            var x: <[<()>,<()>]> = <.1 [<.1>,<.1>]>
            output std x
        """.trimIndent())
        println(out)
        assert(out == "<.1 [<.1>,<.1>]>\n")
    }
    @Test
    fun f07_user_pred () {
        val out = all("""
            var z: <()> = <.1>
            output std z?1
        """.trimIndent())
        assert(out == "1\n")
    }
    @Test
    fun f10_user_disc () {
        val out = all("""
            var z: <(),()> = <.2 ()>
            output std z!2
        """.trimIndent())
        assert(out == "()\n")
    }
    @Test
    fun f11_user_disc_err () {
        val out = all("""
            var z: <(),()> = <.2>
            output std z!1
        """.trimIndent())
        assert(out == "out.exe: out.c:48: main: Assertion `z.tag == 1' failed.\n")
    }
    @Test
    fun f12_user_disc_pred_idx () {
        val out = all("""
            var v: <[<()>,()]> = <.1 [<.1>,()]>
            output std v!1.1?1
        """.trimIndent())
        assert(out == "1\n")
    }
    @Test
    fun f13_user_disc_pred_err () {
        val out = all("""
            output std ()?1
        """.trimIndent())
        assert(out == "(ln 1, col 15): invalid discriminator : type mismatch")
    }
    @Test
    fun f14_user_dots_err () {
        val out = all("""
            var x: <<<()>>> = <.1 <.1 <.1>>>
            output std x!1!2
        """.trimIndent())
        println(out)
        assert(out == "(ln 2, col 16): invalid discriminator : out of bounds")
    }
    @Test
    fun f15_user_dots () {
        val out = all("""
            var x: <<<()>>> = <.1 <.1 <.1>>>
            output std x!1!1!1
        """.trimIndent())
        assert(out == "()\n")
    }

    // IF

    @Test
    fun g01_if () {
        val out = all("""
            var x: <(),()> = <.2>
            if x?1 { } else { output std }
        """.trimIndent())
        println(out)
        assert(out == "()\n")
    }
    @Test
    fun g02_if_pred () {
        val out = all("""
            var x: <(),()> = <.2>
            if x?2 { output std } else { }
        """.trimIndent())
        assert(out == "()\n")
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
            var y: _int = _10
            var x: \_int = \y
            output std /x
        """.trimIndent())
        assert(out == "10\n")
    }
    @Test
    fun i02_ptr_func () {
        val out = all("""
        func f : \_int -> () {
           set /arg = _(*arg+1)
           return
        }
        var x: _int = _1
        call f \x
        output std x
        """.trimIndent())
        assert(out == "2\n")
    }
    @Test
    fun i03_ptr_func () {
        val out = all("""
            func f: \_int->_int { return /arg }
            var g: \_int->_int = f
            var x: _int = _10
            output std g (\x)
        """.trimIndent())
        assert(out == "10\n")
    }
    @Test
    fun i05_ptr_block_err () {
        val out = all("""
            var p1: \_int = ?
            var p2: \_int = ?
            {
                var v: _int = _10
                set p1 = \v
            }
            {
                var v: _int = _20
                set p2 = \v
            }
            output std /p1
        """.trimIndent())
        assert(out == "(ln 5, col 12): invalid assignment : cannot hold local pointer \"v\" (ln 4)")
    }
    @Test
    fun i06_ptr_block_err () {
        val out = all("""
            var x: _int = _10
            var p: \_int = ?
            {
                var y: _int = _10
                set p = \x
                set p = \y
            }
        """.trimIndent())
        assert(out == "(ln 6, col 11): invalid assignment : cannot hold local pointer \"y\" (ln 4)")
    }
    @Test
    fun i07_ptr_func_ok () {
        val out = all("""
            func f : \_int -> \_int {
                return arg
            }
            var v: _int = _10
            var p: \_int = f \v
            output std /p
        """.trimIndent())
        assert(out == "10\n")
    }
    @Test
    fun i08_ptr_func_ok () {
        val out = all("""
            var v: _int = _10
            func f : () -> \_int {
                return \v
            }
            var p: \_int = f ()
            output std /p
        """.trimIndent())
        assert(out == "10\n")
    }
    @Test
    fun i09_ptr_func_err () {
        val out = all("""
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
    fun i10_ptr_func_err () {
        val out = all("""
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
    fun i11_ptr_func_ok () {
        val out = all("""
            func f : \_int -> \_int {
                var ptr: ^\_int = arg
                return ptr
            }
            var v: _int = _10
            var p: \_int = f \v
            output std /p
        """.trimIndent())
        assert(out == "10\n")
    }
    @Test
    fun i12_ptr_ptr_ok () {
        val out = all("""
            var p: \\_int = ?
            var z: _int = _10
            var y: \_int = \z
            set p = \y
            output std //p
        """.trimIndent())
        assert(out == "10\n")
    }
    @Test
    fun i13_ptr_tup () {
        val out = all("""
            var v: [_int,_int] = [_10,_20]
            var p: \_int = \v.1
            set /p = _20
            output std v
        """.trimIndent())
        assert(out == "[20,20]\n")
    }
    @Test
    fun i14_ptr_type () {
        val out = all("""
            var v: <_int> = <.1 _10>
            var p: \_int = \v!1
            set /p = _20
            output std v
        """.trimIndent())
        println(out)
        assert(out == "<.1 20>\n")
    }
    @Test
    fun i15_ptr_tup () {
        val out = all("""
            var x: _int = _10
            var p: [_int,\_int] = [_10,\x]
            var v: _int = _20
            set p.2 = \v
            output std /p.2
        """.trimIndent())
        assert(out == "20\n")
    }
    @Test
    fun i16_ptr_tup () {
        val out = all("""
            var x: _int = _10
            var p: [_int,\_int] = [_10,\x]
            var v: _int = _20
            set p = [_10,\v]
            output std /p.2
        """.trimIndent())
        assert(out == "20\n")
    }
    @Test
    fun i17_ptr_type () {
        val out = all("""
            var x: _int = _10
            var p: <\_int> = <.1 \x>
            var v: _int = _20
            set p!1 = \v
            output std /p!1
        """.trimIndent())
        assert(out == "20\n")
    }
    @Test
    fun i18_ptr_type () {
        val out = all("""
            var x: _int = _10
            var p: <\_int> = <.1 \x>
            var v: _int = _20
            set p = <.1 \v>
            output std /p!1
        """.trimIndent())
        assert(out == "20\n")
    }
    @Test
    fun i19_ptr_tup () {
        val out = all("""
            var x1: [_int,\_int] = ?
            var v: _int = _20
            var x2: [_int,\_int] = [_10,\v]
            set x1 = x2
            output std /x1.2
        """.trimIndent())
        assert(out == "20\n")
    }
    @Test
    fun f09_ptr_type_err () {
        val out = all("""
            var x1: <\_int> = ?
            var v: _int = _20
            var x2: <\_int> = <.1 \v>
            set x1 = x2
            output std /x1!1
        """.trimIndent())
        assert(out == "20\n")
    }
    @Test
    fun f10_ptr_func () {
        val out = all("""
            var v: _int = _10
            func f : \_int -> \_int {
                return \v
            }
            {
                var p: \_int = f (\v)
                output std /p
            }
        """.trimIndent())
        assert(out == "10\n")
    }

    // REC

    @Test
    fun j01_list () {
        val out = all("""
            var l: <^> = <.0>
            output std \l
        """.trimIndent())
        assert(out == "<.0>\n")
    }
    @Test
    fun j02_list () {
        val out = all("""
            var l: <^> = new <.1 <.0>>
            output std \l
        """.trimIndent())
        assert(out == "<.1 <.0>>\n")
    }
    @Test
    fun j03_list () {
        val out = all("""
            var l: <^> = new <.1 new <.1 <.0>>>
            output std \l!1
        """.trimIndent())
        assert(out == "<.1 <.0>>\n")
    }
    @Test
    fun j04_list_disc_null_err () {
        val out = all("""
            var l: <^> = new <.1 <.0>>
            output std l!0
        """.trimIndent())
        assert(out == "out.exe: out.c:71: main: Assertion `l == NULL' failed.\n")
    }
    @Test
    fun j05_list_disc_null_err () {
        val out = all("""
            var l: <^> = <.0>
            output std \l!1
        """.trimIndent())
        assert(out == "out.exe: out.c:67: main: Assertion `l != NULL' failed.\n")
    }
    @Test
    fun j06_list () {
        val out = all("""
            var l: <^> = new <.1 new <.1 <.0>>>
            var p: \<^> = ?
            {
                set p = \l!1
            }
            output std p
        """.trimIndent())
        assert(out == "<.1 <.0>>\n")
    }
    @Test
    fun j07_list_move () {
        val out = all("""
            var l1: <^> = new <.1 <.0>>
            var l2: <^> = move l1
            output std \l1
            output std \l2
        """.trimIndent())
        println(out)
        assert(out == "<.0>\n<.1 <.0>>\n")
    }
    @Test
    fun j08_list_move () {
        val out = all("""
            var l1: <^> = new <.1 <.0>>
            var l2: <^> = <.0>
            set l2 = new <.1 move l1>
            output std \l1
            output std \l2
        """.trimIndent())
        assert(out == "<.0>\n<.1 <.1 <.0>>>\n")
    }
    @Test
    fun j09_list_move () {
        val out = all("""
            var l1: <^> = new <.1 <.0>>
            var l2: [_int,<^>] = [_10, move l1]
            output std \l1
            output std \l2.2
        """.trimIndent())
        println(out)
        assert(out == "<.0>\n<.1 <.0>>\n")
    }
    @Test
    fun j10_rec () {
        val out = all("""
            var n: <<^>> = <.1 new <.1 <.0>>>
            output std \n
        """.trimIndent())
        println(out)
        assert(out == "<.1 <.1 <.0>>>\n")
    }
    @Test
    fun j11_rec_double () {
        val out = all("""
            var n: <<^^>> = new <.1 <.1 new <.1 <.1 <.0>>>>>
            output std \n
        """.trimIndent())
        //println(out)
        assert(out == "<.1 <.1 <.1 <.1 <.0>>>>>\n")
    }
    @Test
    fun j09_tup_list_err () {
        val out = all("""
            var t: [_int,<^>] = [_10, new <.1 <.0>>]
            output std \t
        """.trimIndent())
        assert(out == "[10,<.1 <.0>>]\n")
    }
    @Test
    fun j10_tup_copy_ok () {
        val out = all("""
            var l: <^> = <.0>
            var t1: [<^>] = [move l]
            var t2: [<^>] = copy t1
            output std \t2
        """.trimIndent())
        //println(out)
        //assert(out == "(ln 3, col 17): invalid `move` : expected recursive variable")
        assert(out == "[<.0>]\n")
    }
    @Test
    fun j11_tup_move_ok () {
        val out = all("""
            var l: <^> = <.0>
            var t1: [<^>] = [move l]
            var t2: [<^>] = move t1
        """.trimIndent())
        println(out)
        //assert(out == "(ln 3, col 17): invalid `move` : expected recursive variable")
        assert(out == "OK")
    }

    // SET - TUPLE - DATA

    @Test
    fun k01_set_tuple () {
        val out = all("""
            var xy: [_int,_int] = [_10,_10]
            set xy.1 = _20
            var x: _int = xy.1
            var y: _int = xy.2
            var v: _int = _(x+y)
            output std v
        """.trimIndent())
        assert(out == "30\n")
    }

    // ALL

    @Test
    fun z01 () {
        val out = all("""
        func inv : <(),()> -> <(),()> {
            if arg?1 {
                return <.2>
            } else {
                return <.1>
            }
        }
        var a: <(),()> = <.2>
        var b: <(),()> = inv a
        output std b
        """.trimIndent())
        assert(out == "<.1>\n")
    }
    @Test
    fun z02 () {
        val out = all("""
        var i: _int = _1
        var n: _int = _0
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
}