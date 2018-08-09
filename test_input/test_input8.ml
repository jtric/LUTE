let val sumlist : int list ref = ref [1,2,3,4,5];
    val sum : int ref = ref 0 in
        while not (!sumlist = []) do
                sum := !sum + hd(!sumlist);
        !sum
end;