import java.io.File
import java.io.PushbackReader
import java.io.StringReader

fun ce2c (ce: String): Pair<Boolean,String> {
    val all = All_new(PushbackReader(StringReader(ce), 2))
    lexer(all)
    try {
        val s = parser_stmts(all, Pair(TK.EOF,null))
        s.setUps(null)
        s.setEnvs(null)
        check_01_before_tps(s)
        s.setTypes()
        check_02_after_tps(s)
        return Pair(true, s.code())
    } catch (e: Throwable) {
        CODE.clear()
        //throw e
        return Pair(false, e.message!!)
    }
}

fun c2exe (c: String): Pair<Boolean,String> {
    File("out.c").writeText(c)
    return exec("gcc out.c -o out.exe")
}

fun exe2run (): Pair<Boolean,String> {
    return exec("./out.exe")
    //return exec("valgrind ./out.exe")
}

fun main (args: Array<String>) {
    val inp = File(args[0]).readText()
    val (ok1,out1) = ce2c(inp)
    if (!ok1) {
        println(out1)
        return
    }
    val (ok2,out2) = c2exe(out1)
    if (!ok2) {
        println(out2)
        return
    }
    val (_,out3) = exe2run()
    print(out3)
}

