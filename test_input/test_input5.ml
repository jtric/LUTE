let val a : real ref = ref 2.0;  val b : real ref = ref 3.0
in
  a := !a + !b;
  b := !a * !a;
  b
end;