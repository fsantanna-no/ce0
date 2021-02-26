import org.junit.jupiter.api.MethodOrderer.Alphanumeric
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.io.File

@TestMethodOrder(Alphanumeric::class)
class Env {

    fun all (inp: String): String {
        val (ok1,out1) = All_inp2c(inp)
        if (!ok1) {
            return out1
        }
        return "OK"
    }

    // UNDECLARED

    @Test
    fun a01_undeclared_var () {
        val out = all("output std x")
        assert(out == "(ln 1, col 12): undeclared variable \"x\"")
    }
    @Test
    fun a02_undeclared_func () {
        val out = all("call f ()")
        assert(out == "(ln 1, col 6): undeclared variable \"f\"")
    }
    @Test
    fun a03_undeclared_type () {
        val out = all("var x: Nat = ()")
        assert(out == "(ln 1, col 8): undeclared type \"Nat\"")
    }

    // USER

    @Test
    fun b01_user_sup_undeclared () {
        val out = all("""
            var x: Bool = ()
        """.trimIndent())
        assert(out == "(ln 1, col 8): undeclared type \"Bool\"")
    }
    @Test
    fun b02_user_sub_undeclared () {
        val out = all("""
            type Set {
                X: ()
            }
            output std(Set.Set)
        """.trimIndent())
        assert(out == "(ln 4, col 15): undeclared subcase \"Set\"")
    }
    @Test
    fun b03_user_pred_err () {
        val out = all("""
            type Bool { False: () ; True: () }
            type Z { Y:() }
            var z: Z = Z.Y
            output std z.Z?
        """.trimIndent())
        assert(out == "(ln 4, col 14): undeclared subcase \"Z\"")
    }
    @Test
    fun b04_user_disc_cons_err () {
        val out = all("""
            output std ().Z!
        """.trimIndent())
        assert(out == "(ln 1, col 12): invalid discriminator : expected user type")
    }

    // TYPE

    @Test
    fun c01_type_var () {
        val out = all("""
            var x: Int = ()
        """.trimIndent())
        assert(out == "(ln 1, col 5): invalid assignment to \"x\" : type mismatch")
    }
    @Test
    fun c02_type_set () {
        val out = all("""
            var x: () = ()
            set x = 10
        """.trimIndent())
        assert(out == "(ln 2, col 7): invalid assignment to \"x\" : type mismatch")
    }
    @Test
    fun c03_type_func_ret () {
        val out = all("""
            func f : () -> () { return 10 }
        """.trimIndent())
        assert(out == "(ln 1, col 21): invalid return : type mismatch")
    }
    @Test
    fun c04_type_func_arg () {
        val out = all("""
            func f : ((),()) -> () { }
            call f()
        """.trimIndent())
        println(out)
        assert(out == "(ln 1, col 5): invalid assignment to \"x\" : type mismatch")
    }
}