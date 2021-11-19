- typically, cannot hold pointers to recursive data b/c o dangling pointers
    x = \y!1
    y = ...
    -- now x is pointing to trash

- if struct contains relative pointer, then it can hold, but struct cannot shrink
    <? [<(),\^^^>,^^]>

- must use hold instead of borrow
    - I cannnot "borrow" myself
    - A borrow would not allow pointer to escape or data be manipulated after borrow
