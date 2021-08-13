# Pointers

## Basic Rule

- Cannot hold local upref or pointer in outer scope:

```
var p: \Int = ?
{
    var v: Int = 10
    set p = \v      -- (ln 4, col 13): invalid assignment : cannot hold local pointer "v" (ln 3)
}
```

```
var pout: \Int = ?
{
    var pin: \Int = ?
    set pout = pin
}
        assert(out == "(ln 5, col 11): invalid assignment : cannot hold local pointer "pin" (ln 4)
```

## Functions

- A function can return `arg` or a pointer from an outer scope:

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

- But the return counts as being from the caller scope:

```
var v: Int = 10
func f : () -> \Int {
    return \v
}
var p: \Int = ?
{
    set p = f ()    -- (ln 7, col 11): invalid assignment : cannot hold local pointer "f" (ln 2)
}
```

- A function cannot return a local pointer:

```
func f : () -> \Int
{
    var v: Int = 10
    return \v       -- (ln 3, col 5): invalid assignment : cannot hold local pointer "v" (ln 2)
}
```

- Even if it is an upref to `arg`:

```
func f : Int -> \Int
{
    return \arg     -- (ln 3, col 5): invalid assignment : cannot hold local pointer "arg" (ln 2)
}
```

- A function cannot hold `arg` (or any other local pointer) in the outer scope:

```
var v: \Int = ?
func f : \Int -> () {
    set v = arg     -- (ln 3, col 11): invalid assignment : cannot hold local pointer "arg" (ln 2)
}
```

### Outermost Scope Modifier

- The caret `^` modifier binds a variable to the function outermost scope, which can manipulate `arg`:

```
func f : \Int -> \Int
{
    var ptr: ^\Int = arg    -- ok
    return ptr
}
var v: Int = 10
var p: \Int = f ()
output std p\               -- 10
```

- But these variables cannot be assigned local pointers as expected:

```
func f : \Int -> \Int
{
    var v: Int = 10
    var ptr: ^\Int = \v     -- (ln 4, col 9): invalid assignment : cannot hold local pointer "v" (ln 3)
    return ptr
}
```

- And neither returned as uprefs:

```
func f: \Int -> \\Int
{
    var ptr: ^\Int = arg
    return \ptr             -- (ln 4, col 5): invalid assignment : cannot hold local pointer "ptr" (ln 3)
}
```

### Calls

- A call that returns 

## Tuples and User Types Compounds

- An upref to a subpart of a compound counts as an upref to the compound:

```
var p: \Int = ?
{
    var v: (Int,Int) = (10,20)
    set p = \v.1            -- (ln 4, col 11): invalid assignment : cannot hold local pointer "v" (ln 3)
}
```

```
type X {
    X: Int
}
var p: \Int = ?
{
    var v: X = X.X 10
    set p = \v.X!           -- (ln 7, col 11): invalid assignment : cannot hold local pointer "v" (ln 6)
}
```

- A compound counts as a pointer if it contains a pointer subpart:

```
var x1: (Int,\Int) = ?
{
    var v: Int = 20
    var x2: (Int,\Int) = (10,\v)
    set x1 = x2             -- (ln 5, col 12): invalid assignment : cannot hold local pointer "x2" (ln 4)
}
```

```
type X {
    X: \Int
}
var x1: X = ?
{
    var v: Int = 20
    var x2: X = X.X \v
    set x1 = x2             -- (ln 8, col 12): invalid assignment : cannot hold local pointer "x2" (ln 7)
}
```

- A pointer subpart of an outer compound cannot hold a local upref:

```
var p: (Int,\Int) = (10,?)
{
    var v: Int = 20
    set p = (10,\v)         -- (ln 4, col 11): invalid assignment : cannot hold local pointer "v" (ln 3)
}
```

```
var p: (Int,\Int) = (10,?)
{
    var v: Int = 20
    set p.2 = \v            -- (ln 4, col 13): invalid assignment : cannot hold local pointer "v" (ln 3)
}
```

```
type X {
    X: \Int
}
var p: X = X.X ?
{
    var v: Int = 20
    set p = X.X \v          -- (ln 7, col 11): invalid assignment : cannot hold local pointer "v" (ln 6)
}
```

```
type X {
    X: \Int
}
var p: X = X.X ?
{
    var v: Int = 20
    set p.X! = \v           -- (ln 7, col 14): invalid assignment : cannot hold local pointer "v" (ln 6)
}
```