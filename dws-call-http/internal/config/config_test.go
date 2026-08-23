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
			name:    "missing endpoint fails",
			env:     map[string]string{},
			wantErr: true,
		},
		{
			name: "defaults applied",
			env:  map[string]string{"ENDPOINT": "https://svc/x"},
			check: func(t *testing.T, c Config) {
				if c.Port != "8080" {
					t.Errorf("port: got %q, want 8080", c.Port)
				}
				if c.Method != "POST" {
					t.Errorf("method: got %q, want POST", c.Method)
				}
				if c.Task != "call-http" {
					t.Errorf("task: got %q, want call-http", c.Task)
				}
				if c.BodyMode != BodyPassthrough {
					t.Errorf("bodyMode: got %q, want passthrough", c.BodyMode)
				}
				if c.Output != OutputReplace {
					t.Errorf("output: got %q, want replace", c.Output)
				}
				if c.Timeout != 30*time.Second {
					t.Errorf("timeout: got %s, want 30s", c.Timeout)
				}
				if c.InsecureSkipVerify {
					t.Errorf("insecureSkipVerify: got true, want false")
				}
				if c.Auth.Scheme != AuthNone {
					t.Errorf("auth scheme: got %q, want none", c.Auth.Scheme)
				}
			},
		},
		{
			name: "basic authentication reads secret-backed credentials",
			env: map[string]string{
				"ENDPOINT":      "https://svc/x",
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
				"ENDPOINT":    "https://svc/x",
				"AUTH_SCHEME": "bearer",
				"AUTH_TOKEN":  "token-value",
			},
			check: func(t *testing.T, c Config) {
				if c.Auth.Scheme != AuthBearer || c.Auth.Token != "token-value" {
					t.Errorf("auth: got %+v, want bearer token", c.Auth)
				}
			},
		},
		{
			name: "oauth authentication defaults sidecar port",
			env: map[string]string{
				"ENDPOINT":       "https://svc/x",
				"AUTH_SCHEME":    "oauth2",
				"OAUTH_ENDPOINT": "workflow-oauth-inventory",
			},
			check: func(t *testing.T, c Config) {
				if c.Auth.Scheme != AuthOAuth2 || c.Auth.OAuthEndpoint != "workflow-oauth-inventory" || c.Auth.DaprHTTPPort != "3500" {
					t.Errorf("auth: got %+v, want oauth2 endpoint and port", c.Auth)
				}
			},
		},
		{
			name: "basic authentication requires both credentials",
			env: map[string]string{
				"ENDPOINT":      "https://svc/x",
				"AUTH_SCHEME":   "basic",
				"AUTH_USERNAME": "alice",
			},
			wantErr: true,
		},
		{
			name: "bearer authentication requires token",
			env: map[string]string{
				"ENDPOINT":    "https://svc/x",
				"AUTH_SCHEME": "bearer",
			},
			wantErr: true,
		},
		{
			name: "oauth authentication requires endpoint",
			env: map[string]string{
				"ENDPOINT":    "https://svc/x",
				"AUTH_SCHEME": "oauth2",
			},
			wantErr: true,
		},
		{
			name: "unknown authentication scheme fails",
			env: map[string]string{
				"ENDPOINT":    "https://svc/x",
				"AUTH_SCHEME": "digest",
			},
			wantErr: true,
		},
		{
			name: "method uppercased",
			env:  map[string]string{"ENDPOINT": "https://svc/x", "METHOD": "get"},
			check: func(t *testing.T, c Config) {
				if c.Method != "GET" {
					t.Errorf("method: got %q, want GET", c.Method)
				}
			},
		},
		{
			name: "headers and query parsed",
			env: map[string]string{
				"ENDPOINT": "https://svc/x",
				"HEADERS":  `{"X-Api-Key":"k"}`,
				"QUERY":    `{"region":"{region}"}`,
			},
			check: func(t *testing.T, c Config) {
				if c.Headers["X-Api-Key"] != "k" {
					t.Errorf("headers not parsed: %v", c.Headers)
				}
				if c.Query["region"] != "{region}" {
					t.Errorf("query not parsed: %v", c.Query)
				}
			},
		},
		{
			name:    "invalid headers json fails",
			env:     map[string]string{"ENDPOINT": "https://svc/x", "HEADERS": "not-json"},
			wantErr: true,
		},
		{
			name:    "invalid body mode fails",
			env:     map[string]string{"ENDPOINT": "https://svc/x", "BODY_MODE": "bogus"},
			wantErr: true,
		},
		{
			name:    "template mode without template fails",
			env:     map[string]string{"ENDPOINT": "https://svc/x", "BODY_MODE": "template"},
			wantErr: true,
		},
		{
			name: "template mode with template ok",
			env: map[string]string{
				"ENDPOINT":      "https://svc/x",
				"BODY_MODE":     "template",
				"BODY_TEMPLATE": `{"id":"{orderId}"}`,
			},
			check: func(t *testing.T, c Config) {
				if c.BodyMode != BodyTemplate {
					t.Errorf("bodyMode: got %q", c.BodyMode)
				}
				if c.BodyTemplate == "" {
					t.Errorf("bodyTemplate empty")
				}
			},
		},
		{
			name:    "invalid output fails",
			env:     map[string]string{"ENDPOINT": "https://svc/x", "OUTPUT": "bogus"},
			wantErr: true,
		},
		{
			name:    "invalid timeout fails",
			env:     map[string]string{"ENDPOINT": "https://svc/x", "TIMEOUT": "later"},
			wantErr: true,
		},
		{
			name:    "non-positive timeout fails",
			env:     map[string]string{"ENDPOINT": "https://svc/x", "TIMEOUT": "0s"},
			wantErr: true,
		},
		{
			name: "timeout and insecure parsed",
			env: map[string]string{
				"ENDPOINT":             "https://svc/x",
				"TIMEOUT":              "5s",
				"INSECURE_SKIP_VERIFY": "true",
			},
			check: func(t *testing.T, c Config) {
				if c.Timeout != 5*time.Second {
					t.Errorf("timeout: got %s, want 5s", c.Timeout)
				}
				if !c.InsecureSkipVerify {
					t.Errorf("insecureSkipVerify: got false, want true")
				}
			},
		},
		{
			name:    "invalid insecure bool fails",
			env:     map[string]string{"ENDPOINT": "https://svc/x", "INSECURE_SKIP_VERIFY": "maybe"},
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
