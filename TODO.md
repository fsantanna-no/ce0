- use C stack for normal func
- static alloc for clo/task that do not escape
  - should them even have a @clo annotation?
- catch N, throw N (N = argument)
- \#line, stack debug
- bug expand: /</^@x>@y (x/y do not alternate as expected)
- bug expand: cloneX
- ce1: type, generics, typeclass, subtyping, option
- explicit `free`
- test block pass w/o data (e.g., internal `new` call)
- reserve `arg`,`pub`,`evt`,`ret`
- evt type should be <.fin=(), .throw=_int, ...>
- DLoop:
  1. reject await/bcast/etc inside DLoop
  2. runtime error when self kill
  3. escape loop on error (but, multi-level escape)
  - if DLoop has await, it needs to recreate the i stack
  - throw inside DLoop
    - loop tk in @block { stack tk }
    - break if NULL?

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

