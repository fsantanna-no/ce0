import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.MethodOrderer.Alphanumeric
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.io.File

@TestMethodOrder(Alphanumeric::class)
class TCyclone {

    fun all(inp: String): String {
        val (ok1, out1) = All_inp2c(inp)
        if (!ok1) {
            return out1
        }
        File("out.c").writeText(out1)
        val (ok2, out2) = exec("gcc -Werror out.c -o out.exe")
        if (!ok2) {
            return out2
        }
        val (_, out3) = exec("./out.exe")
        //val (_,out3) = exec("valgrind ./out.exe")
        // search in tests output for
        //  - "definitely lost"
        //  - "Invalid read of size"
        //println(out3)
        return out3
    }

    @Test
    fun strcpy_01() {
        val out = all(
            """
            var strcpy: [@a_1,@b_1,/_char@a_1,/_char@b_1] -> /_char@a_1
            set strcpy = func [@a_1,@b_1,/_char@a_1,/_char@b_1] -> /_char@a_1 {
                return arg.3
            }
            var s1: /_char@local
            set s1 = call strcpy [@local,@local,s1,s1]: @local
            {
                var s2: /_char@local
                set s1 = call strcpy [@local,@local,s1,s2]: @global
                set s2 = call strcpy [@local,@local,s2,s1]: @local   -- TODO: should be ok @a1/@b1
            }
            output std ()
        """.trimIndent()
        )
        assert(out == "()\n") { out }
    }
    @Test
    fun strdup_02() {
        val out = all(
            """
            var strdup: [@_1,/_char@_1] -> /_char@global    -- TODO: @global/@xxx no need to pass
            set strdup = func [@_1,/_char@_1] -> /_char@global {
                var ret: /_char @global
                return ret
            }
            var s1: /_char@local
            --set s1 = call strdup s1 @local
            {
                var s2: /_char@local
                --set s1 = call strdup s2 @global
                set s2 = call strdup s1 @local
            }
            output std ()
        """.trimIndent()
        )
        assert(out == "()\n") { out }
    }
    @Test
    fun fact_03 () {
        val out = all(
            """
            var fact: [@_1,/_int@_1,_int] -> ()
            set fact = func [@_1,/_int@_1,_int] -> () { @f
                var x: _int
                set x = _1
                var n: _int
                set n = arg.3
                if _(n > 1) {
                    call fact [@f,/x,_(n-1):_int]
                }
                set arg.2\ = _(x*n)
            }
            var x: _int
            set x = _0
            call fact [@local, /x, _6:_int]
            output std x
        """.trimIndent()
        )
        assert(out == "720\n") { out }
    }
}