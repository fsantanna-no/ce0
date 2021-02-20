import java.io.PushbackReader

data class All(
    val inp: PushbackReader,
    var tk0: Tk,
    var tk1: Tk,
    var lin: Int = 1,
    var col: Int = 1,
)

fun all_new (inp: PushbackReader): All {
    return All(inp, Tk(TK.ERR,null,1,1), Tk(TK.ERR,null,1,1))
}

