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
        assert(out == "(ln 1, col 1): expected statement : have \"/\"")
    }
    @Test
    fun a05_int () {
        val out = all("""
            var x: Int = 10
            output std x
        """.trimIndent())
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
            var x: Int = _abs(-1)
            output std x
        """.trimIndent())
        assert(out == "1\n")
    }
    @Test
    fun a10_int_set () {
        val out = all("""
            var x: Int = 10
            set x = -20
            output std x
        """.trimIndent())
        assert(out == "-20\n")
    }
    @Test
    fun a11_unk () {
        val out = all("""
            var x: Int = ?
            set x = -20
            output std x
        """.trimIndent())
        println(out)
        assert(out == "-20\n")
    }

    // TUPLES

    @Test
    fun b01_tuple_units () {
        val out = all("""
            var x: ((),()) = ((),())
            var y: () = x.1
            call _output_std_Unit y
        """.trimIndent())
        assert(out == "()\n")
    }
    @Test
    fun b02_tuple_idx () {
        val out = all("""
            output std (((),()).1)
        """.trimIndent())
        assert(out == "()\n")
    }
    @Test
    fun b03_tuple_tuples () {
        val out = all("""
            var v: ((),()) = ((),())
            var x: ((),((),())) = ((),v)
            var y: ((),()) = x.2
            var z: () = y.2
            output std z
        """.trimIndent())
        assert(out == "()\n")
    }
    @Test
    fun b04_tuple_pp () {
        val out = all("""
            var x: ((Int,Int),(Int,Int)) = ((1,2),(3,4))
            output std x
        """.trimIndent())
        assert(out == "((1,2),(3,4))\n")
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
            var x: (Int,_(char*)) = (10,y)
            call _puts x.2
        """.trimIndent())
        assert(out == "hello\n")
    }

    // FUNC / CALL

    @Test
    fun d01_f_int () {
        val out = all("""
            func f: Int -> Int { return arg }
            var x: Int = call f 10
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
            func f: () -> () { output std 10 }
            func g: () -> () { return f () }
            call g ()
        """.trimIndent())
        assert(out == "10\n")
    }
    @Test
    fun d04_arg () {
        val out = all("""
        func f : Int -> Int {
           set arg = _(arg+1)
           return arg
        }
        output std f 1
        """.trimIndent())
        assert(out == "2\n")
    }
    @Test
    fun d05_func_var () {
        val out = all("""
        func f: Int->Int { return arg }
        var p: Int->Int = f
        output std p 10
        """.trimIndent())
        assert(out == "10\n")
    }
    @Test
    fun d06_func_fg () {
        val out = all("""
            func f: Int->Int { return arg }
            func g: (Int->Int, Int) -> Int {
               var f: Int->Int = arg.1
               var v: Int = call f arg.2
               return v
            }
            output std g (f,10)
        """.trimIndent())
        assert(out == "(ln 3, col 8): invalid declaration : \"f\" is already declared (ln 1)")
    }
    @Test
    fun d07_func_fg () {
        val out = all("""
            func f: Int->Int { return arg }
            func g: (Int->Int, Int) -> Int {
               var fx: Int->Int = arg.1
               var v: Int = call fx arg.2
               return v
            }
            output std g (f,10)
        """.trimIndent())
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
            var x: ((),()) = ((),())
            output std x
        """.trimIndent())
        assert(out == "((),())\n")
    }
    @Test
    fun e03_out () {
        val out = all("""
            func output_f: Int -> () { output std arg }
            output f 10
        """.trimIndent())
        assert(out == "10\n")
    }

    // USER

    @Test
    fun f01_bool () {
        val out = all("""
            type Bool { False: () ; True: () }
            var b : Bool = Bool.False()
            output std b
        """.trimIndent())
        assert(out == "False\n")
    }
    @Test
    fun f02_xyz () {
        val out = all("""
            type Z { Z:() }
            type Y { Y:Z }
            type X { X:Y }
            var z : Z = Z.Z()
            var y : Y = Y.Y z
            var x : X = X.X y
            var yy: Y = x.X!
            var zz: Z = yy.Y!
            output std zz
        """.trimIndent())
        println(out)
        assert(out == "Z\n")
    }
    @Test
    fun f05_user_big () {
        val out = all("""
            type Set_ {
                Size:     (_int,_int,_int,_int)
                Color_BG: _int
                Color_FG: _int
            }
            var x: _int = _1
            var y: _int = _2
            var w: _int = _3
            var z: _int = _4
            output std(Set_.Size (x,y,w,x))
        """.trimIndent())
        assert(out == "Size (_,_,_,_)\n")
    }
    @Test
    fun f06_user_big () {
        val out = all("""
            type Z { Z:() }
            type Y { Y:() }
            type X { X:(Y,Z) }
            output std (X.X (Y.Y,Z.Z))
        """.trimIndent())
        assert(out == "X (Y,Z)\n")
    }
    @Test
    fun f07_user_pred () {
        val out = all("""
            type Bool { False: () ; True: () }
            type Z { Z:() }
            var z: Z = Z.Z
            output std z.Z?
        """.trimIndent())
        assert(out == "True\n")
    }
    @Test
    fun f08_user_pred_err () {
        val out = all("""
            type Bool { False: () ; True: () }
            type Z { Z:() }
            var z: Z = Z.Z
            output std z.Err?
        """.trimIndent())
        assert(out == "(ln 4, col 14): invalid `.´ : undeclared subcase \"Err\"")
    }
    @Test
    fun f09_user_disc_err () {
        val out = all("""
            type Z { Z:() }
            var z: Z = Z.Z
            output std z.Err!
        """.trimIndent())
        assert(out == "(ln 3, col 14): invalid `.´ : undeclared subcase \"Err\"")
    }
    @Test
    fun f10_user_disc () {
        val out = all("""
            type Bool { False: () ; True: () }
            type Z { X:() Y:() }
            var z: Z = Z.Y
            output std z.Y!
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
        //println(out)
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
        println(out)
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
            output std x\
        """.trimIndent())
        assert(out == "10\n")
    }
    @Test
    fun i02_ptr_func () {
        val out = all("""
        func f : \Int -> () {
           set arg\ = _(*arg+1)
           return
        }
        var x: Int = 1
        call f \x
        output std x
        """.trimIndent())
        println(out)
        assert(out == "2\n")
    }
    @Test
    fun i03_ptr_func () {
        val out = all("""
            func f: \Int->Int { return arg\ }
            var g: \Int->Int = f
            var x: Int = 10
            output std g (\x)
        """.trimIndent())
        println(out)
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
            output std p1\
        """.trimIndent())
        //println(out)
        assert(out == "(ln 5, col 12): invalid assignment : cannot hold pointer to local \"v\" (ln 4) in outer scope")
    }

    // REC

    @Test
    fun j01_list () {
        val out = all("""
            type @rec List {
               Item: List
            }
            var l: List = List.Nil
            output std \l
        """.trimIndent())
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
        //println(out)
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
        println(out)
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