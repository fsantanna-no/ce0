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
    fun a04_undeclared_var () {
        val out = all("output std x")
        assert(out == "(ln 1, col 12): undeclared variable \"x\"")
    }
    @Test
    fun a04_undeclared_func () {
        val out = all("call f ()")
        assert(out == "(ln 1, col 6): undeclared variable \"f\"")
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
    fun a06_undeclared_type () {
        val out = all("var x: Nat = ()")
        assert(out == "(ln 1, col 8): undeclared type \"Nat\"")
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
            set x = 20
            output std x
        """.trimIndent())
        assert(out == "20\n")
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
        assert(out == "Z\n")
    }
    @Test
    fun f03_user_sup_undeclared () {
        val out = all("""
            var x: Bool = ()
        """.trimIndent())
        assert(out == "(ln 1, col 8): undeclared type \"Bool\"")
    }
    @Test
    fun f04_user_sub_undeclared () {
        val out = all("""
            type Set {
                X: ()
            }
            output std(Set.Set)
        """.trimIndent())
        assert(out == "(ln 4, col 15): undeclared subcase \"Set\"")
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
        println(out)
        assert(out == "True\n")
    }
    @Test
    fun f08_user_pred_err () {
        val out = all("""
            type Bool { False: () ; True: () }
            type Z { Y:() }
            var z: Z = Z.Y
            output std z.Z?
        """.trimIndent())
        assert(out == "(ln 4, col 14): undeclared subcase \"Z\"")
    }
    @Test
    fun f09_user_disc () {
        val out = all("""
            type Bool { False: () ; True: () }
            type Z { X:() Y:() }
            var z: Z = Z.Y
            output std z.Y!
        """.trimIndent())
        assert(out == "()\n")
    }
    @Test
    fun f10_user_disc_err () {
        val out = all("""
            type Bool { False: () ; True: () }
            type Z { X:() Y:() }
            var z: Z = Z.Y
            output std z.X!
        """.trimIndent())
        assert(out == "out.exe: out.c:71: main: Assertion `z.sub == Z_X' failed.\n")
    }
}