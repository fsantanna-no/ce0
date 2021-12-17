import org.junit.jupiter.api.MethodOrderer.Alphanumeric
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.io.File

val nums = """
    var zero: /</^ @local> @local
    set zero = <.0>: /</^ @local> @local
    var one: /</^ @local> @local
    set one = new <.1 zero>:</^ @local> @local
    var two: /</^ @local> @local
    set two = new <.1 one>:</^ @local> @local
""".trimIndent()

val Num  = "/</^@local>@local"
val NumF = "/</^@1>@1"

val add = """
    var add: [$NumF,$NumF] -> $NumF
    set add = func ([$NumF,$NumF] -> $NumF) {
        var x: $NumF
        set x = arg.1
        var y: $NumF
        set y = arg.2
        if y\?0 {
            return x
        } else {
            var ret: $NumF
            set ret = new <.1 add [x,y\!1] @1>:</^@1> @1
            return ret
        }
    }
""".trimIndent()

@TestMethodOrder(Alphanumeric::class)
class TBook {

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
    fun pre_01_nums () {
        val out = all("""
            var zero: /</^ @local> @local
            set zero = <.0>: /</^ @local> @local
            var one: </^ @local>
            set one = <.1 zero>: </^ @local>
            var two: </^ @local>
            set two = <.1 /one>: </^ @local>
            output std /two
        """.trimIndent())
        assert(out == "<.1 <.1 <.0>>>\n") { out }
    }
    @Test
    fun pre_03_add () {
        val out = all("""
            $nums
            $add
            output std add [two,one]
        """.trimIndent())
        assert(out == "<.1 <.1 <.0>>>\n") { out }
    }

    @Test
    fun ch_01_01_square () {
        val out = all("""
            var square: $NumF -> $NumF {
            }
            output std square two
        """.trimIndent())
        assert(out == "<.1 <.1 <.0>>>\n") { out }
    }


}