package config

import (
	"testing"
	"time"
)

func TestLoad(t *testing.T) {
	tests := []struct {
		name    string
		env     map[string]string
		wantErr bool
		check   func(t *testing.T, c Config)
	}{
		{
			name:    "missing service addr fails",
			env:     map[string]string{"METHOD": "pkg.Svc/M"},
			wantErr: true,
		},
		{
			name:    "missing method fails",
			env:     map[string]string{"SERVICE_ADDR": "svc:50051"},
			wantErr: true,
		},
		{
			name: "defaults applied",
			env:  map[string]string{"SERVICE_ADDR": "svc:50051", "METHOD": "greeter.Greeter/SayHello"},
			check: func(t *testing.T, c Config) {
				if c.Port != "8080" {
					t.Errorf("port: got %q, want 8080", c.Port)
				}
				if c.Task != "call-grpc" {
					t.Errorf("task: got %q, want call-grpc", c.Task)
				}
				if c.Service != "greeter.Greeter" || c.Method != "SayHello" {
					t.Errorf("method parse: got %q/%q, want greeter.Greeter/SayHello", c.Service, c.Method)
				}
				if c.Output != OutputReplace {
					t.Errorf("output: got %q, want replace", c.Output)
				}
				if c.Timeout != 30*time.Second {
					t.Errorf("timeout: got %s, want 30s", c.Timeout)
				}
				if c.TLS {
					t.Errorf("tls: got true, want false (h2c default)")
				}
				if c.InsecureSkipVerify {
					t.Errorf("insecureSkipVerify: got true, want false")
				}
				if c.Auth.Scheme != AuthNone {
					t.Errorf("auth scheme: got %q, want none", c.Auth.Scheme)
				}
				if c.ProtoEndpoint != "" {
					t.Errorf("protoEndpoint: got %q, want empty (reflection)", c.ProtoEndpoint)
				}
			},
		},
		{
			name: "leading slash method tolerated",
			env:  map[string]string{"SERVICE_ADDR": "svc:50051", "METHOD": "/greeter.Greeter/SayHello"},
			check: func(t *testing.T, c Config) {
				if c.Service != "greeter.Greeter" || c.Method != "SayHello" {
					t.Errorf("method parse: got %q/%q", c.Service, c.Method)
				}
			},
		},
		{
			name:    "method without slash fails",
			env:     map[string]string{"SERVICE_ADDR": "svc:50051", "METHOD": "greeter.Greeter.SayHello"},
			wantErr: true,
		},
		{
			name:    "method with non-qualified service fails",
			env:     map[string]string{"SERVICE_ADDR": "svc:50051", "METHOD": "Greeter/SayHello"},
			wantErr: true,
		},
		{
			name:    "method with empty method name fails",
			env:     map[string]string{"SERVICE_ADDR": "svc:50051", "METHOD": "greeter.Greeter/"},
			wantErr: true,
		},
		{
			name: "proto endpoint and sha pinned",
			env: map[string]string{
				"SERVICE_ADDR":   "svc:50051",
				"METHOD":         "greeter.Greeter/SayHello",
				"PROTO_ENDPOINT": "https://cfg/greeter.binpb",
				"PROTO_SHA256":   "ABCDEF",
			},
			check: func(t *testing.T, c Config) {
				if c.ProtoEndpoint != "https://cfg/greeter.binpb" {
					t.Errorf("protoEndpoint: got %q", c.ProtoEndpoint)
				}
				if c.ProtoSHA256 != "abcdef" {
					t.Errorf("protoSha256 not lowercased: got %q", c.ProtoSHA256)
				}
			},
		},
		{
			name: "tls and insecure parsed",
			env: map[string]string{
				"SERVICE_ADDR":         "svc:50051",
				"METHOD":               "greeter.Greeter/SayHello",
				"TLS":                  "true",
				"INSECURE_SKIP_VERIFY": "true",
			},
			check: func(t *testing.T, c Config) {
				if !c.TLS || !c.InsecureSkipVerify {
					t.Errorf("tls/insecure: got %v/%v, want true/true", c.TLS, c.InsecureSkipVerify)
				}
			},
		},
		{
			name: "basic authentication reads secret-backed credentials",
			env: map[string]string{
				"SERVICE_ADDR":  "svc:50051",
				"METHOD":        "greeter.Greeter/SayHello",
				"AUTH_SCHEME":   "basic",
				"AUTH_USERNAME": "alice",
				"AUTH_PASSWORD": "pw",
			},
			check: func(t *testing.T, c Config) {
				if c.Auth.Scheme != AuthBasic || c.Auth.Username != "alice" || c.Auth.Password != "pw" {
					t.Errorf("auth: got %+v, want basic alice credentials", c.Auth)
				}
			},
		},
		{
			name: "bearer authentication reads secret-backed token",
			env: map[string]string{
				"SERVICE_ADDR": "svc:50051",
				"METHOD":       "greeter.Greeter/SayHello",
				"AUTH_SCHEME":  "bearer",
				"AUTH_TOKEN":   "token-value",
			},
			check: func(t *testing.T, c Config) {
				if c.Auth.Scheme != AuthBearer || c.Auth.Token != "token-value" {
					t.Errorf("auth: got %+v, want bearer token", c.Auth)
				}
			},
		},
		{
			name: "basic authentication requires both credentials",
			env: map[string]string{
				"SERVICE_ADDR":  "svc:50051",
				"METHOD":        "greeter.Greeter/SayHello",
				"AUTH_SCHEME":   "basic",
				"AUTH_USERNAME": "alice",
			},
			wantErr: true,
		},
		{
			name: "bearer authentication requires token",
			env: map[string]string{
				"SERVICE_ADDR": "svc:50051",
				"METHOD":       "greeter.Greeter/SayHello",
				"AUTH_SCHEME":  "bearer",
			},
			wantErr: true,
		},
		{
			name: "oauth2 is rejected for grpc",
			env: map[string]string{
				"SERVICE_ADDR": "svc:50051",
				"METHOD":       "greeter.Greeter/SayHello",
				"AUTH_SCHEME":  "oauth2",
			},
			wantErr: true,
		},
		{
			name: "unknown authentication scheme fails",
			env: map[string]string{
				"SERVICE_ADDR": "svc:50051",
				"METHOD":       "greeter.Greeter/SayHello",
				"AUTH_SCHEME":  "digest",
			},
			wantErr: true,
		},
		{
			name:    "invalid output fails",
			env:     map[string]string{"SERVICE_ADDR": "svc:50051", "METHOD": "greeter.Greeter/SayHello", "OUTPUT": "bogus"},
			wantErr: true,
		},
		{
			name:    "invalid timeout fails",
			env:     map[string]string{"SERVICE_ADDR": "svc:50051", "METHOD": "greeter.Greeter/SayHello", "TIMEOUT": "later"},
			wantErr: true,
		},
		{
			name:    "non-positive timeout fails",
			env:     map[string]string{"SERVICE_ADDR": "svc:50051", "METHOD": "greeter.Greeter/SayHello", "TIMEOUT": "0s"},
			wantErr: true,
		},
		{
			name:    "invalid tls bool fails",
			env:     map[string]string{"SERVICE_ADDR": "svc:50051", "METHOD": "greeter.Greeter/SayHello", "TLS": "maybe"},
			wantErr: true,
		},
	}

	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			for k, v := range tc.env {
				t.Setenv(k, v)
			}

			c, err := Load()
			if tc.wantErr {
				if err == nil {
					t.Fatalf("expected error, got config %+v", c)
				}
				return
			}
			if err != nil {
				t.Fatalf("unexpected error: %v", err)
			}
			if tc.check != nil {
				tc.check(t, c)
			}
		})
	}
}
