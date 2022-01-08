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
    var four: /</^ @local>
    set four = new <.1 three>:</^>
    var five: /</^ @local>
    set five = new <.1 four>:</^>
""".trimIndent()

fun Num (ptr: Boolean, scope: String): String {
    val ret = "</^$scope>"
    return if (!ptr) ret else "/"+ret+scope
}
val NumTL  = Num(true,  "@local")
val NumA1  = Num(true,  "@a_1")
val NumA2  = Num(true,  "@a_2")
val NumB1  = Num(true,  "@b_1")
val NumC1  = Num(true,  "@c_1")
val NumR1  = Num(true,  "@r_1")
val _NumR1 = Num(false, "@r_1")
val NumS1  = Num(true,  "@s_1")

val clone = """
    var clone: ({}->{@r_1,@a_1}-> $NumA1 -> $NumR1)
    set clone = func ({}->{@r_1,@a_1}-> $NumA1 -> $NumR1) {
        return call add {@r_1,@global,@a_1} [zero, arg]
    }
""".trimIndent()

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

val sub = """
    var sub: ({}->{@r_1,@a_1,@b_1}-> [$NumA1,$NumB1] -> $NumR1)
    set sub = func ({}->{@r_1,@a_1,@b_1}-> [$NumA1,$NumB1] -> $NumR1) {
        var x: $NumA1
        set x = arg.1
        var y: $NumB1
        set y = arg.2
        if x\?0 {
            return x
        } else {
            if y\?0 {
                return call clone {@r_1,@a_1} x
            } else {
                return call sub {@r_1,@a_1,@b_1} [x\!1,y\!1]: @r_1
            }
        }
    }
""".trimIndent()

val mod = """
    var mod: {} -> {@r_1,@a_1,@b_1} -> [$NumA1,$NumB1] -> $NumR1
    set mod = func {} -> {@r_1,@a_1,@b_1} -> [$NumA1,$NumB1] -> $NumR1 {
        if call lt {@a_1,@b_1} arg {
            return call clone {@r_1,@a_1} arg.1: @r_1
        } else {
            var v: $NumTL
            set v = call sub {@local,@a_1,@b_1} arg
            return call mod {@r_1,@local,@b_1} [v,arg.2]: @r_1
        }
    }    
""".trimIndent()

val eq = """
    var eq: ({}->{@a_1,@b_1}-> [$NumA1,$NumB1] -> _int)
    set eq = func ({}->{@a_1,@b_1}-> [$NumA1,$NumB1] -> _int) {
        var x: $NumA1
        set x = arg.1
        var y: $NumB1
        set y = arg.2
        if x\?0 {
            return y\?0
        } else {
            if y\?0 {
                return _0
            } else {
                return call eq {@a_1,@b_1} [x\!1,y\!1]
            }
        }
    }
""".trimIndent()

val lte = """
    var lte: ({}->{@a_1,@b_1}-> [$NumA1,$NumB1] -> _int)
    set lte = func {}->{@a_1,@b_1}-> [$NumA1,$NumB1] -> _int {
        var islt: _int
        set islt = call lt {@a_1,@b_1} [arg.1\!1,arg.2\!1]
        var iseq: _int
        set iseq = call eq {@a_1,@b_1} [arg.1\!1,arg.2\!1]
        return _(islt || iseq)
    }
""".trimIndent()

@TestMethodOrder(Alphanumeric::class)
class TBook {

    fun all(inp: String): String {
        println("nums:  ${nums.count  { it == '\n' }}")
        println("add:   ${add.count   { it == '\n' }}")
        println("clone: ${clone.count { it == '\n' }}")
        println("mul:   ${mul.count   { it == '\n' }}")
        println("lt:    ${lt.count    { it == '\n' }}")
        println("sub:   ${sub.count   { it == '\n' }}")
        println("mod:   ${mod.count   { it == '\n' }}")
        println("eq:    ${eq.count    { it == '\n' }}")
        println("lte:   ${lte.count   { it == '\n' }}")
        println("bton:  ${bton.count  { it == '\n' }}")
        println("ntob:  ${ntob.count  { it == '\n' }}")
        println("or:    ${or.count    { it == '\n' }}")
        println("and:   ${and.count   { it == '\n' }}")

        val (ok1, out1) = ce2c(inp)
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
    fun pre_02_add() {
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
    fun pre_03_clone() {
        val out = all(
            """
            $nums
            $add
            $clone
            output std call clone {@local,@local} two: @local
        """.trimIndent()
        )
        assert(out == "<.1 <.1 <.0>>>\n") { out }
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
    @Test
    fun pre_06_sub() {
        val out = all(
            """
            $nums
            $add
            $clone
            $sub
            output std call sub {@local,@local,@local} [three, two]
        """.trimIndent()
        )
        assert(out == "<.1 <.0>>\n") { out }
    }
    @Test
    fun pre_07_eq() {
        val out = all(
            """
            $nums
            $eq
            output std call eq {@local,@local} [three, two]
            output std call eq {@local,@local} [one, one]
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
    fun ch_01_04_twice_pg11() {
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
    fun ch_01_04_addc_pg12() {
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
    fun ch_01_04_quad_pg12() {
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
    fun ch_01_04_curry_pg13() {
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
    fun ch_01_04_uncurry_pg13() {
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
    @Test
    fun ch_01_04_composition_pg15() {
        val T = "({} -> {@r_1,@a_1} -> $NumA1 -> $NumR1)"
        val S = "({@global} -> {@r_1,@a_1} -> $NumA1 -> $NumR1)"
        val out = all(
            """
            $nums
            $add
            
            var inc: $T
            set inc = func {}->{@r_1,@a_1}-> $NumA1 -> $NumR1 {
                return call add {@r_1,@global,@a_1} [one,arg]: @r_1
            }
            output std call inc {@local,@local} two
            
            var compose: {}->{}->[$T,$T]->$S
            set compose = func {}->{}->[$T,$T]->$S {
                var f: $T
                set f = arg.1
                var g: $T
                set g = arg.2
                return func [f,g] -> $S {
                    var v: $NumTL
                    set v = call f {@local,@a_1} arg: @local
                    return call g {@r_1,@local} v: @r_1
                }
            }
            output std call (call compose [inc,inc]) {@local,@local} one
        """.trimIndent()
        )
        assert(out == "<.1 <.1 <.1 <.0>>>>\n<.1 <.1 <.1 <.0>>>>\n") { out }
    }

    // CHAPTER 1.5

    @Test
    fun ch_01_05_fact_pg23 () {
        val out = all(
            """
            $nums
            $add
            $mul
            
            var fact: {}->{@r_1,@a_1}->$NumA1->$NumR1
            set fact = func {}->{@r_1,@a_1}->$NumA1->$NumR1 {
                if arg\?0 {
                    return new <.1 <.0>:$NumR1>:$_NumR1: @r_1
                } else {
                    var x: $NumTL
                    set x = call fact {@local,@a_1} arg\!1
                    return call mul {@r_1,@a_1,@local} [arg,x]: @r_1
                }
            }
            
            output std call fact {@local,@local} three
        """.trimIndent()
        )
        assert(out == "<.1 <.1 <.1 <.1 <.1 <.1 <.0>>>>>>>\n") { out }
    }

    // CHAPTER 1.6
    // CHAPTER 1.7

    // CHAPTER 2.1

    val B = "<(),()>"
    val and = """
        var and: {} -> {} -> [$B,$B] -> $B
        set and = func {} -> {} -> [$B,$B] -> $B {
            if arg.1?1 {
                return <.1>:<(),()>
            } else {
                return arg.2
            }
        }        
    """.trimIndent()
    val or = """
        var or: {} -> {} -> [$B,$B] -> $B
        set or = func {} -> {} -> [$B,$B] -> $B {
            if arg.1?2 {
                return <.2>:<(),()>
            } else {
                return arg.2
            }
        }        
    """.trimIndent()
    val not = """
        var not: {} -> {} -> <(),()> -> <(),()>
        set not = func {} -> {} -> <(),()> -> <(),()> {
            if arg?1 {
                return <.2>:<(),()>
            } else {
                return <.1>:<(),()>
            }
        }        
    """.trimIndent()

    val beq = """
        var beq: {} -> {} -> [$B,$B] -> $B
        set beq = func {} -> {} -> [$B,$B] -> $B {
            return call or [call and arg, call and [call not arg.1, call not arg.2]] 
        }
        var bneq: {} -> {} -> [$B,$B] -> $B
        set bneq = func {} -> {} -> [$B,$B] -> $B {
            return call not call beq arg 
        }        
    """.trimIndent()

    val ntob = """
        var ntob: {} -> {} -> _int -> $B
        set ntob = func {} -> {} -> _int -> $B {
            if arg {
                return <.2>:$B
            } else {
                return <.1>:$B
            } 
        }
    """.trimIndent()

    val bton = """
        var bton: {} -> {} -> $B -> _int
        set bton = func {} -> {} -> $B -> _int {
            if arg?2 {
                return _1
            } else {
                return _0
            } 
        }
    """.trimIndent()

    @Test
    fun ch_02_01_not_pg30 () {
        val out = all(
            """
            var not: {} -> {} -> <(),()> -> <(),()>
            set not = func {} -> {} -> <(),()> -> <(),()> {
                if arg?1 {
                    return <.2>:<(),()>
                } else {
                    return <.1>:<(),()>
                }
            }
            var ret: <(),()>
            set ret = call not <.1>:<(),()>
            output std /ret
        """.trimIndent()
        )
        assert(out == "<.2>\n") { out }
    }

    @Test
    fun ch_02_01_and_pg30 () {
        val out = all(
            """
            var and: {} -> {} -> [$B,$B] -> $B
            set and = func {} -> {} -> [$B,$B] -> $B {
                if arg.1?1 {
                    return <.1>:<(),()>
                } else {
                    return arg.2
                }
            }
            var ret: <(),()>
            set ret = call and [<.1>:<(),()>,<.2>:<(),()>]
            output std /ret
            set ret = call and [<.2>:<(),()>,<.2>:<(),()>]
            output std /ret
        """.trimIndent()
        )
        assert(out == "<.1>\n<.2>\n") { out }
    }
    @Test
    fun ch_02_01_or_pg30 () {
        val out = all(
            """
            var or: {} -> {} -> [$B,$B] -> $B
            set or = func {} -> {} -> [$B,$B] -> $B {
                if arg.1?2 {
                    return <.2>:<(),()>
                } else {
                    return arg.2
                }
            }
            var ret: <(),()>
            set ret = call or [<.1>:<(),()>,<.2>:<(),()>]
            output std /ret
            set ret = call or [<.2>:<(),()>,<.1>:<(),()>]
            output std /ret
            set ret = call or [<.1>:<(),()>,<.1>:<(),()>]
            output std /ret
        """.trimIndent()
        )
        assert(out == "<.2>\n<.2>\n<.1>\n") { out }
    }
    @Test
    fun ch_02_01_eq_neq_pg31 () {
        val out = all(
            """
            $not
            $and
            $or
            var eq: {} -> {} -> [$B,$B] -> $B
            set eq = func {} -> {} -> [$B,$B] -> $B {
                return call or [call and arg, call and [call not arg.1, call not arg.2]] 
            }
            var neq: {} -> {} -> [$B,$B] -> $B
            set neq = func {} -> {} -> [$B,$B] -> $B {
                return call not call eq arg 
            }
            var ret: <(),()>
            set ret = call eq [<.1>:<(),()>,<.2>:<(),()>]
            output std /ret
            set ret = call neq [<.2>:<(),()>,<.1>:<(),()>]
            output std /ret
            set ret = call eq [<.1>:<(),()>,<.1>:<(),()>]
            output std /ret
        """.trimIndent()
        )
        assert(out == "<.1>\n<.2>\n<.2>\n") { out }
    }

    @Test
    fun ch_02_01_mod_pg33 () {
        val out = all(
            """
            $nums
            $add
            $clone
            $lt
            $sub
            var mod: {} -> {@r_1,@a_1,@b_1} -> [$NumA1,$NumB1] -> $NumR1
            set mod = func {} -> {@r_1,@a_1,@b_1} -> [$NumA1,$NumB1] -> $NumR1 {
                if call lt {@a_1,@b_1} arg {
                    return call clone {@r_1,@a_1} arg.1: @r_1
                } else {
                    var v: $NumTL
                    set v = call sub {@local,@a_1,@b_1} arg
                    return call mod {@r_1,@local,@b_1} [v,arg.2]: @r_1
                }
            }
            var v: $NumTL
            set v = call mod {@local,@local,@local} [three,two]
            output std v
        """.trimIndent()
        )
        assert(out == "<.1 <.0>>\n") { out }
    }

    @Test
    fun ch_02_01_leap_pg33 () {
        val out = all(
            """
            $nums
            $add
            $clone
            $mul
            $lt
            $sub
            $mod
            $eq
            $or
            $and
            $ntob

            var n10: $NumTL
            set n10 = call mul {@local,@local,@local} [five,two]
            var n100: $NumTL
            set n100 = call mul {@local,@local,@local} [n10,n10]
            var n400: $NumTL
            set n400 = call mul {@local,@local,@local} [four,n100]
            
            var leap: {} -> {@a_1} -> $NumA1 -> $B
            set leap = func {} -> {@a_1} -> $NumA1 -> $B {
                var mod4: $NumTL
                set mod4 = call mod {@local,@a_1,@global} [arg,four]
                var mod100: ${NumTL}
                set mod100 = call mod {@local,@a_1,@global} [arg,n100]
                var mod400: ${NumTL}
                set mod400 = call mod {@local,@a_1,@global} [arg,n400]
                return call or [call ntob mod4\?0, call and [call ntob mod100\?1, call ntob mod400\?0]]
            }
            
            var n2000: $NumTL
            set n2000 = call mul {@local,@local,@local} [n400,five]
            var n20: $NumTL
            set n20 = call add {@local,@local,@local} [n10,n10]
            var n1980: $NumTL
            set n1980 = call sub {@local,@local,@local} [n2000,n20]
            var n1979: $NumTL
            set n1979 = call sub {@local,@local,@local} [n1980,one]
            var x: $B
            set x = call leap {@local} n1980
            output std /x
            set x = call leap {@local} n1979
            output std /x
        """.trimIndent()
        )
        assert(out == "<.2>\n<.1>\n") { out }
    }

    @Test
    fun ch_02_01_triangles_pg33 () {
        val Tri = "<(),(),(),()>"
        val out = all(
            """
            $nums
            $add
            $clone
            $mul
            $lt
            $sub
            $eq
            $lte
            $bton
            $ntob
            $or
            -- 119
            var analyse: {} -> {@a_1,@b_1,@c_1} -> [$NumA1,$NumB1,$NumC1] -> $Tri
            set analyse = func {} -> {@a_1,@b_1,@c_1} -> [$NumA1,$NumB1,$NumC1] -> $Tri {
                var xy: $NumTL
                set xy = call add {@local,@a_1,@b_1} [arg.1,arg.2]
                if call lte {@local,@c_1} [xy,arg.3] {
                    return <.1>:$Tri
                }
                if call eq {@a_1,@c_1} [arg.1,arg.3] {
                    return <.2>:$Tri
                }
                if call bton (call or [
                    call ntob (call eq {@a_1,@b_1} [arg.1,arg.2]),
                    call ntob (call eq {@b_1,@c_1} [arg.2,arg.3])
                ]) {
                    return <.3>:$Tri
                }
                return <.4>:$Tri
            }
            var n10: $NumTL
            set n10 = call mul {@local,@local,@local} [five,two]
            var v: $Tri
            set v = call analyse {@local,@local,@local} [n10,n10,n10]
            output std /v
            set v = call analyse {@local,@local,@local} [one,five,five]
            output std /v
            set v = call analyse {@local,@local,@local} [one,one,five]
            output std /v
            set v = call analyse {@local,@local,@local} [two,four,five]
            output std /v
        """.trimIndent()
        )
        assert(out == "<.2>\n<.3>\n<.1>\n<.4>\n") { out }
    }
}