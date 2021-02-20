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
        lexer(all) ; assert(all.tk1.enu==TK.CHAR && (all.tk1.pay as TK_Chr).v=='{')
        lexer(all) ; assert(all.tk1.enu==TK.ARROW)
        lexer(all) ; assert(all.tk1.enu==TK.CHAR && (all.tk1.pay as TK_Chr).v==',')
    }
    @Test
    fun b04_lexer_syms () {
        val all = all_new(PushbackReader(StringReader(": }{ :"), 2))
        lexer(all) ; assert(all.tk1.enu==TK.CHAR && (all.tk1.pay as TK_Chr).v==':')
        lexer(all) ; assert(all.tk1.enu==TK.CHAR && (all.tk1.pay as TK_Chr).v=='}')
        lexer(all) ; assert(all.tk1.enu==TK.CHAR && (all.tk1.pay as TK_Chr).v=='{')
        lexer(all) ; assert(all.tk1.enu==TK.CHAR && (all.tk1.pay as TK_Chr).v==':')
    }

    // KEYWORDS

    @Test
    fun b05_lexer_keys () {
        val all = all_new(PushbackReader(StringReader("xvar var else varx type"), 2))
        lexer(all) ; assert(all.tk1.enu==TK.XVAR && (all.tk1.pay as TK_Str).v=="xvar")
        lexer(all) ; assert(all.tk1.enu==TK.VAR)
        lexer(all) ; assert(all.tk1.enu==TK.ELSE)
        lexer(all) ; assert(all.tk1.enu==TK.XVAR && (all.tk1.pay as TK_Str).v=="varx")
        lexer(all) ; assert(all.tk1.enu==TK.TYPE)
    }

    // XVAR / XUSER

    @Test
    fun b06_lexer_xs () {
        val all = all_new(PushbackReader(StringReader("c1\nc2 c3  \n    \nc4"), 2))
        lexer(all) ; assert(all.tk1.lin==1 && all.tk1.col==1) ; assert(all.tk1.enu==TK.XVAR && (all.tk1.pay.let { (it as TK_Str).v == "c1" }))
        lexer(all) ; assert(all.tk1.lin==2 && all.tk1.col==1) ; assert(all.tk1.enu==TK.XVAR && (all.tk1.pay.let { (it as TK_Str).v == "c2" }))
        lexer(all) ; assert(all.tk1.lin==2 && all.tk1.col==4) ; assert(all.tk1.enu==TK.XVAR && (all.tk1.pay.let { (it as TK_Str).v == "c3" }))
        lexer(all) ; assert(all.tk1.lin==4 && all.tk1.col==1) ; assert(all.tk1.enu==TK.XVAR && (all.tk1.pay.let { (it as TK_Str).v == "c4" }))
    }
    @Test
    fun b07_lexer_xs () {
        val all = all_new(PushbackReader(StringReader("c1 C1 Ca a C"), 2))
        lexer(all) ; assert(all.tk1.lin==1 && all.tk1.col==1)  ; assert(all.tk1.enu==TK.XVAR  && (all.tk1.pay.let { (it as TK_Str).v == "c1" }))
        lexer(all) ; assert(all.tk1.lin==1 && all.tk1.col==4)  ; assert(all.tk1.enu==TK.XUSER && (all.tk1.pay.let { (it as TK_Str).v == "C1" }))
        lexer(all) ; assert(all.tk1.lin==1 && all.tk1.col==7)  ; assert(all.tk1.enu==TK.XUSER && (all.tk1.pay.let { (it as TK_Str).v == "Ca" }))
        lexer(all) ; assert(all.tk1.lin==1 && all.tk1.col==10) ; assert(all.tk1.enu==TK.XVAR  && (all.tk1.pay.let { (it as TK_Str).v == "a" }))
        lexer(all) ; assert(all.tk1.lin==1 && all.tk1.col==12) ; assert(all.tk1.enu==TK.XUSER && (all.tk1.pay.let { (it as TK_Str).v == "C" }))
    }

    // XNAT

    @Test
    fun b07_lexer_xnat () {
        val all = all_new(PushbackReader(StringReader("_char _Tp"), 2))
        lexer(all) ; assert(all.tk1.enu==TK.XNAT && (all.tk1.pay.let { (it as TK_Str).v == "char" }))
        lexer(all) ; assert(all.tk1.enu==TK.XNAT && (all.tk1.pay.let { (it as TK_Str).v == "Tp" }))
    }
    @Test
    fun b08_lexer_xnat () {
        val all = all_new(PushbackReader(StringReader("_{(1)} _(2+2)"), 2))
        lexer(all) ; assert(all.tk1.enu==TK.XNAT && (all.tk1.pay.let { (it as TK_Str).v == "(1)" }))
        lexer(all) ; assert(all.tk1.enu==TK.XNAT && (all.tk1.pay.let { (it as TK_Str).v == "2+2" }))
    }
}