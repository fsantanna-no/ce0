# Ce0 - Core

*Ce* is a simple language with algebraic data types, pointers, first-class
functions, and region-based memory management.
The main goal of *Ce* is to support safe memory management for dynamically
allocated data structures.

An allocated data is always attached to a specific block and cannot move.
When a block terminates, all attached allocations are automatically released.
This prevents memory leaks.
A pointer is also attached to a specific block and cannot point to data
allocated in nested blocks.
This prevents dangling pointer dereferencing.
These ideas have been successfully adopted in Cyclone:
https://cyclone.thelanguage.org/

*Ce0* is the most basic core version of *Ce* with no extensions (syntax sugar,
type inference, etc).

See also *Ce1*: https://github.com/fsantanna/ce1

# INSTALL & RUN

```
$ sudo make install
$ vi x.ce   # output std ()
$ ce0 x.ce
()
$
```

# MANUAL

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

A block may contain an uppercase label to identify its memory region:

```
{ @MYBLOCK          -- `@MYBLOCK` can be referenced in allocations
    ...
}
```

The label `@GLOBAL` corresponds to the outermost block of the program.
The label `@LOCAL`  corresponds to the current block.

## Variable Declaration

A variable declaration introduces an identifier of the given type in the
current block:

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
input std (): _int  -- reads a native `_int` value from stdin
output std _10      -- outputs native `_10` to stdout
```

They are further documented as expressions.

## Sequence

A sequence of statements separated by blanks or semicolons `;` execute one
after the other:

```
var x: _int                 -- first declares `x`
set x = input std (): _int  -- then assigns `_int` input to `x`
output std x                -- finally outputs `x`
```

## Conditional

An `if` tests an `_int` value and executes one of the *true* or *false*
branches depending on the result:

```
if x {
    -- true branch
    call f ()       -- calls `f` if `x` is nonzero
} else {
    -- false branch
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
        break       -- escapes the loop
    }
}
```

## Native

A native statement executes a block of code in the host language *C*:

```
native _{
    printf("Hello World!");
}
```

## Function

A function declaration abstracts a block of statements that can be invoked with
arguments.
The argument can be accessed through the identifier `arg`.
The result can be assigned to the identifier `ret`.
The `return` statement exits a function::

```
set f = func () -> () {
    set ret = arg   -- assigns arg to the result
    return          -- exits function
}
```

Function declarations are further documented as expressions, since they  are
actually `func` expressions assigned to variables.

# 2. TYPES

## Unit

The unit type `()` represents absence of information and has only the single
unit value `()`.

## Native

A native type holds external values from *C*, i.e., values which *Ce* does
not create or manipulate directly.
A native type identifier always starts with an underscore `_`:

```
_char     _int    _{FILE*}
```

## Pointer

A pointer type holds a pointer to another value and can be applied to
any other type with the prefix slash `/`.
A pointer must also specify the block in which its pointed data is held:

```
/_int @LOCAL        -- a pointer to an `_int` held in then current block
/[_int,()] @S       -- a pointer to a tuple held in block `@S`
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

### Recursive Union Pointer

A recursive union is a pointer with a caret subtype pointing upwards:

```
/<[_int, /^@S]>@S   -- a linked list of `_int` held at block `@S`
```

The pointer caret `/^` indicates recursion and refers to the enclosing
recursive union type.
Multiple `n` carets, e.g. `/^^`, refer to the `n` outer enclosing recursive
union pointer type.

The pointer caret can be expanded resulting in equivalent types:

```
/<[_int, /^@S]>@S               -- a linked list of `_int`
/<[_int, /<[_int,/^@S]>@S>@S    -- a linked list of `_int` expanded
```

## Function

`TODO: closure, blocks scopes`

A function type holds a function value and is composed of the prefix `func`
and input and output types separated by an arrow `->`:

```
func () -> _int          -- input is unit and output is `_int`
func [_int,_int] -> ()   -- input is a pair of `_int` and output is unit
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
set x = _10         -- variable `x` holds native `_10`
output std x
```

## Native

A native expression holds a value from *C*.
The expression must specify its type with a colon `:` sufix:

```
_(2+2): _int            -- _(2+2) has type _int
_{f(x,y)}: _(char*)     -- f returns a C string
```

Symbols defined in *Ce* can also be accessed inside native expressions:

```
var x: _int
set x = _10
output std _(x + 10)    -- outputs 20
```

## Pointer Upref & Dnref

A pointer points to a variable holding a value.
An *upref* (*up reference* or *reference*) acquires a pointer to a variable
with the prefix slash `/`.
A *dnref* (*down reference* or *dereference*) recovers a pointed value
given a pointer with the sufix backslash `\`:

```
var x: _int
var y: /_int@LOCAL
set y = /x          -- acquires a pointer to `x`
output std y\       -- recovers the value of `x`
```

## Tuple: Constructor and Discriminator

### Constructor

A tuple holds a fixed number of values:

```
[(),_10]            -- a pair with `()` and native `_10`
[x,(),y]            -- a triple
```

### Discriminator

A tuple discriminator suffixes a tuple with a dot `.` and an numeric
index to evaluate the value at the given position:

```
var tup: [(),_int]
set tup = [(),_10]
output std tup.2    -- outputs `10`
```

## Union: Constructor, Allocation, Discriminator & Predicate

### Constructor

A union constructor creates a value of a union type given a subcase index,
an argument, followed by a colon `:` with the explicit complete union type:

```
<.1 ()>: <(),()>                -- subcase `.1` of `<(),()>` holds unit
<.2 [_10,_0]: <(),[_int,_int]>  -- subcase `.2` holds a tuple
```

### Null Pointer Constructor

A recursive union always includes a null pointer constructor `<.0>` that
represents data termination.
The null constructor must also include a colon sufix `:` with the explicit
complete union type: 

```
var x: /<[_int,/^@S]>@S         -- a linked list of `_int`
set x = <.0>: /<[_int,/^@S]>@S  -- an empty linked list
```

### Allocation

A recursive union constructor uses the `new` operation for dynamic allocation.
It returns a pointer of the type as result of the allocation.
It receives a constructor of the plain type sufixed by a colon `:` with the
block to allocate the data:

```
var z: /</^@S>@S
set z = <.0>: /</^@S>@S             -- null

var x: /</^@S>@S
set x = new (<.1 z>:</^@S>): @S     -- () -> null, allocated in block `@S`
```

### Discriminator

A union discriminator suffixes a union with an exclamation `!` and a
numeric index to access the value as one of its subcases:

```
var x: <(),_int>
... x!1                     -- yields ()

var y: /<[_int,/^@S]>@S
... x\!1.1                  -- yields an `_int`
... x\!1.2\!0               -- yields ()
```

If the discriminated subcase does not match the actual value, the attempted
access raises a runtime error.

### Predicate

A union predicate suffixes a union with a question `?` and a
numeric index to check if the value is of the given subcase:

```
var x: <(),_int>
... x?1                     -- checks if `x` is subcase `1`

var y: /<[_int,/^@S]>@S
... x\?1                    -- checks if list is not empty
```

The result of a predicate is an `_int` value (`_1` if success, `_0` otherwise)
to be compatible with conditional statements.

## Call

A call invokes a function with the given argument:

```
call f ()               -- f   receives unit     ()
call (id) x             -- id  receives variable x
call add [x,y]          -- add receives tuple    [x,y]
```

Calls may also specify blocks for pointer input and output:

```
call f {@S} ptr: @LOCAL     -- calls `f` passing `ptr` at `@S` and return at `@LOCAL`
```

Pointer inputs go in between brackets `{` and `}` before the argument.
Pointer output goes after a colon `:` suffix after the argument.

Calls are further documented with functions.

## Input & Output

Input and output expressions communicate with external I/O devices.
They receive a device to communicate, and an argument to pass to the device:

```
input std (): _int      -- reads an `_int` from stdio
output std [_0,_0]      -- outputs "[0,0]" to stdio
```

An `output` always evaluates to unit `()`.
An `input` evaluates to the explicit type that must be given.

The special device `std` works for the standard input & output device and
accepts any value as argument.

`TODO: input std should only accept :_(char*)`
`TODO: custom devices`

*C* declarations for the I/O devices must prefix their identifiers with
`input_` or `output_`:

```
void output_xxx: (XXX v) {
    ...
}
```

## Function

`TODO`

# 4. LEXICAL RULES

## Comment

A comment starts with a double hyphen `--` and ignores everything
until the end of the line:

```
-- this is a single line comment
```

## Keywords and Symbols

The following keywords are reserved:

```
    break       -- escape loop statement
    call        -- function invocation
    else        -- conditional statement
    func        -- function type
    if          -- conditional statement
    input       -- input invocation
    loop        -- loop statement
    native      -- native statement
    new         -- allocation operation
    output      -- output invocation
    return      -- function return
    set         -- assignment statement
    var         -- variable declaration
```

The following symbols are valid:

```
    {   }       -- block delimeter, block labels
    (   )       -- unit type, unit value, group type & expression
    [   ]       -- tuple delimiter
    <   >       -- union delimiter
    ;           -- sequence separator
    :           -- type and block specification
    ->          -- function type signature
    =           -- variable assignment
    /           -- pointer type, upref operation
    \           -- dnref operation
    ,           -- tuple & union separator
    .           -- tuple discriminator, union constructor
    !           -- union discriminator
    ?           -- union predicate
    ^           -- recursive union
    @           -- block label
```

## Variable Identifier

A variable identifier starts with a lowercase letter and might contain letters,
digits, and underscores:

```
i    myCounter    x_10          -- variable identifiers
```

## Block Label

A constant block label starts with at `@` and contains only uppercase letters.
A parameter block label starts with at `@` and contains only lowercase letters
with an option numeric suffix:

```
@GLOBAL    @MYBLOCK    @a    @a1
```

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

# 5. SYNTAX

```
Stmt ::= { Stmt [`;`] }                             -- sequence                 call f() ; call g()
         `{´ BLOCK Stmt `}´                         -- block                    { @A call f() ; call g() }
      |  `var´ VAR `:´ Type                         -- variable declaration     var x: ()
      |  `set´ Expr `=´ Expr                        -- assignment               set x = _1
      |  `native´ NAT                               -- native                   native _{ printf("hi"); }
      |  (`call´ | `input´ |`output´) ...           -- call, input, output      call f ()
                                                    -- (see in Expr)
      |  `if´ Expr `{´ Stmt `}´ `else´ `{´ Stmt `}´ -- conditional              if x { ... } else { ... }
      |  `loop´ `{´ Stmt `}´                        -- loop                     loop { ... }
      |  `break´                                    -- loop break               break
      |  `return´                                   -- function return          return

Expr ::= `(´ Expr `)´                               -- group                    (x)
      |  `(´ `)´                                    -- unit                     ()
      |  NAT `:´ Type                               -- native expression        _10: _int
      |  VAR                                        -- variable identifier      i
      |  `/´ Expr                                   -- upref                    /x
      |  Expr `\´                                   -- dnref                    x\
      |  `[´ Expr {`,´ Expr} `]´                    -- tuple constructor        [x,()]
      |  Expr `.´ NUM                               -- tuple discriminator      x.1
      |  `<´ `.´ NUM Expr `>´ `:´ Type              -- union constructor        <.1 ()>: <(),()>
      |  `<´ `.´ 0 `>´ `:´ Type                     -- union null pointer       <.0>: /</?>
      |  `new´ Expr.Union `:´ BLOCK                 -- union allocation         new <...>: @LOCAL
      |  Expr `!´ NUM                               -- union discriminator      x!1
      |  Expr `?´ NUM                               -- union predicate          x?0
      |  `input´ VAR Expr `:´ Type                  -- data input               input std (): _int
      |  `output´ VAR Expr                          -- data output              output std [(),_10]
      |  `call´ Expr Blocks Expr [`:´ BLOCK]        -- function call            call f {@S} x: @LOCAL
      |  Type.Func [Upvals] Stmt.Block              -- function body            func ()->() { ... }
            Upvals ::= `[´ VAR {`,´ VAR} `]´

Type ::= `(´ Type `)´                               -- group                    (func ()->())
      |  `(´ `)´                                    -- unit                     ()
      |  NAT                                        -- native type              _char
      |  `^´ { `^´ }                                -- recursive type           ^^
      |  `/´ Type BLOCK                             -- pointer                  /_int@S
      |  `[´ Type {`,´ Type} `]´                    -- tuple                    [(),()]
      |  `<´ Type {`,´ Type} `>´                    -- union                    <(),/^@S>
      |  `func´ [BLOCK] Blocks `->´ Type `->´ Type  -- function                 func f : ()->() { return () }

Blocks ::= `{´ [BLOCK {`,´ BLOCK}] `}´              -- list of scopes           {@LOCAL,@a1}
```

# A. Memory Management

*Ce* relies on the concept of hierarchical blocks to manage memory.
*Ce* guarantees that the scope and lifetime of a given piece of data
coincide and are always attached to a single fixed block.
The scope refers to the visibility of the data, i.e., the ability to
refer to it directly or indirectly through identifiers or pointers.
The lifetime refers to the memory allocation, i.e., the period in
which the data remains in memory.
When the scope and lifetime coincide, access to memory is always safe,
and programs prevent memory leaks and dangling pointer dereferencing.

In the next example, a variable `x` that holds an integer is attached
to an explicit block:

```
{
    var x: _int     -- scope and lifetime of `x` is attached to enclosing block
}
-- `x` is no longer in memory (lifetime), but neither can be referred (scope)
```

A pointer in *Ce* must statically specify a block, which restricts the data
it can point to.
A pointer can only point to data that is attached to a block that lives at
least the same as the block specified in the pointer.
*Ce* verifies that the pointer assignments respect this rule at compile time:

```
{ @A
    var ptrA: /_int @A          -- `ptrA` is restricted to @A
    { @B
        var x: _int             -- `x` is attached to @B
        var ptrB: /_int @B      -- `ptrB` is restricted to @B
        { @C
            var ptrC: /_int @C  -- `ptrC` is restricted to @C
            set ptrA = /x       -- ERROR: `ptrA` lives longer than @B 
            set ptrB = /x       -- OK: `ptrB` lives the same as @B
            set ptrC = /x       -- OK: `ptrC` lives shorter than @B
            ... ptrC\ ...       -- OK: dereference ok
        }
        ... ptrB\ ...           -- OK: dereference ok
    }
    ... ptrA\ ...               -- ERROR: dangling dereference if error was not detected  
}
```

Just like with pointers, a dynamic allocation operation with `new` must also
statically specify a block to which the data becomes attached.
The operation returns a pointer with the same block of the allocation.
*Ce* verifies if the same rules for pointer assignments apply to dynamic data:

```
{ @A
    -- cannot assign `x` to any pointer with blocks outside @B
    { @B
        var x: /</^@A>@A
        set x = new <.1 <.0>:T1>:T2: @A  -- newly allocated data is attached to @A
                                         -- (T1 & T2 are the ommited types of the constructors)                                         
        { @C
            -- can assign `x` to any pointer with blocks inside @B
        }
    }
}
```

`TODO: functions, calls, closures`
