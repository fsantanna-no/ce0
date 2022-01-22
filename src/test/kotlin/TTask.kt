import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.MethodOrderer.Alphanumeric
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.io.File

@TestMethodOrder(Alphanumeric::class)
class TTask {

    fun all (inp: String): String {
        val (ok1,out1) = ce2c(inp)
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
    fun a01_output () {
        val out = all("""
            var f: task @[]->()->()
            set f = task @[]->()->() {
                output std _1:_int
            }
            spawn f ()
            output std _2:_int
        """.trimIndent())
        assert(out == "1\n2\n") { out }
    }
    @Test
    fun a02_await () {
        val out = all("""
            var f: task @[]->()->()
            set f = task @[]->()->() {
                output std _1:_int
                await
                output std _3:_int
            }
            spawn f ()
            output std _2:_int
            awake f _0:_int
        """.trimIndent())
        assert(out == "1\n2\n3\n") { out }
    }
    @Test
    fun a03_var () {
        val out = all("""
            var f: task @[]->()->()
            set f = task @[]->()->() {
                var x: _int
                set x = _10:_int
                await
                output std x
            }
            spawn f ()
            awake f _0:_int
        """.trimIndent())
        assert(out == "10\n") { out }
    }
    @Test
    fun a04_vars () {
        val out = all("""
            var f: task @[]->()->()
            set f = task @[]->()->() {
                {
                    var x: _int
                    set x = _10:_int
                    await
                    output std x
                }
                {
                    var y: _int
                    set y = _20:_int
                    await
                    output std y
                }
            }
            spawn f ()
            awake f _0:_int
            awake f _0:_int
        """.trimIndent())
        assert(out == "10\n20\n") { out }
    }
    @Test
    fun a05_args () {
        val out = all("""
            var f: task @[]->_(char*)->()
            set f = task @[]->_(char*)->() {
                output std arg
                await
                output std evt
                await
                output std evt
            }
            spawn f _("hello"):_(char*)
            awake f _10:_int
            awake f _20:_int
        """.trimIndent())
        assert(out == "\"hello\"\n10\n20\n") { out }
    }
    @Test
    fun a06_par () {
        val out = all("""
            var build : func @[] -> () -> task @[]->()->()
            set build = func @[] -> () -> task @[]->()->() {
                set ret = task @[]->()->() {
                    output std _1:_int
                    await
                    output std _2:_int
                }
            }
            var f: task @[]->()->()
            set f = build ()
            var g: task @[]->()->()
            set g = build ()
            output std _10:_int
            spawn f ()
            output std _11:_int
            spawn g ()
            awake f _0:_int
            awake g _0:_int
            output std _12:_int
        """.trimIndent())
        assert(out == "10\n1\n11\n1\n2\n2\n12\n") { out }
    }
    @Test
    fun a07_bcast () {
        val out = all("""
            var f: task @[]->()->()
            set f = task @[]->()->() {
                await
                output std _(evt+0):_int
            }
            spawn f ()
            
            var g: task @[]->()->()
            set g = task @[]->()->() {
                await
                output std _(evt+10):_int
                await
                output std _(evt+10):_int
            }
            spawn g ()
            
            bcast @GLOBAL _1:_int
            bcast @GLOBAL _2:_int
        """.trimIndent())
        assert(out == "1\n11\n12\n") { out }
    }
    @Test
    fun a08_bcast_block () {
        val out = all("""
            var f: task @[]->()->()
            set f = task @[]->()->() {
                await
                output std _(evt+0):_int
            }
            spawn f ()
            
            {
                var g: task @[]->()->()
                set g = task @[]->()->() {
                    await
                    output std _(evt+10):_int
                    await
                    output std _(evt+10):_int
                }
                spawn g ()
                bcast @LOCAL _1:_int
                bcast @LOCAL _2:_int
            }            
        """.trimIndent())
        assert(out == "11\n12\n") { out }
    }
    @Test
    fun a09_nest () {
        val out = all("""
            var f: task @[]->()->()
            set f = task @[]->()->() {
                output std _1:_int
                await
                var g: task @[]->()->()
                set g = task @[]->()->() {
                    output std _2:_int
                    await
                    output std _3:_int
                }
                spawn g ()
                await
                output std _4:_int
            }
            spawn f ()
            output std _10:_int
            bcast @GLOBAL _0:_int
            output std _11:_int
            bcast @GLOBAL _0:_int
            output std _12:_int
        """.trimIndent())
        assert(out == "1\n10\n2\n11\n3\n4\n12\n") { out }
    }
    @Test
    fun a10_block_out () {
        val out = all("""
            var f: task @[]->()->()
            set f = task @[]->()->() {
                output std _10:_int
                {
                    var g: task @[]->()->()
                    set g = task @[]->()->() {
                        output std _20:_int
                        await
                        output std _21:_int
                        await
                        output std _22:_int     -- can't execute this one
                    }
                    spawn g ()
                    await
                }
                output std _11:_int
                var h: task @[]->()->()
                set h = task @[]->()->() {
                    output std _30:_int
                    await
                    output std _31:_int
                }
                spawn h ()
                await
                output std _12:_int
            }
            spawn f ()
            bcast @GLOBAL _0:_int
            bcast @GLOBAL _0:_int
        """.trimIndent())
        assert(out == "10\n20\n21\n11\n30\n31\n12\n") { out }
    }
}