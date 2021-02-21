sealed class Type (val isptr: Boolean) {
    data class Any  (val x: Boolean):             Type(x)
    data class Unit (val x: Boolean):             Type(x)
    data class Nat  (val x: Boolean, val tk: Tk): Type(x)
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
        all.accept(TK.XNAT) -> Type.Nat(isptr, all.tk0)
        else -> { all.err_expected("type") ; null }
    }
}