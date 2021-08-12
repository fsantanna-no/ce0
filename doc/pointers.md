# Pointers

## Basic Rule

- Cannot hold local upref or pointer in outer scope:

```
var p: \Int = ?
{
    var v: Int = 10
    set p = \v      -- (ln 4, col 13): invalid assignment : cannot hold pointer to local "v" (ln 3) in outer scope
}
```

```
var pout: \Int = ?
{
    var pin: \Int = ?
    set pout = pin
}
        assert(out == "(ln 5, col 11): invalid assignment : cannot hold pointer to local \"pp\" (ln 4) in outer scope")
```

## Functions

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
```

- Even if it is an upref to `arg`:

```
func f : Int -> \Int
{
    return \arg     -- (ln 3, col 5): invalid assignment : cannot hold pointer to local "arg" (ln 2) in outer scope
}
```

## Scope Modifier

- Caret `^` modifier binds variables to outermost scope, which can manipulate `arg`:

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
    var ptr: ^\Int = \v     -- (ln 4, col 9): invalid assignment : cannot hold pointer to local "v" (ln 3) in outer scope
    return ptr
}
```

- And neither returned as uprefs:

```
func f: \Int -> \\Int
{
    var ptr: ^\Int = arg
    return \ptr             -- (ln 4, col 5): invalid assignment : cannot hold pointer to local "ptr" (ln 3) in outer scope
}
```

## Tuples and User Types

- An upref to a subpart counts as an upref to whole value:

```
var p: \Int = ?
{
    var v: (Int,Int) = (10,20)
    set p = \v.1            -- (ln 4, col 11): invalid assignment : cannot hold pointer to local "v" (ln 3) in outer scope
}
```

```
type X {
    X: Int
}
var p: \Int = ?
{
    var v: X = X.X 10
    set p = \v.X!           -- (ln 7, col 11): invalid assignment : cannot hold pointer to local "v" (ln 6) in outer scope
}
```
