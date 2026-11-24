package expr

import (
	"context"
	"math"
	"math/big"
	"reflect"
	"testing"
	"time"
)

func TestDecodeEncode(t *testing.T) {
	t.Run("large integers survive a round trip through evaluation", func(t *testing.T) {
		in, err := DecodeJSON([]byte(`9007199254740993`))
		if err != nil {
			t.Fatalf("DecodeJSON() error = %v", err)
		}
		switch v := in.(type) {
		case int:
			if int64(v) != 9007199254740993 {
				t.Fatalf("DecodeJSON() = %d", v)
			}
		case *big.Int:
			if v.Cmp(big.NewInt(9007199254740993)) != 0 {
				t.Fatalf("DecodeJSON() = %s", v)
			}
		default:
			t.Fatalf("DecodeJSON() type = %T, want int or *big.Int", in)
		}
		p, err := Compile(`. + 1`, nil)
		if err != nil {
			t.Fatalf("Compile() error = %v", err)
		}
		res, err := p.Evaluate(context.Background(), time.Second, Input{Data: in})
		if err != nil {
			t.Fatalf("Evaluate() error = %v", err)
		}
		out, err := Encode(res.Value)
		if err != nil {
			t.Fatalf("Encode() error = %v", err)
		}
		if string(out) != "9007199254740994" {
			t.Errorf("Encode() = %s, want 9007199254740994", out)
		}
	})

	t.Run("numbers beyond int64 decode to big.Int and encode unchanged", func(t *testing.T) {
		in, err := DecodeJSON([]byte(`123456789012345678901234567890`))
		if err != nil {
			t.Fatalf("DecodeJSON() error = %v", err)
		}
		if _, ok := in.(*big.Int); !ok {
			t.Fatalf("DecodeJSON() type = %T, want *big.Int", in)
		}
		out, err := Encode(in)
		if err != nil {
			t.Fatalf("Encode() error = %v", err)
		}
		if string(out) != "123456789012345678901234567890" {
			t.Errorf("Encode() = %s", out)
		}
	})
}

func TestDecodeJSON(t *testing.T) {
	tests := []struct {
		name string
		in   string
		want any
	}{
		{name: "int", in: `1`, want: 1},
		{name: "float", in: `1.5`, want: 1.5},
		{name: "exponent", in: `1e2`, want: 100.0},
		{name: "string", in: `"x"`, want: "x"},
		{name: "null", in: `null`, want: nil},
		{name: "bool", in: `true`, want: true},
		{
			name: "nested", in: `{"a":[1,{"b":2.5}],"c":null}`,
			want: map[string]any{"a": []any{1, map[string]any{"b": 2.5}}, "c": nil},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, err := DecodeJSON([]byte(tt.in))
			if err != nil {
				t.Fatalf("DecodeJSON(%s) error = %v", tt.in, err)
			}
			if !reflect.DeepEqual(got, tt.want) {
				t.Errorf("DecodeJSON(%s) = %#v, want %#v", tt.in, got, tt.want)
			}
		})
	}
}

func TestDecodeJSON_invalid(t *testing.T) {
	for _, in := range []string{``, `{`, `1 2`, `{"a":1} x`} {
		if _, err := DecodeJSON([]byte(in)); err == nil {
			t.Errorf("DecodeJSON(%q) error = nil, want error", in)
		}
	}
}

func TestEncode(t *testing.T) {
	tests := []struct {
		name string
		in   any
		want string
	}{
		{name: "NaN becomes null", in: math.NaN(), want: `null`},
		{name: "+Inf is clamped", in: math.Inf(1), want: `1.7976931348623157e+308`},
		{name: "-Inf is clamped", in: math.Inf(-1), want: `-1.7976931348623157e+308`},
		{name: "keys are sorted", in: map[string]any{"b": 1, "a": 2}, want: `{"a":2,"b":1}`},
		{
			name: "non-finite floats nested", in: map[string]any{"x": []any{math.NaN(), math.Inf(1)}},
			want: `{"x":[null,1.7976931348623157e+308]}`,
		},
		{name: "html is not escaped", in: "<a&b>", want: `"<a&b>"`},
		{name: "U+2028 is emitted raw", in: "a b", want: "\"a b\""},
		{name: "DEL is escaped", in: "a\x7fb", want: `"a\u007fb"`},
		{name: "control characters are escaped", in: "\b\f\x01", want: `"\b\f\u0001"`},
		{name: "nil", in: nil, want: `null`},
		{name: "float", in: 1.5, want: `1.5`},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, err := Encode(tt.in)
			if err != nil {
				t.Fatalf("Encode() error = %v", err)
			}
			if string(got) != tt.want {
				t.Errorf("Encode() = %s, want %s", got, tt.want)
			}
		})
	}
}

func TestEncode_rejectsValuesOutsideTheJQModel(t *testing.T) {
	if _, err := Encode(struct{}{}); err == nil {
		t.Error("Encode(struct{}{}) error = nil, want error")
	}
}
