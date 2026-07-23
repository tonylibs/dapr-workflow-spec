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
			},
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
