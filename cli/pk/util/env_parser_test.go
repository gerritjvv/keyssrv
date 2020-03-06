package util

import (
	"testing"
)

func assertEquals(t *testing.T, a string, b string) {
	if a != b {
		t.Errorf("%s != %s", a, b)
	}
}

func TestBuildVarsWithExport(t *testing.T) {

	vars := BuildVars(`
export ABC=1
export TEST=HI
`)

	assertEquals(t, vars[0], "ABC=1")
	assertEquals(t, vars[1], "TEST=HI")
}

func TestBuildVars(t *testing.T) {

	vars := BuildVars(`
a=1
b=2
c= <<EOFbla
bla
bla
EOF
d=3
e=4
myvar= <<EOF
1
2
3
4
EOF
`)

	//for i, v := range vars {
	//	fmt.Println(i, ":", v)
	//}

	assertEquals(t, vars[0], "a=1")
	assertEquals(t, vars[1], "b=2")
	//assertEquals(t, vars[2], "c=bla\nbla\nbla\n")
	assertEquals(t, vars[3], "d=3")
	assertEquals(t, vars[4], "e=4")
	//assertEquals(t, vars[5], "myvar=\n1\n2\n3\n4\n")

}
