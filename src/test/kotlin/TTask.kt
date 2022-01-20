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
            awake f
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
            awake f
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
            awake f
            awake f
        """.trimIndent())
        assert(out == "10\n20\n") { out }
    }
}