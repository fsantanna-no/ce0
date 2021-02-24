import org.junit.jupiter.api.MethodOrderer.Alphanumeric
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.io.File
import java.io.PushbackReader
import java.io.StringReader

fun exec (cmd: String): String {
    return ProcessBuilder(cmd.split(' '))
        .redirectOutput(ProcessBuilder.Redirect.PIPE)
        .redirectError(ProcessBuilder.Redirect.PIPE)
        .start()
        .inputStream.bufferedReader().readText()
}

@TestMethodOrder(Alphanumeric::class)
class Exec {

    fun all (inp: String): String {
        val (ok1,out1) = All_inp2out(inp)
        if (!ok1) {
            return out1
        }
        File("out.c").writeText(out1)
        exec("gcc out.c -o out.exe")
        val out2 = exec("./out.exe")
        return out2
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
    fun a04_undeclared () {
        val out = all("output std x")
        println(out)
        assert(out == "(ln 1, col 12): undeclared variable \"x\"")
    }
}