let val x1 : int = 5; val ab: int list ref = ref [3]; val y : real ref = ref -1.67 in
        x1 = hd(!ab) + x1 * x1;  
        if hd(!ab) < 2 and not hd(!ab) > 3 then ab := [-2] else y := 3.1415;
        !ab
        end;