import java.io.PushbackReader

fun blanks (all: All) {
    while (true) {
        val c1 = all.inp.read()
        when (c1.toChar()) {
            '\n' -> {   // ignore new lines
                all.lin += 1
                all.col = 1
            }
            ' ' -> {    // ignore new spaces
                all.col += 1
            }
            '-' -> {
                val c2 = all.inp.read()
                if (c2.toChar() == '-') {            // ignore comments
                    all.col += 1
                    all.col += 1
                    while (true) {
                        val c3 = all.inp.read()
                        if (c3 == -1) {              // EOF stops comment
                            break
                        }
                        if (c3.toChar() == '\n') {   // LN stops comment
                            all.inp.unread(c3)
                            break
                        }
                        all.col += 1
                    }
                } else {
                    all.inp.unread(c2)
                    all.inp.unread(c1)
                    return
                }
            }
            else -> {
                all.inp.unread(c1)
                return
            }
        }
    }
}