  let val a : int ref = ref 1 in
while (!a < 20) do
(if (!a < 10) then
        if (!a > 5) then a := !a + 2 else a := !a * 2
        else a := !a + 3);
a
end;