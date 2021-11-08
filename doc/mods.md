# Recursive Values

## Operation Modifiers

Assignments of recursive values requires explicit operation modifiers:

- `new`:    allocates memory for non-zero variants
- `move`:   transfers ownership
- `copy`:   makes deep copies
- `borrow`: borrows ownership
- `hold`:   holds self reference
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

- A `move` requires a plain value not from a *dnref* expression:

```
var y: <^> = move /x    -- (ln 1, col 14): invalid `move` : expected recursive variable
```

### Borrowing

- A pointer assignment requires a `borrow` modifier:

```
var a: \<^> = borrow \x -- ok
var b: \[<^>] = \x      -- (ln 2, col 17): invalid expression : expected `borrow` operation modifier
var c: <^> = borrow x   -- (ln 3, col 14): invalid `borrow` : expected pointer to recursive variable
call f (borrow \x)
```

The main use of borrowing is to avoid moving ownership back and forth when
calling simple functions:

- https://doc.rust-lang.org/book/ch04-02-references-and-borrowing.html

However, in the general case, borrowing can be dangerous due to moves and
deallocation:

```
var x: <^> = new <.1 new <.1 <.0>>>
var y: \<^> = borrow \x!1   -- holds pointer to subpart of x
set x = <.0>                -- (!) releases pointed value
cal f (move x)              -- (!) moves to function that will release pointed value
... /y ...                  -- derefs pointer after free
```

For this reason, recursive subparts of a value cannot be reset while borrowed.

## Borrowing

- A value cannot be transferred or reassigned while borrowed to prevent dangling pointers:

```
var y: \<^> = borrow \x!1   -- pointer to subpart of x
set x = <.0>            -- (ln 2, col 7): invalid assignment of "x" : borrowed in line 1
var z: <^> = move x     -- (ln 3, col 19): invalid move of "x" : borrowed in line 1
... /y ...              -- prevent use-after-free situation, since subpart could be released
```

- Borrows are automatically released when they go out of scope:

```
var x: <^> = ?
{
    var y: \<^> = borrow \x     -- automatic unborrow on scope termination
}
var z: <^> = move x     -- ok: no remaining borrows
```

- Pointers in *dnref* expressions cannot be moved because they imply an active owner:

```
var l: <^> = ...        -- owner
var x: \<^> = \l        -- pointer
var y: <^> = move x\    -- (ln 3, col 14): invalid `move` : expected recursive variable
```


