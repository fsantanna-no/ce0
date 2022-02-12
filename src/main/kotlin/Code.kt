data class Code (val type: String, val pre: String, val stmt: String, val expr: String)
val CODE = ArrayDeque<Code>()

fun Any.self_or_null (): String {
    return if (this.ups_first { it is Expr.Func } == null) "NULL" else "(&task1->task0)"
}

fun Type.pos (): String {
    return when (this) {
        is Type.Alias -> this.tk_.id
        is Type.Unit  -> "int"
        is Type.Pointer   -> this.pln.pos() + "*"
        is Type.Nat   -> this.tk_.src
        is Type.Tuple -> "struct " + this.toce()
        is Type.Union -> "struct " + this.toce()
        is Type.Func  -> this.toce() + "*"
        is Type.Spawn   -> this.tsk.pos()
        is Type.Spawns  -> "Tasks"
    }
}

//it is Type.Pointer && it.pln.noalias() is Type.Union ||
//it is Type.Alias   && it.noalias().let { it is Type.Pointer && it.pln is Type.Union }

fun Type.output_std (c: String, arg: String): String {
    val alias1 = this is Type.Pointer && /*this.pln is Type.Alias &&*/ this.pln.noalias().let { it is Type.Tuple || it is Type.Union }
    val alias2 = this is Type.Alias && this.noalias().let { it is Type.Pointer && (it.pln is Type.Tuple || it.pln is Type.Union) }
    return when {
        alias1 -> "output_std_${(this as Type.Pointer).pln.noalias().toce()}$c($arg);\n"
        alias2 -> "output_std_${(this.noalias() as Type.Pointer).pln.toce()}$c($arg);\n"
        this is Type.Pointer || this is Type.Func -> {
            if (c == "_") "putchar('_');\n" else "puts(\"_\");\n"
        }
        else -> "output_std_${this.noalias().toce()}$c($arg);\n"
    }
}

val TYPEX = mutableSetOf<String>()

fun code_ft (tp: Type) {
    CODE.addFirst(when (tp) {
        is Type.Nat, is Type.Unit, is Type.Alias -> Code("","","","")
        is Type.Pointer -> CODE.removeFirst()
        is Type.Spawn   -> CODE.removeFirst()
        is Type.Spawns  -> CODE.removeFirst()
        is Type.Func -> {
            val out = CODE.removeFirst()
            val pub = if (tp.pub == null) Code("","","","") else CODE.removeFirst()
            val inp = CODE.removeFirst()
            val src = """
                typedef union {
                    int evt;
                    struct {
                        Block* blks[${tp.xscps.second.size}];
                        ${tp.inp.pos()} arg;
                    } pars;
                } X_${tp.toce()};
                typedef struct {
                    Task task0;
                    ${tp.xscps.first.let { if (it == null) "" else "Block* CLO;" }}
                    union {
                        Block* blks[${tp.xscps.second.size}];
                        struct {
                            ${tp.xscps.second.let { if (it.size == 0) "" else
                                it.map { "Block* ${it.scp1.id};\n" }.joinToString("") }
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
            Code(inp.type+pub.type+out.type+src, inp.pre+pub.pre+out.pre, "","")
        }
        is Type.Tuple -> {
            val ce = tp.toce()

            val type = """
                struct $ce {
                    ${tp.vec  // do not filter to keep correct i
                        .mapIndexed { i,sub -> (sub.pos() + " _" + (i+1).toString() + ";\n") }
                        .joinToString("")
                    }
                };
                void output_std_${ce}_ (${tp.pos()}* v);
                void output_std_${ce}  (${tp.pos()}* v);
                
            """.trimIndent()

            val pre = """
                void output_std_${ce}_ (${tp.pos()}* v) {
                    printf("[");
                    ${tp.vec
                        .mapIndexed { i,sub ->
                            val s = when (sub.noalias()) {
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

            """.trimIndent()

            val codes = tp.vec.map { CODE.removeFirst() }.reversed()
            val types = codes.map { it.type }.joinToString("")
            val pres  = codes.map { it.pre  }.joinToString("")
            Code(types+type,pres+pre, "", "")
        }
        is Type.Union -> {
            val ce = tp.toce()

            val type = """
                struct $ce {
                    int tag;
                    union {
                        ${tp.vec  // do not filter to keep correct i
                            .mapIndexed { i,sub -> "${sub.pos()} _${i+1};\n" }
                            .joinToString("")
                        }
                    };
                };
                void output_std_${ce}_ (${tp.pos()}* v);
                void output_std_${ce} (${tp.pos()}* v);

            """.trimIndent()

            val pre = """
                void output_std_${ce}_ (${tp.pos()}* v) {
                    // TODO: only if tp.isrec
                    if (v == NULL) {
                        printf("<.0>");
                        return;
                    }
                    printf("<.%d", v->tag);
                    switch (v->tag) {
                        ${tp.vec
                            .mapIndexed { i,sub ->
                                val s = when (sub.noalias()) {
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

            """.trimIndent()

            val codes = tp.vec.map { CODE.removeFirst() }.reversed()
            val types = codes.map { it.type }.joinToString("")
            val pres  = codes.map { it.pre  }.joinToString("")
            Code(types+type,pres+pre, "", "")
        }
    }.let {
        val ce = tp.toce()
        if (TYPEX.contains(ce)) {
            Code("","","","")
        } else {
            TYPEX.add(ce)
            it
        }
    })
}

fun String.mem (up: Any): String {
    val func = if (up is Expr.Func) up else up.ups_first { it is Expr.Func }
    return when {
        (func == null) -> "(global.$this)"
        (this == "ret") -> "(task1->$this)"
        (this in listOf("arg","pub","evt")) -> "(task2->task1.$this)"
        else -> "(task2->$this)"
    }
}

fun Scope.toce (up: Any): String {
    return when {
        // @GLOBAL is never an argument
        (this.scp1.id == "GLOBAL") -> "GLOBAL"
        // closure block is always an argument
        (up.ups_first {
            it is Expr.Func && it.type.xscps.first.let {
                it?.scp1?.enu==this.scp1.enu && it?.scp1?.id==this.scp1.id
            }
        } != null) -> "(task1->CLO)"
        // @i_1 is always an argument (must be after closure test)
        this.scp1.isscopepar() -> "(task1->${this.scp1.id})"
        // otherwise depends (calls mem)
        else -> {
            val blk = up.env(this.scp1.id) as Stmt.Block
            val mem = "B${blk.n}".mem(up)
            "(&" + mem + ")"
        }
    }
}

fun Any.local (): String {
    return this.ups_first { it is Stmt.Block }.let {
        if (it == null) "GLOBAL" else {
            val mem = ("B" + (it as Stmt.Block).n).mem(this)
            "(&" + mem + ")"
        }
    }
}

fun String.native (up: Any, tk: Tk): String {
    var ret = ""
    var i = 0
    while (i < this.length) {
        ret += if (this[i] == '$') {
            i++
            All_assert_tk(tk, i < this.length) { "invalid `\$´" }
            val open = if (this[i] != '{') false else { i++; true }
            var ce = ""
            while (i<this.length && (this[i].isLetterOrDigit() || this[i] == '_' || (open && this[i]!='}'))) {
                ce += this[i]
                i++
            }
            if (open) {
                All_assert_tk(tk, i < this.length) { "invalid `\$´" }
                assert(this[i]=='}') { "bug found" }
                i++
            }
            ce.mem(up)
        } else {
            this[i++]
        }
    }
    return ret
}

fun Stmt.mem_vars (): String {
    return when (this) {
        is Stmt.Nop, is Stmt.Set, is Stmt.Native, is Stmt.SCall, is Stmt.SSpawn,
        is Stmt.DSpawn, is Stmt.Await, is Stmt.Bcast, is Stmt.Throw,
        is Stmt.Input, is Stmt.Output, is Stmt.Return, is Stmt.Break, is Stmt.Typedef -> ""

        is Stmt.Var -> "${this.xtype!!.pos()} ${this.tk_.id};\n"
        is Stmt.Loop -> this.block.mem_vars()
        is Stmt.DLoop -> this.block.mem_vars()

        is Stmt.Block -> """
            struct {
                Block B${this.n};
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
        is Expr.Unit  -> Code("", "", "", "0")
        is Expr.Nat   -> CODE.removeFirst().let { Code(it.type, it.pre, "", e.tk_.src.native(e, e.tk)) }
        is Expr.Var   -> Code("", "", "", e.tk_.id.mem(e.env(e.tk_.id, true)!!))
        is Expr.Upref -> CODE.removeFirst().let { Code(it.type, it.pre, it.stmt, "(&" + it.expr + ")") }
        is Expr.Dnref -> CODE.removeFirst().let { Code(it.type, it.pre, it.stmt, "(*" + it.expr + ")") }
        is Expr.TDisc -> CODE.removeFirst().let { Code(it.type, it.pre, it.stmt, it.expr + "._" + e.tk_.num) }
        is Expr.Pub   -> CODE.removeFirst().let { Code(it.type, it.pre, it.stmt, it.expr + "->pub") }
        is Expr.UDisc -> CODE.removeFirst().let {
            val ee = it.expr
            val pre = if (e.tk_.num == 0) {
                """
                assert(&${it.expr} == NULL);

                """.trimIndent()
            } else {
                """
                assert(&${it.expr} != NULL);    // TODO: only if e.uni.wtype!!.isrec()
                assert($ee.tag == ${e.tk_.num});

                """.trimIndent()
            }
            Code(it.type, it.pre, it.stmt + pre, ee+"._"+e.tk_.num)
        }
        is Expr.UPred -> CODE.removeFirst().let {
            val ee = it.expr
            val pos = if (e.tk_.num == 0) {
                "(&${it.expr} == NULL)"
            } else { // TODO: only if e.uni.wtype!!.isrec()
                "(&${it.expr} != NULL) && ($ee.tag == ${e.tk_.num})"
            }
            Code(it.type, it.pre, it.stmt, pos)
        }
        is Expr.New  -> CODE.removeFirst().let {
            val ID  = "__tmp_" + e.n
            val ptr = e.wtype!!.noalias() as Type.Pointer

            val pre = """
                ${ptr.pos()} $ID = malloc(sizeof(*$ID));
                assert($ID!=NULL && "not enough memory");
                *$ID = ${it.expr};
                block_push(${ptr.xscp.toce(ptr)}, $ID);

            """.trimIndent()
            Code(it.type, it.pre, it.stmt+pre, ID)
        }
        is Expr.TCons -> {
            val args = (1..e.arg.size).map { CODE.removeFirst() }.reversed()
            Code (
                args.map { it.type }.joinToString(""),
                args.map { it.pre  }.joinToString(""),
                args.map { it.stmt }.joinToString(""),
                args.map { it.expr }.filter { it!="" }.joinToString(", ").let { "((${xp.pos()}) { $it })" }
            )
        }
        is Expr.UCons -> {
            val arg = CODE.removeFirst()
            val tp  = CODE.removeFirst()
            val ID  = "_tmp_" + e.n
            val pos = xp.pos()
            val pre = "$pos $ID = (($pos) { ${e.tk_.num} , ._${e.tk_.num} = ${arg.expr} });\n"
            Code(tp.type+arg.type, tp.pre+arg.pre, arg.stmt + pre, ID)
        }
        is Expr.UNull -> CODE.removeFirst().let { Code(it.type, it.pre,"","NULL") }
        is Expr.Call  -> {
            val arg  = CODE.removeFirst()
            val f    = CODE.removeFirst()
            val blks = e.xscps.first.map { it.toce(e) }.joinToString(",")
            val tpf  = e.f.wtype.let {
                when (it) {
                    is Type.Spawn  -> it.tsk
                    is Type.Spawns -> it.tsk
                    else         -> it
                }
            }
            when {
                (e.f is Expr.Var && e.f.tk_.id=="output_std") -> {
                    Code (
                        f.type + arg.type,
                        f.pre + arg.pre,
                        f.stmt + arg.stmt + e.arg.wtype!!.output_std("", arg.expr),
                        ""
                    )
                }
                (tpf is Type.Func) -> {
                    val istk   = (tpf.tk.enu == TK.TASK)
                    val block  = e.wup.let {
                        if (it is Stmt.DSpawn) {
                            "&" + (it.dst as Expr.Var).tk_.id.mem(e) + ".block"
                        } else {
                            // closure block: allows the func to escape up to it
                            tpf.xscps.first.let {
                                if (it == null) {
                                    e.local()
                                } else {
                                    tpf.xscps.first!!.toce(e.wup!!)
                                }
                            }
                        }
                    }

                    val (ret1,ret2) = when (e.wup) {
                        is Stmt.SSpawn -> Pair("${tpf.toce()}* ret_${e.n};", "ret_${e.n} = frame;")
                        is Stmt.DSpawn -> Pair("", "")
                        else           -> Pair("${tpf.out.pos()} ret_${e.n};", "ret_${e.n} = (((${tpf.toce()}*)frame)->ret);")
                    }

                    val pre = """
                        $ret1
                        {
                            Stack stk_${e.n} = { stack, ${e.self_or_null()}, ${e.local()} };
                            ${tpf.toce()}* frame = (${tpf.toce()}*) malloc(${f.expr}->task0.size);
                            assert(frame!=NULL && "not enough memory");
                            memcpy(frame, ${f.expr}, ${f.expr}->task0.size);
                            ${if (e.wup is Stmt.DSpawn) "frame->task0.isauto = 1;" else ""}
                            block_push($block, frame);
                            ${if (istk) "task_link($block, &frame->task0);" else ""}
                            ${if (tpf.tk.enu != TK.FUNC) "" else "frame->task0.state = TASK_UNBORN;"}
                            ((F_${tpf.toce()})(frame->task0.f)) (
                                &stk_${e.n},
                                frame,
                                (X_${tpf.toce()}) {.pars={{$blks}, ${arg.expr}}}
                            );
                            if (stk_${e.n}.block == NULL) {
                                return;
                            }
                            if (frame->task0.isauto && frame->task0.state==TASK_DEAD) {
                                task_unlink_free($block, &frame->task0);
                            }
                            $ret2
                        }
                        
                        """.trimIndent()
                    Code (
                        f.type + arg.type,
                        f.pre + arg.pre,
                        f.stmt + arg.stmt + pre,
                        "ret_${e.n}"
                    )
                }
                else -> {
                    Code (
                        f.type + arg.type,
                        f.pre + arg.pre,
                        f.stmt + arg.stmt,
                        f.expr + "(" + blks + (if (e.arg is Expr.Unit) "" else arg.expr) + ")"
                    )
                }
            }
        }
        is Expr.Func -> {
            val block = CODE.removeFirst()
            val tp    = CODE.removeFirst()
            val pre = """
                typedef struct Func_${e.n} {
                    ${e.type.toce()} task1;
                    ${e.ups.map { "${e.env(it.id)!!.toType().pos()} ${it.id};\n" }.joinToString("")}
                    ${e.block.mem_vars()}
                } Func_${e.n};
                    
                void func_${e.n} (Stack* stack, Func_${e.n}* task2, X_${e.type.toce()} xxx) {
                    Task*             task0 = &task2->task1.task0;
                    ${e.type.toce()}* task1 = &task2->task1;
                    ${e.type.xscps.second.mapIndexed { i, _ -> "task1->blks[$i] = xxx.pars.blks[$i];\n" }.joinToString("")}
                    assert(task0->state==TASK_UNBORN || task0->state==TASK_AWAITING);
                    switch (task0->pc) {
                        case 0: {                    
                            assert(task0->state == TASK_UNBORN);
                            task2->task1.arg = xxx.pars.arg;
                            ${block.stmt}
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

            val isclo  = (e.type.xscps.first != null)
            val istk   = (e.type.tk.enu == TK.TASK)
            val isnone = !isclo && !istk

            val src = if (isnone) {
                """
                    static Func_${e.n} _frame_${e.n};
                    _frame_${e.n}.task1.task0 = (Task) {
                        TASK_UNBORN, 0, {}, sizeof(Func_${e.n}), (Task_F)func_${e.n}, 0
                    };
                    static Func_${e.n}* frame_${e.n} = &_frame_${e.n};
    
                """.trimIndent()
            } else {
                """
                    Func_${e.n}* frame_${e.n} = (Func_${e.n}*) malloc(sizeof(Func_${e.n}));
                    assert(frame_${e.n}!=NULL && "not enough memory");
                    frame_${e.n}->task1.task0 = (Task) {
                        TASK_UNBORN, 0, {}, sizeof(Func_${e.n}), (Task_F)func_${e.n}, 0
                    };
                    ${e.type.xscps.first.let {
                        if (it==null) "" else
                            "frame_${e.n}->task1.CLO = ${it.toce(e.wup!!)};\n"
                    }}
                    ${e.ups.map {
                        "frame_${e.n}->${it.id} = ${it.id.mem(e.wup!!)};\n"
                    }.joinToString("")}
                    block_push(frame_${e.n}->task1.CLO, frame_${e.n});

                """.trimIndent()
            }
            Code(tp.type+block.type, tp.pre+block.pre+pre, src, "((${e.type.pos()}) frame_${e.n})")
        }
    })
}

// link/unlink block with enclosing block/task
fun Stmt.Block.link_unlink_kill (): Triple<String,String,String> {
    val blk = "B${this.n}".mem(this)
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
        is Stmt.Nop -> Code("", "","","")
        is Stmt.Typedef -> CODE.removeFirst().let {
            val src = """
                //#define output_std_${s.tk_.id}_ output_std_${s.type.toce()}_
                //#define output_std_${s.tk_.id}  output_std_${s.type.toce()}
                typedef ${s.type.pos()} ${s.tk_.id};
                
            """.trimIndent()
            Code(src+it.type, it.pre, "", "")
        }
        is Stmt.Native -> if (s.istype) {
            Code(s.tk_.src.native(s, s.tk) + "\n", "", "", "")
        } else {
            Code("", "", s.tk_.src.native(s, s.tk) + "\n", "")
        }
        is Stmt.Seq -> { val s2=CODE.removeFirst() ; val s1=CODE.removeFirst() ; Code(s1.type+s2.type, s1.pre+s2.pre, s1.stmt+s2.stmt, "") }
        is Stmt.Var -> CODE.removeFirst().let {
            val src = if (s.xtype is Type.Spawns) {
                s.tk_.id.mem(s).let {
                    """
                        $it = (Tasks) { TASK_DEAD, 0, { NULL, NULL }, { NULL, 0, { NULL, NULL, NULL } } };
                        task_link(${s.local()}, (Task*) &$it);
                        $it.links.blk_down = &$it.block;
                        
                    """.trimIndent()
                }
            } else ""
            Code(it.type, it.pre, src,"")
        }
        is Stmt.Set -> {
            val src = CODE.removeFirst()
            val dst = CODE.removeFirst()
            Code(dst.type+src.type, dst.pre+src.pre, dst.stmt+src.stmt + dst.expr+" = "+src.expr + ";\n", "")
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
            Code(tst.type+true_.type+false_.type, tst.pre+true_.pre+false_.pre, src, "")
        }
        is Stmt.Loop -> CODE.removeFirst().let {
            Code(it.type, it.pre, "while (1) { ${it.stmt} }", "")
        }
        is Stmt.DLoop -> {
            val block = CODE.removeFirst()
            val tsks  = CODE.removeFirst()
            val i     = CODE.removeFirst()
            val src   = """
                {
                    ${i.expr} = (${s.i.wtype!!.pos()}) ${tsks.expr}.block.links.tsk_first;
                    while (${i.expr} != NULL) {
                        ${block.stmt}
                        ${i.expr} = (${s.i.wtype!!.pos()}) ${i.expr}->task0.links.tsk_next;
                    }
                }
                
            """.trimIndent()
            Code(tsks.type+i.type+block.type, tsks.pre+i.pre+block.pre, tsks.stmt+i.stmt+src, "")
        }
        is Stmt.Break -> {
            val (_,unlink,kill) = (s.ups_first { it is Stmt.Loop } as Stmt.Loop).block.link_unlink_kill()
            Code("", "", unlink+kill+"break;\n", "")
        }
        is Stmt.Return   -> {
            val src = """
                task0->state = TASK_DEAD;
                return;
                
            """.trimIndent()
            val (_,unlink,kill) = (s.ups_first { it is Expr.Func } as Expr.Func).block.link_unlink_kill()
            Code("", "", unlink+kill+src, "")
        }
        is Stmt.SCall -> CODE.removeFirst().let { Code(it.type, it.pre, it.stmt+it.expr+";\n", "") }
        is Stmt.SSpawn -> {
            val call = CODE.removeFirst()
            val dst  = CODE.removeFirst()
            val src = """
                ${dst.expr} = ${call.expr};
                
            """.trimIndent()
            Code(call.type+dst.type, call.pre+dst.pre, call.stmt+dst.stmt+src, "")
        }
        is Stmt.DSpawn -> { // Expr.Call links call/tsks
            val call = CODE.removeFirst()
            val tsks = CODE.removeFirst()
            Code(tsks.type+call.type, tsks.pre+call.pre, tsks.stmt+call.stmt, "")
        }
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
            Code(it.type, it.pre, it.stmt+src, "")
        }
        is Stmt.Bcast -> CODE.removeFirst().let {
            val src = """
                {
                    Stack stk = { stack, ${s.self_or_null()}, ${s.local()} };
                    block_bcast(&stk, ${s.scp.toce(s)}, 0, ${it.expr});
                    if (stk.block == NULL) {
                        return;
                    }
                }
                
            """.trimIndent()
            Code(it.type, it.pre, it.stmt+src, "")
        }
        is Stmt.Throw -> {
            val src = """
                {
                    Stack stk = { stack, ${s.self_or_null()}, ${s.local()} };
                    block_throw(&stk, &stk);
                    if (stk.block == NULL) {
                        return;
                    }
                }
                
            """.trimIndent()
            Code("", "", src, "")
        }
        is Stmt.Input -> {
            val arg = CODE.removeFirst()
            if (s.dst == null) {
                val tp  = CODE.removeFirst()
                val src = "input_${s.lib.id}_${s.xtype!!.toce()}(${arg.expr});\n"
                Code(tp.type+arg.type, tp.pre+arg.pre, arg.stmt + src, "")
            } else {
                val dst = CODE.removeFirst()
                val tp  = CODE.removeFirst()
                val src = "${dst.expr} = input_${s.lib.id}_${s.xtype!!.toce()}(${arg.expr});\n"
                Code(tp.type+arg.type+dst.type, tp.pre+arg.pre+dst.pre, arg.stmt+dst.stmt+src, "")
            }
        }
        is Stmt.Output -> CODE.removeFirst().let {
            val call = if (s.lib.id == "std") {
                s.arg.wtype!!.output_std("", it.expr)
            } else {
                "output_${s.lib.id}(${it.expr});\n"
            }
            Code(it.type, it.pre, it.stmt+call, "")
        }
        is Stmt.Block -> CODE.removeFirst().let {
            val (link,unlink,kill) = s.link_unlink_kill()
            val src = """
            {
                ${"B${s.n}".mem(s)} = (Block) { NULL, ${if (s.iscatch) s.n else 0}, {NULL,NULL,NULL} };
                $link
                ${it.stmt}
                ${if (!s.iscatch) "" else "case ${s.n}: // catch"}
                $kill
                $unlink
            }
            
            """.trimIndent()

            Code(it.type, it.pre, src, "")
        }
    })
}

fun Stmt.code (): String {
    TYPEX.clear()
    EXPR_WTYPE = false
    this.visit(::code_fs, ::code_fe, ::code_ft, null)
    EXPR_WTYPE = true

    val code = CODE.removeFirst()
    assert(CODE.size == 0)
    assert(code.expr == "")

    return ("""
        #include <assert.h>
        #include <stdio.h>
        #include <stdlib.h>
        #include <string.h>
        
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
            TASK_STATE state;
            int isauto;
            struct {
                struct Task*  tsk_next;     // next Task in the same block
                struct Block* blk_down;     // nested block inside me
            } links;
            int size;
            Task_F f; // (Stack* stack, Task* task, int evt);
            int pc;
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
        
        typedef struct Tasks {              // task + block
            TASK_STATE state;
            int isauto;
            struct {
                Task*  tsk_next;
                Block* blk_down;
            } links;
            Block block;
        } Tasks;

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
        
        void __task_unlink (Block* block, Task* task) {
            Task* prv = NULL; {
                Task* cur = block->links.tsk_first;
                while (cur != task) {
                    prv = cur;
                    cur = cur->links.tsk_next;
                }
            }
            Task* nxt = task->links.tsk_next; {
                while (nxt!=NULL && nxt->state==TASK_DEAD) {
                    nxt = nxt->links.tsk_next;
                }
            }
            if (block->links.tsk_first == task) {
                block->links.tsk_first = nxt;
            }
            if (block->links.tsk_last == task) {
                block->links.tsk_last = prv;
            }
            if (prv != NULL) {
                prv->links.tsk_next = nxt;
            }
        }

        void __task_free (Block* block, Task* task) {
            Pool** tonxt = &block->pool;
            Pool*  cur   = block->pool;
            assert(cur != NULL);
            while (cur != NULL) {
                if (cur->val == task) {
                    *tonxt = cur->nxt; 
                    free(cur->val);
                    free(cur);
                    return;
                }
                tonxt = &cur->nxt;
                cur   = cur->nxt;
            }
            assert(0 && "bug found");
        }
        
        void task_unlink_free (Block* block, Task* task) {
            __task_unlink(block, task);
            __task_free(block, task);
        }
        
        ///

        void block_throw (Stack* top, Stack* cur) {
            if (cur == NULL) {
                assert(0 && "throw without catch");
            } if (cur->block->catch == 0) {
                block_throw(top, cur->stk_up);
            } else {
                assert(cur->task!=NULL && "catch outside task");
                Stack stk = { top, cur->task, cur->block };
                cur->task->pc = cur->block->catch;
                cur->task->f(&stk, cur->task, EVENT_THROW);
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
                        Task* next = task->links.tsk_next;
                        task->f(stack, task, evt);                          // 1.2
                        if (stack->block == NULL) return;
                    }
                    aux(task->links.tsk_next);                              // 1.3
                }
                
                if (task->isauto && task->state==TASK_DEAD) {
                    task_unlink_free(block, task);
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

        ${code.type}
                
        struct {
            ${this.mem_vars()}
        } global;

        ${code.pre}

        void main (void) {
            Block B0 = { NULL, 0, {NULL,NULL,NULL} };
            GLOBAL = &B0;
            Stack _stack_ = { NULL, NULL, &B0 };
            Stack* stack = &_stack_;
            ${code.stmt}
            block_bcast(stack, &B0, 1, EVENT_KILL);
        }

    """).trimIndent()
}
