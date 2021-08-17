# Ce - Manual

*Ce* is simple language with algebraic data types, ownership semantics, and
scoped memory management.

- [Install & Use](../README.md)
- Manual
    1. [Lexical rules](TODO)
    2. [Types](TODO)
    3. [Expressions](TODO)
    4. [Statements](TODO)
    5. [Syntax](TODO)
- [Recursive Data Types](recs.md)
- [Comparison with Rust](rust.md)

# 1. LEXICAL RULES

## Comments

A comment starts with a double hyphen `--` and runs until the end of the line:

```
-- this is a single line comment
```

## Keywords and Symbols

The following keywords are reserved:

```
    arg         -- function argument
    borrow      -- borrow recursive operation
    break       -- escape loop statement
    call        -- function invocation
    copy        -- copy recursive operation
    else        -- conditional statement
    func        -- function declaration
    if          -- conditional statement
    input       -- input invocation
    loop        -- loop statement
    move        -- move recursive operation
    native      -- native statement
    new         -- new recursive operation
    output      -- output invocation
    return      -- function return
    set         -- assignment statement
    std         -- standard I/O function
    var         -- variable declaration
```

The following symbols are valid:

```
    {   }       -- block delimeter
    (   )       -- unit type, unit value, group type & expression
    [   ]       -- tuple delimiter
    <   >       -- union delimiter
    ;           -- sequence separator
    :           -- variable, type, function declaration
    ->          -- function type signature
    =           -- variable assignment
    ,           -- tuple & union separator
    .           -- tuple index, union index predicate & discriminator
    \           -- pointer type, upref & dnref operation
    ^           -- recursive union, outermost scope
    !           -- union discriminator
    ?           -- union predicate, unknown initialization
```

## Identifiers

A variable identifier starts with a lowercase letter and might contain letters,
digits, and underscores:

```
i    myCounter    x_10          -- variable identifiers
```

## Indexes

An tuple/union index is a dot `.` followed by sequence of digits:

```
.0    .20                       -- tuple/union indexes
```

## Native tokens

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

# 2. TYPES

## Unit

The unit type `()` only has the [single value](TODO) `()`.

## Native

A native type holds external [values from the host language](TODO), i.e.,
values which *Ce* does not create or manipulate directly.

Native type identifiers follow the rules for [native tokens](TODO):

```
_char     _int    _{FILE*}
```

## Composite Types

A composite type is created from other types through tuples and unions.

### Tuple

A tuple type holds a value for each of its subtypes.
A tuple type identifier is a comma-separated list of types enclosed with
brackets `[` and `]`:

```
[(),(),())          -- a triple of unit types
[(),[_int,()]]      -- a pair containing another pair
```

### Union

A union type holds a value of one of its subtypes.
A tuple type identifier is a comma-separated list of types enclosed with
angle brackets `<` and `>`:

```
<(),(),()>          -- a union of three unit types
<(),[_int,()]>      -- a union of unit and a pair
<[_int,^]>          -- a recursive union
```

The caret subtype `^` indicates recursion and refers to the enclosing union
type.
A recursive union always includes an implicit unit subcase at its index `.0`.

`TODO: multiple ^`

## Function

A function type holds a [function](TODO) value and is composed of an input and
output types separated by an arrow `->`:

```
() -> _int          -- input is unit and output is Tree
(_int,_int) -> ()   -- input is a pair and output is unit
```

## Pointers

A pointer type can be applied to any other type with the prefix backslash `\`
and holds a pointer to another value:

```
\_int           -- pointer to _int
\(_int,())      -- pointer to tuple
```

# 3. EXPRESSIONS

## Unit

The unit value is the single value of the [unit type](TODO):

```
()
```

## Native expressions

A native expression holds a value of a [host language type](TODO):

```
_printf    _(2+2)     _{f(x,y)}
```

Symbols defined in *Ce* can also be accessed inside native expressions:

```
var x: Int = 10
output std _(x + 10)    -- outputs 20
```

## Variables

A variable holds a value of its [type](TODO):

```
i    myCounter    x_10
```

## Composite Expressions

### Tuples and Indexes

A tuple holds a fixed number of values of a compound [tuple type](TODO):

```
((),False)              -- a pair with () and False
(x,(),y)                -- a triple
```

A tuple index suffixes a tuple with a dot `.` and evaluates to the value at the
given position:

```
(x,()).2                -- yields ()
```

### Union Constructors, Discriminators & Predicates

#### Constructors

A constructor creates a value of a [union type](TODO) given a subcase index and
an argument:

```
.1 ()                   -- subcase 1 holds unit
.0                      -- () is optional
.2 [_10,.0]             -- subcase 2 holds a tuple
```

#### Discriminators

A discriminator accesses the value of a [union type](TODO) as one of its
subcases.
A discriminator expression suffixes the value to access with an index and an
exclamation mark `!`:

```
(.1 ()).1!              -- yields ()

x = .1 [.0,(),.0]
... x.1!.2              -- yields ()
... x.0!                -- error: `x` is a `.1`
```

If the discriminated subcase does not match the actual value, the attempted
access raises a runtime error.

#### Predicates

A predicate checks if the value of a [union type](TODO) is of the given
subcase.
A predicate expression suffixes the value to test with an index and a question
mark `?`:

```
(.1 ()).0?              -- yields 0
.0.0?                   -- yields 1
```

`TODO: bool=native int`

## Calls, Input & Output

A call invokes a [function](TODO) with the given argument:

```
f ()                    -- f   receives unit     ()
call (id) x             -- id  receives variable x
add (x,y)               -- add receives tuple    (x,y)
```

The prefix keyword `call` can appear on assignments and statements, but not in
the middle of expressions.

Just like a `call`, the `input` & `output` keywords also invoke functions, but
with the purpose of communicating with external I/O devices:

```
input libsdl Delay 2000            -- waits 2 seconds
output libsdl Draw Pixel (0,0)     -- draws a pixel on the screen
```

The supported I/O functions and associated behaviors depend on the
platform in use.
The special device `std` works for the standard input & output device and
accepts any value as argument:

```
var x: Int = input std      -- reads an `Int` from stdin (`TODO: not implemented`)
output std x                -- writes the value of `x` to stdout
```

The `input` & `output` expressions can appear on assignments and statements,
but not in the middle of expressions.

The declarations for the I/O functions must prefix their identifiers with
`input_` or `output_`:

```
func output_libsdl: IO_Sdl -> ()
{
    ...
}
```

## Recursive clone & move

The special operation `clone` makes a deep copy of its argument of
recursive type:

```
var y: List = List.Item List.Item List.Nil
var x: List = clone y    -- `x` becomes "Item Item Nil"
```

`TODO: move, types of both`
`TODO: borrow`

## Pointer uprefs & dnrefs

A pointer points to a variable holding a value.
An *upref* (up reference) acquires a pointer to a variable with the prefix
backslash `\`.
A *dnref* (down reference) recovers the value given a pointer with the sufix
backslash `\`:

```
var x: Int = 10
var y: \Int = \x    -- acquires a pointer to `x`
output std y\       -- recovers the value of `x`
```

# 4. STATEMENTS

## Variable declarations

A variable declaration introduces a name of the given type and assigns a value
to it:

```
var x : () = ()                                -- `x` of type `()` holds `()`
var y : Bool = Bool.True                       -- `y` of type `Bool` holds `True`
var z : (Bool,()) = (Bool.False,())            -- `z` of tuple type holds tuple
var n : List = List.Cons(List.Cons(List.Nil))  -- `n` of type `List` holds result of constructor
var u : Int = ?                                -- `x` of type `Int` is not initialized
var p : ^ \Int = ?                             -- `p` of type `\Int` in outermost scope
```

The assignment can be a question mark `?`, which keeps the variable uninitialized.

The type of a pointer declaration can be prefixed with a caret `^` to indicate
that it is bound to the function outermost scope.

## Assignments

An assignment changes the value of a variable, native identifier, tuple index,
or discriminator:

```
set x = ()
set _n = 1
set tup.1 = n
set x.Student! = ()
```

## Calls, Input & Output

The `call`, `input` & `output` statements invoke [functions](#TODO):

```
call f()        -- calls f passing ()
input std       -- input from stdin
output std 10   -- output to stdout
```

## Sequences

Statements execute one after the other and can be separated by semicolons:

```
call f() ; call g()     -- executes f, g, and h in order
call h()
```

## Conditionals

A conditional tests a `Bool` value and executes one of its true or false
branches depending on the test:

```
if x {
    call f()    -- calls f if x is True
} else {
    call g()    -- calls g if x is False
}
```

## Loops

A `loop` executes a block of statements indefinitely until it reaches a `break`
statement:

```
loop {
    ...         -- repeats this command indefinitely
    if ... {    -- until this condition is met
        break
    }
}
```

## Functions, Arguments and Returns

A function declaration binds a block of statements to a name which can be
[invoked](TODO) afterwards.
The declaration also determines the [function type](TODO) with the argument and
return types.
The argument can be accessed through the identifier `arg`.
A `return` exits a function with a value:

```
func f : () -> ()
{
    return arg
}
```

## Blocks

A block delimits, between curly braces `{` and `}`, the scope and visibility of
[variables](TODO):

```
{
    var x: () = ()
    ... x ...           -- `x` is visible here
}
... x ...               -- `x` is not visible here
```

## Native statements

A native statement executes a [native token](TODO) in the host language:

```
native _{
    printf("Hello World!");
}
```

The modifier `pre` makes the native block to be included before the main
program:

```
native pre _{
    #include <math.h>
}
```

# 5. SYNTAX

```
Stmt ::= `var´ VAR `:´ Type                 -- variable declaration     var x: () = ()
            `=´ (Expr | `?´)
      |  `type´ [`@rec´ [`@ptr`]] USER `{`  -- user type declaration    type @rec List {
            { USER `:´ Type [`;´] }         --    subcases                 Cons: List
         `}´                                                        }
      |  `type´ `@pre´ `@rec` [`@ptr`] USER -- type pre declaration     type @pre @rec List
      |  `set´ Expr `=´ Expr                -- assignment               set x = 1
      |  (`call´ | `input´ |` output´)      -- call                     call f()
            (VAR|NAT) [Expr]                -- input & output           input std ; output std 10
      |  `if´ Expr `{´ Stmt `}´             -- conditional              if x { call f() } else { call g() }
         [`else´ `{´ Stmt `}´]
      |  `loop´ `{´ Stmt `}´                -- loop                     loop { break }
      |  `break´                            -- break                    break
      |  `func´ VAR `:´ Type `{´            -- function                 func f : ()->() { return () }
            Stmt
         `}´
      |  `return´ [Expr]                    -- function return          return ()
      |  { Stmt [`;´] }                     -- sequence                 call f() ; call g()
      |  `{´ Stmt `}´                       -- block                    { call f() ; call g() }
      |  `native´ [`pre´] `{´ ... `}´       -- native                   native { printf("hi"); }

Expr ::= `(´ `)´                            -- unit value               ()
      |  NAT                                -- native expression        _printf
      |  VAR                                -- variable identifier      i
      |  `\´ Expr                           -- upref                    \x
      |  Expr `\´                           -- dnref                    x\
      |  `(´ Expr {`,´ Expr} `)´            -- tuple                    (x,())
      |  USER.USER [Expr]                   -- constructor              True ()
      |  [`call´ | `input´ | `output´]      -- call                     f(x)
            (VAR|NAT) [Expr]                -- input & output           input std ; output std 10
      |  Expr `.´ NUM                       -- tuple index              x.1
      |  Expr `.´ USER `!´                  -- discriminator            x.True!
      |  Expr `.´ USER `?´                  -- predicate                x.Nil?
      |  `(´ Expr `)´                       -- group                    (x)

Type ::= `(´ `)´                            -- unit                     ()
      |  NAT                                -- native type              _char
      |  USER                               -- user type                Bool
      |  `(´ Type {`,´ Type} `)´            -- tuple                    ((),())
      |  Type `->´ Type                     -- function                 () -> ()
      |  `\` Type                           -- pointer                  \Int
```
