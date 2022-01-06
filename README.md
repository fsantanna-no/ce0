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

## 1.1. Variable Declaration

A variable declaration introduces an identifier of the given type in the
current scope:

```
var x: ()           -- `x` is of unit type `()`
var y: _int         -- `y` is a native `int`
var z: [_int,_int]  -- `z` is a tuple of ints
```

## 1.2. Block

A block delimits between curly braces `{` and `}` the scope of variables:

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

## 1.3. Assignment

An assignment changes the value of a variable, native identifier, tuple or
union discriminator, or pointer dereference:

```
set x     = ()      -- sets `x` to the unit value `()`
set _n    = _1      -- sets native `_n` to hold native `_1`
set tup.1 = n       -- changes the tuple index value
set ptr\  = v       -- dereferences pointer `ptr` and assigns `v`
```

## 1.4. Call, Input & Output

The `call`, `input` & `output` statements invoke function expressions:

```
call f ()           -- calls `f` passing ()
input std: _int     -- reads a native `_int` value from stdin
output std _10      -- outputs native `_10` to stdout
```

## 1.5. Sequence

A sequence of statements execute one after the other:

```
var x: _int                 -- first declares `x`
set x = input std: _int     -- then assigns input to `x`
output std x                -- finally outputs `x`
```

## 1.6. Conditional

An `if` tests an `_int` value and executes one of the branches depending on the
result:

```
if x {
    call f ()       -- calls `f` if `x` is nonzero
} else {
    call g ()       -- calls `g` otherwise
}
```

## 1.7. Repetition

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

## 1.8. Native

A native statement executes a block of code in the host language:

```
native _{
    printf("Hello World!");
}
```

## 1.9. Function

A function declaration abstracts a block of statements that can be invoked with
arguments afterwards.
The argument can be accessed through the identifier `arg`.
A `return` exits a function with a value:

```
set f = func () -> () {
    return arg      -- function receives and returns `()`
}
```

Note that a function declaration is actually an expression.

# 2. EXPRESSIONS

