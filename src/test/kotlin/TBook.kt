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

fun Num (ptr: Boolean, scope: String): String {
    val ret = "</^$scope>"
    return if (!ptr) ret else "/"+ret+scope
}
val NumTL = Num(true,  "@local")
val NumT1 = Num(true,  "@1")
val NumF1 = Num(false, "@1")

val add = """
    var add: [$NumT1,$NumT1] -> $NumT1
    set add = func ([$NumT1,$NumT1] -> $NumT1) {
        var x: $NumT1
        set x = arg.1
        var y: $NumT1
        set y = arg.2
        if y\?0 {
            return x
        } else {
            return new <.1 add [x,y\!1] @1>:$NumF1 @1
        }
    }
""".trimIndent()

val mul = """
    var mul: [$NumT1,$NumT1] -> $NumT1
    set mul = func ([$NumT1,$NumT1] -> $NumT1) {
        var x: $NumT1
        set x = arg.1
        var y: $NumT1
        set y = arg.2
        if y\?0 {
            return <.0>: $NumT1
        } else {
            var z: ${NumT1}
            set z = mul [x, y\!1] @1
            return add [z,x] @1
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
        assert(out == "<.1 <.1 <.1 <.0>>>>\n") { out }
    }
    @Test
    fun pre_04_mul () {
        val out = all("""
            $nums
            $add
            $mul
            output std mul [two, add [two,one]]
        """.trimIndent())
        assert(out == "<.1 <.1 <.1 <.1 <.1 <.1 <.0>>>>>>>\n") { out }
    }

    @Test
    fun ch_01_01_square () {
        val out = all("""
            $nums
            $add
            $mul
            var square: $NumT1 -> $NumT1
            set square = func $NumT1 -> $NumT1 {
                return mul [arg,arg] @1
            }
            output std square two
        """.trimIndent())
        assert(out == "<.1 <.1 <.1 <.1 <.0>>>>>\n") { out }
    }


}