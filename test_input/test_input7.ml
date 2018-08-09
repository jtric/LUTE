let val a : int ref = ref 1
in
  while (!a < 2 and 3 > 1) do a := 4; 
  !a
end;