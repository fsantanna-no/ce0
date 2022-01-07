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

The label `@global` corresponds to the outermost scope of the program.
The label `@local`  corresponds to the current scope.

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

The `call`, `input` & `output` statements invoke the respective operations:

```
call f _0           -- calls `f` passing `_0`
input std: _int     -- reads a native `_int` value from stdin
output std _10      -- outputs native `_10` to stdout
```

They are further documented as expressions.

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

A pointer can specify the scope in which it is attached:

```
/_int @myblock      -- pointer attached to `@myblock`
/[_int,()] @global  -- pointer attached to the global scope
```

If not specified, a pointer is attached to `@local` by default.

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

### Recursive Union Pointer

A recursive union is always a pointer with a caret subtype pointing upwards:

```
/<[_int, /^]>       -- a linked list of `_int`
```

The pointer caret `/^` indicates recursion and refers to the enclosing
recursive union pointer type.
Multiple `n` carets, e.g. `/^^`, refer to the `n` outer enclosing recursive
union pointer type.

The pointer caret can be expanded resulting in equivalent types:

```
/<[_int, /^]>           -- a linked list of `_int`
/<[_int, /<[_int,/^]>>  -- a linked list of `_int` expanded
```

## Function

`TODO: closure, scopes`

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

## Pointer Upref & Dnref

A pointer points to a variable holding a value.
An *upref* (up reference or reference) acquires a pointer to a variable with
the prefix slash `/`.
A *dnref* (down reference or dereference) recovers the value given a pointer
with the sufix backslash `\`:

```
var x: _int
var y: /_int
set y = /x          -- acquires a pointer to `x`
output std y\       -- recovers the value of `x`
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

A union constructor creates a value of a union type given a subcase index,
an argument, followed by the explicit complete union type:

```
<.1 ()>: <(),()>                -- subcase `.1` of `<(),()>` holds unit
<.2 [_10,_0]: <(),[_int,_int]>  -- subcase `.2` holds a tuple
```

A recursive union always includes a null pointer constructor `<.0 ()>` that
represents data termination:

```
var x: /<[_int,/^]>             -- a linked list of `_int`
set x = <.0 ()>: /<[_int,/^]>   -- empty linked list
```

The unit argument `()` of a unit subtype is optional:

```
<.1>: <(),()>
<.0>: /<[_int,/^]>
```

### Allocation

A recursive union constructor uses the `new` operation for dynamic allocation:

```
var z: /<[_int,/^]>
set z = <.0>: /<[_int,/^]>      -- null

var x: /<[_int,/^]>             -- 10 -> null
set x = new <.1 [_10, z]>>: <[_int,/<[_int,/^]>]>
```

The `new` operation receives a constructor of the plain type and returns a
pointer of the type as result of the allocation.
Note that unlike the result, the type of the constructor is not a pointer.
For this reason, the caret needs to expand to remain pointing to an enclosing
pointer.

### Discriminator

A discriminator accesses the value of a union type as one of its subcases.
A discriminator expression suffixes the value to access with an index and an
exclamation mark `!`:

```
var x: <(),_int>
... x!1                 -- yields ()

var y: /<[_int,/^]>
... x\!1.1              -- yields an `_int`
... x\!1.2\!0           -- yields ()
```

If the discriminated subcase does not match the actual value, the attempted
access raises a runtime error.

### Predicate

A predicate evaluates if the value of a union type is of the given subcase.
A predicate expression suffixes the value to test with an index and a question
mark `?`:

```
var x: <(),_int>
... x?1                 -- checks if `x` is subcase `1`

var y: /<[_int,/^]>
... x\?1                -- checks if list is not empty
```

The result of a predicate is an `_int` value (`_1` if success, `_0` otherwise)
to be compatible with conditional statements.

## Call

`TODO: scope inps/out`

A call invokes a function with the given argument:

```
call f ()               -- f   receives unit     ()
call (id) x             -- id  receives variable x
call add [x,y]          -- add receives tuple    [x,y]
```

## Input & Output

Input and output expressions communicate with external I/O devices.
The special device `std` works for the standard input & output device and
accepts any value as argument:

```
input std: _int         -- reads an `_int` from stdio
output std [_0,_0]      -- outputs "[0,0]" to stdio
```

An `output` receives the device to communicate and an argument.
The result is always `()`.
An `input` receives no argument but needs to specify the result type
explicitly.

`TODO: custom devices`

The host declarations for the I/O devices must prefix their identifiers with
`input_` or `output_`:

```
func output_xxx: XXX -> ()
{
    ...
}
```

## Function

`TODO`

# 4. LEXICAL RULES

## Comment

A comment starts with a double hyphen `--` and runs until the end of the line:

```
-- this is a single line comment
```

## Keywords and Symbols

The following keywords are reserved:

```
    break       -- escape loop statement
    call        -- function invocation
    else        -- conditional statement
    func        -- function declaration
    if          -- conditional statement
    input       -- input invocation
    loop        -- loop statement
    native      -- native statement
    new         -- new recursive operation
    output      -- output invocation
    return      -- function return
    set         -- assignment statement
    var         -- variable declaration
```

The following symbols are valid:

```
    {   }       -- block delimeter
    (   )       -- unit type, unit value, group type & expression
    [   ]       -- tuple delimiter
    <   >       -- union delimiter
    ;           -- sequence separator
    :           -- type and scope specification
    ->          -- function type signature
    =           -- variable assignment
    /           -- pointer type, upref operation
    \           -- dnref operation
    ,           -- tuple & union separator
    .           -- tuple discriminator, union constructor
    !           -- union discriminator
    ?           -- union predicate
    ^           -- recursive union
    @           -- scope identifier
```

## Variable Identifier

A variable identifier starts with a lowercase letter and might contain letters,
digits, and underscores:

```
i    myCounter    x_10          -- variable identifiers
```

## Scope Identifier

`TODO`

### Block

### Function Parameter

## Number

A number is a sequence of digits:

```
0    20
```

Numbers are used in tuple & union discriminators.

## Native Token

A native token starts with an underscore `_` and might contain letters,
digits, and underscores:

```
_char    _printf    _100        -- native identifiers
```

A native token may also be enclosed with curly braces `{` and `}` or
parenthesis `(` and `)`.
In this case, a native token can contain any other characters:

```
_(1 + 1)     _{2 * (1+1)}
```
