import org.junit.jupiter.api.MethodOrderer.Alphanumeric
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.io.PushbackReader
import java.io.StringReader

@TestMethodOrder(Alphanumeric::class)
class TPointers {

    // TYPES

    @Test
    fun a02_ptr () {
        val all = All_new(PushbackReader(StringReader("""
            /()
        """), 2))
        lexer(all)
        val tp = parser_type(all)
        assert((tp as Type.Ptr).scopeDepth()!!.depth == 0)
    }
    @Test
    fun a03_glb () {
        val all = All_new(PushbackReader(StringReader("""
            /() @global
        """), 2))
        lexer(all)
        val tp = parser_type(all)
        assert((tp as Type.Ptr).scopeDepth()!!.depth == 0)
    }

    @Test
    fun b03_var_glb () {
        val all = All_new(PushbackReader(StringReader("""
            {
                var x: /_int @global
            }
        """), 2))
        lexer(all)
        val s = parser_stmt(all)
        aux(s)
        val x = (s as Stmt.Block).body
        assert(((x as Stmt.Var).type as Type.Ptr).scopeDepth()!!.depth == 0)
    }

}