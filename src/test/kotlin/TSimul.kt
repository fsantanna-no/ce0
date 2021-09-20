import org.junit.jupiter.api.MethodOrderer.Alphanumeric
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.io.PushbackReader
import java.io.StringReader

@TestMethodOrder(Alphanumeric::class)
class TSimul {

    fun all (inp: String): Stmt {
        val all = All_new(PushbackReader(StringReader(inp), 2))
        lexer(all)
        var s = parser_stmts(all, Pair(TK.EOF,null))
        s = env_prelude(s)
        aux(s)
        return s
    }

    @Test
    fun a01 () {
        val s = all("output std ()")
        val l = mutableListOf<Expr>()
        class State: IState {
            override fun copy (): State {
                return this
            }
            override fun funcs (f: Expr): Set<Stmt.Block> {
                return emptySet()
            }
        }
        fun fe (e: Expr, st: IState) {
            l.add(e)
        }
        s.simul(State(), null, null, ::fe, mutableListOf())
        assert(l[0] is Expr.Unk)
        assert(l[1] is Expr.Call)
        assert(l[2] is Expr.Unit)
    }
    @Test
    fun a02 () {
        val S = all("if _0 { call _f _100 } else { call _g _200 } ; native _xxx")
        class State: IState {
            var lcur = mutableListOf<Expr>()
            override fun copy (): State {
                val new = State()
                new.lcur.addAll(this.lcur)
                return new
            }
            override fun funcs (f: Expr): Set<Stmt.Block> {
                return emptySet()
            }
        }
        fun fe (e: Expr, st: IState) {
            (st as State).lcur.add(e)
        }
        var fst = true
        fun fs (s: Stmt, st: IState) {
            if (s is Stmt.Nat) {
                val lcur = (st as State).lcur
                println(lcur)
                if (fst) {
                    fst = false
                    assert(lcur.any { it is Expr.Nat && it.tk_.str=="100" })
                    assert(lcur.any { it is Expr.Call && it.f is Expr.Nat && (it.f as Expr.Nat).tk_.str=="f" })
                } else {
                    assert(lcur.any { it is Expr.Nat && it.tk_.str=="200" })
                    assert(lcur.any { it is Expr.Call && it.f is Expr.Nat && (it.f as Expr.Nat).tk_.str=="g" })
                }
            }
        }
        S.simul(State(), ::fs, null, ::fe, mutableListOf())
    }
    @Test
    fun a03 () {
        val S = all("""
            if _0 {
                native _aaa
            } else {
                loop {}
            }
            native _xxx
        """)
        class State: IState {
            var lcur = mutableListOf<Expr>()
            override fun copy (): State {
                val new = State()
                new.lcur.addAll(this.lcur)
                return new
            }
            override fun funcs (f: Expr): Set<Stmt.Block> {
                return emptySet()
            }
        }
        fun fe (e: Expr, st: IState) {
            (st as State).lcur.add(e)
        }
        var n = 0
        fun fs (s: Stmt, st: IState) {
            if (s is Stmt.Nat && s.tk_.str=="xxx") {
                n++
                val lcur = (st as State).lcur
                println(lcur)
            }
        }
        S.simul(State(), ::fs, null, ::fe, mutableListOf())
        assert(n == 1)
    }
    @Test
    fun a04 () {
        val S = all("""
            native _000
            if _0 {
                native _aaa
            } else {
                loop {
                    native _111
                    if _0 {
                        native _bbb
                        break
                    } else {
                        native _ccc
                    }
                    native _ddd
                }
                native _eee
            }
            native _xxx
        """)
        class State: IState {
            var lcur = mutableListOf<String>()
            override fun copy (): State {
                val new = State()
                new.lcur.addAll(this.lcur)
                return new
            }
            override fun funcs (f: Expr): Set<Stmt.Block> {
                return emptySet()
            }
        }
        var n = 0
        fun fs (s: Stmt, st: IState) {
            val lcur = (st as State).lcur
            if (s !is Stmt.Nat) return
            if (s.tk_.str == "xxx") {
                n++
                println(lcur)
                when (n) {
                    1 -> assert(lcur.size==2 && lcur[1]=="aaa")
                    2 -> assert(lcur.size==4 && lcur[2]=="bbb")
                }
                //println(lcur)
            } else {
                lcur.add(s.tk_.str)
            }
        }
        S.simul(State(), ::fs, null, null, mutableListOf())
        assert(n == 2)
    }
    @Test
    fun a05 () {
        val S = all("""
            native _000
            var f: ()->() = func ()->() {
                native _f1
                if _0 {
                    return
                }
                native _f2
                return
            }
            if _0 {
                call f()
                native _aaa
            } else {
                loop {
                    native _111
                    if _0 {
                        native _bbb
                        break
                    } else {
                        native _ccc
                    }
                    native _ddd
                }
                native _eee
            }
            native _xxx
        """)
        class State: IState {
            var lcur = mutableListOf<String>()
            override fun copy (): State {
                val new = State()
                new.lcur.addAll(this.lcur)
                return new
            }
            override fun funcs (f: Expr): Set<Stmt.Block> {
                return setOf((((f as Expr.Var).env()!!.src as XExpr.None).e as Expr.Func).block)
            }
        }
        var n = 0
        fun fs (s: Stmt, st: IState) {
            val lcur = (st as State).lcur
            if (s !is Stmt.Nat) return
            if (s.tk_.str == "xxx") {
                n++
                //println(lcur)
                when (n) {
                    1 -> assert(lcur.toString() == "[000, f1, aaa]")
                    2 -> assert(lcur.toString() == "[000, f1, f2, aaa]")
                    3 -> assert(lcur.toString() == "[000, 111, bbb, eee]")
                }
            } else {
                lcur.add(s.tk_.str)
            }
        }
        S.simul(State(), ::fs, null, null, emptyList())
        assert(n == 3)
    }
    @Test
    fun a06 () {
        val S = all("""
            native _000
            var f: ()->() = func ()->() {
                native _f1
                loop {
                    native _111
                    if _0 {
                        return
                    } else {
                        break
                    }
                    native _222
                }
                native _333
                return
            }
            call f()
            native _xxx            
        """)
        class State: IState {
            var lcur = mutableListOf<String>()
            override fun copy (): State {
                val new = State()
                new.lcur.addAll(this.lcur)
                return new
            }
            override fun funcs (f: Expr): Set<Stmt.Block> {
                return setOf((((f as Expr.Var).env()!!.src as XExpr.None).e as Expr.Func).block)
            }
        }
        var n = 0
        fun fs (s: Stmt, st: IState) {
            val lcur = (st as State).lcur
            if (s !is Stmt.Nat) return
            if (s.tk_.str == "xxx") {
                n++
                println(lcur)
                when (n) {
                    1 -> assert(lcur.toString() == "[000, f1, 111]")
                    2 -> assert(lcur.toString() == "[000, f1, 111, 333]")
                }
            } else {
                lcur.add(s.tk_.str)
            }
        }
        S.simul(State(), ::fs, null, null, emptyList())
        assert(n == 2)
    }
}