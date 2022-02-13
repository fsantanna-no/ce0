import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.MethodOrderer.Alphanumeric
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.io.File

@TestMethodOrder(Alphanumeric::class)
class TTask {

    fun all (inp: String): String {
        val (ok1,out1) = ce2c(inp)
        if (!ok1) {
            return out1
        }
        File("out.c").writeText(out1)
        val (ok2,out2) = exec("gcc -Werror out.c -o out.exe")
        if (!ok2) {
            return out2
        }
        val (_,out3) = exec("$VALGRIND./out.exe")
        //println(out3)
        return out3
    }

    @Test
    fun a01_output () {
        val out = all("""
            var f: task @LOCAL->@[]->()->()->()
            set f = task @LOCAL->@[]->()->()->() {
                output std _1:_int
            }
            var x : active task @LOCAL->@[]->()->()->()
            set x = spawn f ()
            output std _2:_int
        """.trimIndent())
        assert(out == "1\n2\n") { out }
    }
    @Test
    fun a02_await_err2 () {
        val out = all("""
            await ()
        """.trimIndent())
        assert(out.startsWith("(ln 1, col 1): invalid condition : type mismatch")) { out }
    }
    @Test
    fun a02_emit_err () {
        val out = all("""
            emit _1:_int
        """.trimIndent())
        assert(out == "(ln 1, col 1): invalid `emit` : type mismatch : expected Event : have _int") { out }
    }
    @Test
    fun a02_await () {
        val out = all("""
            type Event = <(),_int>
            var f : task @LOCAL->@[]->()->()->()
            set f = task @LOCAL->@[]->()->()->() {
                output std _1:_int
                await evt?2
                output std _3:_int
            }
            var x : active task @LOCAL->@[]->()->()->()
            set x = spawn f ()
            output std _2:_int
            --awake x _1:_int
            emit <.2 _1:_int>:Event
        """.trimIndent())
        assert(out == "1\n2\n3\n") { out }
    }
    @Test
    fun a02_await_err () {
        val out = all("""
            type Event = <(),_int>
            var f: task @LOCAL->@[]->()->()->()
            set f = task @LOCAL->@[]->()->()->() {
                output std _1:_int
                await evt?2
                output std _3:_int
            }
            var x : active task @LOCAL->@[]->()->()->()
            set x = spawn f ()
            output std _2:_int
            --awake x _1:_int
            --awake x _1:_int
            emit @GLOBAL <.2 _1:_int>: Event
            emit @GLOBAL <.2 _1:_int>: Event
        """.trimIndent())
        //assert(out.endsWith("Assertion `(global.x)->task0.state == TASK_AWAITING' failed.\n")) { out }
        assert(out.endsWith("1\n2\n3\n")) { out }
    }
    @Test
    fun a03_var () {
        val out = all("""
            type Event = <(),_int>
            var f: task @LOCAL->@[]->()->()->()
            set f = task @LOCAL->@[]->()->()->() {
                var x: _int
                set x = _10:_int
                await evt?2
                output std x
            }
            var x : active task @LOCAL->@[]->()->()->()
            set x = spawn f ()
            --awake x _1:_int
            emit @GLOBAL <.2 _1:_int>: Event
        """.trimIndent())
        assert(out == "10\n") { out }
    }
    @Test
    fun a04_vars () {
        val out = all("""
            type Event = <(),_int>
            var f: task @LOCAL->@[]->()->()->()
            set f = task @LOCAL->@[]->()->()->() {
                {
                    var x: _int
                    set x = _10:_int
                    await evt?2
                    output std x
                }
                {
                    var y: _int
                    set y = _20:_int
                    await evt?2
                    output std y
                }
            }
            var x : active task @LOCAL->@[]->()->()->()
            set x = spawn f ()
            --awake x _1:_int
            --awake x _1:_int
            emit @GLOBAL <.2 _1:_int>: Event
            emit @GLOBAL <.2 _1:_int>: Event
        """.trimIndent())
        assert(out == "10\n20\n") { out }
    }
    @Test
    fun a05_args_err () {
        val out = all("""
            var f : task @LOCAL->@[]->()->()->()
            var x : active task @LOCAL->@[]->[()]->()->()
            set x = spawn f ()
        """.trimIndent())
        assert(out == "(ln 3, col 9): invalid `spawn` : type mismatch :\n    task @GLOBAL -> @[] -> [()] -> () -> ()\n    task @GLOBAL -> @[] -> () -> () -> ()") { out }
    }
    @Test
    fun a05_args () {
        val out = all("""
            type Event = <(),_int>
            var f : task @LOCAL->@[]->_(char*)->()->()
            set f = task @LOCAL->@[]->_(char*)->()->() {
                output std arg
                await evt?2
                output std evt!2
                await evt?2
                output std evt!2
            }
            var x : active task @LOCAL->@[]->_(char*)->()->()
            set x = spawn f _("hello"):_(char*)
            --awake x _10:_int
            --awake x _20:_int
            emit @GLOBAL <.2 _10:_int>: Event
            emit @GLOBAL <.2 _20:_int>: Event
        """.trimIndent())
        assert(out == "\"hello\"\n10\n20\n") { out }
    }
    @Test
    fun a06_par_err () {
        val out = all("""
            var build : func @[] -> () -> task @LOCAL->@[]->()->()->()
            set build = func @[] -> () -> task @LOCAL->@[]->()->()->() {
                set ret = task @LOCAL->@[]->()->()->() {    -- ERR: not the same @LOCAL
                    output std _1:_int
                    await _(${D}evt != 0):_int
                    output std _2:_int
                }
            }
        """.trimIndent())
        assert(out.startsWith("(ln 3, col 13): invalid return : type mismatch")) { out }
    }
    @Test
    fun a06_par1 () {
        val out = all("""
            type Event = <(),_int>
            var build : task @LOCAL->@[]->()->()->()
            set build = task @LOCAL->@[]->()->()->() {
                output std _1:_int
                await evt?2
                output std _2:_int
            }
            output std _10:_int
            var f : active task @LOCAL->@[]->()->()->()
            set f = spawn build ()
            output std _11:_int
            var g : active task @LOCAL->@[]->()->()->()
            set g = spawn build ()
            --awake f _1:_int
            --awake g _1:_int
            emit @GLOBAL <.2 _1:_int>: Event
            output std _12:_int
        """.trimIndent())
        assert(out == "10\n1\n11\n1\n2\n2\n12\n") { out }
    }
    @Test
    fun a06_par2 () {
        val out = all("""
            type Event = <(),_int>
            var build : func @[r1] -> () -> task @r1->@[]->()->()->()
            set build = func @[r1] -> () -> task @r1->@[]->()->()->() {
                set ret = task @r1->@[]->()->()->() {
                    output std _1:_int
                    await evt?2
                    output std _2:_int
                }
            }
            var f: task @LOCAL->@[]->()->()->()
            set f = build @[LOCAL] ()
            var g: task @LOCAL->@[]->()->()->()
            set g = build @[LOCAL] ()
            output std _10:_int
            var x : active task @LOCAL->@[]->()->()->()
            set x = spawn f ()
            output std _11:_int
            var y : active task @LOCAL->@[]->()->()->()
            set y = spawn g ()
            --awake x _1:_int
            --awake y _1:_int
            emit @GLOBAL <.2 _1:_int>: Event
            output std _12:_int
        """.trimIndent())
        assert(out == "10\n1\n11\n1\n2\n2\n12\n") { out }
    }
    @Test
    fun a07_bcast () {
        val out = all("""
            type Event = <(),_int>
            var f : task @LOCAL->@[]->()->()->()
            set f = task @LOCAL->@[]->()->()->() {
                await evt?2
                output std evt!2
            }
            var x : active task @LOCAL->@[]->()->()->()
            set x = spawn f ()
            
            var g : task @LOCAL->@[]->()->()->()
            set g = task @LOCAL->@[]->()->()->() {
                await evt?2
                var e: _int
                set e = evt!2
                output std _(${D}e+10):_int
                await evt?2
                set e = evt!2
                output std _(${D}e+10):_int
            }
            var y : active task @LOCAL->@[]->()->()->()
            set y = spawn g ()
            
            emit @GLOBAL <.2 _1:_int>: Event
            emit @GLOBAL <.2 _2:_int>: Event
        """.trimIndent())
        assert(out == "1\n11\n12\n") { out }
    }
    @Test
    fun a08_bcast_block () {
        val out = all("""
            type Event = <(),_int>
            var f : task @LOCAL->@[]->()->()->()
            set f = task @LOCAL->@[]->()->()->() {
                await _1:_int
                var iskill: _int
                set iskill = evt?1
                native _(assert(${D}iskill);)
                output std _0:_int    -- only on kill
            }
            var x : active task @LOCAL->@[]->()->()->()
            set x = spawn f ()
            
            {
                var g : task @LOCAL->@[]->()->()->()
                set g = task @LOCAL->@[]->()->()->() {
                    await evt?2
                    var e: _int
                    set e = evt!2
                    output std _(${D}e+10):_int
                    await evt?2
                    set e = evt!2
                    output std _(${D}e+10):_int
                }
                var y : active task @LOCAL->@[]->()->()->()
                set y = spawn g ()
                emit @LOCAL <.2 _1:_int>: Event
                emit @LOCAL <.2 _2:_int>: Event
            }            
        """.trimIndent())
        assert(out == "11\n12\n0\n") { out }
    }
    @Test
    fun a08_bcast_block2 () {
        val out = all("""
            type Event = <(),_int>
            {
                var f : task @LOCAL->@[]->()->()->()
                set f = task @LOCAL->@[]->()->()->() {
                    await _1:_int
                    var iskill: _int
                    set iskill = evt?1
                    native _(assert(${D}iskill);)
                    output std _0:_int    -- only on kill
                }
                var x : active task @LOCAL->@[]->()->()->()
                set x = spawn f ()
                
                {
                    var g : task @LOCAL->@[]->()->()->()
                    set g = task @LOCAL->@[]->()->()->() {
                        var e: _int
                        await evt?2
                        set e = evt!2
                        output std _(${D}e+10):_int
                        await evt?2
                        set e = evt!2
                        output std _(${D}e+10):_int
                    }
                    var y : active task @LOCAL->@[]->()->()->()
                    set y = spawn g ()
                    emit @LOCAL <.2 _1:_int>: Event
                    emit @LOCAL <.2 _2:_int>: Event
                }
            }
        """.trimIndent())
        assert(out == "11\n12\n0\n") { out }
    }
    @Test
    fun a09_nest () {
        val out = all("""
            type Event = <(),_int>
            var f : task @LOCAL->@[]->()->()->()
            set f = task @LOCAL->@[]->()->()->() {
                output std _1:_int
                await evt?2
                var g : task @LOCAL->@[]->()->()->()
                set g = task @LOCAL->@[]->()->()->() {
                    output std _2:_int
                    await evt?2
                    output std _3:_int
                }
                var xg : active task @LOCAL->@[]->()->()->()
                set xg = spawn g ()
                await evt?2
                output std _4:_int
            }
            var x : active task @LOCAL->@[]->()->()->()
            set x = spawn f ()
            output std _10:_int
            emit @GLOBAL <.2 _1:_int>: Event
            output std _11:_int
            emit @GLOBAL <.2 _1:_int>: Event
            output std _12:_int
        """.trimIndent())
        assert(out == "1\n10\n2\n11\n3\n4\n12\n") { out }
    }
    @Test
    fun a10_block_out () {
        val out = all("""
            type Event = <(),_int>
            var f : task @LOCAL->@[]->()->()->()
            set f = task @LOCAL->@[]->()->()->() {
                output std _10:_int
                {
                    var g : task @LOCAL->@[]->()->()->()
                    set g = task @LOCAL->@[]->()->()->() {
                        output std _20:_int
                        await _1:_int
                        output std _21:_int
                        await _1:_int
                        if evt?1 {
                            output std _0:_int      -- only on kill
                        } else {
                            output std _22:_int     -- can't execute this one
                        }
                    }
                    var y : active task @LOCAL->@[]->()->()->()
                    set y = spawn g ()
                    await evt?2
                }
                output std _11:_int
                var h : task @LOCAL->@[]->()->()->()
                set h = task @LOCAL->@[]->()->()->() {
                    output std _30:_int
                    await evt?2
                    output std _31:_int
                }
                var z : active task @LOCAL->@[]->()->()->()
                set z = spawn h ()
                await evt?2
                output std _12:_int
            }
            var x : active task @LOCAL->@[]->()->()->()
            set x = spawn f ()
            emit @GLOBAL <.2 _1:_int>: Event
            emit @GLOBAL <.2 _1:_int>: Event
        """.trimIndent())
        assert(out == "10\n20\n21\n0\n11\n30\n31\n12\n") { out }
    }
    @Test
    fun a11_self_kill () {
        val out = all("""
            type Event = <(),_int>
            var g : task @LOCAL->@[]->()->()->()
            set g = task @LOCAL->@[]->()->()->() {
                var f : task @LOCAL->@[]->()->()->()
                set f = task @LOCAL->@[]->()->()->() {
                    output std _1:_int
                    await evt?2
                    output std _4:_int
                    emit @GLOBAL <.2 _1:_int>: Event
                    output std _999:_int
                }
                var x : active task @LOCAL->@[]->()->()->()
                set x = spawn f ()
                output std _2:_int
                await evt?2
                output std _5:_int
            }
            output std _0:_int
            var y : active task @LOCAL->@[]->()->()->()
            set y = spawn g ()
            output std _3:_int
            emit @GLOBAL <.2 _1:_int>: Event
            output std _6:_int
       """.trimIndent())
        assert(out == "0\n1\n2\n3\n4\n5\n6\n") { out }
    }
    @Test
    fun a12_self_kill () {
        val out = all("""
            type Event = <(),_int>
            var g : task @LOCAL->@[]->()->()->()
            set g = task @LOCAL->@[]->()->()->() {
                var f : task @LOCAL->@[]->()->()->()
                set f = task @LOCAL->@[]->()->()->() {
                    output std _1:_int
                    await evt?2
                    output std _4:_int
                    var kkk : func @[]->()->()
                    set kkk = func @[]->()->() {
                        emit @GLOBAL <.2 _1:_int>: Event
                    }
                    call kkk ()
                    output std _999:_int
                }
                var x : active task @LOCAL->@[]->()->()->()
                set x = spawn f ()
                output std _2:_int
                await evt?2
                output std _5:_int
            }
            output std _0:_int
            var y : active task @LOCAL->@[]->()->()->()
            set y = spawn g ()
            output std _3:_int
            emit @GLOBAL <.2 _1:_int>: Event
            output std _6:_int
       """.trimIndent())
        assert(out == "0\n1\n2\n3\n4\n5\n6\n") { out }
    }

    // DEFER

    @Test
    fun b01_defer () {
        val out = all("""
            type Event = <(),_int>
            var f : task @LOCAL->@[]->()->()->()
            set f = task @LOCAL->@[]->()->()->() {
                var defer : task @LOCAL->@[]->()->()->()
                set defer = task @LOCAL->@[]->()->()->() {
                    await evt?1
                    output std _2:_int
                }
                var xdefer : active task @LOCAL->@[]->()->()->()
                set xdefer = spawn defer ()
                output std _0:_int
                await evt?2
                output std _1:_int
            }
            var x : active task @LOCAL->@[]->()->()->()
            set x = spawn f ()
            --awake x _1:_int
            emit @GLOBAL <.2 _1:_int>: Event
        """.trimIndent())
        assert(out == "0\n1\n2\n") { out }
    }
    @Test
    fun b02_defer_block () {
        val out = all("""
            type Event = <(),_int>
            var f : task @LOCAL->@[]->()->()->()
            set f = task @LOCAL->@[]->()->()->() {
                {
                    var defer : task @LOCAL->@[]->()->()->()
                    set defer = task @LOCAL->@[]->()->()->() {
                        await evt?1
                        output std _2:_int
                    }
                    var xdefer : active task @LOCAL->@[]->()->()->()
                    set xdefer = spawn defer ()
                    output std _0:_int
                    await evt?2
                }
                output std _1:_int
            }
            var x : active task @LOCAL->@[]->()->()->()
            set x = spawn f ()
            --awake x _1:_int
            emit @GLOBAL <.2 _1:_int>: Event
        """.trimIndent())
        assert(out == "0\n2\n1\n") { out }
    }

    // THROW / CATCH

    @Test
    fun c00_catch () {
        val out = all("""
           catch {
           }
        """.trimIndent())
        assert(out == "(ln 1, col 7): invalid `catch` : requires enclosing task") { out }
    }

    @Test
    fun c00_err () {
        val out = all("""
            var f : task @LOCAL->@[]->()->()->()
            var x : task @LOCAL->@[]->()->()->()
            set x = spawn f ()
        """.trimIndent())
        assert(out.startsWith("(ln 3, col 5): invalid `spawn` : type mismatch : expected active task")) { out }
    }
    @Test
    fun c00_throw () {
        val out = all("""
            type Event = <(),_int>
            var h : task @LOCAL->@[]->()->()->()
            set h = task @LOCAL->@[]->()->()->() {
               catch {
                    var f : task @LOCAL->@[]->()->()->()
                    set f = task @LOCAL->@[]->()->()->() {
                        await evt?1
                        output std _1:_int
                    }
                    var x : active task @LOCAL->@[]->()->()->()
                    set x = spawn f ()
                    throw
               }
               output std _2:_int
           }
           var z : active task @LOCAL->@[]->()->()->()
           set z = spawn h ()
           output std _3:_int
        """.trimIndent())
        assert(out == "1\n2\n3\n") { out }
    }
    @Test
    fun c01_throw () {
        val out = all("""
            type Event = <(),_int>
            var h : task @LOCAL->@[]->()->()->()
            set h = task @LOCAL->@[]->()->()->() {
                catch {
                    var f : task @LOCAL->@[]->()->()->()
                    set f = task @LOCAL->@[]->()->()->() {
                        await evt?2
                        output std _999:_int
                    }
                    var g : task @LOCAL->@[]->()->()->()
                    set g = task @LOCAL->@[]->()->()->() {
                        await evt?1
                        output std _1:_int
                    }
                    var x : active task @LOCAL->@[]->()->()->()
                    set x = spawn f ()
                    var y : active task @LOCAL->@[]->()->()->()
                    set y = spawn g ()
                    output std _0:_int
                    throw
                    output std _999:_int
                }
                output std _2:_int
           }
           var z : active task @LOCAL->@[]->()->()->()
           set z = spawn h ()
           output std _3:_int
        """.trimIndent())
        assert(out == "0\n1\n2\n3\n") { out }
    }
    @Test
    fun c02_throw_par2 () {
        val out = all("""
            type Event = <(),_int>
            var main : task @LOCAL->@[]->()->()->()
            set main = task @LOCAL->@[]->()->()->() {
                var fg : task @LOCAL->@[]->()->()->()
                set fg = task @LOCAL->@[]->()->()->() {
                    var f : task @LOCAL->@[]->()->()->()
                    set f = task @LOCAL->@[]->()->()->() {
                        await evt?2
                        output std _999:_int
                    }
                    var g: task @LOCAL->@[]->()->()->()
                    set g = task @LOCAL->@[]->()->()->() {
                        await evt?1
                        output std _2:_int
                    }
                    await evt?2
                    var xf : active task @LOCAL->@[]->()->()->()
                    set xf = spawn f ()
                    var xg : active task @LOCAL->@[]->()->()->()
                    set xg = spawn g ()
                    throw
                }
                var h : task @LOCAL->@[]->()->()->()
                set h = task @LOCAL->@[]->()->()->() {
                    await evt?1
                    output std _1:_int
                }
                var xfg : active task @LOCAL->@[]->()->()->()
                var xh : active task @LOCAL->@[]->()->()->()
                catch {
                    set xfg = spawn fg ()
                    set xh = spawn h ()
                    emit @GLOBAL <.2 _1:_int>: Event
                    output std _999:_int
                }
            }
            var xmain : active task @LOCAL->@[]->()->()->()
            set xmain = spawn main ()
            output std _3:_int
        """.trimIndent())
        assert(out == "1\n2\n3\n") { out }
    }
    @Test
    fun c03_throw_func () {
        val out = all("""
            type Event = <(),_int>
            var err : func @[]->()->()
            set err = func @[]->()->() {
                throw
            }
            var h : task @LOCAL->@[]->()->()->()
            set h = task @LOCAL->@[]->()->()->() {
               catch {
                    var f: task @LOCAL->@[]->()->()->()
                    set f = task @LOCAL->@[]->()->()->() {
                        await _1:_int
                        output std _1:_int
                    }
                    var xf: active task @LOCAL->@[]->()->()->()
                    set xf = spawn f ()
                    call err ()
                    output std _999:_int
               }
               output std _2:_int
           }
           var xh : active task @LOCAL->@[]->()->()->()
           set xh = spawn h ()
           output std _3:_int
        """.trimIndent())
        assert(out == "1\n2\n3\n") { out }
    }
    @Disabled
    @Test
    fun c03_try_catch () {
        val out = all("""
            catch (file not found) {
                var f = open ()
                defer {
                    call close f
                }
                loop {
                    var c = read f
                    ... throw err ...
                }
            }
        """.trimIndent())
        assert(out == "0\n1\n2\n") { out }
    }

    // FIELDS

    @Test
    fun d00_err () {
        val out = all("""
            var f : task @LOCAL->@[]->()->_int->()
            set f.pub = _4:_int
        """.trimIndent())
        assert(out.startsWith("(ln 2, col 7): invalid \"pub\" : type mismatch : expected active task")) { out }
    }
    @Test
    fun d01_field () {
        val out = all("""
            var f : task @LOCAL->@[]->()->_int->()
            set f = task @LOCAL->@[]->()->_int->() {
                set pub = _3:_int
                output std _1:_int
            }
            var xf: active task @LOCAL->@[]->()->_int->()
            set xf = spawn f ()
            output std _2:_int
            output std xf.pub
            set xf.pub = _4:_int
            output std xf.pub
        """.trimIndent())
        assert(out == "1\n2\n3\n4\n") { out }
    }

    // SPAWN / DYNAMIC

    @Test
    fun e01_spawn_err1 () {
        val out = all("""
            spawn task @LOCAL->@[]->()->()->() {
            } ()
        """.trimIndent())
        assert(out == "(ln 2, col 5): expected `in` : have end of file") { out }
    }
    @Test
    fun e01_spawn_err2 () {
        val out = all("""
            var f : func @LOCAL->@[]->()->()
            var fs : active tasks @LOCAL->@[]->()->()->()
            spawn f () in fs
        """.trimIndent())
        assert(out.startsWith("(ln 3, col 7): invalid `spawn` : type mismatch : expected task")) { out }
    }
    @Test
    fun e01_spawn_err3 () {
        val out = all("""
            var f : task @LOCAL->@[]->()->()->()
            spawn f () in ()
        """.trimIndent())
        assert(out.startsWith("(ln 2, col 15): invalid `spawn` : type mismatch : expected active tasks")) { out }
    }
    @Test
    fun e01_spawn_err4 () {
        val out = all("""
            var f : task @LOCAL->@[]->()->()->()
            var fs : active tasks @LOCAL->@[]->[()]->()->()
            spawn f () in fs
        """.trimIndent())
        assert(out == "(ln 3, col 1): invalid `spawn` : type mismatch :\n    tasks @GLOBAL -> @[] -> [()] -> () -> ()\n    task @GLOBAL -> @[] -> () -> () -> ()") { out }
    }
    @Test
    fun e02_spawn_free () {
        val out = all("""
            type Event = <(),_int>
            var f : task @LOCAL->@[]->()->()->()
            set f = task @LOCAL->@[]->()->()->() {
                output std _1:_int
                await evt?2
                output std _3:_int
            }
            var fs : active tasks @LOCAL->@[]->()->()->()
            spawn f () in fs
            output std _2:_int
            emit @GLOBAL <.2 _1:_int>: Event
            output std _4:_int
        """.trimIndent())
        assert(out == "1\n2\n3\n4\n") { out }
    }

    // POOL / TASKS / LOOPT

    @Test
    fun f01_err () {
        val out = all("""
            var xs: active tasks @LOCAL->@[]->()->_int->()
            var x:  task @LOCAL->@[]->()->_int->()
            loop x in xs {
            }
        """.trimIndent())
        assert(out.startsWith("(ln 3, col 6): invalid `loop` : type mismatch : expected task type")) { out }

    }
    @Test
    fun f02_err () {
        val out = all("""
            var xs: active tasks @LOCAL->@[]->[()]->_int->()
            var x:  active task  @LOCAL->@[]->()->_int->()
            loop x in xs {
            }
        """.trimIndent())
        assert(out == "(ln 3, col 1): invalid `loop` : type mismatch :\n    active task @GLOBAL -> @[] -> () -> _int -> ()\n    active tasks @GLOBAL -> @[] -> [()] -> _int -> ()") { out }

    }
    @Test
    fun f03_err () {
        val out = all("""
            var x: ()
            loop x in () {
            }
        """.trimIndent())
        assert(out.startsWith("(ln 2, col 6): invalid `loop` : type mismatch : expected task type")) { out }
    }
    @Test
    fun f04_err () {
        val out = all("""
            var x: active task @LOCAL->@[]->()->_int->()
            loop x in () {
            }
        """.trimIndent())
        assert(out.startsWith("(ln 2, col 11): invalid `loop` : type mismatch : expected tasks type")) { out }
    }

    @Test
    fun f05_loop () {
        val out = all("""
            var fs: active tasks @LOCAL->@[]->()->_int->()
            var f: active task @LOCAL->@[]->()->_int->()
            loop f in fs {
            }
            output std ()
        """.trimIndent())
        assert(out == "()\n") { out }
    }

    @Test
    fun f06_pub () {
        val out = all("""
            type Event = <(),_int>
            var f : task @LOCAL->@[]->()->_int->()
            set f = task @LOCAL->@[]->()->_int->() {
                set pub = _3:_int
                output std _1:_int
                await _1:_int
            }
            var fs: active tasks @LOCAL->@[]->()->_int->()
            spawn f () in fs
            var x: active task @LOCAL->@[]->()->_int->()
            loop x in fs {
                output std x.pub
            }
        """.trimIndent())
        assert(out == "1\n3\n") { out }
    }

    @Test
    fun f07_kill () {
        val out = all("""
            var f : task @LOCAL->@[]->()->_int->()
            set f = task @LOCAL->@[]->()->_int->() {
                set pub = _3:_int
                output std _1:_int
                --await _1:_int
            }
            var fs: active tasks @LOCAL->@[]->()->_int->()
            spawn f () in fs
            var x: active task @LOCAL->@[]->()->_int->()
            loop x in fs {
                output std x.pub
            }
        """.trimIndent())
        assert(out == "1\n") { out }
    }

    @Test
    fun f08_natural () {
        val out = all("""
            type Event = <(),_int>
            var f : task @LOCAL->@[]->_int->_int->()
            set f = task @LOCAL->@[]->_int->_int->() {
                set pub = arg
                output std pub
                await _1:_int
            }
            var g : task @LOCAL->@[]->_int->_int->()
            set g = task @LOCAL->@[]->_int->_int->() {
                set pub = arg
                output std pub
                await _1:_int
                await _1:_int
            }

            var xs: active tasks @LOCAL->@[]->_int->_int->()
            spawn f _1:_int in xs
            spawn g _2:_int in xs

            var x: active task @LOCAL->@[]->_int->_int->()
            loop x in xs {
                output std x.pub
            }
            
            emit @GLOBAL <.2 _10:_int>: Event
            
            loop x in xs {
                output std x.pub
            }
            
            output std ()
        """.trimIndent())
        assert(out == "1\n2\n1\n2\n2\n()\n") { out }
    }

    @Disabled   // TODO: can't kill itself b/c i becomes dangling
    @Test
    fun f09_dloop_kill () {
        val out = all("""
            var f : task @LOCAL->@[]->()->_int->()
            set f = task @LOCAL->@[]->()->_int->() {
                set pub = _3:_int
                output std _1:_int
                await _1:_int
            }
            var fs: active tasks @LOCAL->@[]->()->_int->()
            spawn f () in fs
            var x: active task @LOCAL->@[]->()->_int->()
            loop x in fs {
                emit @GLOBAL _5:_int
                output std x.pub
            }
        """.trimIndent())
        assert(out == "1\n") { out }
    }
}