import org.junit.jupiter.api.MethodOrderer.Alphanumeric
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.io.File
import java.io.PushbackReader
import java.io.StringReader

@TestMethodOrder(Alphanumeric::class)
class Env {

    fun all (inp: String): String {
        val (ok1,out1) = All_inp2c(inp)
        if (!ok1) {
            return out1
        }
        return "OK"
    }

    fun pre (inp: String): Stmt {
        val all = All_new(PushbackReader(StringReader(inp), 2))
        lexer(all)
        var s = parser_stmts(all, Pair(TK.EOF,null))
        env_PRV_set(s, null)
        return s
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
        //println(out)
        assert(out == "(ln 1, col 6): undeclared variable \"f\"")
    }
    @Test
    fun a03_undeclared_type () {
        val out = all("var x: Nat = ()")
        assert(out == "(ln 1, col 8): undeclared type \"Nat\"")
    }

    // REDECLARED

    @Test
    fun a04_redeclared () {
        val out = all("var x: () = () ; var x: Int = 1")
        assert(out == "(ln 1, col 22): invalid declaration : \"x\" is already declared (ln 1)")
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
        assert(out == "(ln 4, col 14): invalid `.´ : undeclared subcase \"Z\"")
    }
    @Test
    fun b04_user_disc_cons_err () {
        val out = all("""
            output std ().Z!
        """.trimIndent())
        assert(out == "(ln 1, col 12): invalid `.´ : expected user type")
    }
    @Test
    fun b06_user_norec_err () {
        val out = all("""
            type @rec NoRec { X: () ; Y: () }
        """.trimIndent())
        assert(out == "(ln 1, col 11): invalid type declaration : unexpected `@rec´")
    }
    @Test
    fun b07_user_rec_err () {
        val out = all("""
            type Rec { X: Rec ; Y: () }
        """.trimIndent())
        assert(out == "(ln 1, col 15): undeclared type \"Rec\"")
    }
    @Test
    fun b08_user_rec_err () {
        val out = all("""
            type @rec Rec1 { X: Rec1 ; Y: () }
            type Rec2 { X: Rec1 ; Y: () }
        """.trimIndent())
        assert(out == "(ln 2, col 6): invalid type declaration : expected `@rec´")
    }
    @Test
    fun b09_user_err () {
        val out = all("""
            type Z { Z:() }
            type @rec List {
                Item: List
            }
            var l: List = Z.Z
        """.trimIndent())
        assert(out == "(ln 5, col 5): invalid assignment to \"l\" : type mismatch")
    }
    @Test
    fun b10_user_empty_err () {
        val out = all("""
            type Z { Z:() }
            type @rec List {
                Item: List
            }
            var l: List = List.Item Z.Z
        """.trimIndent())
        assert(out == "(ln 5, col 20): invalid constructor \"Item\" : type mismatch")
    }
    @Test
    fun b11_user_empty_err () {
        val out = all("""
            type Z { Z:() }
            type @rec List {
                Item: List
            }
            var l: List = List.Item List.Nil
            output std \l.Z!
        """.trimIndent())
        assert(out == "(ln 6, col 15): invalid `.´ : undeclared subcase \"Z\"")
    }
    @Test
    fun b12_user_empty_ok () {
        val out = all("""
            type Z { Z:() }
            type @rec List {
                Item: List
            }
            var l: List = List.Item List.Nil
            output std \l.Nil!
        """.trimIndent())
        assert(out == "OK")
    }

    @Test
    fun b12_not_rec () {
        val out = all("""
            type @pre @rec List
            type List {
                Item: Int
            }
        """.trimIndent())
        assert(out == "(ln 2, col 6): unmatching type declaration (ln 1)")
    }
    @Test
    fun b13_not_rec () {
        val out = all("""
            type @pre @rec List
            type @rec List {
                Item: Int
            }
        """.trimIndent())
        assert(out == "(ln 2, col 11): invalid type declaration : unexpected `@rec´")
    }

    // TODO: test if empty is part of isrec

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
        assert(out == "(ln 2, col 6): invalid call to \"f\" : type mismatch")
    }
    @Test
    fun c05_type_idx () {
        val out = all("""
            var x: () = (1,2).1
        """.trimIndent())
        assert(out == "(ln 1, col 5): invalid assignment to \"x\" : type mismatch")
    }
    @Test
    fun c06_type_idx () {
        val out = all("""
            var x: (Int,Int) = (1,2)
            set x.1 = ()
        """.trimIndent())
        assert(out == "(ln 2, col 9): invalid assignment : type mismatch")
    }
    @Test
    fun c07_type_upref () {
        val out = all("""
            var x: \Int = 10
        """.trimIndent())
        assert(out == "(ln 1, col 5): invalid assignment to \"x\" : type mismatch")
    }
    @Test
    fun c08_type_upref () {
        val out = all("""
            var y: Int = 10
            var x: Int = \y
        """.trimIndent())
        assert(out == "(ln 2, col 5): invalid assignment to \"x\" : type mismatch")
    }
    @Test
    fun c09_type_upref () {
        val out = all("""
            var y: Int = 10
            var x: \Int = \y
        """.trimIndent())
        assert(out == "OK")
    }
    @Test
    fun c10_type_upref () {
        val out = all("""
            var y: () = ()
            var x: \Int = \y
        """.trimIndent())
        assert(out == "(ln 2, col 5): invalid assignment to \"x\" : type mismatch")
    }
    @Test
    fun c11_type_upref () {
        val out = all("""
            var y: Int = 10
            var x: \Int = \y
            var z: _x = \x
        """.trimIndent())
        assert(out == "OK")
    }
    @Test
    fun c12_type_dnref () {
        val out = all("""
            var x: Int = 10
            output std /x
        """.trimIndent())
        //println(out)
        assert(out == "(ln 2, col 12): invalid `/` : expected pointer type")
    }
    @Test
    fun c13_type_dnref () {
        val out = all("""
            var x: Int = 10
            var y: \Int = \x
            var z: \Int = /y
        """.trimIndent())
        assert(out == "(ln 3, col 5): invalid assignment to \"z\" : type mismatch")
    }

    // DEPTH

    @Test
    fun d01_block () {
        val s = pre("var x: Int = 10 ; { output std x }")
        val blk = (s as Stmt.Seq).s2 as Stmt.Block
        val x = (blk.body as Stmt.Call).call.arg
        val X = x.idToStmt("x")
        assert(X!!.getDepth() == 0)
        assert((s.s2 as Stmt.Block).getDepth() == 1)
        //println("<<<")
    }

    @Test
    fun d02_func () {
        val s = pre("var x: Int = 10 ; func f: ()->() { var y: Int = 10 ; output std x }")
        val blk = ((s as Stmt.Seq).s2 as Stmt.Func).block as Stmt.Block
        val seq = ((((blk.body as Stmt.Seq).s2 as Stmt.Seq).s2 as Stmt.Block).body as Stmt.Seq)
        val call = (seq.s2 as Stmt.Call)
        val x = call.call.arg
        val X = x.idToStmt("x")
        assert(X!!.getDepth() == 0)
        assert(seq.s1.getDepth() == 2)
    }

    // POINTERS

    @Test
    fun e01_ptr_block_err () {
        val out = all("""
            var p1: \Int = ?
            var p2: \Int = ?
            {
                var v: Int = 10
                set p1 = \v
            }
            {
                var v: Int = 20
                set p2 = \v
            }
            output std /p1
        """.trimIndent())
        assert(out == "(ln 5, col 12): invalid assignment : cannot hold pointer to local \"v\" (ln 4) in outer scope")
    }

    @Test
    fun e02_ptr_block_err () {
        val out = all("""
            var x: Int = 10
            var p: \Int = ?
            {
                var y: Int = 10
                set p = \x
                set p = \y
            }
        """.trimIndent())
        assert(out == "(ln 6, col 11): invalid assignment : cannot hold pointer to local \"y\" (ln 4) in outer scope")
    }

    @Test
    fun e03_ptr_func_ok () {
        val out = all("""
            func f : \Int -> \Int {
                return arg
            }
            var v: Int = 10
            var p: \Int = f \v
            output std /p
        """.trimIndent())
        assert(out == "OK")
    }

    @Test
    fun e04_ptr_func_ok () {
        val out = all("""
            var v: Int = 10
            func f : () -> \Int {
                return \v
            }
            var p: \Int = f ()
            output std /p
        """.trimIndent())
        assert(out == "OK")
    }

    @Test
    fun e05_ptr_func_err () {
        val out = all("""
            func f : () -> \Int {
                var v: Int = 10
                return \v
            }
            var v: Int = 10
            var p: \Int = f ()
            output std /p
        """.trimIndent())
        assert(out == "(ln 3, col 5): invalid assignment : cannot hold pointer to local \"v\" (ln 2) in outer scope")
    }

    @Test
    fun e06_ptr_func_err () {
        val out = all("""
            func f : \Int -> \Int {
                var ptr: \Int = arg
                return ptr
            }
            var v: Int = 10
            var p: \Int = f \v
            output std /p
        """.trimIndent())
        assert(out == "(ln 3, col 5): invalid assignment : cannot hold pointer to local \"ptr\" (ln 2) in outer scope")
    }

    @Test
    fun e07_ptr_caret_ok () {
        val out = all("""
            func f : \Int -> \Int {
                var ptr: ^\Int = arg
                return ptr
            }
            var v: Int = 10
            var p: \Int = f \v
            output std /p
        """.trimIndent())
        assert(out == "OK")
    }

    @Test
    fun e08_ptr_caret_err () {
        val out = all("""
            func f : \Int -> \Int {
                var x: Int = 10
                var ptr: ^\Int = \x
                return ptr
            }
            var v: Int = 10
            var p: \Int = f \v
            output std /p
        """.trimIndent())
        assert(out == "(ln 3, col 9): invalid assignment : cannot hold pointer to local \"x\" (ln 2) in outer scope")
    }

    // TODO: caret outside function in global scope
    @Test
    fun e09_ptr_caret_err () {
        val out = all("""
            var ptr: ^\Int = ?
        """.trimIndent())
        assert(out == "OK")
    }

    @Test
    fun e10_ptr_tuple_err () {
        val out = all("""
            var p: \(Int,Int) = ?
            {
                var y: (Int,Int) = (10,20)
                set p = \y
            }
        """.trimIndent())
        assert(out == "(ln 4, col 11): invalid assignment : cannot hold pointer to local \"y\" (ln 3) in outer scope")
    }
    @Test
    fun e11_ptr_user_err () {
        val out = all("""
            type X {
                Y: ()
            }
            var p: \X = ?
            {
                var y: X = X.Y
                set p = \y
            }
        """.trimIndent())
        assert(out == "(ln 7, col 11): invalid assignment : cannot hold pointer to local \"y\" (ln 6) in outer scope")
    }
    @Test
    fun e12_ptr_ptr_err () {
        val out = all("""
            var p: \\Int = ?
            {
                var y: \Int = ?
                set p = \y
            }
        """.trimIndent())
        //println(out)
        assert(out == "(ln 4, col 11): invalid assignment : cannot hold pointer to local \"y\" (ln 3) in outer scope")
    }
    @Test
    fun e13_ptr_ptr_ok () {
        val out = all("""
            var p: \\Int = ?
            var z: Int = 10
            var y: \Int = \z
            set p = \y
            output std //p
        """.trimIndent())
        assert(out == "OK")
    }
}