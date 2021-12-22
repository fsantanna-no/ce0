import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.MethodOrderer.Alphanumeric
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.io.File

val nums = """
    var zero: /</^ @local> @local
    set zero = <.0>: /</^ @local> @local
    var one: /</^ @local> @local
    set one = new <.1 zero>:</^ @local>: @local
    var two: /</^ @local> @local
    set two = new <.1 one>:</^ @local>: @local
    var three: /</^ @local> @local
    set three = new <.1 two>:</^ @local>: @local
""".trimIndent()

fun Num (ptr: Boolean, scope: String): String {
    val ret = "</^$scope>"
    return if (!ptr) ret else "/"+ret+scope
}
val NumTL = Num(true,  "@local")
val NumT1 = Num(true,  "@_1")
val NumF1 = Num(false, "@_1")

val add = """
    var add: [@_1,$NumT1,$NumT1] -> $NumT1
    set add = func ([@_1,$NumT1,$NumT1] -> $NumT1) {
        var x: $NumT1
        set x = arg.2
        var y: $NumT1
        set y = arg.3
        if y\?0 {
            return x
        } else {
            return new <.1 add [@_1,x,y\!1]: @_1>:$NumF1: @_1
        }
    }
""".trimIndent()

val mul = """
    var mul: [@_1,$NumT1,$NumT1] -> $NumT1
    set mul = func ([@_1,$NumT1,$NumT1] -> $NumT1) {
        var x: $NumT1
        set x = arg.2
        var y: $NumT1
        set y = arg.3
        if y\?0 {
            return <.0>: $NumT1
        } else {
            var z: ${NumT1}
            set z = mul [@_1, x, y\!1]: @_1
            return add [@_1, z,x]: @_1
        }
    }
""".trimIndent()

val lt = """
    var lt: [@_1,$NumT1,$NumT1] -> _int
    set lt = func [@_1,$NumT1,$NumT1] -> _int {
        if arg.3\?0 {
            return _0
        } else {
            if arg.2\?0 {
                return _1
            } else {
                return lt [@_1,arg.2\!1,arg.3\!1]
            }
        }
    }
""".trimIndent()

@TestMethodOrder(Alphanumeric::class)
class TBook {

    fun all(inp: String): String {
        println("nums: ${nums.count { it == '\n' }}")
        println("add:  ${add.count { it == '\n' }}")
        println("mul:  ${mul.count { it == '\n' }}")
        println("lt:   ${lt.count { it == '\n' }}")

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
    fun pre_01_nums() {
        val out = all(
            """
            var zero: /</^ @local> @local
            set zero = <.0>: /</^ @local> @local
            var one: </^ @local>
            set one = <.1 zero>: </^ @local>
            var two: </^ @local>
            set two = <.1 /one>: </^ @local>
            output std /two
        """.trimIndent()
        )
        assert(out == "<.1 <.1 <.0>>>\n") { out }
    }

    @Test
    fun pre_03_add() {
        val out = all(
            """
            $nums
            $add
            output std add [@local,two,one]: @local
        """.trimIndent()
        )
        assert(out == "<.1 <.1 <.1 <.0>>>>\n") { out }
    }

    @Test
    fun pre_04_mul() {
        val out = all(
            """
            $nums
            $add
            $mul
            output std mul [@local, two, add [@local, two,one]]
        """.trimIndent()
        )
        assert(out == "<.1 <.1 <.1 <.1 <.1 <.1 <.0>>>>>>>\n") { out }
    }

    @Test
    fun pre_05_lt() {
        val out = all(
            """
            $nums
            $lt
            output std lt [@local, two, one]
            output std lt [@local, one, two]
        """.trimIndent()
        )
        assert(out == "0\n1\n") { out }
    }

    // CHAPTER 1.1

    @Test
    fun ch_01_01_square_pg02() {
        val out = all(
            """
            $nums
            $add
            $mul
            var square: [@_1,$NumT1] -> $NumT1
            set square = func [@_1,$NumT1] -> $NumT1 {
                return mul [@_1,arg.2,arg.2]: @_1
            }
            output std square [@local,two]: @local
        """.trimIndent()
        )
        assert(out == "<.1 <.1 <.1 <.1 <.0>>>>>\n") { out }
    }

    @Test
    fun ch_01_01_smaller_pg02() {
        val out = all(
            """
            $nums
            $lt
            var smaller: [@_1,$NumT1,$NumT1] -> $NumT1
            set smaller = func [@_1,$NumT1,$NumT1] -> $NumT1 {
                if lt arg {
                    return arg.2
                } else {
                    return arg.3
                }
            }
            output std smaller [@local,one,two]: @local
            output std smaller [@local,two,one]: @local
        """.trimIndent()
        )
        assert(out == "<.1 <.0>>\n<.1 <.0>>\n") { out }
    }

    @Test
    fun ch_01_01_delta_pg03() {
        println("TODO")
    }

    // CHAPTER 1.2

    @Test
    fun ch_01_02_three_pg05() {
        val out = all(
            """
            $nums
            var f_three: [@_1,$NumT1] -> $NumT1
            set f_three = func [@_1,$NumT1] -> $NumT1 {
                return three
            }
            output std f_three [@local,one]
        """.trimIndent()
        )
        assert(out == "<.1 <.1 <.1 <.0>>>>\n") { out }
    }
    @Disabled // TODO: infinite loop
    @Test
    fun ch_01_02_infinity_pg05() {
        val out = all(
            """
            var infinity: () -> $NumT1
            set infinity = func (@_1) -> $NumT1 {
                output std _10:_int
                return new <.1 infinity() @_1>:$NumF1 @_1
            }
            output std infinity ()
        """.trimIndent()
        )
        assert(out == "<.1 <.1 <.1 <.0>>>>\n") { out }
    }

    // CHAPTER 1.3

    @Test
    fun ch_01_03_multiply_pg09() {
        val out = all(
            """
            $nums
            $add
            $mul
            var multiply: [@_1,$NumT1,$NumT1] -> $NumT1
            set multiply = func [@_1,$NumT1,$NumT1] -> $NumT1 {
                if arg.2\?0 {
                    return <.0>:${NumT1}
                } else {
                    return mul [@_1,arg.2,arg.3]: @_1
                }
            }
            output std multiply [@local,two,three]: @local
        """.trimIndent()
        )
        assert(out == "<.1 <.1 <.1 <.1 <.1 <.1 <.0>>>>>>>\n") { out }
    }

    // CHAPTER 1.4

    @Disabled  // TODO: currying requires returning function
    @Test
    fun ch_01_04_currying_pg11() {
        val out = all(
            """
            $nums
            $lt
            var f: [@_1,_int] -> (_int->_int) @_1 {
            }
            var smallerc: $NumT1 -> ($NumT1->$NumT1)
            set smallerc = func $NumT1 -> ($NumT1->$NumT1) {
                var x: $NumT1
                set x = arg
                return func $NumT1 -> $NumT1 {  -- would require annotation to hold x (func [x] ...)
                    return lt [x,arg]
                }
            }
            var f: $NumTL -> $NumTL
            set f = smallerc two   -- smallerc could keep two in memory as long as smallerc does not live longer than two
            output std f one
            output std f three
        """.trimIndent()
        )
        assert(out == "<.1 <.0>>\n<.1 <.1 <.0>>>\n") { out }
    }
    @Test
    fun ch_01_04_twice_pg11() {
        val out = all(
            """
            $nums
            $add
            $mul
            var square: [@_1,$NumT1] -> $NumT1
            set square = func [@_1,$NumT1] -> $NumT1 {
                return mul [@_1,arg.2,arg.2]: @_1
            }
            var twice: [@_1, [@_1,$NumT1]->$NumT1, $NumT1] -> $NumT1
            set twice = func [@_1, [@_1,$NumT1]->$NumT1, $NumT1] -> $NumT1 {
                return arg.2 [@_1, arg.2 [@_1,arg.3]: @_1]: @_1
            }
            output std twice [@local,square,two]: @local
        """.trimIndent()
        )
        assert(out == "<.1 <.1 <.1 <.1 <.1 <.1 <.1 <.1 <.1 <.1 <.1 <.1 <.1 <.1 <.1 <.1 <.0>>>>>>>>>>>>>>>>>\n") { out }
    }
}