# Recursive Values

## Operation Modifiers

Assignments of recursive values requires explicit operation modifiers:

- `new`:    allocates memory for non-zero variants
- `move`:   transfers ownership
- `copy`:   makes deep copies
- `borrow`: borrows ownership
- *none*:   assignments of non-recursive values

### New

- A variant constructor requires a `new` modifier, except for `<.0>` variants:

```
var l1: <^> = new <.1 new <.1 <.0>>> -- ok
var l2: <^> = <.1 <.0>> -- (ln 2, col 17): invalid expression : expected `new` operation modifier
var l3: <^> = new <.0>  -- (ln 3, col 15): invalid `new` : expected variant constructor
```

### Move & Copy

- A variable assignment requires a `move` or `copy` modifier:

```
var a: <^> = move x     -- ok
var b: <^> = x          -- (ln 2, col 14): invalid expression : expected operation modifier
var c: _int = copy _10  -- (ln 3, col 15): invalid `copy` : expected recursive variable
var d: <^> = copy <.0>  -- (ln 1, col 14): invalid `copy` : expected recursive variable
```

### Borrow

- A pointer assignment requires a `borrow` modifier:

```
var a: \<^> = borrow \x -- ok
var b: \[<^>] = \x      -- (ln 2, col 17): invalid expression : expected `borrow` operation modifier
var c: <^> = borrow x   -- (ln 3, col 14): invalid `borrow` : expected pointer to recursive variable
```

## Borrowing

- A value cannot be transferred or reassigned while borrowed to prevent dangling pointers:

```
var y: \<^> = borrow \x
set x = <.0>            -- (ln 2, col 7): invalid assignment of "x" : borrowed in line 1
var z: <^> = move x     -- (ln 3, col 19): invalid move of "x" : borrowed in line 1
... /y ...              -- prevents access to dangling pointer
```

- Only after xxx go out of scope:

```
var y: \<^> = borrow \x
set x = <.0>            -- (ln 2, col 5): invalid access to \"x\" : borrowed in line 2
```
