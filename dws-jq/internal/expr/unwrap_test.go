package expr

import "testing"

func TestUnwrap(t *testing.T) {
	tests := []struct {
		name string
		in   string
		want string
	}{
		{name: "wrapped", in: "${ .a }", want: ".a"},
		{name: "wrapped with outer spaces", in: "  ${.a}  ", want: ".a"},
		{name: "bare", in: ".a", want: ".a"},
		{name: "wrapped object", in: "${ { a: 1 } }", want: "{ a: 1 }"},
		{name: "two wrappers take outermost", in: "${ .a } + ${ .b }", want: ".a } + ${ .b"},
		{name: "unterminated stays bare", in: "${ .a", want: "${ .a"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := Unwrap(tt.in); got != tt.want {
				t.Errorf("Unwrap(%q) = %q, want %q", tt.in, got, tt.want)
			}
		})
	}
}
