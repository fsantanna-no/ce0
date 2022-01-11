```
var table_types: MutableMap<String, Pair<(All)->Boolean,(All)->Type>> = mutableMapOf (
"Unit" to Pair({ it.accept(TK.UNIT) }, {Type.Unit(it.tk0 as Tk.Sym)})
)
```

```
type Player [
    name: _(char*),
    age:  _int,
    sub:  <
        Warrior: [mana:_int, guild:_(char*)],
        Mage: [...],
    >
]

val x: Player.Warrior = ["Arthur", 32, [10,"arcane"]]
```
