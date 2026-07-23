package runner

import "testing"

func TestInterpolate(t *testing.T) {
	tests := []struct {
		name    string
		tmpl    string
		data    map[string]any
		want    string
		wantErr bool
	}{
		{
			name: "single string placeholder",
			tmpl: "https://svc/orders/{orderId}",
			data: map[string]any{"orderId": "abc"},
			want: "https://svc/orders/abc",
		},
		{
			name: "integer number renders without decimal",
			tmpl: "https://svc/orders/{orderId}",
			data: map[string]any{"orderId": float64(42)},
			want: "https://svc/orders/42",
		},
		{
			name: "float renders without trailing zeros",
			tmpl: "amount={amount}",
			data: map[string]any{"amount": 12.5},
			want: "amount=12.5",
		},
		{
			name: "boolean placeholder",
			tmpl: "flag={active}",
			data: map[string]any{"active": true},
			want: "flag=true",
		},
		{
			name: "multiple placeholders",
			tmpl: "https://svc/{region}/orders/{orderId}",
			data: map[string]any{"region": "eu", "orderId": "x1"},
			want: "https://svc/eu/orders/x1",
		},
		{
			name: "no placeholders passes through",
			tmpl: "https://svc/health",
			data: map[string]any{},
			want: "https://svc/health",
		},
		{
			name:    "missing key errors",
			tmpl:    "https://svc/orders/{orderId}",
			data:    map[string]any{"other": "v"},
			wantErr: true,
		},
		{
			name:    "reports all missing keys",
			tmpl:    "{a}/{b}",
			data:    map[string]any{},
			wantErr: true,
		},
	}

	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			got, err := Interpolate(tc.tmpl, tc.data)
			if tc.wantErr {
				if err == nil {
					t.Fatalf("expected error, got nil (result %q)", got)
				}
				return
			}
			if err != nil {
				t.Fatalf("unexpected error: %v", err)
			}
			if got != tc.want {
				t.Fatalf("got %q, want %q", got, tc.want)
			}
		})
	}
}
