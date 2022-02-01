- loop tk in @block { stack tk }
- natural termination, free

- remover // TODO: call stack
  - usar stack de C para fcs normais
- rever local/mem/scp1.toce
- catch N, throw N
- spawn dyn @block
  - public fields
  - iterate over tasks
- \#line, stack debug


- Parser in parts to simplify ce1:

```
var table_types: MutableMap<String, Pair<(All)->Boolean,(All)->Type>> = mutableMapOf (
"Unit" to Pair({ it.accept(TK.UNIT) }, {Type.Unit(it.tk0 as Tk.Sym)})
)
```

# Subtyping
    - structural:   [(),()] <: [()]
    - nominal:      Player.Warrior <: Warrior
    - both:         T [(),()] <:  [()]
      T [(),()] xx S[()]  -- structural but not nominal

```
type Bool = <(),()>
```

```
type Player [
    name: _(char*),
    age:  _int,
    sub:  <
        Warrior: [mana:_int, guild:_(char*)],
        Mage: [...],
    >
]
val x: Player.Warrior = ["Arthur", 32, [10,"arcane"]]
```

# Tasks

- either assignable or anonymous
    - assignable `var`: remains in scope memory even after termination
    - anonymous `pool`: reclaimed on termination
- state:
    - unborn:   expecting spawn & arguments
    - running:  running up to await
    - awaiting: reached await
    - paused:   from tk.pause, do not resume
    - dead:     finished execution, no resume, holds return
- broadcast
    - all & first
    - up & down
    - receives scope
    - resumes task hierarchy, skips paused
    - passes event value

```
var  tk:  task () -> ()     -- assignable
pool tks: task () -> ()     -- anonymous

set tk = task () -> () {
    ...
    e = await e / ()
    ...
}
spawn  tk (...) 
kill   tk
status tk
pause  tk
awake [tk,e]
broadcast up tk e
broadcast down scp e
await _int  // condition using evt

task f {
    defer {
        ...
    }
    ...
    spawn h ()
    ...
    catch cnd {
        defer {
            ...
        }
        ...
        spawn g()       <--- 1. g awakes from bcast and throws cond
        await ...
    }
                        <--- 2. catch/defer awakes
                        <--- 3. defer awakes
}
```

finalize/every/pool

par/or do
    ...
with
    ...
end

spawn tk1
spawn tk2
await_all [tk1,tk2]
await_any [tk1,tk2]

