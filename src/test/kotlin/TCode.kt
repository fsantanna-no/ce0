import org.junit.jupiter.api.MethodOrderer.Alphanumeric
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder

@TestMethodOrder(Alphanumeric::class)
class TCode {

    val tp_unit = Type.Unit(Tk.Sym(TK.UNIT,1,1,"()"))

    // TYPE

    @Test
    fun a01_type_unit () {
        assert(tp_unit.toce() == "Unit")
    }
    @Test
    fun a02_type_tuple () {
        val tp = Type.Tuple(Tk.Chr(TK.CHAR,1,1,'['), arrayOf(tp_unit,tp_unit))
        assert(tp.toce() == "T_Unit_Unit_T") { tp.toce() }
    }

    // EXPR

    @Test
    fun b01_expr_unit () {
        val e = Expr.Unit(Tk.Sym(TK.UNIT,1,1,"()"))
        e.wtype = tp_unit
        code_fe(e)
        assert(CODE.removeFirst().expr == "0")
    }
    @Test
    fun b02_expr_var () {
        val e = Expr.Var(Tk.Str(TK.XVAR,1,1,"xxx"))
        e.wenv =
            Stmt.Var (
                Tk.Str(TK.XVAR,1,1,"xxx"),
                Type.Nat(Tk.Nat(TK.XNAT,1,1,null,"int"))
            )
        e.wtype = Type.Nat(Tk.Nat(TK.XNAT,1,1,null,"int"))
        code_fe(e)
        CODE.removeFirst().let { assert(it.expr == "xxx") { it.expr } }
    }
    @Test
    fun b03_expr_nat () {
        val e = Expr.Var(Tk.Str(TK.XNAT,1,1,"xxx"))
        e.wenv =
            Stmt.Var (
                Tk.Str(TK.XVAR,1,1,"xxx"),
                Type.Nat(Tk.Nat(TK.XNAT,1,1,null,"int"))
            )
        e.wtype = Type.Nat(Tk.Nat(TK.XNAT,1,1,null,"int"))
        code_fe(e)
        assert(CODE.removeFirst().expr == "xxx")
    }
    @Test
    fun b04_expr_tuple () {
        val e = Expr.TCons (
            Tk.Chr(TK.CHAR,0, 0, '('),
            arrayOf (
                Expr.Unit(Tk.Sym(TK.UNIT,1,1,"()")),
                Expr.Unit(Tk.Sym(TK.UNIT,1,1,"()")),
            )
        )
        e.wtype = Type.Tuple(Tk.Chr(TK.CHAR,1,1,'['), listOf(tp_unit,tp_unit).toTypedArray())
        e.arg[0].wtype = tp_unit
        e.arg[1].wtype = tp_unit
        e.visit(false, null, ::code_fe, null)
        CODE.removeFirst().expr.let {
            assert(it == "((struct T_Unit_Unit_T) { 0, 0 })")
        }
    }
    @Test
    fun b05_expr_index () {
        val e = Expr.TDisc (
            Tk.Num(TK.XNUM,1,1,1),
            Expr.Var(Tk.Str(TK.XVAR,1,1,"x"))
        )
        e.tup.wenv =
            Stmt.Var (
                Tk.Str(TK.XVAR,1,1,"x"),
                Type.Tuple(Tk.Chr(TK.CHAR,1,1,'('), arrayOf(Type.Nat(Tk.Nat(TK.XNAT,1,1,null,"int"))))
            )
        e.wtype = Type.Nat(Tk.Nat(TK.XNAT,1,1,null,"int"))
        e.tup.wtype = Type.Tuple(Tk.Chr(TK.CHAR,1,1,'('), arrayOf(Type.Nat(Tk.Nat(TK.XNAT,1,1,null,"int"))))
        e.visit(false, null, ::code_fe, null)
        CODE.removeFirst().expr.let {
            assert(it == "x._1")
        }
    }

    // STMT

    @Test
    fun c01_stmt_pass () {
        val s = Stmt.Nop(Tk.Err(TK.ERR,1,1,""))
        s.visit(false, ::code_fs, null, null)
        assert(CODE.removeFirst().stmt == "")
        assert(CODE.size == 0)
    }

    // CODE

    @Test
    fun d01 () {
        val s = Stmt.Nop(Tk.Err(TK.ERR,1,1,""))
        val out = s.code()
        assert(out == """
            #include <assert.h>
            #include <stdio.h>
            #include <stdlib.h>
            
            #define input_std_int(x)     ({ int _x ; scanf("%d",&_x) ; _x ; })
            #define output_std_Unit_(x)  printf("()")
            #define output_std_Unit(x)   (output_std_Unit_(x), puts(""))
            #define output_std_int_(x)   printf("%d",x)
            #define output_std_int(x)    (output_std_int_(x), puts(""))
            #define output_std_char__(x) printf("%s",x)
            #define output_std_char_(x)  (output_std_char__(x), puts(""))
            #define output_std_Ptr_(x)   printf("%p",x)
            #define output_std_Ptr(x)    (output_std_Ptr_(x), puts(""))

            typedef struct Pool {
                void* val;
                struct Pool* nxt;
            } Pool;
            
            void pool_free (Pool** pool) {
                while (*pool != NULL) {
                    Pool* cur = *pool;
                    *pool = cur->nxt;
                    free(cur->val);
                    free(cur);
                }
                *pool = NULL;
            }

            void pool_push (Pool** root, void* val) {
                Pool* pool = malloc(sizeof(Pool));
                assert(pool!=NULL && "not enough memory");
                pool->val = val;
                pool->nxt = *root;
                *root = pool;
            }

            Pool** pool_GLOBAL;





            int main (void) {
                Pool* pool  __attribute__((__cleanup__(pool_free))) = NULL;
                pool_GLOBAL = &pool;
                Pool** pool_LOCAL  = &pool;
                
            }

        """.trimIndent()) { out }
    }
}