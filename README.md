# Ce

*Ce* is simple language with algebraic data types, pointers, first-class
functions, and region-based memory management.

The main goal of *Ce* is to support safe memory management for dynamic data
structures.
Pointers hold allocated data which are attached to lexical blocks known as
regions.
When a block terminates, all attached allocations are automatically released.
This prevents memory leaks.
Pointers cannot be reassigned to pointers in outer blocks.
This prevents dereferencing dangling pointers.
These ideas have been successfully adopted in Cyclone:
https://cyclone.thelanguage.org/

# 1. STATEMENTS

## Block

A block delimits the scope of variables between curly braces:

```
{
    var x: ()
    ... x ...       -- `x` is visible here
}
... x ...           -- `x` is not visible here
```

A block may contain a label to identify its memory region:

```
{ @myblock          -- `@myblock` can be referenced in allocations
    ...
}
```

## Variable Declaration

A variable declaration introduces an identifier of the given type in the
current scope:

```
var x: ()           -- `x` is of unit type `()`
var y: _int         -- `y` is a native `int`
var z: [_int,_int]  -- `z` is a tuple of ints
```

## Assignment

An assignment changes the value of a variable, native identifier, tuple or
union discriminator, or pointer dereference:

```
set x     = ()      -- sets `x` to the unit value `()`
set _n    = _1      -- sets native `_n` to hold native `_1`
set tup.1 = n       -- changes the tuple index value
set ptr\  = v       -- dereferences pointer `ptr` and assigns `v`
```

## Call, Input & Output

The `call`, `input` & `output` statements invoke function expressions:

```
call f _0           -- calls `f` passing `_0`
input std: _int     -- reads a native `_int` value from stdin
output std _10      -- outputs native `_10` to stdout
```

## Sequence

A sequence of statements execute one after the other:

```
var x: _int                 -- first declares `x`
set x = input std: _int     -- then assigns `_int` input to `x`
output std x                -- finally outputs `x`
```

## Conditional

An `if` tests an `_int` value and executes one of the branches depending on the
result:

```
if x {
    call f ()       -- calls `f` if `x` is nonzero
} else {
    call g ()       -- calls `g` otherwise
}
```

## Repetition

A `loop` executes a block of statements indefinitely until it reaches a `break`
statement:

```
loop {
    ...             -- repeats this command indefinitely
    if ... {        -- until this condition is met
        break
    }
}
```

## Native

A native statement executes a block of code in the host language:

```
native _{
    printf("Hello World!");
}
```

## Function

A function declaration abstracts a block of statements that can be invoked with
arguments.
The argument can be accessed through the identifier `arg`.
The `return` statement exits a function with a value:

```
set f = func () -> () {
    return arg      -- `f` receives and returns `()`
}
```

*Note that a function declaration is actually a `func` expression assigned to
a variable.*

# 2. TYPES

## Unit

The unit type `()` has only the single unit value `()`.

## Native

A native type holds external values from the host language, i.e., values which
*Ce* does not create or manipulate directly:

```
_char     _int    _{FILE*}
```

Native type identifiers start with an underscore.

## Pointer

A pointer type can be applied to any other type with the prefix slash `/` and
holds a pointer to another value:

```
/_int           -- pointer to _int
/[_int,()]      -- pointer to tuple
```

## Tuple

A tuple type holds a value for each of its subtypes.
A tuple type identifier is a comma-separated list of types enclosed with
brackets `[` and `]`:

```
[(),(),())          -- a triple of unit types
[(),[_int,()]]      -- a pair containing another pair
```

## Union

A union type holds a value of one of its subtypes.
A tuple type identifier is a comma-separated list of types enclosed with
angle brackets `<` and `>`:

```
<(),(),()>          -- a union of three unit types
<(),[_int,()]>      -- a union of unit and a pair
```

### Recursive Union

A recursive union is always a pointer with a caret subtype pointing upwards:

```
\<[_int,\^]>        -- a linked list of `_int`
```

The caret `^` indicates recursion and refers to the enclosing recursive
union type.
Multiple `n` carets, e.g. `^^`, refer to the `n` outer enclosing recursive
union type.

## Function

A function type holds a function value and is composed of an input and output
types separated by an arrow `->`:

```
() -> _int          -- input is unit and output is `_int`
[_int,_int] -> ()   -- input is a pair of `_int` and output is unit
```

# 3. EXPRESSIONS

## Unit

The unit value is the single value of the unit type:

```
()
```

## Variable

A variable holds a value of its type:

```
var x: _int
set x = _10
output std x        -- variable `x` holds native `_10`
```

## Native

A native expression holds a value of the host language:

```
_printf    _(2+2)     _{f(x,y)}
```

Symbols defined in *Ce* can also be accessed inside native expressions:

```
var x: _int
set x = _10
output std _(x + 10)    -- outputs 20
```

## Tuple Constructor and Discriminator

### Constructor

A tuple holds a fixed number of values:

```
[(),_10]            -- a pair with `()` and native `_10`
[x,(),y]            -- a triple
```

### Discriminator

A tuple discriminator suffixes a tuple with a dot `.` and a number to evaluate
the value at the given position:

```
var tup: [(),_int]
set tup = [(),_10]
output std tup.2    -- outputs `10`
```

## Union Constructor, Allocation, Discriminator & Predicate

### Constructor

A union constructor creates a value of an union type given a subcase index,
an argument, followed by the explicit complete union type:

```
<.1 ()>: <(),()>                -- subcase .1 of `<(),()>` holds unit
<.2 [_10,_0]: <(),[_int,_int]>  -- subcase .2 holds a tuple
```

### Allocation

<.0 ()>: /<[_int,/^]>           -- subcase .0 is valid recursive unions

A recursive union always includes a null pointer constructor `<.0 ()>` that
represents termination.

The unit argument `()` of a unit subtype is optional.

### Discriminator

A discriminator accesses the value of a union type as one of its subcases.
A discriminator expression suffixes the value to access with an index and an
exclamation mark `!`:

```
x!1               -- yields ()

set x = <.1 [<.0>,(),<.0>]>
... x!1.2               -- yields ()
... x!0                 -- error: `x` is a `.1`
```

If the discriminated subcase does not match the actual value, the attempted
access raises a runtime error.

### Predicate

A predicate checks if the value of a [union type](TODO) is of the given
subcase.
A predicate expression suffixes the value to test with an index and a question
mark `?`:

```
(.1 ()).0?              -- yields 0
.0.0?                   -- yields 1
```


