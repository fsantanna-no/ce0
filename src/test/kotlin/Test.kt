import org.junit.jupiter.api.MethodOrderer.Alphanumeric
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.io.PushbackReader
import java.io.StringReader

@TestMethodOrder(Alphanumeric::class)
class Tests {
    @Test
    fun a00_buffer () {
        val inp = PushbackReader(StringReader("Hello World!"),2)
        assert(inp.read().toChar() == 'H')
        val c = inp.read()
        assert(c.toChar() == 'e')
        inp.unread(c)
        assert(inp.read().toChar() == 'e')
    }
    @Test
    fun a01_buffer () {
        val inp = PushbackReader(StringReader("Hello World!"),2)
        assert(inp.read().toChar() == 'H')
        val c = inp.read()
        assert(c.toChar() == 'e')
        inp.unread('e'.toInt())
        inp.unread('H'.toInt())
        assert(inp.read().toChar() == 'H')
    }
    @Test
    fun a02_buffer () {
        val inp = PushbackReader(StringReader(""),2)
        assert(inp.read().toChar() == (-1).toChar())
    }

    // BLANKS

    @Test
    fun b01_lexer_blanks () {
        val inp = PushbackReader(StringReader("-- foobar"),2)
        val all = All(inp, Tk(TK.ERR,null,0,0), Tk(TK.ERR,null,0,0))
        blanks(all)
        assert(inp.read() == 65535)     // for some reason, it returns this value after reading -1
        assert(inp.read() == -1)        // then, it returns -1 correctly
        assert(inp.read() == -1)        // then, it returns -1 correctly
        assert(all.lin == 1)
        assert(all.col == 10)
    }
    @Test
    fun b02_lexer_blanks () {
        val inp = PushbackReader(StringReader("-- c1\n--c2\n\n"), 2)
        val all = All(inp, Tk(TK.ERR,null,0,0), Tk(TK.ERR,null,0,0))
        blanks(all)
        assert(all.lin == 4)
        assert(all.col == 1)
    }

    // SYMBOLS

    @Test
    fun b03_lexer_syms () {
        val all = all_new(PushbackReader(StringReader("{ -> ,"), 2))
        lexer(all)
            assert(all.tk1.enu==TK.CHAR && (all.tk1.pay.let { (it as TK_Chr).v == '{' }))
        lexer(all)
            assert(all.tk1.enu==TK.ARROW)
        lexer(all)
            assert(all.tk1.enu==TK.CHAR && (all.tk1.pay.let { (it as TK_Chr).v == ',' }))
    }
}