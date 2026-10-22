package basicauth

import "testing"

func TestMatch(t *testing.T) {
	cfg := &Config{Endpoint: "pets", Username: "alice", Password: "s3cret"}
	tests := []struct {
		name string
		uri  string
		want bool
	}{
		// Matches.
		{"method path with sub-path", "/v1.0/invoke/pets/method/v1/pets/42", true},
		{"bare method path", "/v1.0/invoke/pets/method", true},
		{"method path with trailing slash", "/v1.0/invoke/pets/method/", true},
		{"query string is ignored", "/v1.0/invoke/pets/method/search?q=a", true},
		{"query string on the bare method path", "/v1.0/invoke/pets/method?q=a", true},
		{"dots inside a query string are ignored", "/v1.0/invoke/pets/method/x?p=../..", true},
		{"dots inside a segment name are not a dot segment", "/v1.0/invoke/pets/method/a..b/.hidden/..foo/bar.", true},
		{"percent-encoded text that is not a dot", "/v1.0/invoke/pets/method/%2a/%2e%2ex/x%2e", true},

		// Endpoint boundary.
		{"endpoint name prefix", "/v1.0/invoke/pets-v2/method/x", false},
		{"endpoint name suffix", "/v1.0/invoke/mypets/method/x", false},
		{"another endpoint", "/v1.0/invoke/orders/method/x", false},
		{"endpoint is case sensitive", "/v1.0/invoke/Pets/method/x", false},
		{"percent-encoded endpoint is not decoded", "/v1.0/invoke/p%65ts/method/x", false},
		{"method boundary: methods", "/v1.0/invoke/pets/methods", false},
		{"method boundary: methodx", "/v1.0/invoke/pets/method-x/y", false},
		{"no method segment", "/v1.0/invoke/pets", false},
		{"no method segment with slash", "/v1.0/invoke/pets/", false},
		{"endpoint only in the query", "/v1.0/invoke/orders/method/x?next=/v1.0/invoke/pets/method/y", false},

		// Non-invocation paths.
		{"state API", "/v1.0/state/store", false},
		{"healthz", "/healthz", false},
		{"root", "/", false},
		{"empty", "", false},
		{"no leading slash", "v1.0/invoke/pets/method/x", false},
		{"wrong version prefix", "/v1.1/invoke/pets/method/x", false},
		{"upper-case prefix", "/V1.0/invoke/pets/method/x", false},
		{"nested prefix", "/x/v1.0/invoke/pets/method/x", false},

		// Dot segments, raw and percent-encoded in any case.
		{"raw .. then another endpoint", "/v1.0/invoke/pets/method/../../orders/method/x", false},
		{"encoded .. then another endpoint", "/v1.0/invoke/pets/method/%2e%2e/%2e%2e/orders/method/x", false},
		{"upper-case encoded ..", "/v1.0/invoke/pets/method/%2E%2E/%2E%2E/orders/method/x", false},
		{"mixed-case encoded ..", "/v1.0/invoke/pets/method/%2e%2E/x", false},
		{"half-encoded ..", "/v1.0/invoke/pets/method/.%2e/x", false},
		{"half-encoded .. the other way", "/v1.0/invoke/pets/method/%2e./x", false},
		{"raw . segment", "/v1.0/invoke/pets/method/./x", false},
		{"encoded . segment", "/v1.0/invoke/pets/method/%2e/x", false},
		{"trailing ..", "/v1.0/invoke/pets/method/x/..", false},
		{"trailing encoded ..", "/v1.0/invoke/pets/method/x/%2e%2e", false},
		{"trailing .", "/v1.0/invoke/pets/method/.", false},
		{"dot segment before the method", "/v1.0/invoke/pets/../orders/method/x", false},
		{"dot segment with query", "/v1.0/invoke/pets/method/../x?q=a", false},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := cfg.Match(tt.uri); got != tt.want {
				t.Errorf("Match(%q) = %v, want %v", tt.uri, got, tt.want)
			}
		})
	}
}

func TestMatchUsesTheConfiguredEndpoint(t *testing.T) {
	cfg := &Config{Endpoint: "pets-v2"}
	if !cfg.Match("/v1.0/invoke/pets-v2/method/x") {
		t.Error("pets-v2 should match its own path")
	}
	if cfg.Match("/v1.0/invoke/pets/method/x") {
		t.Error("pets-v2 must not match pets")
	}
}
