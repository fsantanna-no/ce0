sealed class Type (val isptr: Boolean) {
    class Any  (isptr: Boolean):             Type(isptr)
    class Unit (isptr: Boolean):             Type(isptr)
    class Nat  (isptr: Boolean, val tk: Tk): Type(isptr)
}

fun All.accept (enu: TK, chr: Char? = null): Boolean {
    return when {
        (this.tk1.enu != enu) -> false
        (chr == null)         -> true
        else -> (this.tk1.pay as TK_Chr).v == chr
    }
}

fun parser_type (all: All): Type? {
    val isptr = false
    return when {
        all.accept(TK.UNIT) -> Type.Unit(isptr)
        else -> { all.err_expected("type") ; null }
    }
}