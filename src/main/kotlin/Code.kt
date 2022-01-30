val TYPEX = mutableSetOf<String>()
val TYPES = mutableListOf<Triple<Pair<String,Set<String>>,String,String>>()

fun Any.self_or_null (): String {
    return if (this.ups_first { it is Expr.Func } == null) "NULL" else "(&task1->task0)"
}

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

fun Type.output_std (c: String, arg: String): String {
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
            val src = """
                typedef union {
                    int evt;
                    struct {
                        Block* blks[${tp.xscp1s.second.size}];
                        ${tp.inp.pos()} arg;
                    } pars;
                } X_${tp.toce()};
                typedef struct {
                    Task task0;
                    ${tp.xscp1s.first.let { if (it == null) "" else "Block* ${it.lbl_num()};" }}
                    union {
                        Block* blks[${tp.xscp1s.second.size}];
                        struct {
                            ${tp.xscp1s.second.let { if (it.size == 0) "" else
                                it.map { "Block* ${it.lbl_num()};\n" }.joinToString("") }
                            }
                        };
                    };
                    union {
                        ${tp.inp.pos()} arg;
                        int evt;
                    };
                    ${tp.pub.let { if (it == null) "" else it.pos() + " pub;" }}
                    ${tp.out.pos()} ret;
                } ${tp.toce()};
                typedef void (*F_${tp.toce()}) (Stack*, ${tp.toce()}*, X_${tp.toce()});
                
            """.trimIndent()

            TYPES.add(Triple(
                Pair(tp.toce(), deps(setOf(tp.inp,tp.out))),
                src,
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
                            sub.output_std("_", s)
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
                                    is Type.Union, is Type.Tuple -> "putchar(' ');\n" + sub.output_std("_", "&v->_${i+1}")
                                    else -> "putchar(' ');\n" + sub.output_std("_", "v->_${i+1}")
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

fun String.mem (up: Any): String {
    val func = if (up is Expr.Func) up else up.ups_first { it is Expr.Func }
    return when {
        (func == null) -> "(global.$this)"
        (this in arrayOf("arg","pub","evt","ret")) -> "(task1->$this)"
        else -> "(task2->$this)"
    }
}

fun Tk.Scp1.lbl_num (): String {
    return this.lbl + this.num.let { if (it==null) "" else "_"+it }
}

fun Tk.Scp1.toce (up: Any): String {
    return when {
        // @GLOBAL is never an argument
        (this.lbl == "GLOBAL")   -> "GLOBAL"
        // @LOCAL depends (calls mem inside local())
        (this.lbl == "LOCAL")    -> up.local()
        // @i_1 is always an argument
        (this.enu == TK.XSCPVAR) -> "(task1->${this.lbl_num()})"
        // closure block is always an argument
        (up.ups_first {
            it is Expr.Func && it.type.xscp1s.first.let {
                it?.enu==this.enu && it?.lbl==this.lbl && it?.num==this.num
            }
        } != null) -> "(task1->${this.lbl_num()})"
        // otherwise depends (calls mem)
        else -> {
            val blk = up.env(this.lbl) as Stmt.Block
            val mem = ("block_" + blk.n).mem(up)
            "(&" + mem + ")"
        }
    }
}

fun Any.local (): String {
    return this.ups_first { it is Stmt.Block }.let {
        if (it == null) "GLOBAL" else {
            val mem = ("block_" + (it as Stmt.Block).n).mem(this)
            "(&" + mem + ")"
        }
    }
}

fun Stmt.mem_vars (): String {
    return when (this) {
        is Stmt.Nop, is Stmt.SSet, is Stmt.ESet, is Stmt.Nat,
        is Stmt.SCall, is Stmt.Spawn, is Stmt.Await, is Stmt.Awake, is Stmt.Bcast, is Stmt.Throw,
        is Stmt.Inp, is Stmt.Out, is Stmt.Ret, is Stmt.Break -> ""

        is Stmt.Var -> "${this.xtype!!.pos()} ${this.tk_.str};\n"
        is Stmt.Loop -> this.block.mem_vars()

        is Stmt.Block -> """
            struct {
                Block block_${this.n};
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

fun code_fe (e: Expr) {
    val xp = e.wtype!!
    CODE.addFirst(when (e) {
        is Expr.Unit  -> Code("", "", "0")
        is Expr.Nat   -> Code("", "", e.tk_.src)
        is Expr.Var   -> Code("", "", e.tk_.str.mem(e.env(true)!!))
        is Expr.Upref -> CODE.removeFirst().let {
            Code(it.type, it.stmt, "(&" + it.expr + ")")
        }
        is Expr.Dnref -> CODE.removeFirst().let {
            Code(it.type, it.stmt, "(*" + it.expr + ")")
        }
        is Expr.TDisc -> CODE.removeFirst().let { Code(it.type, it.stmt, it.expr + "._" + e.tk_.num) }
        is Expr.Pub   -> CODE.removeFirst().let { Code(it.type, it.stmt, it.expr + "->pub") }
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
            val ID  = "__tmp_" + e.n
            val ptr = e.wtype as Type.Ptr

            val pre = """
                ${ptr.pos()} $ID = malloc(sizeof(*$ID));
                assert($ID!=NULL && "not enough memory");
                *$ID = ${it.expr};
                block_push(${ptr.xscp1.toce(ptr)}, $ID);

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
            val ID  = "_tmp_" + e.n
            val sup = "struct " + xp.toce()
            val pre = "$sup $ID = (($sup) { ${e.tk_.num} , ._${e.tk_.num} = ${it.expr} });\n"
            Code(it.type, it.stmt + pre, ID)
        }
        is Expr.UNull -> Code("","","NULL")
        is Expr.Call  -> {
            val arg  = CODE.removeFirst()
            val f    = CODE.removeFirst()
            val blks = e.xscp1s.first.map { it.toce(e) }.joinToString(",")

            val (pre,pos) =
                if (e.f is Expr.Var && e.f.tk_.str=="output_std") {
                //if (ff!=null && ff.ptr is Expr.Var && ff.ptr.tk_.str=="output_std") {
                    Pair(e.arg.wtype!!.output_std("", arg.expr), "")
                } else {
                    val tpf = e.f.wtype
                    if (tpf is Type.Func) {
                        val xxx = if (e.getUp() is Stmt.Awake) {
                            "(X_${tpf.toce()}) {.evt=${arg.expr}}"
                        } else {
                            "(X_${tpf.toce()}) {.pars={{$blks}, ${arg.expr}}}"
                        }
                        val pre = """
                            ${tpf.out.pos()} ret_${e.n};    // TODO: call stack
                            {
                                Stack stk_${e.n} = { stack, ${e.self_or_null()}, ${e.local()} };
                                ((F_${tpf.toce()})(${f.expr}->task0.f)) (&stk_${e.n}, ${f.expr}, $xxx);
                                ${if (tpf.tk.enu != TK.FUNC) "" else "${f.expr}->task0.state = TASK_UNBORN;"}
                                if (stk_${e.n}.block == NULL) {
                                    return;
                                }
                                ret_${e.n} = (${f.expr}->ret);
                            }
                            
                        """.trimIndent()
                        Pair(pre, "ret_${e.n}")
                    } else {
                        val cll = f.expr + "(" + blks + (if (e.arg is Expr.Unit) "" else arg.expr) + ")"
                        Pair("", cll)
                    }
                }
            Code(f.type+arg.type, f.stmt+arg.stmt+pre, pos)
        }
        is Expr.Func  -> CODE.removeFirst().let {
            val isclo   = (e.type.xscp1s.first != null)
            val istk    = (e.type.tk.enu == TK.TASK)
            val isnone  = !isclo && !istk
            val tsk_var = "func_${e.n}"

            val pre = """
                typedef struct Func_${e.n} {
                    ${e.type.toce()} task1;
                    ${e.ups.map { "${e.env(it.str)!!.toType().pos()} ${it.str};\n" }.joinToString("")}
                    ${e.block.mem_vars()}
                } Func_${e.n};
                    
                void f_$tsk_var (Stack* stack, ${e.type.toce()}* task1, X_${e.type.toce()} xxx) {
                    Task*        task0 = (Task*)        task1;
                    Func_${e.n}* task2 = (Func_${e.n}*) task1;
                    ${if (istk) "" else """     // TODO: call stack
                        Func_${e.n} _task2_ = *task2;
                        task2 = &_task2_;
                        
                    """.trimIndent()}
                    ${e.type.xscp1s.second.mapIndexed { i,_ -> "task1->blks[$i] = xxx.pars.blks[$i];\n" }.joinToString("")}
                    assert(task0->state==TASK_UNBORN || task0->state==TASK_AWAITING);
                    switch (task0->pc) {
                        case 0: {                    
                            assert(task0->state == TASK_UNBORN);
                            task1->arg = xxx.pars.arg;
                            ${it.stmt}
                            task0->state = TASK_DEAD;
                            break;
                        }
                        default:
                            assert(0 && "invalid PC");
                            break;
                    }
                    return;
                }

            """.trimIndent()

            val clo_block = if (isclo) e.type.xscp1s.first!!.toce(e.wup!!) else e.local()
            val src = """
                ${if (isnone) "static" else ""}
                Func_${e.n} pln_$tsk_var;
                pln_$tsk_var.task1.task0.f     = (Task_F) f_$tsk_var;
                pln_$tsk_var.task1.task0.state = TASK_UNBORN;
                pln_$tsk_var.task1.task0.pc    = 0;
                ${e.type.xscp1s.first.let { if (it==null) "" else "pln_$tsk_var.task1.${it.lbl_num()} = ${it.toce(e.wup!!)};\n" }}
                ${e.ups.map { "pln_$tsk_var.${it.str} = ${it.str.mem(e.wup!!)};\n" }.joinToString("")}
                ${e.type.toce()}* ptr_$tsk_var = (${e.type.toce()}*)
                    ${if (isnone) "&pln_$tsk_var" else "malloc(sizeof(Func_${e.n}))"}; // TODO: malloc only if escapes
                assert(ptr_$tsk_var!=NULL && "not enough memory");                
                ${if (isnone) "" else """
                    *((Func_${e.n}*)ptr_$tsk_var) = pln_$tsk_var;
                    block_push($clo_block, ptr_$tsk_var);
                    
                """.trimIndent()}                   
                task_link($clo_block, (Task*) ptr_$tsk_var);
                                        
            """.trimIndent()

            Code(it.type+pre, src, "ptr_$tsk_var")
        }
    })
}

// link/unlink block with enclosing block/task
fun Stmt.Block.link_unlink_kill (): Triple<String,String,String> {
    val blk = "block_${this.n}".mem(this)
    val (link,unlink) = this.ups_first { it is Expr.Func || it is Stmt.Block }.let {
        when {
            // found block above me: link/unlink me as nested block
            (it is Stmt.Block) -> Pair (
                "${this.local()}->links.blk_down = &$blk;\n",
                "${this.local()}->links.blk_down = NULL;\n"
            )
            // found task above myself: link/unlink me as first block
            (it is Expr.Func && it.tk.enu==TK.TASK) -> Pair (
                "task0->links.blk_down = &$blk;\n",
                "task0->links.blk_down = NULL;\n"
            )
            // found func above me: link/unlink me in the stack
            (it is Expr.Func && it.tk.enu==TK.FUNC) -> Pair (
                "stack->block->links.blk_down = &$blk;\n",
                "if (stack->block != NULL) stack->block->links.blk_down = NULL;\n"
            )
            // GLOBAL: nothing to link
            else -> Pair("","")
        }
    }
    return Triple(link, unlink, "block_bcast(stack, &$blk, 1, EVENT_KILL);\n")
}

fun code_fs (s: Stmt) {
    CODE.addFirst(when (s) {
        is Stmt.Nop -> Code("","","")
        is Stmt.Nat -> Code("", s.tk_.src + "\n", "")
        is Stmt.Seq -> { val s2=CODE.removeFirst() ; val s1=CODE.removeFirst() ; Code(s1.type+s2.type, s1.stmt+s2.stmt, "") }
        is Stmt.Var -> Code("","","")
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
        is Stmt.Break -> {
            val (_,unlink,kill) = (s.ups_first { it is Stmt.Loop } as Stmt.Loop).block.link_unlink_kill()
            Code("", unlink+kill+"break;\n", "")
        }
        is Stmt.Ret   -> {
            val src = """
                task0->state = TASK_DEAD;
                return;
                
            """.trimIndent()
            val (_,unlink,kill) = (s.ups_first { it is Expr.Func } as Expr.Func).block.link_unlink_kill()
            Code("", unlink+kill+src, "")
        }
        is Stmt.SCall -> CODE.removeFirst().let { Code(it.type, it.stmt+it.expr+";\n", "") }
        is Stmt.Spawn -> CODE.removeFirst().let { Code(it.type, it.stmt+it.expr+";\n", "") }
        is Stmt.Await -> CODE.removeFirst().let {
            val src = """
                {
                    task0->pc = ${s.n};      // next awake
                    task0->state = TASK_AWAITING;
                    return;                 // await (1 = awake ok)
                case ${s.n}:                // awake here
                    assert(task0->state == TASK_AWAITING);
                    task1->evt = xxx.evt;
                    if (!(${it.expr})) {
                        return;             // (0 = awake no)
                    }
                    task0->state = TASK_RUNNING;
                }
                
            """.trimIndent()
            Code(it.type, it.stmt+src, "")
        }
        is Stmt.Awake -> CODE.removeFirst().let {
            val call = it.expr + ";\n"
            Code(it.type, it.stmt+call, "")
        }
        is Stmt.Bcast -> CODE.removeFirst().let {
            val src = """
                {
                    Stack stk = { stack, ${s.self_or_null()}, ${s.local()} };
                    block_bcast(&stk, ${s.scp1.toce(s)}, 0, ${it.expr});
                    if (stk.block == NULL) {
                        return;
                    }
                }
                
            """.trimIndent()
            Code(it.type, it.stmt+src, "")
        }
        is Stmt.Throw -> {
            val src = """
                {
                    Stack stk = { stack, ${s.self_or_null()}, ${s.local()} };
                    block_throw(&stk);
                    if (stk.block == NULL) {
                        return;
                    }
                }
                
            """.trimIndent()
            Code("", src, "")
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
                s.arg.wtype!!.output_std("", it.expr)
            } else {
                "output_${s.lib.str}(${it.expr});\n"
            }
            Code(it.type, it.stmt+call, "")
        }
        is Stmt.Block -> CODE.removeFirst().let {
            val (link,unlink,kill) = s.link_unlink_kill()
            val src = """
            {
                ${"block_${s.n}".mem(s)} = (Block) { NULL, ${if (s.iscatch) s.n else 0}, {NULL,NULL,NULL} };
                $link
                ${it.stmt}
                ${if (!s.iscatch) "" else "case ${s.n}: // catch"}
                $kill
                $unlink
            }
            
            """.trimIndent()

            Code(it.type, src, "")
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
        #define output_std_char_(x)  (output_std_char__(x), puts(""))
        #define output_std_Ptr_(x)   printf("%p",x)
        #define output_std_Ptr(x)    (output_std_Ptr_(x), puts(""))
        
        ///
        
        typedef struct Pool {
            void* val;
            struct Pool* nxt;
        } Pool;
        
        struct Block;
        struct Task;
        
        // When a block K terminates, it traverses the stack and sets to NULL
        // all matching blocks K in the stack.
        // All call/spawn/awake/bcast operations need to test if its enclosing
        // block is still alive before continuing.
        typedef struct Stack {
            struct Stack* stk_up;
            struct Task*  task;
            struct Block* block;
        } Stack;
        
        typedef enum {
            TASK_UNBORN, TASK_RUNNING, TASK_AWAITING, TASK_PAUSED, TASK_DEAD
        } TASK_STATE;
        
        typedef enum {
            EVENT_KILL=0, EVENT_THROW=1, EVENT_NORMAL=2 // (or more)
        } EVENT;
        
        // stack, task, evt
        typedef void (*Task_F) (Stack*, struct Task*, int);
        
        typedef struct Task {
            Task_F f; // (Stack* stack, Task* task, int evt);
            TASK_STATE state;
            int pc;
            struct {
                struct Task*  tsk_next;     // next Task in the same block
                struct Block* blk_down;     // nested block inside me
            } links;
        } Task;
        
        typedef struct Block {
            Pool* pool;                     // allocations in this block
            int catch;                      // label to jump on catch
            struct {
                struct Task*  tsk_first;    // first Task inside me
                struct Task*  tsk_last;     // current last Task inside me
                struct Block* blk_down;     // nested Block inside me 
            } links;
        } Block;
        
        ///
        
        void block_free (Block* block) {
            while (block->pool != NULL) {
                Pool* cur = block->pool;
                block->pool = cur->nxt;
                free(cur->val);
                free(cur);
            }
            block->pool = NULL;
        }
        
        void block_push (Block* block, void* val) {
            Pool* pool = malloc(sizeof(Pool));
            assert(pool!=NULL && "not enough memory");
            pool->val = val;
            pool->nxt = block->pool;
            block->pool = pool;
        }
        
        ///
        
        void task_link (Block* block, Task* task) {
            Task* last = block->links.tsk_last;
            if (last == NULL) {
                assert(block->links.tsk_first == NULL);
                block->links.tsk_first = task;
            } else {
                last->links.tsk_next = task;
            }
            block->links.tsk_last = task;
            task->links.tsk_next  = NULL;
            task->links.blk_down = NULL;
        }

        ///

        void block_throw (Stack* stack) {
            if (stack == NULL) {
                assert(0 && "throw without catch");
            } if (stack->block->catch == 0) {
                block_throw(stack->stk_up);
            } else {
                assert(stack->task!=NULL && "catch outside task");
                Stack stk = { stack, stack->task, stack->block };
                stack->task->pc = stack->block->catch;
                stack->task->f(&stk, stack->task, EVENT_THROW);
            }            
        }
        
        ///
        
        // 1. awake my inner tasks  (they started before the nested block)
        // 1.1. awake tasks in inner block in current task
        // 1.2. awake current task  (it is blocked after inner block. but before next task)
        // 1.3. awake next task
        // 2. awake my nested block (it started after the inner tasks)            

        void block_bcast (Stack* stack, Block* block, int up, EVENT evt) {
            // X. clear stack from myself
            if (evt == EVENT_KILL) {
                Stack* stk = stack;
                while (stk != NULL) {
                    if (stk->block == block) {
                        stk->block = NULL;
                    }
                    stk = stk->stk_up;
                }
            }
            
            void aux (Task* task) {
                if (task == NULL) return;
                
                if (up) {
                    aux(task->links.tsk_next);                              // 1.3
                    //assert(task->links.blk_down != NULL);
                    if (task->links.blk_down != NULL) { // maybe unlinked by natural termination
                        block_bcast(stack, task->links.blk_down, up, evt);  // 1.1
                    }
                    if (task->state == TASK_AWAITING) {
                        task->f(stack, task, evt);                          // 1.2
                        if (evt == EVENT_KILL) {
                            task->state = TASK_DEAD;
                        }
                    }
                } else {
                    //assert(task->links.blk_down != NULL);
                    if (task->links.blk_down != NULL) { // maybe unlinked by natural termination
                        Stack stk = { stack, task, task->links.blk_down };
                        block_bcast(&stk, task->links.blk_down, up, evt);   // 1.1
                        if (stk.block == NULL) return;
                        if (stack->block == NULL) return;
                    }
                    if (task->state == TASK_AWAITING) {
                        task->f(stack, task, evt);                          // 1.2
                        if (stack->block == NULL) return;
                    }
                    aux(task->links.tsk_next);                              // 1.3
                }
            }
            
            if (up) {
                if (block->links.blk_down != NULL) {   // 2. awake my nested block
                    block_bcast(stack, block->links.blk_down, up, evt);
                }                
                aux(block->links.tsk_first);           // 1. awake my inner tasks
            } else {
                aux(block->links.tsk_first);            // 1. awake my inner tasks
                if (block->links.blk_down != NULL) {   // 2. awake my nested block
                    block_bcast(stack, block->links.blk_down, up, evt);
                }
            }
            
            // X. free myself
            if (evt == EVENT_KILL) {
                block_free(block);
            }
        }

        ///
        
        Block* GLOBAL;

        ${TPS.map { it.second }.joinToString("")}
        ${TPS.map { it.third  }.joinToString("")}
        
        struct {
            ${this.mem_vars()}
        } global;

        ${code.type}

        void main (void) {
            Block block_0 = { NULL, 0, {NULL,NULL,NULL} };
            GLOBAL = &block_0;
            Stack _stack_ = { NULL, NULL, &block_0 };
            Stack* stack = &_stack_;
            ${code.stmt}
            block_bcast(stack, &block_0, 1, EVENT_KILL);
        }

    """).trimIndent()
}
