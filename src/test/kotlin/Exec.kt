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
            var b : <(),()> = .1
            output std b
        """.trimIndent())
        assert(out == ".1\n")
    }
    @Test
    fun f02_xyz () {
        val out = all("""
            var z : <()> = .1()
            var y : <<()>> = .1 z
            var x : <<<()>>> = .1 y
            var yy: <<()>> = x.1!
            var zz: <()> = yy.1!
            output std zz
        """.trimIndent())
        assert(out == ".1\n")
    }
    @Test
    fun f05_user_big () {
        val out = all("""
            var s: <[_int,_int,_int,_int],_int,_int> = .1 [_1,_2,_3,_4]
            output std s
        """.trimIndent())
        assert(out == ".1 [1,2,3,4]\n")
    }
    @Test
    fun f06_user_big () {
        val out = all("""
            var x: <[<()>,<()>]> = .1 [.1,.1]
            output std x
        """.trimIndent())
        println(out)
        assert(out == ".1 [.1,.1]\n")
    }
    @Test
    fun f07_user_pred () {
        val out = all("""
            var z: <()> = .1
            output std z.1?
        """.trimIndent())
        assert(out == "1\n")
    }
    @Test
    fun f10_user_disc () {
        val out = all("""
            var z: <(),()> = .2 ()
            output std z.2!
        """.trimIndent())
        assert(out == "()\n")
    }
    @Test
    fun f11_user_disc_err () {
        val out = all("""
            type Bool { False: () ; True: () }
            type Z { X:() Y:() }
            var z: Z = Z.Y
            output std z.X!
        """.trimIndent())
        assert(out == "out.exe: out.c:85: main: Assertion `z.sub == Z_X' failed.\n")
    }
    @Test
    fun f12_user_disc_pred_idx () {
        val out = all("""
            type Bool { False: () ; True: () }
            type X { Z:() }
            type A { B:(X,()) }
            output std (A.B (X.Z,())).B!.1.Z?
        """.trimIndent())
        assert(out == "True\n")
    }
    @Test
    fun f13_user_disc_pred_err () {
        val out = all("""
            output std ().Z?
        """.trimIndent())
        assert(out == "(ln 1, col 12): invalid `.´ : expected user type")
    }
    @Test
    fun f14_user_dots_err () {
        val out = all("""
            type Z { Z:() }
            type Y { Y:Z }
            type X { X:Y }
            var x: X = X.X Y.Y Z.Z
            output std x.X!.Z!
        """.trimIndent())
        assert(out == "(ln 5, col 17): invalid `.´ : undeclared subcase \"Z\"")
    }
    @Test
    fun f15_user_dots () {
        val out = all("""
            type Z { Z:() }
            type Y { Y:Z }
            type X { X:Y }
            var x: X = X.X Y.Y Z.Z
            output std x.X!.Y!.Z!
        """.trimIndent())
        assert(out == "()\n")
    }

    // IF

    @Test
    fun g01_if () {
        val out = all("""
            type Bool { False: () ; True: () }
            if Bool.False { } else { output std }
        """.trimIndent())
        assert(out == "()\n")
    }
    @Test
    fun g02_if_pred () {
        val out = all("""
            type Bool { False: () ; True: () }
            var x: Bool = Bool.True
            if x.False? { } else { output std }
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
            var y: Int = 10
            var x: \Int = \y
            output std /x
        """.trimIndent())
        assert(out == "10\n")
    }
    @Test
    fun i02_ptr_func () {
        val out = all("""
        func f : \Int -> () {
           set /arg = _(*arg+1)
           return
        }
        var x: Int = 1
        call f \x
        output std x
        """.trimIndent())
        assert(out == "2\n")
    }
    @Test
    fun i03_ptr_func () {
        val out = all("""
            func f: \Int->Int { return /arg }
            var g: \Int->Int = f
            var x: Int = 10
            output std g (\x)
        """.trimIndent())
        assert(out == "10\n")
    }
    @Test
    fun i04_ptr_pre () {
        val out = all("""
            type @pre @rec Bb
            type @rec Aa {
               Aa1: Bb
            }
            type @rec Bb {
               Bb1: Aa
            }
            var n: Aa = Aa.Aa1 Bb.Bb1 Aa.Aa1 Bb.Nil
            output std \n
        """.trimIndent())
        assert(out == "Aa1 (Bb1 (Aa1 (Nil)))\n")
    }
    @Test
    fun i05_ptr_block_err () {
        val out = all("""
            var p1: \Int = ?
            var p2: \Int = ?
            {
                var v: Int = 10
                set p1 = \v
            }
            {
                var v: Int = 20
                set p2 = \v
            }
            output std /p1
        """.trimIndent())
        assert(out == "(ln 5, col 12): invalid assignment : cannot hold local pointer \"v\" (ln 4)")
    }
    @Test
    fun i06_ptr_block_err () {
        val out = all("""
            var x: Int = 10
            var p: \Int = ?
            {
                var y: Int = 10
                set p = \x
                set p = \y
            }
        """.trimIndent())
        assert(out == "(ln 6, col 11): invalid assignment : cannot hold local pointer \"y\" (ln 4)")
    }
    @Test
    fun i07_ptr_func_ok () {
        val out = all("""
            func f : \Int -> \Int {
                return arg
            }
            var v: Int = 10
            var p: \Int = f \v
            output std /p
        """.trimIndent())
        assert(out == "10\n")
    }
    @Test
    fun i08_ptr_func_ok () {
        val out = all("""
            var v: Int = 10
            func f : () -> \Int {
                return \v
            }
            var p: \Int = f ()
            output std /p
        """.trimIndent())
        assert(out == "10\n")
    }
    @Test
    fun i09_ptr_func_err () {
        val out = all("""
            func f : () -> \Int {
                var v: Int = 10
                return \v
            }
            var v: Int = 10
            var p: \Int = f ()
            output std /p
        """.trimIndent())
        assert(out == "(ln 3, col 5): invalid assignment : cannot hold local pointer \"v\" (ln 2)")
    }
    @Test
    fun i10_ptr_func_err () {
        val out = all("""
            func f : \Int -> \Int {
                var ptr: \Int = arg
                return ptr
            }
            var v: Int = 10
            var p: \Int = f \v
            output std /p
        """.trimIndent())
        assert(out == "(ln 3, col 5): invalid assignment : cannot hold local pointer \"ptr\" (ln 2)")
    }
    @Test
    fun i11_ptr_func_ok () {
        val out = all("""
            func f : \Int -> \Int {
                var ptr: ^\Int = arg
                return ptr
            }
            var v: Int = 10
            var p: \Int = f \v
            output std /p
        """.trimIndent())
        assert(out == "10\n")
    }
    @Test
    fun i12_ptr_ptr_ok () {
        val out = all("""
            var p: \\Int = ?
            var z: Int = 10
            var y: \Int = \z
            set p = \y
            output std //p
        """.trimIndent())
        assert(out == "10\n")
    }
    @Test
    fun i13_ptr_tup () {
        val out = all("""
            var v: (Int,Int) = (10,20)
            var p: \Int = \v.1
            set /p = 20
            output std v
        """.trimIndent())
        assert(out == "(20,20)\n")
    }
    @Test
    fun i14_ptr_type () {
        val out = all("""
            type X {
                X: Int
            }
            var v: X = X.X 10
            var p: \Int = \v.X!
            set /p = 20
            output std v
        """.trimIndent())
        assert(out == "X (20)\n")
    }
    @Test
    fun i15_ptr_tup () {
        val out = all("""
            var x: Int = 10
            var p: (Int,\Int) = (10,\x)
            var v: Int = 20
            set p.2 = \v
            output std /p.2
        """.trimIndent())
        assert(out == "20\n")
    }
    @Test
    fun i16_ptr_tup () {
        val out = all("""
            var x: Int = 10
            var p: (Int,\Int) = (10,\x)
            var v: Int = 20
            set p = (10,\v)
            output std /p.2
        """.trimIndent())
        assert(out == "20\n")
    }
    @Test
    fun i17_ptr_type () {
        val out = all("""
            type X {
                X: \Int
            }
            var x: Int = 10
            var p: X = X.X \x
            var v: Int = 20
            set p.X! = \v
            output std /p.X!
        """.trimIndent())
        assert(out == "20\n")
    }
    @Test
    fun i18_ptr_type () {
        val out = all("""
            type X {
                X: \Int
            }
            var x: Int = 10
            var p: X = X.X \x
            var v: Int = 20
            set p = X.X \v
            output std /p.X!
        """.trimIndent())
        assert(out == "20\n")
    }
    @Test
    fun i19_ptr_tup () {
        val out = all("""
            var x1: (Int,\Int) = ?
            var v: Int = 20
            var x2: (Int,\Int) = (10,\v)
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
            var x2: <\_int> = .1 \v
            set x1 = x2
            output std /x1.1!
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
            var l: <^> = .0
            output std \l
        """.trimIndent())
        println(out)
        assert(out == "Nil\n")
    }
    @Test
    fun j02_list () {
        val out = all("""
            type @rec List {
               Item: List
            }
            var l: List = List.Item List.Nil
            output std \l
        """.trimIndent())
        assert(out == "Item (Nil)\n")
    }
    @Test
    fun j03_list () {
        val out = all("""
            type @rec List {
               Item: List
            }
            var l: List = List.Item List.Item List.Nil
            output std \l.Item!
        """.trimIndent())
        assert(out == "Item (Nil)\n")
    }
    @Test
    fun j04_list_disc_null_err () {
        val out = all("""
            type @rec List {
               Item: List
            }
            var l: List = List.Item List.Nil
            output std l.Nil!
        """.trimIndent())
        assert(out == "out.exe: out.c:71: main: Assertion `l == NULL' failed.\n")
    }
    @Test
    fun j05_list_disc_null_err () {
        val out = all("""
            type @rec List {
               Item: List
            }
            var l: List = List.Nil
            output std \l.Item!
        """.trimIndent())
        assert(out == "out.exe: out.c:63: main: Assertion `l != NULL' failed.\n")
    }
    @Test
    fun j06_list () {
        val out = all("""
            type @rec List {
               Item: List
            }
            var l: List = List.Item List.Item List.Nil
            var p: \List = ?
            {
                set p = \l.Item!
            }
            output std p
        """.trimIndent())
        assert(out == "Item (Nil)\n")
    }
    @Test
    fun j07_list_move () {
        val out = all("""
            type @rec List {
               Item: List
            }
            var l1: List = List.Item List.Nil
            var l2: List = move l1
            output std \l1
            output std \l2
        """.trimIndent())
        assert(out == "Nil\nItem (Nil)\n")
    }
    @Test
    fun j08_list_move () {
        val out = all("""
            type @rec List {
               Item: List
            }
            var l1: List = List.Item List.Nil
            var l2: List = List.Nil
            set l2 = List.Item move l1
            output std \l1
            output std \l2
        """.trimIndent())
        assert(out == "Nil\nItem (Item (Nil))\n")
    }
    @Test
    fun j09_list_move () {
        val out = all("""
            type @rec List {
               Item: List
            }
            var l1: List = List.Item List.Nil
            var l2: (Int,List) = (10, move l1)
            output std \l1
            output std \l2.2
        """.trimIndent())
        assert(out == "Nil\nItem (Item (Nil))\n")
    }

    // SET - TUPLE - DATA

    @Test
    fun k01_set_tuple () {
        val out = all("""
            var xy: (Int,Int) = (10,10)
            set xy.1 = 20
            var x: Int = xy.1
            var y: Int = xy.2
            var v: Int = _(x+y)
            output std v
        """.trimIndent())
        assert(out == "30\n")
    }

    // ALL

    @Test
    fun z01 () {
        val out = all("""
        type Bool {
            False: ()
            True:  ()
        }
        func inv : Bool -> Bool {
            var tst: Bool = arg.True?
            if tst {
                var v: Bool = Bool.False()
                return v
            } else {
                var v: Bool = Bool.True
                return v
            }
        }
        var a: Bool = Bool.True
        var b: Bool = inv a
        output std b
        """.trimIndent())
        assert(out == "False\n")
    }
    @Test
    fun z02 () {
        val out = all("""
        type Bool {
            False: ()
            True:  ()
        }
        func bool: Int -> Bool {
            return _(arg ? (Bool){Bool_True} : (Bool){Bool_False})
        }
        var i: Int = 1
        var n: Int = 0
        loop {
            set n = _(n + i)
            set i = _(i + 1)
            if bool _(i > 5) {
                break
            }
        }
        output std n
        """.trimIndent())
        assert(out == "15\n")
    }
}