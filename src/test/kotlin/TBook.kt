import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.MethodOrderer.Alphanumeric
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.io.File

val nums = """
    var zero: /</^ @LOCAL>@LOCAL
    set zero = <.0>: /</^ @LOCAL> @LOCAL
    var one: /</^ @LOCAL>@LOCAL
    set one = new <.1 zero>:</^ @LOCAL>: @LOCAL
    var two: /</^@LOCAL>@LOCAL
    set two = new <.1 one>:</^ @LOCAL>: @LOCAL
    var three: /</^ @LOCAL>@LOCAL
    set three = new <.1 two>:</^@LOCAL>: @LOCAL
    var four: /</^ @LOCAL>@LOCAL
    set four = new <.1 three>:</^@LOCAL>: @LOCAL
    var five: /</^ @LOCAL>@LOCAL
    set five = new <.1 four>:</^@LOCAL>: @LOCAL
""".trimIndent()

fun Num (ptr: Boolean, scope: String): String {
    val ret = "</^$scope>"
    return if (!ptr) ret else "/"+ret+scope
}
val NumTL  = Num(true,  "@LOCAL")
val NumA1  = Num(true,  "@a1")
val NumA2  = Num(true,  "@a2")
val NumB1  = Num(true,  "@b1")
val NumC1  = Num(true,  "@c1")
val NumR1  = Num(true,  "@r1")
val _NumR1 = Num(false, "@r1")
val NumS1  = Num(true,  "@s1")

val clone = """
    var clone : func @[r1,a1]-> $NumA1 -> $NumR1
    set clone = func @[r1,a1]-> $NumA1 -> $NumR1 {
        if arg\?0 {
            set ret = <.0>:$NumR1
        } else {
            set ret = new <.1 clone @[r1,a1] arg\!1: @r1>:$_NumR1: @r1
        }
    }
""".trimIndent()

val add = """
    var add : func @[r1,a1,b1]-> [$NumA1,$NumB1] -> $NumR1
    set add = func @[r1,a1,b1]-> [$NumA1,$NumB1] -> $NumR1 {
        var x: $NumA1
        set x = arg.1
        var y: $NumB1
        set y = arg.2
        if y\?0 {
            set ret = clone @[r1,a1] x: @r1
        } else {
            set ret = new <.1 add @[r1,a1,b1] [x,y\!1]: @r1>:$_NumR1: @r1
        }
    }
""".trimIndent()

val mul = """
    var mul : func @[r1,a1,b1]-> [$NumA1,$NumB1] -> $NumR1
    set mul = func @[r1,a1,b1]-> [$NumA1,$NumB1] -> $NumR1 {
        var x: $NumA1
        set x = arg.1
        var y: $NumB1
        set y = arg.2
        if y\?0 {
            set ret = <.0>: $NumR1
        } else {
            var z: $NumTL
            set z = mul @[r1,a1,b1] [x, y\!1]
            set ret = add @[r1,a1,LOCAL] [x,z]: @r1
        }
    }
""".trimIndent()

val lt = """
    var lt : func @[a1,b1]-> [$NumA1,$NumB1] -> _int
    set lt = func @[a1,b1]-> [$NumA1,$NumB1] -> _int {
        if arg.2\?0 {
            set ret = _0:_int
        } else {
            if arg.1\?0 {
                set ret = _1:_int
            } else {
                set ret = lt @[a1,b1] [arg.1\!1,arg.2\!1]
            }
        }
    }
""".trimIndent()

val sub = """
    var sub : func @[r1,a1,b1]-> [$NumA1,$NumB1] -> $NumR1
    set sub = func @[r1,a1,b1]-> [$NumA1,$NumB1] -> $NumR1 {
        var x: $NumA1
        set x = arg.1
        var y: $NumB1
        set y = arg.2
        if x\?0 {
            set ret = <.0>: $NumR1
        } else {
            if y\?0 {
                set ret = clone @[r1,a1] x
            } else {
                set ret = sub @[r1,a1,b1] [x\!1,y\!1]: @r1
            }
        }
    }
""".trimIndent()

val mod = """
    var mod : func @[r1,a1,b1] -> [$NumA1,$NumB1] -> $NumR1
    set mod = func @[r1,a1,b1] -> [$NumA1,$NumB1] -> $NumR1 {
        if lt @[a1,b1] arg {
            set ret = clone @[r1,a1] arg.1: @r1
        } else {
            var v: $NumTL
            set v = sub @[LOCAL,a1,b1] arg
            set ret = mod @[r1,LOCAL,b1] [v,arg.2]: @r1
        }
    }    
""".trimIndent()

val eq = """
    var eq : func @[a1,b1]-> [$NumA1,$NumB1] -> _int
    set eq = func @[a1,b1]-> [$NumA1,$NumB1] -> _int {
        var x: $NumA1
        set x = arg.1
        var y: $NumB1
        set y = arg.2
        if x\?0 {
            set ret = y\?0
        } else {
            if y\?0 {
                set ret = _0:_int
            } else {
                set ret = eq @[a1,b1] [x\!1,y\!1]
            }
        }
    }
""".trimIndent()

val lte = """
    var lte : func @[a1,b1]-> [$NumA1,$NumB1] -> _int
    set lte = func @[a1,b1]-> [$NumA1,$NumB1] -> _int {
        var islt: _int
        set islt = lt @[a1,b1] [arg.1\!1,arg.2\!1]
        var iseq: _int
        set iseq = eq @[a1,b1] [arg.1\!1,arg.2\!1]
        set ret = _(${D}islt || ${D}iseq): _int
    }
""".trimIndent()

@TestMethodOrder(Alphanumeric::class)
class TBook {

    fun all(inp: String): String {
        println("nums:  ${nums.count  { it == '\n' }}")
        println("clone: ${clone.count { it == '\n' }}")
        println("add:   ${add.count   { it == '\n' }}")
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
        val (_,out3) = exec("$VALGRIND./out.exe")
        //println(out3)
        return out3
    }

    @Test
    fun pre_01_nums() {
        val out = all(
            """
            var zero: /</^ @LOCAL>@LOCAL
            set zero = <.0>: /</^@LOCAL> @LOCAL
            var one: </^@LOCAL>
            set one = <.1 zero>: </^ @LOCAL>
            var two: </^@LOCAL>
            set two = <.1 /one>: </^@LOCAL>
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
            $clone
            $add
            output std add @[LOCAL,LOCAL,LOCAL] [two,one]: @LOCAL
        """.trimIndent()
        )
        assert(out == "<.1 <.1 <.1 <.0>>>>\n") { out }
    }
    @Test
    fun pre_03_clone() {
        val out = all(
            """
            $nums
            $clone
            output std clone @[LOCAL,LOCAL] two: @LOCAL
        """.trimIndent()
        )
        assert(out == "<.1 <.1 <.0>>>\n") { out }
    }
    @Test
    fun pre_04_mul() {
        val out = all(
            """
            $nums
            $clone
            $add
            $mul
            output std mul @[LOCAL,LOCAL,LOCAL] [two, add @[LOCAL,LOCAL,LOCAL] [two,one]]
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
            output std lt @[LOCAL,LOCAL] [two, one]
            output std lt @[LOCAL,LOCAL] [one, two]
        """.trimIndent()
        )
        assert(out == "0\n1\n") { out }
    }
    @Test
    fun pre_06_sub() {
        val out = all(
            """
            $nums
            $clone
            $add
            $sub
            output std sub @[LOCAL,LOCAL,LOCAL] [three, two]
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
            output std eq @[LOCAL,LOCAL] [three, two]
            output std eq @[LOCAL,LOCAL] [one, one]
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
            $clone
            $add
            $mul
            var square: func @[r1,a1]-> $NumA1 -> $NumR1
            set square = func @[r1,a1]-> $NumA1 -> $NumR1 {
                set ret = mul @[r1,a1,a1] [arg,arg]: @r1
            }
            output std square @[LOCAL,LOCAL] two
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
            var smaller: func @[a1,a2]-> [$NumA1,$NumA2] -> $NumA2
            set smaller = func @[a1,a2]-> [$NumA1,$NumA2] -> $NumA2 {
                if lt @[a1,a2] arg {
                    set ret = arg.1
                } else {
                    set ret = arg.2
                }
            }
            output std smaller @[LOCAL,LOCAL] [one,two]: @LOCAL
            output std smaller @[LOCAL,LOCAL] [two,one]: @LOCAL
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
            var f_three: func @[r1]-> $NumR1 -> $NumR1
            set f_three = func @[r1]-> $NumR1 -> $NumR1 {
                set ret = three
            }
            output std f_three @[LOCAL] one
        """.trimIndent()
        )
        assert(out == "<.1 <.1 <.1 <.0>>>>\n") { out }
    }
    @Disabled // TODO: infinite loop
    @Test
    fun ch_01_02_infinity_pg05() {
        val out = all(
            """
            var infinity: func @[r1]-> () -> $NumR1
            set infinity = func @[r1]-> () -> $NumR1 {
                output std _10:_int
                set ret = new <.1 infinity() @r1>:$_NumR1 @r1
            }
            output std infinity @[LOCAL] ()
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
            $clone
            $add
            $mul
            var multiply: func @[r1,a1,b1]-> [$NumA1,$NumB1] -> $NumR1
            set multiply = func @[r1,a1,b1]-> [$NumA1,$NumB1] -> $NumR1 {
                if arg.1\?0 {
                    set ret = <.0>:${NumR1}
                } else {
                    set ret = mul @[r1,a1,b1] [arg.1,arg.2]: @r1
                }
            }
            output std multiply @[LOCAL,LOCAL,LOCAL] [two,three]
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
            var smallerc:  func @[r1]-> $NumR1 -> (func @r1->@[]-> $NumR1->$NumR1)
            set smallerc = func @[r1]-> $NumR1 -> (func @r1->@[]-> $NumR1->$NumR1) {
                var x: $NumR1
                set x = arg
                set ret = func @r1->@[]-> $NumR1->$NumR1 [x] {
                    if (lt @[r1,r1] [x,arg]) {
                        set ret = x
                    } else {
                        set ret = arg
                    }
                }
            }
            var f: func @LOCAL->@[]-> $NumTL -> $NumTL
            set f = smallerc @[LOCAL] two: @LOCAL   -- smallerc could keep two in memory as long as smallerc does not live longer than two
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
            $clone
            $add
            $mul
            var square: func @[r1,a1]-> $NumA1 -> $NumR1
            set square = func @[r1,a1]-> $NumA1 -> $NumR1 {
                set ret = mul @[r1,a1,a1] [arg,arg]: @r1
            }
            var twice: func @[r1,a1]-> [func @[r1,a1]-> $NumA1->$NumR1, $NumA1] -> $NumR1
            set twice = func @[r1,a1]-> [func @[r1,a1]-> $NumA1->$NumR1, $NumA1] -> $NumR1 {
                set ret = arg.1 @[r1,r1] (arg.1 @[r1,a1] arg.2: @r1): @r1
            }
            output std twice @[LOCAL,LOCAL] [square,two]: @LOCAL
        """.trimIndent()
        )
        assert(out == "<.1 <.1 <.1 <.1 <.1 <.1 <.1 <.1 <.1 <.1 <.1 <.1 <.1 <.1 <.1 <.1 <.0>>>>>>>>>>>>>>>>>\n") { out }
    }
    @Test
    fun ch_01_04_addc_pg12() {
        val out = all(
            """
            $nums
            $clone
            $add
            -- 33
            var plusc: func @[a1]-> $NumA1 -> (func @a1->@[r1,b1]->$NumB1->$NumR1)
            set plusc = func @[a1]-> $NumA1 -> (func @a1->@[r1,b1]->$NumB1->$NumR1) {
                var x: $NumA1
                set x = arg
                set ret = func @a1->@[r1,b1]->$NumB1->$NumR1 [x] {
                    set ret = add @[r1,a1,b1] [x,arg]: @r1
                }
            }
            var f: func @LOCAL->@[r1,b1]->$NumB1->$NumR1
            set f = plusc @[LOCAL] one
            output std f @[LOCAL,LOCAL] two: @LOCAL
            output std f @[LOCAL,LOCAL] one: @LOCAL
            output std (plusc @[LOCAL] one) @[LOCAL,LOCAL] zero: @LOCAL
        """.trimIndent()
        )
        assert(out == "<.1 <.1 <.1 <.0>>>>\n<.1 <.1 <.0>>>\n<.1 <.0>>\n") { out }
    }
    @Test
    fun ch_01_04_quad_pg12() {
        val out = all(
            """
            $nums
            $clone
            $add
            $mul
            var square: func @[r1,a1]-> $NumA1 -> $NumR1
            set square = func @[r1,a1]-> $NumA1 -> $NumR1 {
                set ret = mul @[r1,a1,a1] [arg,arg]: @r1
            }
            var twicec:  func @[] -> (func @[r1,a1]->$NumA1->$NumR1) -> (func @GLOBAL->@[s1,b1]->$NumB1->$NumS1)
            set twicec = func @[] -> (func @[r1,a1]->$NumA1->$NumR1) -> (func @GLOBAL->@[s1,b1]->$NumB1->$NumS1) {
                var f: func @[r1,a1]->$NumA1->$NumR1
                set f = arg
                set ret = func @GLOBAL->@[s1,b1]->$NumB1->$NumS1 [f] {
                    set ret = f @[s1,s1] (f @[s1,b1] arg)
                }
            }
            var quad: func @GLOBAL->@[s1,b1]->$NumB1->$NumS1
            set quad = twicec square
            output std quad @[LOCAL,LOCAL] two
        """.trimIndent()
        )
        assert(out == "<.1 <.1 <.1 <.1 <.1 <.1 <.1 <.1 <.1 <.1 <.1 <.1 <.1 <.1 <.1 <.1 <.0>>>>>>>>>>>>>>>>>\n") { out }
    }
    @Test
    fun ch_01_04_curry_pg13() {
        val fadd = "func @[r1,a1,b1] -> [$NumA1,$NumB1] -> $NumR1"
        val ret2 = "func @a1->@[r1,b1]->$NumB1->$NumR1"
        val ret1 = "func @GLOBAL -> @[a1] -> $NumA1 -> $ret2"
        val out = all(
            """
            $nums
            $clone
            $add
            var curry: func @[] -> $fadd -> $ret1
            set curry = func @[] -> $fadd -> $ret1 {
                var f: $fadd
                set f = arg
                set ret = $ret1 [f] {
                    var x: $NumA1
                    set x = arg
                    var ff: $fadd
                    set ff = f
                    set ret = $ret2 [ff,x] {
                        var y: $NumB1
                        set y = arg
                        set ret = ff @[r1,a1,b1] [x,y]: @r1
                    }
                }
            }
            var addc: $ret1
            set addc = curry add
            output std (addc @[LOCAL] one) @[LOCAL,LOCAL] two
        """.trimIndent()
        )
        assert(out == "<.1 <.1 <.1 <.0>>>>\n") { out }
    }
    @Test
    fun ch_01_04_uncurry_pg13() {
        val fadd  = "func @[r1,a1,b1] -> [$NumA1,$NumB1] -> $NumR1"
        val fadd2 = "func @GLOBAL -> @[r1,a1,b1] -> [$NumA1,$NumB1] -> $NumR1"
        val ret2  = "func @a1->@[r1,b1]->$NumB1->$NumR1"
        val ret1  = "func @GLOBAL -> @[a1] -> $NumA1 -> $ret2"
        val out = all(
            """
            $nums
            $clone
            $add

            var curry: func @[] -> $fadd -> $ret1
            set curry = func @[] -> $fadd -> $ret1 {
                var f: $fadd
                set f = arg
                set ret = $ret1 [f] {
                    var x: $NumA1
                    set x = arg
                    var ff: $fadd
                    set ff = f
                    set ret = $ret2 [ff,x] {
                        var y: $NumB1
                        set y = arg
                        set ret = ff @[r1,a1,b1] [x,y]: @r1
                    }
                }
            }

            var uncurry: func @[] -> $ret1 -> $fadd2
            set uncurry = func @[] -> $ret1 -> $fadd2 {
                var f: $ret1
                set f = arg
                set ret = $fadd2 [f] {
                    set ret = (f @[a1] arg.1) @[r1,b1] arg.2
                }
            }
            
            var addc: $ret1
            set addc = curry add
            
            var addu: $fadd2
            set addu = uncurry addc
            
            output std addu @[LOCAL,LOCAL,LOCAL] [one,two]
        """.trimIndent()
        )
        assert(out == "<.1 <.1 <.1 <.0>>>>\n") { out }
    }
    @Test
    fun ch_01_04_composition_pg15() {
        val T = "func @[r1,a1] -> $NumA1 -> $NumR1"
        val S = "func @GLOBAL -> @[r1,a1] -> $NumA1 -> $NumR1"
        val out = all(
            """
            $nums
            $clone
            $add
            
            var inc: $T
            set inc = func @[r1,a1]-> $NumA1 -> $NumR1 {
                set ret = add @[r1,GLOBAL,a1] [one,arg]: @r1
            }
            output std inc @[LOCAL,LOCAL] two
            
            var compose: func @[]->[$T,$T]->$S
            set compose = func @[]->[$T,$T]->$S {
                var f: $T
                set f = arg.1
                var g: $T
                set g = arg.2
                set ret = $S [f,g] {
                    var v: $NumTL
                    set v = f @[LOCAL,a1] arg: @LOCAL
                    set ret = g @[r1,LOCAL] v: @r1
                }
            }
            output std (compose [inc,inc]) @[LOCAL,LOCAL] one
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
            $clone
            $add
            $mul
            
            var fact: func @[r1,a1]->$NumA1->$NumR1
            set fact = func @[r1,a1]->$NumA1->$NumR1 {
                if arg\?0 {
                    set ret = new <.1 <.0>:$NumR1>:$_NumR1: @r1
                } else {
                    var x: $NumTL
                    set x = fact @[LOCAL,a1] arg\!1
                    set ret = mul @[r1,a1,LOCAL] [arg,x]: @r1
                }
            }
            
            output std fact @[LOCAL,LOCAL] three
        """.trimIndent()
        )
        assert(out == "<.1 <.1 <.1 <.1 <.1 <.1 <.0>>>>>>>\n") { out }
    }

    // CHAPTER 1.6
    // CHAPTER 1.7

    // CHAPTER 2.1

    val B = "<(),()>"
    val and = """
        var and: func @[] -> [$B,$B] -> $B
        set and = func @[] -> [$B,$B] -> $B {
            if arg.1?1 {
                set ret = <.1()>:<(),()>
            } else {
                set ret = arg.2
            }
        }        
    """.trimIndent()
    val or = """
        var or: func @[] -> [$B,$B] -> $B
        set or = func @[] -> [$B,$B] -> $B {
            if arg.1?2 {
                set ret = <.2()>:<(),()>
            } else {
                set ret = arg.2
            }
        }        
    """.trimIndent()
    val not = """
        var not: func @[] -> <(),()> -> <(),()>
        set not = func @[] -> <(),()> -> <(),()> {
            if arg?1 {
                set ret = <.2()>:<(),()>
            } else {
                set ret = <.1()>:<(),()>
            }
        }        
    """.trimIndent()

    val beq = """
        var beq: func @[] -> [$B,$B] -> $B
        set beq = func @[] -> [$B,$B] -> $B {
            set ret = or [and arg, and [not arg.1, not arg.2]] 
        }
        var bneq: func @[] -> [$B,$B] -> $B
        set bneq = func @[] -> [$B,$B] -> $B {
            set ret = not beq arg 
        }        
    """.trimIndent()

    val ntob = """
        var ntob: func @[] -> _int -> $B
        set ntob = func @[] -> _int -> $B {
            if arg {
                set ret = <.2()>:$B
            } else {
                set ret = <.1()>:$B
            } 
        }
    """.trimIndent()

    val bton = """
        var bton: func @[] -> $B -> _int
        set bton = func @[] -> $B -> _int {
            if arg?2 {
                set ret = _1: _int
            } else {
                set ret = _0: _int
            } 
        }
    """.trimIndent()

    @Test
    fun ch_02_01_not_pg30 () {
        val out = all(
            """
            var not: func @[] -> <(),()> -> <(),()>
            set not = func @[] -> <(),()> -> <(),()> {
                if arg?1 {
                    set ret = <.2()>:<(),()>
                } else {
                    set ret = <.1()>:<(),()>
                }
            }
            var xxx: <(),()>
            set xxx = not <.1()>:<(),()>
            output std /xxx
        """.trimIndent()
        )
        assert(out == "<.2>\n") { out }
    }

    @Test
    fun ch_02_01_and_pg30 () {
        val out = all(
            """
            var and: func @[] -> [$B,$B] -> $B
            set and = func @[] -> [$B,$B] -> $B {
                if arg.1?1 {
                    set ret = <.1()>:<(),()>
                } else {
                    set ret = arg.2
                }
            }
            var xxx: <(),()>
            set xxx = and [<.1()>:<(),()>,<.2()>:<(),()>]
            output std /xxx
            set xxx = and [<.2()>:<(),()>,<.2()>:<(),()>]
            output std /xxx
        """.trimIndent()
        )
        assert(out == "<.1>\n<.2>\n") { out }
    }
    @Test
    fun ch_02_01_or_pg30 () {
        val out = all(
            """
            var or: func @[] -> [$B,$B] -> $B
            set or = func @[] -> [$B,$B] -> $B {
                if arg.1?2 {
                    set ret = <.2()>:<(),()>
                } else {
                    set ret = arg.2
                }
            }
            var xxx: <(),()>
            set xxx = or [<.1()>:<(),()>,<.2()>:<(),()>]
            output std /xxx
            set xxx = or [<.2()>:<(),()>,<.1()>:<(),()>]
            output std /xxx
            set xxx = or [<.1()>:<(),()>,<.1()>:<(),()>]
            output std /xxx
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
            var eq: func @[] -> [$B,$B] -> $B
            set eq = func @[] -> [$B,$B] -> $B {
                set ret = or [and arg, and [not arg.1, not arg.2]] 
            }
            var neq: func @[] -> [$B,$B] -> $B
            set neq = func @[] -> [$B,$B] -> $B {
                set ret = not eq arg 
            }
            var xxx: <(),()>
            set xxx = eq [<.1()>:<(),()>,<.2()>:<(),()>]
            output std /xxx
            set xxx = neq [<.2()>:<(),()>,<.1()>:<(),()>]
            output std /xxx
            set xxx = eq [<.2()>:<(),()>,<.1()>:<(),()>]
            output std /xxx
            set xxx = eq [<.1()>:<(),()>,<.1()>:<(),()>]
            output std /xxx
        """.trimIndent()
        )
        assert(out == "<.1>\n<.2>\n<.1>\n<.2>\n") { out }
    }

    @Test
    fun ch_02_01_mod_pg33 () {
        val out = all(
            """
            $nums
            $clone
            $add
            $lt
            $sub
            var mod: func @[r1,a1,b1] -> [$NumA1,$NumB1] -> $NumR1
            set mod = func @[r1,a1,b1] -> [$NumA1,$NumB1] -> $NumR1 {
                if lt @[a1,b1] arg {
                    set ret = clone @[r1,a1] arg.1: @r1
                } else {
                    var v: $NumTL
                    set v = sub @[LOCAL,a1,b1] arg
                    set ret = mod @[r1,LOCAL,b1] [v,arg.2]: @r1
                }
            }
            var v: $NumTL
            set v = mod @[LOCAL,LOCAL,LOCAL] [three,two]
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
            $clone
            $add
            $mul
            $lt
            $sub
            $mod
            $eq
            $or
            $and
            $ntob

            var n10: $NumTL
            set n10 = mul @[LOCAL,LOCAL,LOCAL] [five,two]
            var n100: $NumTL
            set n100 = mul @[LOCAL,LOCAL,LOCAL] [n10,n10]
            var n400: $NumTL
            set n400 = mul @[LOCAL,LOCAL,LOCAL] [four,n100]
            
            var leap: func @[a1] -> $NumA1 -> $B
            set leap = func @[a1] -> $NumA1 -> $B {
                var mod4: $NumTL
                set mod4 = mod @[LOCAL,a1,GLOBAL] [arg,four]
                var mod100: ${NumTL}
                set mod100 = mod @[LOCAL,a1,GLOBAL] [arg,n100]
                var mod400: ${NumTL}
                set mod400 = mod @[LOCAL,a1,GLOBAL] [arg,n400]
                set ret = or [ntob mod4\?0, and [ntob mod100\?1, ntob mod400\?0]]
            }
            
            var n2000: $NumTL
            set n2000 = mul @[LOCAL,LOCAL,LOCAL] [n400,five]
            var n20: $NumTL
            set n20 = add @[LOCAL,LOCAL,LOCAL] [n10,n10]
            var n1980: $NumTL
            set n1980 = sub @[LOCAL,LOCAL,LOCAL] [n2000,n20]
            var n1979: $NumTL
            set n1979 = sub @[LOCAL,LOCAL,LOCAL] [n1980,one]
            var x: $B
            set x = leap @[LOCAL] n1980
            output std /x
            set x = leap @[LOCAL] n1979
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
            $clone
            $add
            $mul
            $lt
            $sub
            $eq
            $lte
            $bton
            $ntob
            $or
            -- 123
            var analyse: func @[a1,b1,c1] -> [$NumA1,$NumB1,$NumC1] -> $Tri
            set analyse = func @[a1,b1,c1] -> [$NumA1,$NumB1,$NumC1] -> $Tri {
                var xy: $NumTL
                set xy = add @[LOCAL,a1,b1] [arg.1,arg.2]
                if lte @[LOCAL,c1] [xy,arg.3] {
                    set ret = <.1()>:$Tri
                    return
                } else {}
                if eq @[a1,c1] [arg.1,arg.3] {
                    set ret = <.2()>:$Tri
                    return
                } else {}
                if bton (or [
                    ntob (eq @[a1,b1] [arg.1,arg.2]),
                    ntob (eq @[b1,c1] [arg.2,arg.3])
                ]) {
                    set ret = <.3()>:$Tri
                    return
                } else {}
                set ret = <.4()>:$Tri
            }
            var n10: $NumTL
            set n10 = mul @[LOCAL,LOCAL,LOCAL] [five,two]
            var v: $Tri
            set v = analyse @[LOCAL,LOCAL,LOCAL] [n10,n10,n10]
            output std /v
            set v = analyse @[LOCAL,LOCAL,LOCAL] [one,five,five]
            output std /v
            set v = analyse @[LOCAL,LOCAL,LOCAL] [one,one,five]
            output std /v
            set v = analyse @[LOCAL,LOCAL,LOCAL] [two,four,five]
            output std /v
        """.trimIndent()
        )
        assert(out == "<.2>\n<.3>\n<.1>\n<.4>\n") { out }
    }
}
