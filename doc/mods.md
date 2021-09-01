# Operation Modifiers

Assignments of recursive values requires explicit operation modifiers:

- `new`:    allocates memory for non-zero variants
- `move`:   transfers ownership
- `copy`:   makes deep copies
- `borrow`: borrows ownership
- *none*:   assignments of non-recursive values

## New

- Union constructors require `new` modifiers, except for `<.0>` variants:

```
var l1: <^> = new <.1 new <.1 <.0>>>
var l2: <^> = <.1 <.0>> -- (ln 2, col 17): invalid expression : expected `new` operation modifier
var l3: <^> = new <.0>  -- (ln 3, col 15): invalid `new` : expected variant constructor
```

## Move & Copy

- Variables require `move` or `copy` modifiers:

```
var a: <^> = move x
var b: <^> = x          -- (ln 2, col 14): invalid expression : expected operation modifier
var c: _int = copy _10  -- (ln 3, col 15): invalid `copy` : expected recursive variable
var d: <^> = copy <.0>  -- (ln 1, col 14): invalid `copy` : expected recursive variable
```

## Borrow

- Pointers require `borrow` modifiers:

```
var a: \<^> = borrow \x
var b: \[<^>] = \x      -- (ln 2, col 17): invalid expression : expected `borrow` operation modifier
var c: <^> = borrow x   -- (ln 3, col 14): invalid `borrow` : expected pointer to recursive variable
```

