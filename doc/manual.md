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

# 5. SYNTAX

```
Stmt ::= `var´ VAR `:´ Type                 -- variable declaration     var x: () = ()
            `=´ ([X] Expr | `?´)
      |  `type´ [`@rec´ [`@ptr`]] USER `{`  -- user type declaration    type @rec List {
            { USER `:´ Type [`;´] }         --    subcases                 Cons: List
         `}´                                                        }
      |  `type´ `@pre´ `@rec` [`@ptr`] USER -- type pre declaration     type @pre @rec List
      |  `set´ Expr `=´ [X] Expr            -- assignment               set x = 1
      |  (`call´ | `input´ |` output´)      -- call                     call f()
            (VAR|NAT) [Expr]                -- input & output           input std ; output std 10
      |  `if´ Expr `{´ Stmt `}´             -- conditional              if x { call f() } else { call g() }
         [`else´ `{´ Stmt `}´]
      |  `loop´ `{´ Stmt `}´                -- loop                     loop { break }
      |  `break´                            -- break                    break
      |  `func´ VAR `:´ Type `{´            -- function                 func f : ()->() { return () }
            Stmt
         `}´
      |  `return´ [[X] Expr]                -- function return          return ()
      |  { Stmt [`;´] }                     -- sequence                 call f() ; call g()
      |  `{´ Stmt `}´                       -- block                    { call f() ; call g() }
      |  `native´ [`pre´] `{´ ... `}´       -- native                   native { printf("hi"); }

Expr ::= `(´ `)´                            -- unit value               ()
      |  NAT                                -- native expression        _printf
      |  VAR                                -- variable identifier      i
      |  `\´ Expr                           -- upref                    \x
      |  `/´ Expr                           -- dnref                    /x
      |  `[´ [X] Expr {`,´ [X] Expr} `]´    -- tuple constructor        [x,()]
      |  `<´ `.´ NUM [[X] Expr] `>´         -- union constructor        <.1 ()>
      |  [`call´ | `input´ | `output´]      -- call                     f(x)
            (VAR|NAT) [[X] Expr]            -- input & output           input std ; output std 10
      |  Expr `.´ NUM                       -- tuple discriminator      x.1
      |  Expr `!´ NUM                       -- union discriminator      x!1
      |  Expr `?´ NUM                       -- union predicate          x?0
      |  `(´ Expr `)´                       -- group                    (x)

X ::= `borrow´ | `copy´ | `move´ | `new´

Type ::= `(´ `)´                            -- unit                     ()
      |  NAT                                -- native type              _char
      | `^` { `^` }                         -- recursive type           ^
      |  `\` Type                           -- pointer                  \_int
      |  `[´ Type {`,´ Type} `]´            -- tuple                    [(),()]
      |  `<´ Type {`,´ Type} `>´            -- union                    <^,()>
      |  Type `->´ Type                     -- function                 () -> ()
```
