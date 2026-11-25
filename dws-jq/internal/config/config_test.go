package config

import (
	"encoding/json"
	"errors"
	"go/ast"
	"go/parser"
	"go/token"
	"os"
	"path/filepath"
	"reflect"
	"sort"
	"strings"
	"testing"
	"time"
)

// contractDir holds the env fixtures the controller side (2m.2) must match.
var contractDir = filepath.Join("..", "..", "testdata", "contract")

// envOf returns a lookup over env, so a test never sees the developer's shell.
func envOf(env map[string]string) func(string) string {
	return func(key string) string { return env[key] }
}

func TestLoad_defaults(t *testing.T) {
	got, err := load(envOf(map[string]string{"JQ_EXPRESSION": "."}))
	if err != nil {
		t.Fatalf("load() error = %v", err)
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

// TestLoad_readsProcessEnvironment covers the one piece load does not: Load
// hands it the process environment.
func TestLoad_readsProcessEnvironment(t *testing.T) {
	for _, name := range Names() {
		t.Setenv(name, "")
	}
	t.Setenv("JQ_EXPRESSION", ".a")
	t.Setenv("PORT", "9000")

	got, err := Load()
	if err != nil {
		t.Fatalf("Load() error = %v", err)
	}
	if got.Expression != ".a" || got.Port != "9000" {
		t.Errorf("got %#v, want Expression .a and Port 9000", got)
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
			got, err := load(envOf(map[string]string{"JQ_EXPRESSION": ".", "JQ_VARIABLES": tt.raw}))
			if err != nil {
				t.Fatalf("load() error = %v", err)
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
			env := map[string]string{}
			if tt.wantName != "JQ_EXPRESSION" {
				env["JQ_EXPRESSION"] = "."
			}
			for k, v := range tt.env {
				env[k] = v
			}

			_, err := load(envOf(env))
			var fe *FieldError
			if !errors.As(err, &fe) {
				t.Fatalf("load() error = %v, want *FieldError", err)
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

// contractCases is the Config each fixture under testdata/contract must load
// to. Where a fixture sets a value, it differs from the default, so a read the
// loader drops changes the result.
var contractCases = map[string]Config{
	"env-minimal.json": {
		Expression:   "${ .count > 0 }",
		Variables:    []string{},
		Timeout:      5 * time.Second,
		Site:         "dws-jq",
		Port:         "8080",
		DaprHTTPPort: "3500",
	},
	"env-variables.json": {
		Expression:   "$item < 3",
		Variables:    []string{"context", "item", "index"},
		Timeout:      250 * time.Millisecond,
		Site:         "processItems.jq.while",
		Port:         "8081",
		DaprHTTPPort: "3500",
		LogFormat:    "json",
	},
	"env-secrets.json": {
		Expression:   `$secrets["api-token"]`,
		Variables:    []string{},
		Timeout:      5 * time.Second,
		SecretStore:  "kubernetes",
		Site:         "fulfillOrder.jq.from",
		Port:         "8080",
		DaprHTTPPort: "3501",
		DaprAPIToken: "test-token",
	},
}

// TestConfigContract pins the names the loader reads to the fixtures: the set
// of names load asks its lookup for must equal the union of fixture keys, so a
// read no fixture has, or a fixture key nothing reads, fails here.
func TestConfigContract(t *testing.T) {
	fixtures := fixtureFiles(t)

	t.Run("every fixture has an expected Config", func(t *testing.T) {
		if len(fixtures) != len(contractCases) {
			t.Fatalf("found fixtures %v, table covers %d", fixtures, len(contractCases))
		}
		for _, name := range fixtures {
			if _, ok := contractCases[name]; !ok {
				t.Errorf("fixture %s has no expected Config", name)
			}
		}
	})

	for _, name := range fixtures {
		t.Run(name, func(t *testing.T) {
			got, err := load(envOf(readFixture(t, name)))
			if err != nil {
				t.Fatalf("load() error = %v", err)
			}
			if want := contractCases[name]; !reflect.DeepEqual(got, want) {
				t.Errorf("got %#v, want %#v", got, want)
			}
		})
	}

	t.Run("names read equal fixture keys", func(t *testing.T) {
		keys := map[string]bool{}
		read := map[string]bool{}
		for _, name := range fixtureFiles(t) {
			env := readFixture(t, name)
			for k := range env {
				keys[k] = true
			}
			_, _ = load(func(key string) string {
				read[key] = true
				return env[key]
			})
		}
		if got, want := sortedKeys(read), sortedKeys(keys); !reflect.DeepEqual(got, want) {
			t.Errorf("names load reads = %v, fixture key union = %v", got, want)
		}
	})

	t.Run("Names is what load reads", func(t *testing.T) {
		names := append([]string(nil), Names()...)
		sort.Strings(names)
		read := map[string]bool{}
		_, _ = load(func(key string) string { read[key] = true; return "" })
		if want := sortedKeys(read); !reflect.DeepEqual(names, want) {
			t.Errorf("Names() = %v, load reads %v", names, want)
		}
	})
}

// TestLoadIsTheOnlyEnvironmentRead keeps every read behind load's lookup: an
// os.Getenv anywhere else in the package would escape the recorder that
// TestConfigContract relies on.
func TestLoadIsTheOnlyEnvironmentRead(t *testing.T) {
	files, err := filepath.Glob("*.go")
	if err != nil {
		t.Fatal(err)
	}
	fset := token.NewFileSet()
	var reads []string
	for _, file := range files {
		if strings.HasSuffix(file, "_test.go") {
			continue
		}
		f, err := parser.ParseFile(fset, file, nil, 0)
		if err != nil {
			t.Fatal(err)
		}
		ast.Inspect(f, func(n ast.Node) bool {
			sel, ok := n.(*ast.SelectorExpr)
			if !ok {
				return true
			}
			if pkg, ok := sel.X.(*ast.Ident); ok && (pkg.Name == "os" || pkg.Name == "syscall") {
				switch sel.Sel.Name {
				case "Getenv", "LookupEnv", "Environ", "ExpandEnv", "Expand":
					reads = append(reads, fset.Position(sel.Pos()).String()+" "+pkg.Name+"."+sel.Sel.Name)
				}
			}
			return true
		})
	}
	if len(reads) != 1 || !strings.HasSuffix(reads[0], " os.Getenv") {
		t.Errorf("environment reads = %v, want exactly one os.Getenv (in Load)", reads)
	}
}

// fixtureFiles returns the base names of every env-*.json fixture.
func fixtureFiles(t *testing.T) []string {
	t.Helper()
	paths, err := filepath.Glob(filepath.Join(contractDir, "env-*.json"))
	if err != nil {
		t.Fatal(err)
	}
	if len(paths) == 0 {
		t.Fatalf("no fixtures under %s", contractDir)
	}
	names := make([]string, len(paths))
	for i, p := range paths {
		names[i] = filepath.Base(p)
	}
	return names
}

func readFixture(t *testing.T, name string) map[string]string {
	t.Helper()
	raw, err := os.ReadFile(filepath.Join(contractDir, name))
	if err != nil {
		t.Fatalf("read fixture: %v", err)
	}
	var env map[string]string
	if err := json.Unmarshal(raw, &env); err != nil {
		t.Fatalf("parse fixture %s: %v", name, err)
	}
	return env
}

func sortedKeys(m map[string]bool) []string {
	out := make([]string, 0, len(m))
	for k := range m {
		out = append(out, k)
	}
	sort.Strings(out)
	return out
}
