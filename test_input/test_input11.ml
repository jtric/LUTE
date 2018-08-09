  val x : int ref = ref 0;
  val z : real ref = ref 2.0;
  val y : real list ref = ref [1.0];
  if ((!x < 4) and (!x > 3)) then
        y := !z :: [!z]
        else
        x := !x / 2;
if ((!x = 0) or (!z > 1.0)) then
        (x := 6 - 1; z := 2.1)
        else
        z := 1.0 * !z;