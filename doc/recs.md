# Ce - Recursive Data Types

*Ce* is simple language with algebraic data types, ownership semantics, and
scoped memory management.

- [Install & Use](../README.md)
- [Manual](manual.md)
- Recursive Data Types
- [Comparison with Rust](rust.md)

<!--
# A. Pools and recursive types

## Goals

Recursive types, such as lists and trees, require dynamic memory allocation
since their sizes are unbounded.

Pools are containers for mutually referred values of recursive types.
A pool is associated with the root variable of a recursive type.
When the root goes out of scope, all pool memory is automatically reclaimed.
A pool can be optionally declared with a bounded size, allowing it to be
allocated in the stack.
Pools enable to the following properties for recursive types:

- bounded memory allocation
- deterministic deallocation
- no garbage collection
-->

## Memory management

Recursive data types, such as lists and trees, rely on dynamic memory
allocation since they typically represent complex data structures that evolve
over time.
They are also manipulated by different parts of the program, even outside
the scope in which they are first instantiated.
These two characteristics, dynamic allocation and scope portability, need to be
managed by the language somehow.

*Ce* approaches recursive data types with support for algebraic data types with
ownership semantics and scoped memory management.
Allocation is bound to the scope of the assignee, i.e. a destination variable,
which is the owner of the value.
A value has exactly one owner at any given time.
Deallocation occurs automatically when the scope of the owner terminates.
Ownership can be transferred by reassigning the value to another assignee,
which can live in another scope.
Single ownership implies that recursive values can only form trees of
ownerships, but not generic DAGs (e.g., graphs with cycles).
In some situations, a value can be shared with a pointer without transferring
ownership, in which case generic graphs are possible.

*Ce* ensures that deallocation occurs exactly once at the very moment when
there are no more active pointers to the value.
In particular, *Ce* must prevent the following cases:

- Memory leaks: when a value cannot be reached but is not deallocated and remains in memory.
- Dangling references: when a value is deallocated but can still be reached (aka. *use-after-free*).
- Double frees: when a value is deallocated multiple times.

## Basics

*Ce* supports tuples (`[...]`) and tagged unions (`<...>`) to create
user-defined composite types:

```
var x1: <(), [_int,_int]> = <.1>    -- <.1 ()> is optional
var x2: <(), [_int,_int]> = <.2 [_4,_5]>
```

A value of type `<(), [_int,_int]>` is either an unit or a pair of two `_int`
values.
The value `x1` is tagged with the first variant `.1` and holds unit.
The value `x1` is tagged with the second variant `.2` and holds the pair
`[4,5]`.

These composite types are also known as *algebraic data types* because they are
composed of sums (unions) and products (tuples).

An union is recursive if one of its variants is the caret symbol `^`:

```
-- l = 10 -> 20 -> ()
var l: <[_int,^]> = new <.1 [_10, new <.1 [_20, <.0>]>]>
```

Recursive unions always have an implicit `.0` variant of type unit.
This way, the type of `l` represents a list which is either the empty variant
`.0` or a pair that holds an `_int` and a sublist.
Constructors of recursive variants require the operation modifier `new` to
indicate that a memory allocation is taking place.
The special `.0` unit constructor does not allocate memory and should not use
`new`.

A variable of a recursive union holds a *strong reference (ref)* to its value,
but not the actual value.
Constructors always dynamically allocate recursive values in the heap:

```
var l: <[_int,^]> = new <.1 [_1, <.0>]>
    ^                ^
    |              __|__
   / \            /     \
  | l |--------> |   1   | <-- actual allocated memory with the linked list
   \_/   ref     |  null | <-- .0 unit is the null terminator
    |             \_____/
    |                |
  stack             heap
```

The assigned variable is the owner of the allocated value, which is
automatically deallocated when the enclosing scope terminates:

```
{
    var l: <[_int,^]> = new <.1 [_1, <.0>]>
}
-- scope terminates, memory pointed by `l` is deallocated
```

## Pointers

A variable can be shared (or pointed, or borrowed) with the prefix backslash
`\`.
In this case, both the owner and the pointer refer to the same allocated value:

```
var x:  <[_int,^]> = new <.1 [_1, <.0>]>
var y: \<[_int,^]> = borrow \x
    ^                |
    |              __|__
   / \   ref      /     \
  | x |--------> |   1   | <-- actual allocated memory with the linked list
   \_/       /   |  null |
    |       /     \_____/
    |      /         |
  stack   /         heap
    |    / ptr
   / \  /
  | y |-
   \_/
```

Sharing a recursive value requires the explicit `borrow` operation modifier.
A pointer to a recursive value holds a *weak reference (ptr)* to its value and
distinguishes from a reference (ref) as follows:

- A pointer does not own the allocated value.
- A pointer requires [pointer operations](TODO) to manipulate the allocated value.

<!--
`TODO: pointers.md`
`general rules for mutation`
`mutation in description`

`type @rec`         <- sem-1: move, clone
`type @rec @ptr`    <- sem-2: growonly, no copy/move
`type @rec @imm`    <- sem-0: share w/ refs (no pointers)

- the three are incompatible
    - sem-1:
        - nao pode nem pegar ponteiro
        - a move would compromise internal pointers (sem-2)
        - a move could dealloc some (root) shared data leading to wild pointer (sem-3)
-->

## Mutation Modes

A recursive type that contains pointers to itself is classified as
*append only* (as opposed to the default *movable*):

```
type List_With_Tail {
    List_WT: (List, \List)  -- list + pointer to tail
}
```

Append-only values can only grow and cannot move its subparts:

```
var lt: List_With_Tail = List_WT (Item (1,Nil), ?)
set lt.List_WT!.2 = \lt.List_WT!.1  -- lt.1 is held in a pointer
set lt.List_WT!.1 = Nil             -- cannot free it or the pointer becomes dangling
```

## Ownership and Borrowing

*Ce* imposes that the ownership and borrowing of recursive data types adhere to
a set of rules as follows:

1. Every allocated constructor has exactly a single owner at any given time. Not zero, not two or more.
    - The owner is a variable that lives in the stack and reaches the allocated value.
2. When the owner goes out of scope, the allocated memory is automatically
   deallocated.
3. Ownership can be transferred in three ways:
    - Assigning the current owner to another variable, which becomes the new owner (e.g. `set new = old`).
    - Passing the owner to a function call argument, which becomes the new owner (e.g. `f(old)`).
    - Returning the owner from a function call to an assignee, which becomes the new owner (e.g. `set new = f()`).
4. (`growable`) The original owner is invalidated after transferring its ownership.
<!--
4. (`movable`) When transferring ownership, the original owner is automatically assigned its empty subcase.
-->
5. Ownership cannot be transferred to the current owner's subtree (e.g. `set x.1 = x`).
6. Ownership cannot be transferred with an active pointer to it in scope.
7. A pointer cannot escape or survive outside the scope of its owner.

All rules are verified at compile time, i.e., there are no runtime checks or
extra overheads.

### Ownership transfer

(`growable`)
As stated in rule 4, an ownership transfer invalidates the original owner and
rejects further accesses to it:

```
{
    var x: List = Item(1, Nil)  -- `x` is the original owner
    var y: List = x             -- `y` is the new owner
    ... x ...                   -- error: `x` cannot be referred again
    ... y ...                   -- ok
}
```

<!--
(`movable`)
As stated in rule 4, ownership transfer assigns an empty value to the original
owner:

```
{
    var x: List = Item(1, Nil)      -- `x` is the original owner
    var y: List = x                 -- `y` is the new owner
    ... x ...                       -- `x` now holds Nil
    ... y ...                       -- `y` holds `Item(1, Nil)`
}
```
-->

Ownership transfer ensures that rule 1 is preserved.
If ownership could be shared, deallocation in rule 2 would be ambiguous or
cause a double free, since owners could be in different scopes:

```
{
    var x: List = Item(1, Nil)  -- `x` is the original owner
    {
        var y: List = x         -- `y` is the new owner
    }                           -- deallocate here?
}                               -- or here or both?
```

Ownership transfer is particularly important when the value must survive the
allocation scope, which is typical of constructor functions:

```
func build: () -> List {
    var tmp: List = ...     -- `tmp` is the initial owner
    return tmp              -- `return` transfers ownership to outside
}                           --   (we don't want to deallocate it now)
var l: List = build()       -- `l` is the new owner
```

As stated in rule 5, the owner of a value cannot be transferred to its own
subtree.
This also takes into account pointers to the subtree.
This rule prevents cycles, which also ensures that rule 1 is preserved:

```
var l: List = Item Nil
var p: \List = \l.Item!     -- `p` points to the end of `l`
set p\ = l                  -- error: cannot transfer `l` to the end of itself
```

<!--
(`movable`)
It is possible to transfer only part of a recursive value.
In this case, the removed part will be automatically reset to the empty subcase:

```
var x: List = Item(1, Item(2, Nil)) -- after: Item(1,Nil)
var y: List = x.Item!               -- after: Item(2,Nil)
```
-->

Finally, it is also possible to make a "void transfer" through a `set`
statement. <!--(`movable` can also make it for subparts)-->
In this case, the value with lost ownership will be deallocated immediately:

```
var x: List = Item(1, Nil)  -- previous value
set x = Nil                 -- previous value is deallocated
```

## Borrowing

In many situations, transferring ownership is undesirable, such as when passing
a value to a narrower scope for temporary manipulation:

```
var l: List = ...       -- `l` is the owner
... length(\l) ...      -- `l` is borrowed to the call
... l ...               -- `l` is still the owner

func length: (\List -> Int) {
    ... -- use pointer to borrowed data
}
```

Rule 6 states that if there is an active pointer to a value, then its ownership
cannot be transferred:

```
var l: List = ...       -- owner
var x: \List = \l       -- active pointer
call g(l)               -- error: cannot transfer with active pointer
... x ...               -- use-after-free
```

This rule prevents that a transfer eventually deallocates a value that is still
reachable through an active pointer (i.e, *use-after-free*).
This rule implies that a pointer *dnref* can never be transferred because the
pointer must be pointing to some value, and hence is active:

```
var l: List = ...       -- owner
var x: \List = \l       -- active pointer
var y: List = x\        -- error: cannot transfer with active pointer
```

Rule 7 states that a pointer cannot escape or survive outside the scope of its
owner:

```
func f: () -> \List {
    var l: List = ...       -- `l` is the owner
    return \l               -- error: cannot return pointer to deallocated value
}
```

```
var x: \List = ...          -- outer scope
{
    var l2: List = ...
    set x = \l2             -- error: cannot hold pointer from inner scope
}
... x ...                   -- use-after-free
```

If surviving pointers were allowed, they would refer to deallocated values,
resulting in a dangling reference (i.e, *use-after-free*).

For the same reason, *Ce* disallows tuples or function arguments to hold
pointers to different scopes:

```
type Tp {
    Tp1: \Int
}
func f : (\Int,\Tp) -> () { -- 2nd argument (possibly pointing to wider scope)
    set arg.2\.Tp1! = arg.1 -- holds 1st argument (possibly pointing to narrower scope)
    return ()               -- this leads to a dangling reference
}
var i: Int = 10
var tp: Tp = Tp1 \i         -- wider scope
{
    var j: Int = 0          -- narrower scope
    call f (\j,\tp)         -- ERROR: passing pointers with different scopes
}
output std tp.Tp1!\         -- use of dangling reference
```

The call to `f` passes a tuple with two pointers, each pointing to a variable
in a different scope.
We might try to take the pointer with the wider scope and hold inside it the
pointer with narrower scope, as illustrated in the body of `f`.
In this case, the pointer with narrower scope will become dangling when its
scope terminates, but will still be reachable from the pointer to the wider
scope, as illustrated in the `output` call.
*Ce* refuses this program when we try to create the tuple with the two
pointers.

<!--
All dependencies of an assignment are tracked and all constructors are
allocated in the same pool.
When the pool itself goes out of scope, all allocated memory inside it is
traversed and is automatically reclaimed.

The pool may be declared with bounded size (e.g. `y[64]`), which limits the
number of nodes in the tree.
This allows the pool items to be allocated in the stack (instead of `malloc`).
When the pool goes out of scope, the stack unwinds and all memory is
automatically reclaimed.

Internally, the pool is forwarded to all constructors locations where the
actual allocations takes place:

```
void f (Pool* pool) {
    Nat* _2 = pool_alloc(pool, sizeof(Nat));    // constructors allocate
    Nat* _1 = pool_alloc(pool, sizeof(Nat));    // in the forwarded pool
    *_2 = (Nat) { Succ, {._Succ=NULL} };
    *_1 = (Nat) { Succ, {._Succ=_2} };
    x = _1;
    return x;
}

Nat  _yv[64];               // stack-allocated buffer (if bounded)
Pool _yp = { _yv, 64, 0 };  // buffer, max size, cur size
Nat* y = f(&_yp);           // pool is NULL if unbounded
```

!--
If the pool is unbouded (e.g. `y[]`), all allocation is made in the heap with
`malloc`.
Then, when the root reference (e.g. `y`) goes out of scope, it is traversed to
`free` all memory.
--

## Details

### Pool allocation

A bounded pool is defined internally as follows:

```
typedef struct {
    void* buf;      // stack-allocated buffer
    int   max;      // maximum size
    int   cur;      // current size
} Pool;
```

Pool allocation depends if the pool is bounded or unbounded:

```
void* pool_alloc (Pool* pool, int n) {
    if (pool == NULL) {                     // pool is unbounded
        return malloc(n);                   // fall back to `malloc`
    } else {
        void* ret = &pool->buf[pool->cur];
        pool->cur += n;                     // nodes are allocated sequentially
        if (pool->cur <= cur->max) {
            return ret;
        } else {
            return NULL;
        }
    }
}
```

--

A dynamic constructor must check if all allocations succeeded.

Illustrative example:

```
func f: () -> Nat {
    var x: Nat = Succ(Succ(Nil))
    return x
}
var y[]: Nat = f()    -- y[] or y[N]
```

Generated code:

```
void f (Pool* pool) {
    int _cur = pool->cur;                       // current pool size
    Nat* _2 = pool_alloc(pool, sizeof(Nat));
    Nat* _1 = pool_alloc(pool, sizeof(Nat));
    if (_2==NULL || _1==NULL) {                 // one of them failed
        if (pool == NULL) {
            free(_1);                           // free both
            free(_2);
        } else {
            pool->cur = _cur;                   // restore pool size
        }
    } else {
        *_2 = (Nat) { Succ, {._Succ=NULL} };    // assign both
        *_1 = (Nat) { Succ, {._Succ=_2} };
        x = _1;                                 // root value
    }
}
```

### Pool deallocation

- stack
- __cleanup__

### Tracking assignments

1. Check the root assignment for dependencies in nested scopes:

```
var y: Nat = Succ(Succ(Nil))    -- same scope: static allocation
```

```
var y[]: Nat = f()              -- body of `f` is nested: pool allocation
```

2. Check `return` of body for dependencies:

```
return x                        -- check `x`
```

```
var x: Nat = Succ(Succ(Nil))    -- constructor must be allocated in the received pool
```

### TODO

```
-- OK
call show(Succ(Nil))        -- ok stack

-- ERR
-- `f` returns `Nat` but have no pool to allocated it
-- if call returns isrec, it must be in an assignment or in a return (to use pool from outside)
func f: () -> Nat {}
call f()                    -- missing pool for return of "f"

-- OK
var three: Nat = ...
func fthree: () -> Nat {
    return three            -- should not use pool b/c defined outside
}
```

-->
