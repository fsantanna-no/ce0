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
            override fun fs (f: Expr): Set<Expr.Func> {
                return emptySet()
            }
        }
        fun fe (e: Expr, st: State) {
            l.add(e)
        }
        s.simul(State(), null, null, ::fe)
        assert(l[0] is Expr.Unk)
        assert(l[1] is Expr.Unit)
        assert(l[2] is Expr.Call)
    }

    @Test
    fun a02 () {
        val S = all("if _0 { call _f _1 } else { call _g _2 }")
        val lall = mutableListOf<Expr>()
        class State: IState {
            var lcur = mutableListOf<Expr>()
            override fun copy (): State {
                val new = State()
                new.lcur.addAll(this.lcur)
                return new
            }
            override fun fs (f: Expr): Set<Expr.Func> {
                return emptySet()
            }
        }
        fun fe (e: Expr, st: State) {
            st.lcur.add(e)
            lall.add(e)
        }
        fun fs (s: Stmt, st: State) {
            if (s == S) {
                println(st.lcur)
            }
        }
        S.simul(State(), ::fs, null, ::fe)
        println(lall)
    }
}