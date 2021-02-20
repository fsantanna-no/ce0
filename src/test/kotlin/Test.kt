import org.junit.jupiter.api.MethodOrderer.Alphanumeric
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.io.PushbackReader
import java.io.StringReader

@TestMethodOrder(Alphanumeric::class)
class Tests {
    @Test
    fun a00_buffer () {
        val inp = PushbackReader(StringReader("Hello World!"))
        assert(inp.read().toChar() == 'H')
        val c = inp.read()
        assert(c.toChar() == 'e')
        inp.unread(c)
        assert(inp.read().toChar() == 'e')
    }

    @Test
    fun a01_blanks () {
        val inp = PushbackReader(StringReader("-- foobar"))
        val all = All(inp)
        blanks(all)
        assert(inp.read() == 65535)     // for some reason, it returns this value after reading -1
        assert(inp.read() == -1)        // then, it returns -1 correctly
        assert(inp.read() == -1)        // then, it returns -1 correctly
        assert(all.lin == 1)
        assert(all.col == 10)
    }
    @Test
    fun a02_blanks () {
        val inp = PushbackReader(StringReader("-- c1\n--c2\n\n"))
        val all = All(inp)
        blanks(all)
        assert(all.lin == 4)
        assert(all.col == 1)
    }
}