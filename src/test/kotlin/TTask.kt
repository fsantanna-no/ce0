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
        val (_,out3) = exec("./out.exe")
        //val (_,out3) = exec("valgrind ./out.exe")
            // search in tests output for
            //  - "definitely lost"
            //  - "Invalid read of size"
        //println(out3)
        return out3
    }

    @Test
    fun a01_output () {
        val out = all("""
            var f: task @LOCAL->@[]->()->()
            set f = task @LOCAL->@[]->()->() {
                output std _1:_int
            }
            spawn f ()
            output std _2:_int
        """.trimIndent())
        assert(out == "1\n2\n") { out }
    }
    @Test
    fun a02_await_err2 () {
        val out = all("""
            await ()
        """.trimIndent())
        assert(out == "(ln 1, col 1): invalid condition : type mismatch") { out }
    }
    @Test
    fun a02_await () {
        val out = all("""
            var f: task @LOCAL->@[]->()->()
            set f = task @LOCAL->@[]->()->() {
                output std _1:_int
                await _(self->mem.evt != 0):_int
                output std _3:_int
            }
            spawn f ()
            output std _2:_int
            awake f _1:_int
        """.trimIndent())
        assert(out == "1\n2\n3\n") { out }
    }
    @Test
    fun a02_await_err () {
        val out = all("""
            var f: task @LOCAL->@[]->()->()
            set f = task @LOCAL->@[]->()->() {
                output std _1:_int
                await _(self->mem.evt != 0):_int
                output std _3:_int
            }
            spawn f ()
            output std _2:_int
            awake f _1:_int
            awake f _1:_int
        """.trimIndent())
        assert(out.endsWith("Assertion `task->state==TASK_UNBORN || task->state==TASK_AWAITING' failed.\n")) { out }
    }
    @Test
    fun a03_var () {
        val out = all("""
            var f: task @LOCAL->@[]->()->()
            set f = task @LOCAL->@[]->()->() {
                var x: _int
                set x = _10:_int
                await _(self->mem.evt != 0):_int
                output std x
            }
            spawn f ()
            awake f _1:_int
        """.trimIndent())
        assert(out == "10\n") { out }
    }
    @Test
    fun a04_vars () {
        val out = all("""
            var f: task @LOCAL->@[]->()->()
            set f = task @LOCAL->@[]->()->() {
                {
                    var x: _int
                    set x = _10:_int
                    await _(self->mem.evt != 0):_int
                    output std x
                }
                {
                    var y: _int
                    set y = _20:_int
                    await _(self->mem.evt != 0):_int
                    output std y
                }
            }
            spawn f ()
            awake f _1:_int
            awake f _1:_int
        """.trimIndent())
        assert(out == "10\n20\n") { out }
    }
    @Test
    fun a05_args () {
        val out = all("""
            var f: task @LOCAL->@[]->_(char*)->()
            set f = task @LOCAL->@[]->_(char*)->() {
                output std arg
                await _(self->mem.evt != 0):_int
                output std evt
                await _(self->mem.evt != 0):_int
                output std evt
            }
            spawn f _("hello"):_(char*)
            awake f _10:_int
            awake f _20:_int
        """.trimIndent())
        assert(out == "\"hello\"\n10\n20\n") { out }
    }
    @Test
    fun a06_par_err () {
        val out = all("""
            var build : func @[] -> () -> task @LOCAL->@[]->()->()
            set build = func @[] -> () -> task @LOCAL->@[]->()->() {
                set ret = task @LOCAL->@[]->()->() {    -- ERR: not the same @LOCAL
                    output std _1:_int
                    await _(self->mem.evt != 0):_int
                    output std _2:_int
                }
            }
        """.trimIndent())
        assert(out == "(ln 3, col 13): invalid return : type mismatch") { out }
    }
    @Test
    fun a06_par () {
        val out = all("""
            var build : func @[@r1] -> () -> task @r1->@[]->()->()
            set build = func @[@r1] -> () -> task @r1->@[]->()->() {
                set ret = task @r1->@[]->()->() {
                    output std _1:_int
                    await _(self->mem.evt != 0):_int
                    output std _2:_int
                }
            }
            var f: task @LOCAL->@[]->()->()
            set f = build @[@LOCAL] ()
            var g: task @LOCAL->@[]->()->()
            set g = build @[@LOCAL] ()
            output std _10:_int
            spawn f ()
            output std _11:_int
            spawn g ()
            awake f _1:_int
            awake g _1:_int
            output std _12:_int
        """.trimIndent())
        assert(out == "10\n1\n11\n1\n2\n2\n12\n") { out }
    }
    @Test
    fun a07_bcast () {
        val out = all("""
            var f : task @LOCAL->@[]->()->()
            set f = task @LOCAL->@[]->()->() {
                await _(self->mem.evt != 0):_int
                output std _(self->mem.evt+0):_int
            }
            spawn f ()
            
            var g : task @LOCAL->@[]->()->()
            set g = task @LOCAL->@[]->()->() {
                await _(self->mem.evt != 0):_int
                output std _(self->mem.evt+10):_int
                await _(self->mem.evt != 0):_int
                output std _(self->mem.evt+10):_int
            }
            spawn g ()
            
            bcast @GLOBAL _1:_int
            bcast @GLOBAL _2:_int
        """.trimIndent())
        assert(out == "1\n11\n12\n") { out }
    }
    @Test
    fun a08_bcast_block () {
        val out = all("""
            var f: task @LOCAL->@[]->()->()
            set f = task @LOCAL->@[]->()->() {
                await _1:_int
                output std _(self->mem.evt+0):_int    -- only on kill
            }
            spawn f ()
            
            {
                var g: task @LOCAL->@[]->()->()
                set g = task @LOCAL->@[]->()->() {
                    await _(self->mem.evt != 0):_int
                    output std _(self->mem.evt+10):_int
                    await _(self->mem.evt != 0):_int
                    output std _(self->mem.evt+10):_int
                }
                spawn g ()
                bcast @LOCAL _1:_int
                bcast @LOCAL _2:_int
            }            
        """.trimIndent())
        assert(out == "11\n12\n0\n") { out }
    }
    @Test
    fun a09_nest () {
        val out = all("""
            var f: task @LOCAL->@[]->()->()
            set f = task @LOCAL->@[]->()->() {
                output std _1:_int
                await _(self->mem.evt != 0):_int
                var g: task @LOCAL->@[]->()->()
                set g = task @LOCAL->@[]->()->() {
                    output std _2:_int
                    await _(self->mem.evt != 0):_int
                    output std _3:_int
                }
                spawn g ()
                await _(self->mem.evt != 0):_int
                output std _4:_int
            }
            spawn f ()
            output std _10:_int
            bcast @GLOBAL _1:_int
            output std _11:_int
            bcast @GLOBAL _1:_int
            output std _12:_int
        """.trimIndent())
        assert(out == "1\n10\n2\n11\n3\n4\n12\n") { out }
    }
    @Test
    fun a10_block_out () {
        val out = all("""
            var f: task @LOCAL->@[]->()->()
            set f = task @LOCAL->@[]->()->() {
                output std _10:_int
                {
                    var g: task @LOCAL->@[]->()->()
                    set g = task @LOCAL->@[]->()->() {
                        output std _20:_int
                        await _1:_int
                        output std _21:_int
                        await _1:_int
                        if _(self->mem.evt == 0):_int {
                            output std _0:_int      -- only on kill
                        } else {
                            output std _22:_int     -- can't execute this one
                        }
                    }
                    spawn g ()
                    await _(self->mem.evt != 0):_int
                }
                output std _11:_int
                var h: task @LOCAL->@[]->()->()
                set h = task @LOCAL->@[]->()->() {
                    output std _30:_int
                    await _(self->mem.evt != 0):_int
                    output std _31:_int
                }
                spawn h ()
                await _(self->mem.evt != 0):_int
                output std _12:_int
            }
            spawn f ()
            bcast @GLOBAL _1:_int
            bcast @GLOBAL _1:_int
        """.trimIndent())
        assert(out == "10\n20\n21\n0\n11\n30\n31\n12\n") { out }
    }
    @Test
    fun a11_self_kill () {
        val out = all("""
            var g : task @LOCAL->@[]->()->()
            set g = task @LOCAL->@[]->()->() {
                var f : task @LOCAL->@[]->()->()
                set f = task @LOCAL->@[]->()->() {
                    output std _1:_int
                    await _(self->mem.evt != 0):_int
                    output std _4:_int
                    bcast @GLOBAL _1:_int
                    output std _999:_int
                }
                spawn f ()
                output std _2:_int
                await _(self->mem.evt != 0):_int
                output std _5:_int
            }
            output std _0:_int
            spawn g ()
            output std _3:_int
            bcast @GLOBAL _1:_int
            output std _6:_int
       """.trimIndent())
        assert(out == "0\n1\n2\n3\n4\n5\n6\n") { out }
    }
    @Test
    fun a12_self_kill () {
        val out = all("""
            var g : task @LOCAL->@[]->()->()
            set g = task @LOCAL->@[]->()->() {
                var f : task @LOCAL->@[]->()->()
                set f = task @LOCAL->@[]->()->() {
                    output std _1:_int
                    await _(self->mem.evt != 0):_int
                    output std _4:_int
                    var kkk : func @[]->()->()
                    set kkk = func @[]->()->() {
                        bcast @GLOBAL _1:_int
                    }
                    call kkk ()
                    output std _999:_int
                }
                spawn f ()
                output std _2:_int
                await _(self->mem.evt != 0):_int
                output std _5:_int
            }
            output std _0:_int
            spawn g ()
            output std _3:_int
            bcast @GLOBAL _1:_int
            output std _6:_int
       """.trimIndent())
        assert(out == "0\n1\n2\n3\n4\n5\n6\n") { out }
    }

    // DEFER

    @Test
    fun b01_defer () {
        val out = all("""
            var f: task @LOCAL->@[]->()->()
            set f = task @LOCAL->@[]->()->() {
                var defer : task @LOCAL->@[]->()->()
                set defer = task @LOCAL->@[]->()->() {
                    await _(self->mem.evt == 0):_int
                    output std _2:_int
                }
                spawn defer ()
                output std _0:_int
                await _(self->mem.evt != 0):_int
                output std _1:_int
            }
            spawn f ()
            awake f _1:_int
        """.trimIndent())
        assert(out == "0\n1\n2\n") { out }
    }
    @Test
    fun b02_defer_block () {
        val out = all("""
            var f: task @LOCAL->@[]->()->()
            set f = task @LOCAL->@[]->()->() {
                {
                    var defer : task @LOCAL->@[]->()->()
                    set defer = task @LOCAL->@[]->()->() {
                        await _(self->mem.evt == 0):_int
                        output std _2:_int
                    }
                    spawn defer ()
                    output std _0:_int
                    await _(self->mem.evt != 0):_int
                }
                output std _1:_int
            }
            spawn f ()
            awake f _1:_int
        """.trimIndent())
        assert(out == "0\n2\n1\n") { out }
    }

    // THROW / CATCH

    @Test
    fun c00_throw () {
        val out = all("""
            var h: task @LOCAL->@[]->()->()
            set h = task @LOCAL->@[]->()->() {
               catch {
                    var f: task @LOCAL->@[]->()->()
                    set f = task @LOCAL->@[]->()->() {
                        await _1:_int
                        output std _1:_int
                    }
                    spawn f ()
                    throw
               }
               output std _2:_int
           }
           spawn h ()
           output std _3:_int
        """.trimIndent())
        assert(out == "1\n2\n3\n") { out }
    }
    @Test
    fun c01_throw () {
        val out = all("""
            var h: task @LOCAL->@[]->()->()
            set h = task @LOCAL->@[]->()->() {
                catch {
                    var f: task @LOCAL->@[]->()->()
                    set f = task @LOCAL->@[]->()->() {
                        await _(self->mem.evt != 0):_int
                        output std _999:_int
                    }
                    var g: task @LOCAL->@[]->()->()
                    set g = task @LOCAL->@[]->()->() {
                        await _1:_int
                        output std _1:_int
                    }
                    spawn f ()
                    spawn g ()
                    output std _0:_int
                    throw
                    output std _999:_int
                }
                output std _2:_int
           }
           spawn h ()
           output std _3:_int
        """.trimIndent())
        assert(out == "0\n1\n2\n3\n") { out }
    }
    @Test
    fun c02_throw_par2 () {
        val out = all("""
            var main : task @LOCAL->@[]->()->()
            set main = task @LOCAL->@[]->()->() {
                var fg: task @LOCAL->@[]->()->()
                set fg = task @LOCAL->@[]->()->() {
                    var f: task @LOCAL->@[]->()->()
                    set f = task @LOCAL->@[]->()->() {
                        await _(self->mem.evt == 2):_int
                        output std _999:_int
                    }
                    var g: task @LOCAL->@[]->()->()
                    set g = task @LOCAL->@[]->()->() {
                        await _(self->mem.evt == 0):_int
                        output std _2:_int
                    }
                    await _(self->mem.evt != 0):_int
                    spawn f ()
                    spawn g ()
                    throw
                }
                var h: task @LOCAL->@[]->()->()
                set h = task @LOCAL->@[]->()->() {
                    await _(self->mem.evt == 0):_int
                    output std _1:_int
                }
                catch {
                    spawn fg ()
                    spawn h ()
                    bcast @GLOBAL _5:_int
                    output std _999:_int
                }
            }
            spawn main ()
            output std _3:_int
        """.trimIndent())
        assert(out == "1\n2\n3\n") { out }
    }
    @Test
    fun c03_throw_func () {
        val out = all("""
            var err : func @[]->()->()
            set err = func @[]->()->() {
                throw
            }
            var h: task @LOCAL->@[]->()->()
            set h = task @LOCAL->@[]->()->() {
               catch {
                    var f: task @LOCAL->@[]->()->()
                    set f = task @LOCAL->@[]->()->() {
                        await _1:_int
                        output std _1:_int
                    }
                    spawn f ()
                    call err ()
                    output std _999:_int
               }
               output std _2:_int
           }
           spawn h ()
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

    // SPAWN / DYNAMIC

    @Test
    fun e01_spawn () {
        val out = all("""
            spawn task @LOCAL->@[]->()->() {
                output std _1:_int
                await _(self->mem.evt != 0):_int
                output std _3:_int
            } ()
            output std _2:_int
            bcast @GLOBAL _1:_int
            output std _4:_int
        """.trimIndent())
        assert(out == "1\n2\n3\n4\n") { out }
    }
    @Test
    fun e02_spawn_free () {
        val out = all("""
            spawn task @LOCAL->@[]->()->() {
                output std _1:_int
                await _(self->mem.evt != 0):_int
                output std _3:_int
            } ()
            output std _2:_int
            bcast @GLOBAL _1:_int
            output std _4:_int
        """.trimIndent())
        assert(out == "1\n2\n3\n4\n") { out }
    }
}