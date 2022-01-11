import kotlin.math.absoluteValue

val TYPEX = mutableSetOf<String>()
val TYPES = mutableListOf<Triple<Pair<String,Set<String>>,String,String>>()

fun Type.pos (): String {
    return when (this) {
        is Type.Rec -> TODO(this.toString())
        is Type.Unit  -> "int"
        is Type.Ptr   -> this.pln.pos() + "*"
        is Type.Nat   -> this.tk_.src
        is Type.Tuple -> "struct " + this.toce()
        is Type.Union -> "struct " + this.toce()
        is Type.Func  -> this.toce() + "*"
    }
}

fun Type.output (c: String, arg: String): String {
    val tupuni = this is Type.Ptr && (this.pln is Type.Tuple || this.pln is Type.Union)
    return when {
        tupuni -> "output_std_${(this as Type.Ptr).pln.toce()}$c($arg);\n"
        this is Type.Ptr || this is Type.Func -> {
            if (c == "_") "putchar('_');\n" else "puts(\"_\");\n"
        }
        else -> "output_std_${this.toce()}$c($arg);\n"
    }
}

fun deps (tps: Set<Type>): Set<String> {
    return tps
        .filter { it !is Type.Rec }
        //.filter { !it.exactlyRec() }
        .map { Pair(it.toce(),it.pos()) }
        .filter { it.second.last()!='*' }
        .map { it.first }
        .toSet()
}

fun code_ft (tp: Type) {
    tp.toce().let {
        if (TYPEX.contains(it)) {
            return
        }
        TYPEX.add(it)
    }

    when (tp) {
        is Type.Func -> {
            val pools = tp.scp1s.second.let { if (it.size == 0) "" else
                it.map { "Pool**" }.joinToString(",") + ","
            }
            val ret = if (tp.scp1s.first == null) {
                "${tp.out.pos()} ${tp.toce()} ($pools ${tp.inp.pos()})"
            } else {
                """
                    struct {
                        ${tp.out.pos()} (*f) (void** ups, $pools ${tp.inp.pos()});
                        void** ups;     // pool + up1 + ...
                    } ${tp.toce()}
                """.trimIndent()
            }
            TYPES.add(Triple(
                Pair(tp.toce(), deps(setOf(tp.inp,tp.out))),
                "typedef $ret;\n",
                ""
            ))
        }
        is Type.Tuple -> {
            val ce = tp.toce()

            val struct = Pair("""
                struct $ce;                    

            """.trimIndent(), """
                struct $ce {
                    ${tp.vec  // do not filter to keep correct i
                        .mapIndexed { i,sub -> (sub.pos() + " _" + (i+1).toString() + ";\n") }
                        .joinToString("")
                    }
                };

            """.trimIndent())

            TYPES.add(Triple(
                //Pair(ce, deps(tp.vec.toSet()).let { println(ce);println(it);it }),
                Pair(ce, deps(tp.vec.toSet())),
    """
                ${if (tp.containsRec()) struct.first else struct.second }
                void output_std_${ce}_ (${tp.pos()}* v);
                void output_std_${ce} (${tp.pos()}* v);
                
            ""","""
                ${if (tp.containsRec()) struct.second else "" }
                void output_std_${ce}_ (${tp.pos()}* v) {
                    printf("[");
                    ${tp.vec
                        .mapIndexed { i,sub ->
                            val s = when (sub) {
                                is Type.Union, is Type.Tuple -> "&v->_${i + 1}"
                                else -> "v->_${i + 1}"
                            }
                            sub.output("_", s)
                        }
                        .joinToString("putchar(',');\n")
                    }
                    printf("]");
                }
                void output_std_${ce} (${tp.pos()}* v) {
                    output_std_${ce}_(v);
                    puts("");
                }

            """.trimIndent()))
        }
        is Type.Union -> {
            val ce    = tp.toce()
            val tpexp = tp.expand()

            val struct = Pair ("""
                struct $ce;

            """.trimIndent(),
            """
                struct $ce {
                    int tag;
                    union {
                        ${tpexp  // do not filter to keep correct i
                            .mapIndexed { i,sub -> "${sub.pos()} _${i+1};\n" }
                            .joinToString("")
                        }
                    };
                };

            """.trimIndent())

            TYPES.add(Triple(
                //Pair(ce, deps(tpexp.vec.toSet()).let { println(ce);println(it);it }),
                Pair(ce, deps(tpexp.toSet())),
            """
                ${if (tp.containsRec()) struct.first else struct.second }
                void output_std_${ce}_ (${tp.pos()}* v);
                void output_std_${ce} (${tp.pos()}* v);

            """.trimIndent(),
            """
                ${if (tp.containsRec()) struct.second else "" }
                void output_std_${ce}_ (${tp.pos()}* v) {
                    ${
                        if (!tp.isrec) "" else """
                            if (v == NULL) {
                                printf("<.0>");
                                return;
                            }

                        """.trimIndent()
                    }
                    printf("<.%d", v->tag);
                    switch (v->tag) {
                        ${tpexp
                            .mapIndexed { i,sub ->
                                val s = when (sub) {
                                    is Type.Unit -> ""
                                    is Type.Union, is Type.Tuple -> "putchar(' ');\n" + sub.output("_", "&v->_${i+1}")
                                    else -> "putchar(' ');\n" + sub.output("_", "v->_${i+1}")
                                }
                                """
                                case ${i+1}:
                                    $s
                                break;

                                """.trimIndent()
                            }.joinToString("")
                        }
                    }
                    putchar('>');
                }
                void output_std_${ce} (${tp.pos()}* v) {
                    output_std_${ce}_(v);
                    puts("");
                }

            """.trimIndent()))
        }
    }
}

data class Code (val type: String, val stmt: String, val expr: String)
val CODE = ArrayDeque<Code>()

fun Tk.Scp1.toce (): String {
    return "pool_" + this.lbl + (if (this.num==null) "" else ("_"+this.num))
}

fun code_fe (e: Expr) {
    val xp = e.type!!
    CODE.addFirst(when (e) {
        is Expr.Unit -> Code("", "", "0")
        is Expr.Nat -> Code("", "", e.tk_.src)
        is Expr.Var -> {
            val up = e.ups_first { it is Expr.Func && it.ups.any { it.str==e.tk_.str } }
            if (up != null) {
                val idx = (up as Expr.Func).ups.indexOfFirst { it.str==e.tk_.str }
                Code("", "", "((${xp.pos()})ups[${1+idx}])")    // +1 skips pool
            } else {
                Code("", "", e.tk_.str)
            }
        }
        is Expr.Upref -> CODE.removeFirst().let {
            Code(it.type, it.stmt, "(&" + it.expr + ")")
        }
        is Expr.Dnref -> CODE.removeFirst().let {
            Code(it.type, it.stmt, "(*" + it.expr + ")")
        }
        is Expr.TDisc -> CODE.removeFirst().let { Code(it.type, it.stmt, it.expr + "._" + e.tk_.num) }
        is Expr.UDisc -> CODE.removeFirst().let {
            val ee = it.expr
            val uni = e.uni.type!!
            val pre = if (e.tk_.num == 0) {
                """
                assert(&${it.expr} == NULL);

                """.trimIndent()
            } else {
                """
                ${ if (uni.let { it is Type.Union && it.isrec }) "assert(&${it.expr} != NULL);\n" else "" }
                assert($ee.tag == ${e.tk_.num});

                """.trimIndent()
            }
            Code(it.type, it.stmt + pre, ee+"._"+e.tk_.num)
        }
        is Expr.UPred -> CODE.removeFirst().let {
            val ee = it.expr
            val pos = if (e.tk_.num == 0) {
                "(&${it.expr} == NULL)"
            } else {
                (if (e.uni.type.let { it is Type.Union && it.isrec }) "(&${it.expr} != NULL) && " else "") +
                "($ee.tag == ${e.tk_.num})"
            }
            Code(it.type, it.stmt, pos)
        }
        is Expr.New  -> CODE.removeFirst().let {
            val ID  = "__tmp_" + e.hashCode().absoluteValue
            val ptr = e.type as Type.Ptr

            val up = e.ups_first { it is Expr.Func && (it.type as Type.Func).scp1s.first.let { it!=null && it.lbl==ptr.scp1.lbl && it.num==ptr.scp1.num } }
            val pool = if (up == null) ptr.scp1.toce() else "((Pool**)ups[0])"

            val pre = """
                ${ptr.pos()} $ID = malloc(sizeof(*$ID));
                assert($ID!=NULL && "not enough memory");
                *$ID = ${it.expr};
                pool_push($pool, $ID);

            """.trimIndent()
            Code(it.type, it.stmt+pre, ID)
        }
        is Expr.TCons -> {
            val args = (1..e.arg.size).map { CODE.removeFirst() }.reversed()
            Code (
                args.map { it.type }.joinToString(""),
                args.map { it.stmt }.joinToString(""),
                args.map { it.expr }.filter { it!="" }.joinToString(", ").let { "((${xp.pos()}) { $it })" }
            )
        }
        is Expr.UCons -> CODE.removeFirst().let {
            if (e.tk_.num == 0) {
                Code(it.type,it.stmt,"NULL")
            } else {
                val ID  = "_tmp_" + e.hashCode().absoluteValue
                val sup = "struct " + xp.toce()
                val pre = "$sup $ID = (($sup) { ${e.tk_.num} , ._${e.tk_.num} = ${it.expr} });\n"
                Code(it.type, it.stmt + pre, ID)
            }
        }
        is Expr.Inp -> {
            Code("", "", "input_${e.lib.str}_${e.type!!.toce()}()")
        }
        is Expr.Out  -> {
            val arg = CODE.removeFirst()
            val call = if (e.lib.str == "std") {
                e.arg.type!!.output("", arg.expr)
            } else {
                "output_${e.lib.str}(${arg.expr})"
            }
            Code(arg.type, arg.stmt, call)
        }
        is Expr.Call  -> {
            val arg = CODE.removeFirst()
            val f   = CODE.removeFirst()
            //val ff  = e.f as? Expr.Dnref

            val pools = e.scp1s.first.let { if (it.size == 0) "" else (it.map { out ->
                val up = e.ups_first { it is Expr.Func && (it.type as Type.Func).scp1s.first.let { it!=null && it.lbl==out.lbl && it.num==out.num } }
                if (up == null) out.toce() else "((Pool**)ups[0])"
            }.joinToString(",") + ",") }

            val snd =
                if (e.f is Expr.Var && e.f.tk_.str=="output_std") {
                //if (ff!=null && ff.ptr is Expr.Var && ff.ptr.tk_.str=="output_std") {
                    e.arg.type!!.output("", arg.expr)
                } else {
                    val tpf = e.f.type
                    val xxx = if (tpf is Type.Nat && e.arg is Expr.Unit) "" else arg.expr
                    if (tpf is Type.Func && tpf.scp1s.first!=null) {
                        // only closures that escape (@a_1)
                        f.expr + "->f(" + f.expr + "->ups, " + pools + xxx + ")"
                    } else {
                        f.expr + "(" + pools + xxx + ")"
                    }
                }
            Code(f.type+arg.type, f.stmt+arg.stmt, snd)
        }
        is Expr.Func  -> CODE.removeFirst().let {
            val ID  = "_func_" + e.hashCode().absoluteValue
            val fid = if (e.ups.size == 0) ID else ID+"_f"
            val ups = if (e.ups.size == 0) "" else "void** ups,"
            val pools = e.type_.scp1s.second.let { if (it.size == 0) "" else
                it.map { "Pool** ${it.toce()}" }.joinToString(",") + ","
            }
            val pool = e.type_.scp1s.first?.toce()
            val clo = if (e.type_.scp1s.first == null) "" else """
                void** ups = malloc(${e.ups.size+1} * sizeof(void*));   // +1 pool
                assert(ups!=NULL && "not enough memory");
                pool_push($pool, ups);
                ups[0] = $pool;
                ${e.ups.mapIndexed { i,up -> "ups[${i+1}] = ${up.str};\n" }.joinToString("")}
                ${e.type_.pos()} $ID = malloc(sizeof(*$ID));
                // closure needs to go into the pool b/c it has to be a pointer and go into other closures
                pool_push($pool, $ID);
                *$ID = (${e.type_.toce()}) { $fid, ups }; 
            """.trimIndent()
            val pre = """
                ${e.type_.out.pos()} $fid ($ups $pools ${e.type_.inp.pos()} arg) {
                    ${e.type_.out.pos()} ret;
                    ${it.stmt}
                    return ret;
                }

            """.trimIndent()
            Code(it.type+pre, clo, ID)
        }
    })
}

fun code_fs (s: Stmt) {
    CODE.addFirst(when (s) {
        is Stmt.Nop -> Code("","","")
        is Stmt.Nat  -> Code("", s.tk_.src + "\n", "")
        is Stmt.Seq  -> { val s2=CODE.removeFirst() ; val s1=CODE.removeFirst() ; Code(s1.type+s2.type, s1.stmt+s2.stmt, "") }
        is Stmt.Set  -> {
            val src = CODE.removeFirst()
            val dst = CODE.removeFirst()
            Code(dst.type+src.type, dst.stmt+src.stmt + dst.expr+" = "+src.expr + ";\n", "")
        }
        is Stmt.If -> {
            val false_ = CODE.removeFirst()
            val true_  = CODE.removeFirst()
            val tst = CODE.removeFirst()
            val src = tst.stmt + """
                if (${tst.expr}) {
                    ${true_.stmt}
                } else {
                    ${false_.stmt}
                }

            """.trimIndent()
            Code(tst.type+true_.type+false_.type, src, "")
        }
        is Stmt.Loop  -> CODE.removeFirst().let {
            Code(it.type, "while (1) { ${it.stmt} }", "")
        }
        is Stmt.Break -> Code("", "break;\n", "")
        is Stmt.Ret   -> Code("", "return ret;\n", "")
        is Stmt.SExpr -> CODE.removeFirst().let { Code(it.type, it.stmt+it.expr+";\n", "") }
        is Stmt.Block -> CODE.removeFirst().let {
            val src = """
            {
                Pool* pool  __attribute__((__cleanup__(pool_free))) = NULL;
                Pool** pool_local = &pool;
                ${if (s.scope == null) "" else "Pool** ${s.scope.toce()} = &pool;"}
                ${it.stmt}
            }
            
            """.trimIndent()
            Code(it.type, src, "")
        }
        is Stmt.Var -> {
            val src = "${s.type!!.pos()} ${s.tk_.str};\n"
            if (s.ups_first { it is Stmt.Block } == null) {
                Code(src, "", "")   // globals go outside main
            } else {
                Code("", src, "")
            }
        }
    })
}

fun Stmt.code (): String {
    TYPEX.clear()
    TYPES.clear()
    this.visit(true, ::code_fs, ::code_fe, ::code_ft)
    //val TYPES = mutableListOf<Triple<Pair<String,Set<String>>,String,String>>()

    val ord = TYPES.map { it.first }.toMap() // [ce={ce}]
    fun gt (a: String, b: String): Boolean {
        return (ord[a]!=null && (ord[a]!!.contains(b) || ord[a]!!.any { gt(it,b) }))
    }
    val TPS = //TYPES
    ///*
        TYPES.sortedWith(Comparator { x: Triple<Pair<String, Set<String>>, String, String>, y: Triple<Pair<String, Set<String>>, String, String> ->
            when {
                gt(x.first.first, y.first.first) ->  1
                gt(y.first.first, x.first.first) -> -1
                else -> 0
            }
            /*
            when {
                (x.first.second.contains(y.first.first)) ->  1
                (y.first.second.contains(x.first.first)) -> -1
                else -> 0
            }
             */
        })
    //*/

    val code = CODE.removeFirst()
    assert(CODE.size == 0)
    assert(code.expr == "")

    return ("""
        #include <assert.h>
        #include <stdio.h>
        #include <stdlib.h>
        
        #define input_std_int()      ({ int _x ; scanf("%d",&x) ; x ; })
        #define output_std_Unit_(x)  printf("()")
        #define output_std_Unit(x)   (output_std_Unit_(x), puts(""))
        #define output_std_int_(x)   printf("%d",x)
        #define output_std_int(x)    (output_std_int_(x), puts(""))
        #define output_std_char__(x) printf("\"%s\"",x)
        #define output_std_char_(x)  (output_std_int_(x), puts(""))
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
        
        Pool** pool_global;

        ${TPS.map { it.second }.joinToString("")}
        ${TPS.map { it.third  }.joinToString("")}
        ${code.type}

        int main (void) {
            Pool* pool  __attribute__((__cleanup__(pool_free))) = NULL;
            pool_global = &pool;
            Pool** pool_local  = &pool;
            ${code.stmt}
        }

    """).trimIndent()
}
