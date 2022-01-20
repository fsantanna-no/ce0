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
            val isclo  = (tp.xscp1s.first != null)
            val istk   = (tp.tk.enu == TK.TASK)
            val isnone = !isclo && !istk

            val xpools = tp.xscp1s.second.let { if (it.size == 0) "" else
                it.map { "Pool**" }.joinToString(",") + ","
            }

            val ret = if (isnone) {
                "typedef ${tp.out.pos()} ${tp.toce()} ($xpools ${tp.inp.pos()});\n"
                //"${tp.out.pos()} ${tp.toce()} $fid ($pc $ups $pools ${e.type.inp.pos()})"
            } else {
                val xfdata = if (isnone) "" else "struct ${tp.toce()}* fdata,"
                """
                    typedef struct ${tp.toce()} {
                        ${tp.out.pos()} (*f) ($xfdata $xpools ${tp.inp.pos()});
                        ${if (!istk) "" else "int pc;"}
                        ${if (!isclo) "" else """
                            Pool* pool;
                            void* mem;
                            
                        """.trimIndent()}
                    } ${tp.toce()};
                    
                """.trimIndent()
            }
            TYPES.add(Triple(
                Pair(tp.toce(), deps(setOf(tp.inp,tp.out))),
                ret,
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
    val xp = e.wtype!!
    CODE.addFirst(when (e) {
        is Expr.Unit -> Code("", "", "0")
        is Expr.Nat -> Code("", "", e.tk_.src)
        is Expr.Var -> {
            val mem = e.ups_first { it is Expr.Func && (it.ups.any { it.str==e.tk_.str } || it.type.tk.enu==TK.TASK) }
            if (mem != null) {
                Code("", "", "(fdata->mem.${e.tk_.str})")
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
            val uni = e.uni.wtype!!
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
                (if (e.uni.wtype.let { it is Type.Union && it.isrec }) "(&${it.expr} != NULL) && " else "") +
                "($ee.tag == ${e.tk_.num})"
            }
            Code(it.type, it.stmt, pos)
        }
        is Expr.New  -> CODE.removeFirst().let {
            val ID  = "__tmp_" + e.hashCode().absoluteValue
            val ptr = e.wtype as Type.Ptr

            val up = e.ups_first { it is Expr.Func && (it.wtype as Type.Func).xscp1s.first.let { it!=null && it.lbl==ptr.xscp1.lbl && it.num==ptr.xscp1.num } }
            val pool = if (up == null) ptr.xscp1.toce() else "(fdata->pool)"

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
            val ID  = "_tmp_" + e.hashCode().absoluteValue
            val sup = "struct " + xp.toce()
            val pre = "$sup $ID = (($sup) { ${e.tk_.num} , ._${e.tk_.num} = ${it.expr} });\n"
            Code(it.type, it.stmt + pre, ID)
        }
        is Expr.UNull -> Code("","","NULL")
        is Expr.Call  -> {
            val arg = CODE.removeFirst()
            val f   = CODE.removeFirst()
            //val ff  = e.f as? Expr.Dnref

            val pools = e.xscp1s.first.let { if (it.size == 0) "" else (it.map { out ->
                val up = e.ups_first { it is Expr.Func && (it.wtype as Type.Func).xscp1s.first.let { it!=null && it.lbl==out.lbl && it.num==out.num } }
                if (up == null) out.toce() else "(fdata->pool)"
            }.joinToString(",") + ",") }

            val snd =
                if (e.f is Expr.Var && e.f.tk_.str=="output_std") {
                //if (ff!=null && ff.ptr is Expr.Var && ff.ptr.tk_.str=="output_std") {
                    e.arg.wtype!!.output("", arg.expr)
                } else {
                    val tpf = e.f.wtype
                    if (tpf is Type.Func) {
                        val isclo  = (tpf.xscp1s.first != null)
                        val istk   = (tpf.tk.enu == TK.TASK)
                        val isnone = !isclo && !istk
                        if (isnone) {
                            f.expr + "(" + pools + arg.expr + ")"
                        } else {
                            f.expr + "->f(" + f.expr + ", " + pools + arg.expr + ")"
                        }
                    } else {
                        f.expr + "(" + pools + (if (e.arg is Expr.Unit) "" else arg.expr) + ")"
                    }
                }
            Code(f.type+arg.type, f.stmt+arg.stmt, snd)
        }
        is Expr.Func  -> CODE.removeFirst().let {
            val isclo  = (e.type.xscp1s.first != null)
            val istk   = (e.type.tk.enu == TK.TASK)
            val isnone = !isclo && !istk

            val hash    = e.hashCode().absoluteValue
            val fstruct = "struct ${e.type.toce()}_$hash"
            val fvar    = "_func_$hash"

            val src = if (isnone) "" else """
                $fstruct* $fvar = malloc(sizeof($fstruct));
                assert($fvar!=NULL && "not enough memory");
                $fvar->f = ${fvar}_f;
                ${if (!isclo) "" else {
                    val pool = e.type.xscp1s.first!!.toce()
                    """
                    $fvar->pool = $pool;
                    ${e.ups.map { "$fvar->mem.${it.str} = ${it.str};\n" }.joinToString("")}
                    pool_push($pool, $fvar);

                    """.trimIndent()}
                }
                ${if (!istk) "" else """
                    $fvar->pc = 0;
                    
                """.trimIndent()}
                
            """.trimIndent()

            val xfdata = if (isnone) "" else "$fstruct* fdata,"
            val xpools = e.type.xscp1s.second.let { if (it.size == 0) "" else
                it.map { "Pool** ${it.toce()}" }.joinToString(",") + ","
            }

            fun Stmt.mem_vars (): String {
                return when (this) {
                    is Stmt.Nop, is Stmt.SSet, is Stmt.ESet, is Stmt.Nat,
                    is Stmt.SCall, is Stmt.Spawn, is Stmt.Await, is Stmt.Awake,
                    is Stmt.Inp, is Stmt.Out, is Stmt.Ret, is Stmt.Break -> ""

                    is Stmt.Var -> "${this.xtype!!.pos()} ${this.tk_.str};\n"
                    is Stmt.Loop -> this.block.mem_vars()

                    is Stmt.Block -> """
                        struct {
                            ${this.body.mem_vars()}
                        };
                        
                    """.trimIndent()

                    is Stmt.If -> """
                        union {
                            ${this.true_.mem_vars()}
                            ${this.false_.mem_vars()}
                        };
                            
                    """.trimIndent()

                    is Stmt.Seq -> {
                        if (this.s1 !is Stmt.Block) {
                            this.s1.mem_vars() + this.s2.mem_vars()
                        } else {
                            """
                                union {
                                    ${this.s1.mem_vars()}
                                    struct {
                                        ${this.s2.mem_vars()}
                                    };
                                };
                            """.trimIndent()
                        }
                    }
                }
            }

            val pre = """
                ${if (isnone) "" else {
                    """
                    $fstruct {
                        ${e.type.out.pos()} (*f) ($xfdata $xpools ${e.type.inp.pos()});
                        ${if (!istk) "" else "int pc;"}
                        Pool** pool;
                        struct {
                            ${e.ups.map { "${e.env(it.str)!!.toType().pos()} ${it.str};\n" }.joinToString("")}
                            ${e.block.mem_vars()}
                        } mem;
                    };
                        
                    """.trimIndent()
                }}
                
                // int f (XXX* fdata, Pools**, int arg);
                ${e.type.out.pos()} ${if (isnone) fvar else fvar+"_f"} ($xfdata $xpools ${e.type.inp.pos()} arg) {
                    ${e.type.out.pos()} ret;
                    ${if (!istk) "" else "switch (fdata->pc) {\ncase 0:\n"}                    
                    ${it.stmt}
                    ${if (!istk) "" else "}"}                    
                    return ret;
                }

            """.trimIndent()
            Code(it.type+pre, src, "((${e.type.toce()}*)$fvar)")
        }
    })
}

fun code_fs (s: Stmt) {
    CODE.addFirst(when (s) {
        is Stmt.Nop -> Code("","","")
        is Stmt.Nat  -> Code("", s.tk_.src + "\n", "")
        is Stmt.Seq  -> { val s2=CODE.removeFirst() ; val s1=CODE.removeFirst() ; Code(s1.type+s2.type, s1.stmt+s2.stmt, "") }
        is Stmt.SSet  -> {
            val src = CODE.removeFirst()
            val dst = CODE.removeFirst()
            Code(dst.type+src.type, dst.stmt+src.stmt + dst.expr+" = "+src.expr + ";\n", "")
        }
        is Stmt.ESet  -> {
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
        is Stmt.SCall -> CODE.removeFirst().let { Code(it.type, it.stmt+it.expr+";\n", "") }
        is Stmt.Spawn -> CODE.removeFirst().let { Code(it.type, it.stmt+it.expr+";\n", "") }
        is Stmt.Await -> {
            val N = s.hashCode()
            val src = """
                fdata->pc = $N; // next awake
                return 0;       // await
                case $N:        // awake here
                
            """.trimIndent()
            Code("", src, "")
        }
        is Stmt.Awake -> CODE.removeFirst().let {
            val call = it.expr + "->f(" + it.expr + ", 0);\n"
            Code(it.type, it.stmt+call, "")
        }
        is Stmt.Inp   -> CODE.removeFirst().let {
            if (s.wup is Stmt.SSet) {
                Code(it.type, it.stmt, "input_${s.lib.str}_${s.xtype!!.toce()}(${it.expr})")
            } else {
                Code(it.type, it.stmt + "input_${s.lib.str}_${s.xtype!!.toce()}(${it.expr});\n", "")
            }
        }
        is Stmt.Out -> CODE.removeFirst().let {
            val call = if (s.lib.str == "std") {
                s.arg.wtype!!.output("", it.expr)
            } else {
                "output_${s.lib.str}(${it.expr});\n"
            }
            Code(it.type, it.stmt+call, "")
        }
        is Stmt.Block -> CODE.removeFirst().let {
            val src = """
            {
                Pool* pool  __attribute__((__cleanup__(pool_free))) = NULL;
                Pool** pool_LOCAL = &pool;
                ${if (s.xscp1 == null) "" else "Pool** ${s.xscp1.toce()} = &pool;"}
                ${it.stmt}
            }
            
            """.trimIndent()
            Code(it.type, src, "")
        }
        is Stmt.Var -> {
            val src = "${s.xtype!!.pos()} ${s.tk_.str};\n"
            when {
                // globals, go outside main
                (s.ups_first { it is Stmt.Block } == null) -> Code(src, "", "")

                // task var, ignore here, do to task struct
                ((s.ups_first { it is Expr.Func } as Expr.Func?).let { it!=null && it.type.tk.enu==TK.TASK }) -> Code("", "", "")

                // otherwise, declare here
                else -> Code("", src, "")
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
        
        #define input_std_int(x)     ({ int _x ; scanf("%d",&_x) ; _x ; })
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
        
        Pool** pool_GLOBAL;

        ${TPS.map { it.second }.joinToString("")}
        ${TPS.map { it.third  }.joinToString("")}
        ${code.type}

        int main (void) {
            Pool* pool  __attribute__((__cleanup__(pool_free))) = NULL;
            pool_GLOBAL = &pool;
            Pool** pool_LOCAL  = &pool;
            ${code.stmt}
        }

    """).trimIndent()
}
