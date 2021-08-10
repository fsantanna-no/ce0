# Pointers

- Cannot hold local pointer in outer scope:

```
var p: \Int = ?
{
    var v: Int = 10
    set p = \v      -- (ln 4, col 13): invalid assignment : cannot hold pointer to local "v" (ln 3) in outer scope
}
```

- Function can return pointer from outer scope or `arg`:

```
func f : \Int -> \Int
{
    return arg      -- ok
}
var v: Int = 10
var p: \Int = f \v
output std p\       -- 10
```

```
var v: Int = 10
func f : () -> \Int {
    return \v       -- ok
}
var p: \Int = f ()
output std p\       -- 10
```

- Function cannot return local pointer:

```
func f : () -> \Int
{
    var v: Int = 10
    return \v       -- (ln 3, col 5): invalid assignment : cannot hold pointer to local "v" (ln 2) in outer scope
}
var v: Int = 10
var p: \Int = f ()
output std p\
```

- TODO
    - `var x: ^\Int` (pointer to outer scope declared in inner scope)
