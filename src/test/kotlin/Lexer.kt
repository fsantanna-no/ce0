import org.junit.jupiter.api.MethodOrderer.Alphanumeric
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.io.PushbackReader
import java.io.StringReader

@TestMethodOrder(Alphanumeric::class)
class Lexer {
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
        val all = All(inp, Tk.Err(TK.ERR,1,1,""), Tk.Err(TK.ERR,1,1,""))
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
        val all = All(inp, Tk.Err(TK.ERR,1,1,""), Tk.Err(TK.ERR,1,1,""))
        blanks(all)
        assert(all.lin == 4)
        assert(all.col == 1)
    }

    // SYMBOLS

    @Test
    fun b03_lexer_syms () {
        val all = All_new(PushbackReader(StringReader("{ -> , ()"), 2))
        lexer(all) ; assert(all.tk1.enu==TK.CHAR && (all.tk1 as Tk.Chr).chr=='{')
        lexer(all) ; assert(all.tk1.enu==TK.ARROW)
        lexer(all) ; assert(all.tk1.enu==TK.CHAR && (all.tk1 as Tk.Chr).chr==',')
        lexer(all) ; assert(all.tk1.enu==TK.UNIT)
        lexer(all) ; assert(all.tk1.enu==TK.EOF)
    }
    @Test
    fun b04_lexer_syms () {
        val all = All_new(PushbackReader(StringReader(": }{ :"), 2))
        lexer(all) ; assert(all.tk1.enu==TK.CHAR && (all.tk1 as Tk.Chr).chr==':')
        lexer(all) ; assert(all.tk1.enu==TK.CHAR && (all.tk1 as Tk.Chr).chr=='}')
        lexer(all) ; assert(all.tk1.enu==TK.CHAR && (all.tk1 as Tk.Chr).chr=='{')
        lexer(all) ; assert(all.tk1.enu==TK.CHAR && (all.tk1 as Tk.Chr).chr==':')
    }

    // KEYWORDS

    @Test
    fun b05_lexer_keys () {
        val all = All_new(PushbackReader(StringReader("xvar var else varx type output @rec"), 2))
        lexer(all) ; assert(all.tk1.enu==TK.XVAR && (all.tk1 as Tk.Str).str=="xvar")
        lexer(all) ; assert(all.tk1.enu==TK.VAR)
        lexer(all) ; assert(all.tk1.enu==TK.ELSE)
        lexer(all) ; assert(all.tk1.enu==TK.XVAR && (all.tk1 as Tk.Str).str=="varx")
        lexer(all) ; assert(all.tk1.enu==TK.TYPE)
        lexer(all) ; assert(all.tk1.enu==TK.OUT)
        lexer(all) ; assert(all.tk1.enu==TK.AREC && (all.tk1 as Tk.Key).key=="@rec")
    }

    // XVAR / XUSER

    @Test
    fun b06_lexer_xs () {
        val all = All_new(PushbackReader(StringReader("c1\nc2 c3  \n    \nc4"), 2))
        lexer(all) ; assert(all.tk1.lin==1 && all.tk1.col==1) ; assert(all.tk1.enu==TK.XVAR && (all.tk1 as Tk.Str).str=="c1")
        lexer(all) ; assert(all.tk1.lin==2 && all.tk1.col==1) ; assert(all.tk1.enu==TK.XVAR && (all.tk1 as Tk.Str).str=="c2")
        lexer(all) ; assert(all.tk1.lin==2 && all.tk1.col==4) ; assert(all.tk1.enu==TK.XVAR && (all.tk1 as Tk.Str).str=="c3")
        lexer(all) ; assert(all.tk1.lin==4 && all.tk1.col==1) ; assert(all.tk1.enu==TK.XVAR && (all.tk1 as Tk.Str).str=="c4")
    }
    @Test
    fun b07_lexer_xs () {
        val all = All_new(PushbackReader(StringReader("c1 C1 Ca a C"), 2))
        lexer(all) ; assert(all.tk1.lin==1 && all.tk1.col==1)  ; assert(all.tk1.enu==TK.XVAR  && (all.tk1 as Tk.Str).str=="c1")
        lexer(all) ; assert(all.tk1.lin==1 && all.tk1.col==4)  ; assert(all.tk1.enu==TK.XUSER && (all.tk1 as Tk.Str).str=="C1")
        lexer(all) ; assert(all.tk1.lin==1 && all.tk1.col==7)  ; assert(all.tk1.enu==TK.XUSER && (all.tk1 as Tk.Str).str=="Ca")
        lexer(all) ; assert(all.tk1.lin==1 && all.tk1.col==10) ; assert(all.tk1.enu==TK.XVAR  && (all.tk1 as Tk.Str).str=="a")
        lexer(all) ; assert(all.tk1.lin==1 && all.tk1.col==12) ; assert(all.tk1.enu==TK.XUSER && (all.tk1 as Tk.Str).str=="C")
    }

    // XNAT

    @Test
    fun b07_lexer_xnat () {
        val all = All_new(PushbackReader(StringReader("_char _Tp"), 2))
        lexer(all) ; assert(all.tk1.enu==TK.XNAT && (all.tk1 as Tk.Str).str=="char")
        lexer(all) ; assert(all.tk1.enu==TK.XNAT && (all.tk1 as Tk.Str).str=="Tp")
    }
    @Test
    fun b08_lexer_xnat () {
        val all = All_new(PushbackReader(StringReader("_{(1)} _(2+2)"), 2))
        lexer(all) ; assert(all.tk1.enu==TK.XNAT && (all.tk1 as Tk.Str).str=="(1)")
        lexer(all) ; assert(all.tk1.enu==TK.XNAT && (all.tk1 as Tk.Str).str=="2+2")
    }

    // XNUM

    @Test
    fun b09_lexer_xnum () {
        val all = All_new(PushbackReader(StringReader(".1a"), 2))
        lexer(all) ; assert(all.tk1.enu==TK.CHAR && (all.tk1 as Tk.Chr).chr== '.')
        lexer(all) ; assert(all.tk1.enu==TK.ERR && (all.tk1 as Tk.Err).err=="1a")
    }
    @Test
    fun b10_lexer_xnum () {
        val all = All_new(PushbackReader(StringReader("10"), 2))
        lexer(all) ; assert(all.tk1.enu==TK.XNUM && (all.tk1 as Tk.Num).num==10)
    }
    @Test
    fun b11_lexer_xnum_neg_err () {
        val all = All_new(PushbackReader(StringReader("-10a"), 2))
        lexer(all) ; assert(all.tk1.enu==TK.ERR && (all.tk1 as Tk.Err).err=="-10a")
    }
    @Test
    fun b12_lexer_xnum_neg_err () {
        val all = All_new(PushbackReader(StringReader("-10\n"), 2))
        lexer(all) ; assert(all.tk1.enu==TK.XNUM && (all.tk1 as Tk.Num).num==-10)
    }
}