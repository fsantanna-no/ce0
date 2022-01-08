import java.io.File
import java.io.PushbackReader
import java.io.StringReader

fun All_inp2c (inp: String): Pair<Boolean,String> {
    val all = All_new(PushbackReader(StringReader(inp), 2))
    lexer(all)
    try {
        var s = parser_stmts(all, Pair(TK.EOF,null))
        aux_clear()
        s.aux_upsenvs(null, null)
        check_01_before_tps(s)
        s.aux_tps()
        check_02_after_tps(s)
        return Pair(true, s.code())
    } catch (e: Throwable) {
        CODE.clear()
        //throw e
        return Pair(false, e.message!!)
    }
}

fun exec (cmd: String): Pair<Boolean,String> {
    val p = ProcessBuilder(cmd.split(' '))
        //.redirectOutput(ProcessBuilder.Redirect.PIPE)
        .redirectErrorStream(true)
        .start()
    val ret = p.waitFor()
    val str = p.inputStream.bufferedReader().readText()
    return Pair(ret==0, str)
}

fun main (args: Array<String>) {
    val inp = File(args[0]).readText()
    val (ok1,out1) = All_inp2c(inp)
    if (!ok1) {
        println(out1)
    }
    File("out.c").writeText(out1)
    val (ok2,out2) = exec("gcc out.c -o out.exe")
    if (!ok2) {
        println(out2)
    }
    val (_,out3) = exec("./out.exe")
    //val (_,out3) = exec("valgrind ./out.exe")
    print(out3)
}

