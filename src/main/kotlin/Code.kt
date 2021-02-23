fun code_expr (e: Expr): String {
    return when (e) {
        is Expr.Unit -> ""
        is Expr.Var  -> (e.tk.pay as TK_Str).v
        is Expr.Nat  -> (e.tk.pay as TK_Str).v
        else -> error("TODO")
    }
}