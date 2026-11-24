package config

import (
	"encoding/json"
	"errors"
	"os"
	"path/filepath"
	"reflect"
	"sort"
	"testing"
	"time"
)

// clearEnv blanks every name Load reads so a developer's shell cannot leak
// into a test. t.Setenv restores the original values afterwards.
func clearEnv(t *testing.T) {
	t.Helper()
	for _, name := range Names {
		t.Setenv(name, "")
	}
}

func TestLoad_defaults(t *testing.T) {
	clearEnv(t)
	t.Setenv("JQ_EXPRESSION", ".")

	got, err := Load()
	if err != nil {
		t.Fatalf("Load() error = %v", err)
	}
	want := Config{
		Expression:   ".",
		Variables:    []string{},
		Timeout:      5 * time.Second,
		Site:         "dws-jq",
		Port:         "8080",
		DaprHTTPPort: "3500",
	}
	if !reflect.DeepEqual(got, want) {
		t.Errorf("got %#v, want %#v", got, want)
	}
}

func TestLoad_variables(t *testing.T) {
	tests := []struct {
		name string
		raw  string
		want []string
	}{
		{name: "order is kept", raw: `["context","item","index"]`, want: []string{"context", "item", "index"}},
		{name: "underscore and digits", raw: `["_a","b_1"]`, want: []string{"_a", "b_1"}},
		{name: "empty array", raw: `[]`, want: []string{}},
		{name: "whitespace around array", raw: ` ["item"] `, want: []string{"item"}},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			clearEnv(t)
			t.Setenv("JQ_EXPRESSION", ".")
			t.Setenv("JQ_VARIABLES", tt.raw)

			got, err := Load()
			if err != nil {
				t.Fatalf("Load() error = %v", err)
			}
			if !reflect.DeepEqual(got.Variables, tt.want) {
				t.Errorf("got %#v, want %#v", got.Variables, tt.want)
			}
		})
	}
}

func TestLoad_rejects(t *testing.T) {
	tests := []struct {
		name     string
		env      map[string]string
		wantName string
	}{
		{name: "expression unset", env: nil, wantName: "JQ_EXPRESSION"},
		{name: "expression whitespace", env: map[string]string{"JQ_EXPRESSION": "  \t\n"}, wantName: "JQ_EXPRESSION"},
		{name: "variables object", env: map[string]string{"JQ_VARIABLES": `{"a":1}`}, wantName: "JQ_VARIABLES"},
		{name: "variables numbers", env: map[string]string{"JQ_VARIABLES": `[1]`}, wantName: "JQ_VARIABLES"},
		{name: "variables not json", env: map[string]string{"JQ_VARIABLES": `item`}, wantName: "JQ_VARIABLES"},
		{name: "variables null", env: map[string]string{"JQ_VARIABLES": `null`}, wantName: "JQ_VARIABLES"},
		{name: "variables duplicate", env: map[string]string{"JQ_VARIABLES": `["item","item"]`}, wantName: "JQ_VARIABLES"},
		{name: "variables leading digit", env: map[string]string{"JQ_VARIABLES": `["1x"]`}, wantName: "JQ_VARIABLES"},
		{name: "variables empty name", env: map[string]string{"JQ_VARIABLES": `[""]`}, wantName: "JQ_VARIABLES"},
		{name: "variables reserved secrets", env: map[string]string{"JQ_VARIABLES": `["secrets"]`}, wantName: "JQ_VARIABLES"},
		{name: "variables reserved ENV", env: map[string]string{"JQ_VARIABLES": `["ENV"]`}, wantName: "JQ_VARIABLES"},
		{name: "variables reserved __loc__", env: map[string]string{"JQ_VARIABLES": `["__loc__"]`}, wantName: "JQ_VARIABLES"},
		{name: "variables with dollar", env: map[string]string{"JQ_VARIABLES": `["$item"]`}, wantName: "JQ_VARIABLES"},
		{name: "timeout zero", env: map[string]string{"JQ_TIMEOUT": "0s"}, wantName: "JQ_TIMEOUT"},
		{name: "timeout negative", env: map[string]string{"JQ_TIMEOUT": "-1s"}, wantName: "JQ_TIMEOUT"},
		{name: "timeout not a duration", env: map[string]string{"JQ_TIMEOUT": "soon"}, wantName: "JQ_TIMEOUT"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			clearEnv(t)
			if _, ok := tt.env["JQ_EXPRESSION"]; !ok && tt.wantName != "JQ_EXPRESSION" {
				t.Setenv("JQ_EXPRESSION", ".")
			}
			for k, v := range tt.env {
				t.Setenv(k, v)
			}

			_, err := Load()
			var fe *FieldError
			if !errors.As(err, &fe) {
				t.Fatalf("Load() error = %v, want *FieldError", err)
			}
			if fe.Name != tt.wantName {
				t.Errorf("FieldError.Name = %q, want %q", fe.Name, tt.wantName)
			}
			if fe.Reason == "" {
				t.Errorf("FieldError.Reason is empty")
			}
		})
	}
}

func TestFieldError_Error(t *testing.T) {
	got := (&FieldError{Name: "JQ_TIMEOUT", Reason: "must be positive"}).Error()
	if want := "JQ_TIMEOUT: must be positive"; got != want {
		t.Errorf("got %q, want %q", got, want)
	}
}

func TestConfigContract(t *testing.T) {
	tests := []struct {
		fixture string
		want    Config
	}{
		{
			fixture: "env-minimal.json",
			want: Config{
				Expression:   "${ .count > 0 }",
				Variables:    []string{},
				Timeout:      5 * time.Second,
				Site:         "dws-jq",
				Port:         "8080",
				DaprHTTPPort: "3500",
			},
		},
		{
			fixture: "env-variables.json",
			want: Config{
				Expression:   "$item < 3",
				Variables:    []string{"context", "item", "index"},
				Timeout:      250 * time.Millisecond,
				Site:         "processItems.jq.while",
				Port:         "8080",
				DaprHTTPPort: "3500",
				LogFormat:    "json",
			},
		},
		{
			fixture: "env-secrets.json",
			want: Config{
				Expression:   `$secrets["api-token"]`,
				Variables:    []string{},
				Timeout:      5 * time.Second,
				SecretStore:  "kubernetes",
				Site:         "fulfillOrder.jq.from",
				Port:         "8080",
				DaprHTTPPort: "3500",
				DaprAPIToken: "test-token",
			},
		},
	}

	seen := map[string]bool{}
	for _, tt := range tests {
		t.Run(tt.fixture, func(t *testing.T) {
			env := readFixture(t, tt.fixture)
			for k := range env {
				seen[k] = true
			}
			clearEnv(t)
			for k, v := range env {
				t.Setenv(k, v)
			}

			got, err := Load()
			if err != nil {
				t.Fatalf("Load() error = %v", err)
			}
			if !reflect.DeepEqual(got, tt.want) {
				t.Errorf("got %#v, want %#v", got, tt.want)
			}
		})
	}

	t.Run("names equal fixture keys", func(t *testing.T) {
		// Every fixture in the directory must be covered by the table above.
		files, err := filepath.Glob(filepath.Join("..", "..", "testdata", "contract", "env-*.json"))
		if err != nil {
			t.Fatal(err)
		}
		if len(files) != len(tests) {
			t.Fatalf("found %d fixtures, table covers %d", len(files), len(tests))
		}

		union := make([]string, 0, len(seen))
		for k := range seen {
			union = append(union, k)
		}
		sort.Strings(union)
		names := append([]string(nil), Names...)
		sort.Strings(names)
		if !reflect.DeepEqual(names, union) {
			t.Errorf("config.Names = %v, fixture key union = %v", names, union)
		}
	})
}

func readFixture(t *testing.T, name string) map[string]string {
	t.Helper()
	raw, err := os.ReadFile(filepath.Join("..", "..", "testdata", "contract", name))
	if err != nil {
		t.Fatalf("read fixture: %v", err)
	}
	var env map[string]string
	if err := json.Unmarshal(raw, &env); err != nil {
		t.Fatalf("parse fixture %s: %v", name, err)
	}
	return env
}
