import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.MethodOrderer.Alphanumeric
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.io.File

val nums = """
    var zero: /</^ @local>
    set zero = <.0>: /</^ @local> @local
    var one: /</^ @local>
    set one = new <.1 zero>:</^ @local>
    var two: /</^>
    set two = new <.1 one>:</^ @local>
    var three: /</^ @local>
    set three = new <.1 two>:</^>
""".trimIndent()

fun Num (ptr: Boolean, scope: String): String {
    val ret = "</^$scope>"
    return if (!ptr) ret else "/"+ret+scope
}
val NumTL  = Num(true,  "@local")
val NumA1  = Num(true,  "@a_1")
val NumA2  = Num(true,  "@a_2")
val NumB1  = Num(true,  "@b_1")
val NumR1  = Num(true,  "@r_1")
val _NumR1 = Num(false, "@r_1")
val NumS1  = Num(true,  "@s_1")

val add = """
    var add: ({}->{@r_1,@a_1,@b_1}-> [$NumA1,$NumB1] -> $NumR1)
    set add = func ({}->{@r_1,@a_1,@b_1}-> [$NumA1,$NumB1] -> $NumR1) {
        var x: $NumA1
        set x = arg.1
        var y: $NumB1
        set y = arg.2
        if y\?0 {
            return x
        } else {
            return new <.1 call add {@r_1,@a_1,@b_1} [x,y\!1]: @r_1>:$_NumR1: @r_1
        }
    }
""".trimIndent()

val mul = """
    var mul: ({}->{@r_1,@a_1,@b_1}-> [$NumA1,$NumB1] -> $NumR1)
    set mul = func ({}->{@r_1,@a_1,@b_1}-> [$NumA1,$NumB1] -> $NumR1) {
        var x: $NumR1
        set x = arg.1
        var y: $NumR1
        set y = arg.2
        if y\?0 {
            return <.0>: $NumR1
        } else {
            var z: ${NumR1}
            set z = call mul {@r_1,@a_1,@b_1} [x, y\!1]: @r_1
            return call add {@r_1,@a_1,@b_1} [z,x]: @r_1
        }
    }
""".trimIndent()

val lt = """
    var lt: ({}->{@a_1,@b_1}-> [$NumA1,$NumB1] -> _int)
    set lt = func {}->{@a_1,@b_1}-> [$NumA1,$NumB1] -> _int {
        if arg.2\?0 {
            return _0
        } else {
            if arg.1\?0 {
                return _1
            } else {
                return call lt {@a_1,@b_1} [arg.1\!1,arg.2\!1]
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
            var zero: /</^ @local>
            set zero = <.0>: /</^> @local
            var one: </^>
            set one = <.1 zero>: </^ @local>
            var two: </^>
            set two = <.1 /one>: </^>
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
            output std call add {@local,@local,@local} [two,one]: @local
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
            output std call mul {@local,@local,@local} [two, call add {@local,@local,@local} [two,one]]
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
            output std call lt {@local,@local} [two, one]
            output std call lt {@local,@local} [one, two]
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
            var square: ({}->{@r_1,@a_1}-> $NumA1 -> $NumR1)
            set square = func {}->{@r_1,@a_1}-> $NumA1 -> $NumR1 {
                return call mul {@r_1,@a_1,@a_1} [arg,arg]: @r_1
            }
            output std call square {@local,@local} two
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
            var smaller: ({}->{@a_1,@a_2}-> [$NumA1,$NumA2] -> $NumA2)
            set smaller = func ({}->{@a_1,@a_2}-> [$NumA1,$NumA2] -> $NumA2) {
                if call lt {@a_1,@a_2} arg {
                    return arg.1
                } else {
                    return arg.2
                }
            }
            output std call smaller {@local,@local} [one,two]: @local
            output std call smaller {@local,@local} [two,one]: @local
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
            var f_three: ({}->{@r_1}-> $NumR1 -> $NumR1)
            set f_three = func {}->{@r_1}-> $NumR1 -> $NumR1 {
                return three
            }
            output std call f_three {@local} one
        """.trimIndent()
        )
        assert(out == "<.1 <.1 <.1 <.0>>>>\n") { out }
    }
    @Disabled // TODO: infinite loop
    @Test
    fun ch_01_02_infinity_pg05() {
        val out = all(
            """
            var infinity: {}->{@r_1}-> () -> $NumR1
            set infinity = func {}->{@r_1}-> () -> $NumR1 {
                output std _10:_int
                return new <.1 infinity() @r_1>:$_NumR1 @r_1
            }
            output std call infinity {@local} ()
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
            var multiply: ({}->{@r_1,@a_1,@b_1}-> [$NumA1,$NumB1] -> $NumR1)
            set multiply = func {}->{@r_1,@a_1,@b_1}-> [$NumA1,$NumB1] -> $NumR1 {
                if arg.1\?0 {
                    return <.0>:${NumR1}
                } else {
                    return call mul {@r_1,@a_1,@b_1} [arg.1,arg.2]: @r_1
                }
            }
            output std call multiply {@local,@local,@local} [two,three]
        """.trimIndent()
        )
        assert(out == "<.1 <.1 <.1 <.1 <.1 <.1 <.0>>>>>>>\n") { out }
    }

    // CHAPTER 1.4

    @Test
    fun ch_01_04_currying_pg11() {
        val out = all(
            """
            $nums
            $lt
            var smallerc:       {}->{@r_1}-> $NumR1 -> ({@r_1}->{}-> $NumR1->$NumR1)
            set smallerc = func {}->{@r_1}-> $NumR1 -> ({@r_1}->{}-> $NumR1->$NumR1) {
                var x: $NumR1
                set x = arg
                return func [x]->{@r_1}->{}-> $NumR1->$NumR1 {
                    if (call lt {@r_1,@r_1} [x,arg]) {
                        return x
                    } else {
                        return arg
                    }
                }
            }
            var f: ({@local}->{}-> $NumTL -> $NumTL)
            --var f: ({@r_1}->{}-> $NumR1->$NumR1)
            set f = call smallerc {@local} two: @local   -- smallerc could keep two in memory as long as smallerc does not live longer than two
            output std call f one
            output std call f three
        """.trimIndent()
        )
        assert(out == "<.1 <.0>>\n<.1 <.1 <.0>>>\n") { out }
    }
    @Test
    fun ch_01_05_twice_pg11() {
        val out = all(
            """
            $nums
            $add
            $mul
            var square: ({}->{@r_1,@a_1}-> $NumA1 -> $NumR1)
            set square = func {}->{@r_1,@a_1}-> $NumA1 -> $NumR1 {
                return call mul {@r_1,@a_1,@a_1} [arg,arg]: @r_1
            }
            var twice: ({}->{@r_1,@a_1}-> [({}->{@r_1,@a_1}-> $NumA1->$NumR1), $NumA1] -> $NumR1)
            set twice = func {}->{@r_1,@a_1}-> [({}->{@r_1,@a_1}-> $NumA1->$NumR1), $NumA1] -> $NumR1 {
                return call arg.1 {@r_1,@a_1} (call arg.1 {@r_1,@a_1} arg.2: @r_1): @r_1
            }
            output std call twice {@local,@local} [square,two]: @local
        """.trimIndent()
        )
        assert(out == "<.1 <.1 <.1 <.1 <.1 <.1 <.1 <.1 <.1 <.1 <.1 <.1 <.1 <.1 <.1 <.1 <.0>>>>>>>>>>>>>>>>>\n") { out }
    }
    @Test
    fun ch_01_06_addc_pg12() {
        val out = all(
            """
            $nums
            $add
            var plusc: {}->{@a_1}-> $NumA1 -> ({@a_1}->{@r_1,@b_1}->$NumB1->$NumR1)
            set plusc = func {}->{@a_1}-> $NumA1 -> ({@a_1}->{@r_1,@b_1}->$NumB1->$NumR1) {
                var x: $NumA1
                set x = arg
                return func [x]->{@a_1}->{@r_1,@b_1}->$NumB1->$NumR1 {
                    return call add {@r_1,@a_1,@b_1} [x,arg]: @r_1
                }
            }
            var f: {@local}->{@r_1,@b_1}->$NumB1->$NumR1
            set f = call plusc {@local} one
            output std call f {@local,@local} two: @local
            output std call f {@local,@local} one: @local
            output std call (call plusc {@local} one) {@local,@local} zero: @local
        """.trimIndent()
        )
        assert(out == "<.1 <.1 <.1 <.0>>>>\n<.1 <.1 <.0>>>\n<.1 <.0>>\n") { out }
    }
    @Test
    fun ch_01_07_quad_pg12() {
        val out = all(
            """
            $nums
            $add
            $mul
            var square: ({}->{@r_1,@a_1}-> $NumA1 -> $NumR1)
            set square = func {}->{@r_1,@a_1}-> $NumA1 -> $NumR1 {
                return call mul {@r_1,@a_1,@a_1} [arg,arg]: @r_1
            }
            var twicec:       {} -> {} -> ({}->{@r_1,@a_1}->$NumA1->$NumR1) -> ({@global}->{@s_1,@b_1}->$NumB1->$NumS1)
            set twicec = func {} -> {} -> ({}->{@r_1,@a_1}->$NumA1->$NumR1) -> ({@global}->{@s_1,@b_1}->$NumB1->$NumS1) {
                var f: ({}->{@r_1,@a_1}->$NumA1->$NumR1)
                set f = arg
                return func [f] -> ({@global}->{@s_1,@b_1}->$NumB1->$NumS1) {
                    return call f {@s_1,@b_1} (call f {@s_1,@b_1} arg)
                }
            }
            var quad: ({@global}->{@s_1,@b_1}->$NumB1->$NumS1)
            set quad = call twicec square
            output std call quad {@local,@local} two
        """.trimIndent()
        )
        assert(out == "<.1 <.1 <.1 <.1 <.1 <.1 <.1 <.1 <.1 <.1 <.1 <.1 <.1 <.1 <.1 <.1 <.0>>>>>>>>>>>>>>>>>\n") { out }
    }
    @Test
    fun ch_01_08_curry_pg13() {
        val fadd = "({} -> {@r_1,@a_1,@b_1} -> [$NumA1,$NumB1] -> $NumR1)"
        val ret2  = "({@a_1}->{@r_1,@b_1}->$NumB1->$NumR1)"
        val ret1  = "({@global} -> {@a_1} -> $NumA1 -> $ret2)"
        val out = all(
            """
            $nums
            $add
            var curry: {} -> {} -> $fadd -> $ret1
            set curry = func {} -> {} -> $fadd -> $ret1 {
                var f: $fadd
                set f = arg
                return func [f] -> $ret1 {
                    var x: $NumA1
                    set x = arg
                    var ff: $fadd
                    set ff = f
                    return func [ff,x] -> $ret2 {
                        var y: $NumB1
                        set y = arg
                        return call ff {@r_1,@a_1,@b_1} [x,y]: @r_1
                    }
                }
            }
            var addc: $ret1
            set addc = call curry add
            output std call (call addc {@local} one) {@local,@local} two
        """.trimIndent()
        )
        assert(out == "<.1 <.1 <.1 <.0>>>>\n") { out }
    }
    @Test
    fun ch_01_09_uncurry_pg13() {
        val fadd  = "({} -> {@r_1,@a_1,@b_1} -> [$NumA1,$NumB1] -> $NumR1)"
        val fadd2 = "({@global} -> {@r_1,@a_1,@b_1} -> [$NumA1,$NumB1] -> $NumR1)"
        val ret2  = "({@a_1}->{@r_1,@b_1}->$NumB1->$NumR1)"
        val ret1  = "({@global} -> {@a_1} -> $NumA1 -> $ret2)"
        val out = all(
            """
            $nums
            $add

            var curry: {} -> {} -> $fadd -> $ret1
            set curry = func {} -> {} -> $fadd -> $ret1 {
                var f: $fadd
                set f = arg
                return func [f] -> $ret1 {
                    var x: $NumA1
                    set x = arg
                    var ff: $fadd
                    set ff = f
                    return func [ff,x] -> $ret2 {
                        var y: $NumB1
                        set y = arg
                        return call ff {@r_1,@a_1,@b_1} [x,y]: @r_1
                    }
                }
            }

            var uncurry: {} -> {} -> $ret1 -> $fadd2
            set uncurry = func {} -> {} -> $ret1 -> $fadd2 {
                var f: $ret1
                set f = arg
                return func [f] -> $fadd2 {
                    return call (call f {@a_1} arg.1) {@r_1,@b_1} arg.2
                }
            }
            
            var addc: $ret1
            set addc = call curry add
            
            var addu: $fadd2
            set addu = call uncurry addc
            
            output std call addu {@local,@local,@local} [one,two]
        """.trimIndent()
        )
        assert(out == "<.1 <.1 <.1 <.0>>>>\n") { out }
    }
}