- emit EVENT_TASK to correct scope (not GLOBAL)
  - can infer from func signature
- bugs
  - BookPtr
    - make inference repeat scope?
      - but only for type, not func
  - $x (x in other task)
  - check 00/01 detect var in between funcs
  - Type.Alias.supOf (check scopes)
- refactor XTypes to use visit
- ce1:
  - if/until condition
    - _int -> Bool:<(),()>
  - await task
  - defer
  - var [x,y] = arg   (arg is tuple)
  - option, subtyping, generics, typeclass
- isSupOf for Nat, make it false against others
- return -> escape?
  - cross task
  - SetBlock
    - var x: () = { ... escape () }
- XLexer para ce1
  - ce0: :-/:+
- Never type
- typedef para task (birds example)
- pico-ce
  - image scale (birds)
- change @[@a1,@a2] -> @[a,b: a>b]
  - check constraint in func comparison
- accept @A vs var a
- var z = spawn h ()
  - optional in ce0?
  - optional in ce1?
- \#line, stack debug
- optimizations
  - use C stack for normal func
  - static alloc for clo/task that do not escape
    - should them even have a @clo annotation?
- catch N, throw N (N = argument)
- explicit `free`
- test block pass w/o data (e.g., internal `new` call)
- reserve `arg`,`pub`,`evt`,`ret`
- remove CLO from task
  - always local, can never escape
    - ~task must support parametric closure and check if spawn is local~
      - ~task @a ...~
- DLoop:
  - is it possible to create new state=TASK_POOL_LOOP
    and not free while in it?
  1. reject await/emit/etc inside DLoop
  2. runtime error when self kill
  3. escape loop on error (but, multi-level escape)
  - if DLoop has await, it needs to recreate the i stack
  - throw inside DLoop
    - loop tk in @block { stack tk }
    - break if NULL?
  4. keep freelist cleared on finish
    {
      defer{free()}
      loop { ... }
    }
- output
  - std ? --> toString ?
    - output std toString /x
  - should print alias?
    - List <...>

# Subtyping
    - structural:   [(),()] <: [()]
    - nominal:      Player.Warrior <: Warrior
    - both:         T [(),()] <:  [()]
      T [(),()] xx S[()]  -- structural but not nominal

```
type Bool = <(),()>
```

```
type Bool = <   // Q. use `=´ or `:´ as below? R. `=´ is symmetric to `type`
  False = (),
  True  = ()
>
```

```
type Player = [
    name: _(char*),
    age:  _int,
    sub:  <
        Warrior: [mana:_int, guild:_(char*)],
        Mage: [...],
    >
]
val x: Player.Warrior = [name="Arthur", age=32, [10,"arcane"]]
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
        spawn g()       <--- 1. g awakes from emit and throws cond
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

