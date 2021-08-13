import org.junit.jupiter.api.MethodOrderer.Alphanumeric
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.io.PushbackReader
import java.io.StringReader

@TestMethodOrder(Alphanumeric::class)
class Env {

    fun inp2env (inp: String): String {
        val all = All_new(PushbackReader(StringReader(inp), 2))
        lexer(all)
        try {
            var s = parser_stmts(all, Pair(TK.EOF,null))
            s = env_prelude(s)
            env_PRV_set(s, null)
            check_dcls(s)
            check_types(s)
            check_pointers(s)
            return "OK"
        } catch (e: Throwable) {
            //throw e
            return e.message!!
        }
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
        val out = inp2env("output std x")
        assert(out == "(ln 1, col 12): undeclared variable \"x\"")
    }
    @Test
    fun a02_undeclared_func () {
        val out = inp2env("call f ()")
        assert(out == "(ln 1, col 6): undeclared variable \"f\"")
    }
    @Test
    fun a03_undeclared_type () {
        val out = inp2env("var x: Nat = ()")
        assert(out == "(ln 1, col 8): undeclared type \"Nat\"")
    }

    // REDECLARED

    @Test
    fun a04_redeclared () {
        val out = inp2env("var x: () = () ; var x: Int = 1")
        assert(out == "(ln 1, col 22): invalid declaration : \"x\" is already declared (ln 1)")
    }

    // USER

    @Test
    fun b01_user_sup_undeclared () {
        val out = inp2env("""
            var x: Bool = ()
        """.trimIndent())
        assert(out == "(ln 1, col 8): undeclared type \"Bool\"")
    }
    @Test
    fun b02_user_sub_undeclared () {
        val out = inp2env("""
            type Set {
                X: ()
            }
            output std(Set.Set)
        """.trimIndent())
        assert(out == "(ln 4, col 15): undeclared subcase \"Set\"")
    }
    @Test
    fun b03_user_pred_err () {
        val out = inp2env("""
            type Bool { False: () ; True: () }
            type Z { Y:() }
            var z: Z = Z.Y
            output std z.Z?
        """.trimIndent())
        assert(out == "(ln 4, col 14): invalid `.´ : undeclared subcase \"Z\"")
    }
    @Test
    fun b04_user_disc_cons_err () {
        val out = inp2env("""
            output std ().Z!
        """.trimIndent())
        assert(out == "(ln 1, col 12): invalid `.´ : expected user type")
    }
    @Test
    fun b06_user_norec_err () {
        val out = inp2env("""
            type @rec NoRec { X: () ; Y: () }
        """.trimIndent())
        assert(out == "(ln 1, col 11): invalid type declaration : unexpected `@rec´")
    }
    @Test
    fun b07_user_rec_err () {
        val out = inp2env("""
            type Rec { X: Rec ; Y: () }
        """.trimIndent())
        assert(out == "(ln 1, col 15): undeclared type \"Rec\"")
    }
    @Test
    fun b08_user_rec_err () {
        val out = inp2env("""
            type @rec Rec1 { X: Rec1 ; Y: () }
            type Rec2 { X: Rec1 ; Y: () }
        """.trimIndent())
        assert(out == "(ln 2, col 6): invalid type declaration : expected `@rec´")
    }
    @Test
    fun b09_user_err () {
        val out = inp2env("""
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
        val out = inp2env("""
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
        val out = inp2env("""
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
        val out = inp2env("""
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
        val out = inp2env("""
            type @pre @rec List
            type List {
                Item: Int
            }
        """.trimIndent())
        assert(out == "(ln 2, col 6): unmatching type declaration (ln 1)")
    }
    @Test
    fun b13_not_rec () {
        val out = inp2env("""
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
        val out = inp2env("""
            var x: Int = ()
        """.trimIndent())
        assert(out == "(ln 1, col 5): invalid assignment to \"x\" : type mismatch")
    }
    @Test
    fun c02_type_set () {
        val out = inp2env("""
            var x: () = ()
            set x = 10
        """.trimIndent())
        assert(out == "(ln 2, col 7): invalid assignment to \"x\" : type mismatch")
    }
    @Test
    fun c03_type_func_ret () {
        val out = inp2env("""
            func f : () -> () { return 10 }
        """.trimIndent())
        assert(out == "(ln 1, col 21): invalid return : type mismatch")
    }
    @Test
    fun c04_type_func_arg () {
        val out = inp2env("""
            func f : ((),()) -> () { }
            call f()
        """.trimIndent())
        assert(out == "(ln 2, col 6): invalid call to \"f\" : type mismatch")
    }
    @Test
    fun c05_type_idx () {
        val out = inp2env("""
            var x: () = (1,2).1
        """.trimIndent())
        assert(out == "(ln 1, col 5): invalid assignment to \"x\" : type mismatch")
    }
    @Test
    fun c06_type_idx () {
        val out = inp2env("""
            var x: (Int,Int) = (1,2)
            set x.1 = ()
        """.trimIndent())
        assert(out == "(ln 2, col 9): invalid assignment : type mismatch")
    }
    @Test
    fun c07_type_upref () {
        val out = inp2env("""
            var x: \Int = 10
        """.trimIndent())
        assert(out == "(ln 1, col 5): invalid assignment to \"x\" : type mismatch")
    }
    @Test
    fun c08_type_upref () {
        val out = inp2env("""
            var y: Int = 10
            var x: Int = \y
        """.trimIndent())
        assert(out == "(ln 2, col 5): invalid assignment to \"x\" : type mismatch")
    }
    @Test
    fun c09_type_upref () {
        val out = inp2env("""
            var y: Int = 10
            var x: \Int = \y
        """.trimIndent())
        assert(out == "OK")
    }
    @Test
    fun c10_type_upref () {
        val out = inp2env("""
            var y: () = ()
            var x: \Int = \y
        """.trimIndent())
        assert(out == "(ln 2, col 5): invalid assignment to \"x\" : type mismatch")
    }
    @Test
    fun c11_type_upref () {
        val out = inp2env("""
            var y: Int = 10
            var x: \Int = \y
            var z: _x = \x
        """.trimIndent())
        assert(out == "OK")
    }
    @Test
    fun c12_type_dnref () {
        val out = inp2env("""
            var x: Int = 10
            output std /x
        """.trimIndent())
        assert(out == "(ln 2, col 12): invalid `/` : expected pointer type")
    }
    @Test
    fun c13_type_dnref () {
        val out = inp2env("""
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
        val x = (blk.body as Stmt.Call).call.arg.e
        val X = x.idToStmt("x")
        assert(X!!.getDepth() == 0)
        assert((s.s2 as Stmt.Block).getDepth() == 0)
        //println("<<<")
    }
    @Test
    fun d02_func () {
        val s = pre("var x: Int = 10 ; func f: ()->() { var y: Int = 10 ; output std x }")
        val blk = ((s as Stmt.Seq).s2 as Stmt.Func).block as Stmt.Block
        val seq = ((((blk.body as Stmt.Seq).s2 as Stmt.Seq).s2 as Stmt.Block).body as Stmt.Seq)
        val call = (seq.s2 as Stmt.Call)
        val x = call.call.arg.e
        val X = x.idToStmt("x")
        assert(X!!.getDepth() == 0)
        assert(seq.s1.getDepth() == 2)
    }

    // POINTERS

    @Test
    fun e01_ptr_block_err () {
        val out = inp2env("""
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
        assert(out == "(ln 5, col 12): invalid assignment : cannot hold local pointer \"v\" (ln 4)")
    }
    @Test
    fun e02_ptr_block_err () {
        val out = inp2env("""
            var x: Int = 10
            var p: \Int = ?
            {
                var y: Int = 10
                set p = \x
                set p = \y
            }
        """.trimIndent())
        assert(out == "(ln 6, col 11): invalid assignment : cannot hold local pointer \"y\" (ln 4)")
    }
    @Test
    fun e03_ptr_err () {
        val out = inp2env("""
            var pout: \Int = ?
            {
                var pin: \Int = ?
                set pout = pin
            }
        """.trimIndent())
        assert(out == "(ln 4, col 14): invalid assignment : cannot hold local pointer \"pin\" (ln 3)")
    }
    @Test
    fun e04_ptr_ok () {
        val out = inp2env("""
            var pout: \Int = ?
            {
                var pin: \Int = ?
                set pin = pout
            }
        """.trimIndent())
        assert(out == "OK")
    }

    // POINTERS - DOUBLE

    @Test
    fun f01_ptr_ptr_err () {
        val out = inp2env("""
            var p: \\Int = ?
            {
                var y: \Int = ?
                set p = \y
            }
        """.trimIndent())
        assert(out == "(ln 4, col 11): invalid assignment : cannot hold local pointer \"y\" (ln 3)")
    }
    @Test
    fun f02_ptr_ptr_ok () {
        val out = inp2env("""
            var p: \\Int = ?
            var z: Int = 10
            var y: \Int = \z
            set p = \y
            output std //p
        """.trimIndent())
        assert(out == "OK")
    }
    @Test
    fun f03_ptr_ptr_err () {
        val out = inp2env("""
            var p: \\Int = ?
            {
                var z: Int = 10
                var y: \Int = \z
                set p = \y
            }
        """.trimIndent())
        assert(out == "(ln 5, col 11): invalid assignment : cannot hold local pointer \"y\" (ln 4)")
    }
    @Test
    fun f04_ptr_ptr_err () {
        val out = inp2env("""
            var p: \Int = ?
            {
                var x: Int = 10
                var y: \Int = \x
                var z: \\Int = \y
                set p = /z
            }
        """.trimIndent())
        assert(out == "(ln 6, col 11): invalid assignment : cannot hold local pointer \"z\" (ln 5)")
    }

    // POINTERS - FUNC - CALL

    @Test
    fun g01_ptr_func_ok () {
        val out = inp2env("""
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
    fun g02_ptr_func_ok () {
        val out = inp2env("""
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
    fun g03_ptr_func_err () {
        val out = inp2env("""
            func f : () -> \Int {
                var v: Int = 10
                return \v
            }
            var v: Int = 10
            var p: \Int = f ()
            output std /p
        """.trimIndent())
        assert(out == "(ln 3, col 5): invalid assignment : cannot hold local pointer \"v\" (ln 2)")
    }
    @Test
    fun g04_ptr_func_err () {
        val out = inp2env("""
            func f : \Int -> \Int {
                var ptr: \Int = arg
                return ptr
            }
            var v: Int = 10
            var p: \Int = f \v
            output std /p
        """.trimIndent())
        assert(out == "(ln 3, col 5): invalid assignment : cannot hold local pointer \"ptr\" (ln 2)")
    }
    @Test
    fun g05_ptr_caret_ok () {
        val out = inp2env("""
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
    fun g06_ptr_caret_err () {
        val out = inp2env("""
            func f : \Int -> \Int {
                var x: Int = 10
                var ptr: ^\Int = \x
                return ptr
            }
            var v: Int = 10
            var p: \Int = f \v
            output std /p
        """.trimIndent())
        assert(out == "(ln 3, col 9): invalid assignment : cannot hold local pointer \"x\" (ln 2)")
    }

    // TODO: caret outside function in global scope
    @Test
    fun g07_ptr_caret_err () {
        val out = inp2env("""
            var ptr: ^\Int = ?
        """.trimIndent())
        assert(out == "OK")
    }

    @Test
    fun g08_ptr_arg_err () {
        val out = inp2env("""
            func f: Int -> \Int
            {
                return \arg
            }
        """.trimIndent())
        assert(out == "(ln 3, col 5): invalid assignment : cannot hold local pointer \"arg\" (ln 2)")
    }
    @Test
    fun g09_ptr_arg_err () {
        val out = inp2env("""
            func f: Int -> \Int
            {
                var ptr: ^\Int = \arg
                return ptr
            }
        """.trimIndent())
        assert(out == "(ln 3, col 9): invalid assignment : cannot hold local pointer \"arg\" (ln 2)")
    }
    @Test
    fun g10_ptr_out_err () {
        val out = inp2env("""
            func f: \Int -> \\Int
            {
                var ptr: ^\Int = arg
                return \ptr
            }
        """.trimIndent())
        assert(out == "(ln 4, col 5): invalid assignment : cannot hold local pointer \"ptr\" (ln 3)")
    }
    @Test
    fun g11_ptr_func () {
        val out = inp2env("""
            var v: Int = 10
            func f : () -> \Int {
                return \v
            }
            var p: \Int = ?
            {
                set p = f ()
            }
        """.trimIndent())
        assert(out == "(ln 7, col 11): invalid assignment : cannot hold local pointer \"f\" (ln 2)")
    }
    @Test
    fun g12_ptr_func () {
        val out = inp2env("""
            var v: Int = 10
            func f : \Int -> \Int {
                return \v
            }
            var p: \Int = ?
            {
                set p = f (\v)
            }
        """.trimIndent())
        assert(out == "(ln 7, col 11): invalid assignment : cannot hold local pointer \"f\" (ln 2)")
    }
    @Test
    fun g13_ptr_func () {
        val out = inp2env("""
            var v: \Int = ?
            func f : \Int -> () {
                set v = arg
            }
        """.trimIndent())
        assert(out == "(ln 3, col 11): invalid assignment : cannot hold local pointer \"arg\" (ln 2)")
    }

    // POINTERS - TUPLE - TYPE

    @Test
    fun h01_ptr_tuple_err () {
        val out = inp2env("""
            var p: \(Int,Int) = ?
            {
                var y: (Int,Int) = (10,20)
                set p = \y
            }
        """.trimIndent())
        assert(out == "(ln 4, col 11): invalid assignment : cannot hold local pointer \"y\" (ln 3)")
    }
    @Test
    fun h02_ptr_user_err () {
        val out = inp2env("""
            type X {
                Y: ()
            }
            var p: \X = ?
            {
                var y: X = X.Y
                set p = \y
            }
        """.trimIndent())
        assert(out == "(ln 7, col 11): invalid assignment : cannot hold local pointer \"y\" (ln 6)")
    }
    @Test
    fun h03_ptr_tup () {
        val out = inp2env("""
            var v: (Int,Int) = (10,20)
            var p: \Int = \v.1
            set /p = 20
            output std v
        """.trimIndent())
        assert(out == "OK")
    }
    @Test
    fun h04_ptr_tup_err () {
        val out = inp2env("""
            var p: \Int = ?
            {
                var v: (Int,Int) = (10,20)
                set p = \v.1
            }
        """.trimIndent())
        assert(out == "(ln 4, col 11): invalid assignment : cannot hold local pointer \"v\" (ln 3)")
    }
    @Test
    fun h05_ptr_type_err () {
        val out = inp2env("""
            type X {
                X: Int
            }
            var p: \Int = ?
            {
                var v: X = X.X 10
                set p = \v.X!
            }
        """.trimIndent())
        assert(out == "(ln 7, col 11): invalid assignment : cannot hold local pointer \"v\" (ln 6)")
    }
    @Test
    fun h06_ptr_tup_err () {
        val out = inp2env("""
            var p: (Int,\Int) = (10,?)
            {
                var v: Int = 20
                set p.2 = \v
            }
        """.trimIndent())
        assert(out == "(ln 4, col 13): invalid assignment : cannot hold local pointer \"v\" (ln 3)")
    }
    @Test
    fun h07_ptr_tup_err () {
        val out = inp2env("""
            var p: (Int,\Int) = (10,?)
            {
                var v: Int = 20
                set p = (10,\v)
            }
        """.trimIndent())
        assert(out == "(ln 4, col 11): invalid assignment : cannot hold local pointer \"v\" (ln 3)")
    }
    @Test
    fun h08_ptr_type_err () {
        val out = inp2env("""
            type X {
                X: \Int
            }
            var p: X = X.X ?
            {
                var v: Int = 20
                set p.X! = \v
            }
        """.trimIndent())
        assert(out == "(ln 7, col 14): invalid assignment : cannot hold local pointer \"v\" (ln 6)")
    }
    @Test
    fun h09_ptr_type_err () {
        val out = inp2env("""
            type X {
                X: \Int
            }
            var p: X = X.X ?
            {
                var v: Int = 20
                set p = X.X \v
            }
        """.trimIndent())
        assert(out == "(ln 7, col 11): invalid assignment : cannot hold local pointer \"v\" (ln 6)")
    }
    @Test
    fun h10_ptr_tup_err () {
        val out = inp2env("""
            var x1: (Int,\Int) = ?
            {
                var v: Int = 20
                var x2: (Int,\Int) = (10,\v)
                set x1 = x2
            }
        """.trimIndent())
        assert(out == "(ln 5, col 12): invalid assignment : cannot hold local pointer \"x2\" (ln 4)")
    }
    @Test
    fun h11_ptr_type_err () {
        val out = inp2env("""
            type X {
                X: \Int
            }
            var x1: X = ?
            {
                var v: Int = 20
                var x2: X = X.X \v
                set x1 = x2
            }
        """.trimIndent())
        assert(out == "(ln 8, col 12): invalid assignment : cannot hold local pointer \"x2\" (ln 7)")
    }

    // TYPE - REC - MOVE - CLONE - BORROW

    @Test
    fun i01_list () {
        val out = inp2env("""
            type @rec List {
               Item: List
            }
            var p: \List = ?
            {
                var l: List = List.Item List.Item List.Nil
                set p = \l
            }
            output std p
        """.trimIndent())
        assert(out == "(ln 7, col 11): invalid assignment : cannot hold local pointer \"l\" (ln 6)")
    }
    @Test
    fun i02_rec_err () {
        val out = inp2env("""
            type @rec X {
                X: ()
            }
        """.trimIndent())
        assert(out == "(ln 1, col 11): invalid type declaration : unexpected `@rec´")
    }
    @Test
    fun i03_rec_pre_err () {
        val out = inp2env("""
            type @pre List
            type @rec List {
                Item: List
            }
        """.trimIndent())
        assert(out == "(ln 2, col 11): unmatching type declaration (ln 1)")
    }
    @Test
    fun i04_rec_pre_err () {
        val out = inp2env("""
            type @pre @rec List
            type List {
                Item: List
            }
        """.trimIndent())
        assert(out == "(ln 2, col 6): unmatching type declaration (ln 1)")
    }
    @Test
    fun i05_rec_pre_ok () {
        val out = inp2env("""
            type @pre @rec List
            type @rec List {
                Item: List
            }
        """.trimIndent())
        assert(out == "OK")
    }
}