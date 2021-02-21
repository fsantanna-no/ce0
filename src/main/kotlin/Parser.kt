sealed class Type (val isptr: Boolean) {
    class Type_Any  (isptr: Boolean):             Type(isptr)
    class Type_Unit (isptr: Boolean):             Type(isptr)
    class Type_Nat  (isptr: Boolean, val tk: Tk): Type(isptr)
}

fun parser_type (all: All): Type? {
    all.err_expected("type")
    return null
}