package logging

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"log/slog"
	"reflect"
	"regexp"
	"strings"
	"sync"
	"testing"

	"go.opentelemetry.io/otel/trace"
)

const (
	goldenTraceID = "4bf92f3577b34da6a3ce929d0e0e4736"
	goldenSpanID  = "00f067aa0ba902b7"
	goldenHeader  = "00-" + goldenTraceID + "-" + goldenSpanID + "-01"
)

var timestampPattern = regexp.MustCompile(`^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?Z$`)

// goldenCase is one row of the docs/log-format.md golden table. want holds
// every key but timestamp; the exception keys other than exception.message
// are checked separately because they are runtime shaped.
type goldenCase struct {
	name    string
	level   slog.Level
	msg     string
	ctx     context.Context
	attrs   []any
	want    map[string]any
	excType string // expected exception.type, empty when none
	excMsg  string // expected (scrubbed) exception.message
	mustNot string // substring the whole line must not contain
}

func goldenCases() []goldenCase {
	base := context.Background()
	return []goldenCase{
		{
			name: "1 plain", level: slog.LevelInfo, msg: "Starting Dapr workflow runtime", ctx: base,
			want: map[string]any{"severity": "INFO", "message": "Starting Dapr workflow runtime"},
		},
		{
			name: "2 in-span", level: slog.LevelInfo, msg: "Invoking step",
			ctx: WithTraceparent(base, goldenHeader),
			want: map[string]any{
				"severity": "INFO", "message": "Invoking step",
				"trace_id": goldenTraceID, "span_id": goldenSpanID,
			},
		},
		{
			name: "3 out-of-span", level: slog.LevelWarn, msg: "Definition store not reachable, retrying", ctx: base,
			want: map[string]any{"severity": "WARN", "message": "Definition store not reachable, retrying"},
		},
		{
			name: "4 invalid-context", level: slog.LevelInfo, msg: "Invalid context",
			ctx:  WithTraceparent(base, "00-00000000000000000000000000000000-0000000000000000-01"),
			want: map[string]any{"severity": "INFO", "message": "Invalid context"},
		},
		{
			name: "5 exception", level: slog.LevelError, msg: "Activity failed", ctx: base,
			attrs:   []any{"error", errors.New("boom")},
			want:    map[string]any{"severity": "ERROR", "message": "Activity failed"},
			excType: "*errors.errorString", excMsg: "boom",
		},
		{
			name: "6 multi-line", level: slog.LevelInfo, msg: "line one\nline two", ctx: base,
			want: map[string]any{"severity": "INFO", "message": "line one\nline two"},
		},
		{
			name: "7 bearer", level: slog.LevelInfo, ctx: base,
			msg:     "calling admin with Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJkd3MifQ.c2lnbmF0dXJl",
			want:    map[string]any{"severity": "INFO", "message": "calling admin with Authorization: [REDACTED]"},
			mustNot: "eyJhbGciOi",
		},
		{
			name: "8 bare-bearer", level: slog.LevelWarn, ctx: base,
			msg:     "upstream rejected Bearer eyJhbGciOiJIUzI1NiJ9.e30.sig12345",
			want:    map[string]any{"severity": "WARN", "message": "upstream rejected Bearer [REDACTED]"},
			mustNot: "eyJhbGciOi",
		},
		{
			name: "9 password", level: slog.LevelInfo, ctx: base,
			msg:     "connecting with user=dws password=hunter2",
			want:    map[string]any{"severity": "INFO", "message": "connecting with user=dws password=[REDACTED]"},
			mustNot: "hunter2",
		},
		{
			name: "10 json-secret", level: slog.LevelInfo, ctx: base,
			msg:     `payload {"client_secret":"s3cr3t-value","scope":"read"}`,
			want:    map[string]any{"severity": "INFO", "message": `payload {"client_secret":"[REDACTED]","scope":"read"}`},
			mustNot: "s3cr3t-value",
		},
		{
			name: "11 exception-secret", level: slog.LevelError, msg: "Call failed", ctx: base,
			attrs:   []any{"error", errors.New("401 for token=abc12345")},
			want:    map[string]any{"severity": "ERROR", "message": "Call failed"},
			excType: "*errors.errorString", excMsg: "401 for token=[REDACTED]",
			mustNot: "abc12345",
		},
		{
			name: "12 fatal", level: LevelFatal, msg: "Runtime terminated", ctx: base,
			want: map[string]any{"severity": "FATAL", "message": "Runtime terminated"},
		},
	}
}

func decodeLine(t *testing.T, line []byte) map[string]any {
	t.Helper()
	var got map[string]any
	if err := json.Unmarshal(line, &got); err != nil {
		t.Fatalf("output is not one JSON object: %v\n%q", err, line)
	}
	return got
}

func TestLogFormatContract(t *testing.T) {
	for _, tc := range goldenCases() {
		t.Run(tc.name, func(t *testing.T) {
			var buf bytes.Buffer
			logger := New(&buf, "json", nil)
			logger.Log(tc.ctx, tc.level, tc.msg, tc.attrs...)

			out := buf.Bytes()
			if len(out) == 0 || out[len(out)-1] != '\n' || bytes.Count(out, []byte("\n")) != 1 {
				t.Fatalf("want exactly one newline-terminated line, got %q", out)
			}
			if tc.mustNot != "" && strings.Contains(string(out), tc.mustNot) {
				t.Errorf("line leaks %q: %s", tc.mustNot, out)
			}

			got := decodeLine(t, out)
			ts, _ := got["timestamp"].(string)
			if !timestampPattern.MatchString(ts) {
				t.Errorf("timestamp %q does not match the contract pattern", ts)
			}
			delete(got, "timestamp")

			if got["logger"] != "dws-jq" {
				t.Errorf("logger = %v, want dws-jq", got["logger"])
			}
			delete(got, "logger")

			if tc.excType != "" {
				if got["exception.type"] != tc.excType {
					t.Errorf("exception.type = %v, want %s", got["exception.type"], tc.excType)
				}
				if got["exception.message"] != tc.excMsg {
					t.Errorf("exception.message = %v, want %q", got["exception.message"], tc.excMsg)
				}
				st, _ := got["exception.stacktrace"].(string)
				if st == "" || !strings.Contains(st, tc.excMsg) {
					t.Errorf("exception.stacktrace = %q, want non-empty containing %q", st, tc.excMsg)
				}
				delete(got, "exception.type")
				delete(got, "exception.message")
				delete(got, "exception.stacktrace")
			}

			// Whatever is left must equal the expected line: absent keys are absent.
			if !reflect.DeepEqual(got, tc.want) {
				t.Errorf("record mismatch\n got: %v\nwant: %v", got, tc.want)
			}
		})
	}
}

func TestNew_textFallback(t *testing.T) {
	for _, format := range []string{"", "text", "yaml"} {
		t.Run(fmt.Sprintf("%q", format), func(t *testing.T) {
			var buf bytes.Buffer
			New(&buf, format, nil).Info("hello world", "k", "v")

			out := buf.String()
			if strings.HasPrefix(out, "{") {
				t.Fatalf("want text output, got JSON-looking %q", out)
			}
			for _, want := range []string{"level=INFO", `msg="hello world"`, "k=v"} {
				if !strings.Contains(out, want) {
					t.Errorf("text output %q lacks %q", out, want)
				}
			}
		})
	}
}

func TestNew_caseInsensitive(t *testing.T) {
	for _, format := range []string{" JSON ", "Json", "json\n"} {
		var buf bytes.Buffer
		New(&buf, format, nil).Info("hello")
		if got := decodeLine(t, buf.Bytes()); got["message"] != "hello" {
			t.Errorf("format %q: message = %v", format, got["message"])
		}
	}
}

func TestNew_redactsBeforeScrub(t *testing.T) {
	redact := func(s string) string { return strings.ReplaceAll(s, "hunter3", "***") }
	for _, format := range []string{"json", "text"} {
		t.Run(format, func(t *testing.T) {
			var buf bytes.Buffer
			New(&buf, format, redact).Info("value hunter3", "detail", "also hunter3")

			out := buf.String()
			if strings.Contains(out, "hunter3") {
				t.Errorf("line leaks the secret: %s", out)
			}
			if !strings.Contains(out, "value ***") || !strings.Contains(out, "also ***") {
				t.Errorf("want the *** placeholder in message and attribute, got %s", out)
			}
		})
	}
}

// The "***" placeholder must survive the scrubber, and a secret that only the
// redactor knows must be gone before the scrubber's regexes run on it.
func TestNew_redactionRunsBeforeScrubber(t *testing.T) {
	redact := func(s string) string { return strings.ReplaceAll(s, "zzz-secret", "***") }
	var buf bytes.Buffer
	New(&buf, "json", redact).Error("failed", "error", errors.New("bad value zzz-secret"))

	got := decodeLine(t, buf.Bytes())
	if got["exception.message"] != "bad value ***" {
		t.Errorf("exception.message = %v", got["exception.message"])
	}
	if strings.Contains(buf.String(), "zzz-secret") {
		t.Errorf("stack trace or message leaks the secret: %s", buf.String())
	}
}

func TestJSONRecordShape_extrasAndErrors(t *testing.T) {
	var buf bytes.Buffer
	logger := New(&buf, "json", nil)

	// Extras are appended, contract names are never reused, and an error under
	// any key becomes exception.*.
	logger.Warn("rejected",
		"site", "eval",
		"count", 3,
		"ratio", 0.5,
		"ok", true,
		"message", "shadow",
		"severity", "shadow",
		"err", errors.New("bad input"),
	)
	got := decodeLine(t, buf.Bytes())

	if got["message"] != "rejected" || got["severity"] != "WARN" {
		t.Errorf("contract keys were overwritten: %v", got)
	}
	if got["site"] != "eval" || got["count"] != float64(3) || got["ratio"] != 0.5 || got["ok"] != true {
		t.Errorf("extras missing or mistyped: %v", got)
	}
	if got["exception.message"] != "bad input" || got["exception.type"] != "*errors.errorString" {
		t.Errorf("error under key err not mapped to exception.*: %v", got)
	}
	if _, ok := got["err"]; ok {
		t.Errorf("error attribute leaked as an extra key: %v", got)
	}
	var shadowed int
	for key, value := range got {
		if value == "shadow" {
			shadowed++
			if key == "message" || key == "severity" {
				t.Errorf("attribute reused contract key %q", key)
			}
		}
	}
	if shadowed != 2 {
		t.Errorf("want both shadowing attributes kept under other names, got %v", got)
	}
}

func TestJSONRecordShape_extrasKeepKeyOrderAfterContract(t *testing.T) {
	var buf bytes.Buffer
	New(&buf, "json", nil).Info("m", "site", "x")
	line := buf.String()
	order := []string{`"timestamp"`, `"severity"`, `"logger"`, `"message"`, `"site"`}
	last := -1
	for _, key := range order {
		idx := strings.Index(line, key)
		if idx <= last {
			t.Fatalf("key %s out of order in %s", key, line)
		}
		last = idx
	}
}

func TestJSONHandler_groupsAndWithAttrs(t *testing.T) {
	var buf bytes.Buffer
	logger := New(&buf, "json", nil).With("service", "jq").WithGroup("req").With("id", "r1")
	logger.Info("done", slog.Group("site", slog.String("kind", "eval")), "n", 1)

	got := decodeLine(t, buf.Bytes())
	for key, want := range map[string]any{
		"service": "jq", "req.id": "r1", "req.site.kind": "eval", "req.n": float64(1),
	} {
		if got[key] != want {
			t.Errorf("%s = %v, want %v (record %v)", key, got[key], want, got)
		}
	}
}

func TestJSONHandler_scrubsAttributeValues(t *testing.T) {
	var buf bytes.Buffer
	New(&buf, "json", nil).Info("m", "detail", "got password=hunter2 here", "n", 1)

	if strings.Contains(buf.String(), "hunter2") {
		t.Errorf("attribute value not scrubbed: %s", buf.String())
	}
	if got := decodeLine(t, buf.Bytes()); got["detail"] != "got password=[REDACTED] here" {
		t.Errorf("detail = %v", got["detail"])
	}
}

func TestJSONHandler_levels(t *testing.T) {
	tests := []struct {
		level slog.Level
		want  string
	}{
		{slog.LevelInfo, "INFO"},
		{slog.LevelWarn, "WARN"},
		{slog.LevelError, "ERROR"},
		{LevelFatal, "FATAL"},
		{LevelFatal + 4, "FATAL"},
	}
	for _, tc := range tests {
		var buf bytes.Buffer
		New(&buf, "json", nil).Log(context.Background(), tc.level, "m")
		if got := decodeLine(t, buf.Bytes()); got["severity"] != tc.want {
			t.Errorf("level %d: severity = %v, want %s", tc.level, got["severity"], tc.want)
		}
	}

	// Debug is below the default threshold, in both modes.
	for _, format := range []string{"json", "text"} {
		var buf bytes.Buffer
		New(&buf, format, nil).Debug("hidden")
		if buf.Len() != 0 {
			t.Errorf("%s: debug record was written: %q", format, buf.String())
		}
	}
}

func TestText_fatalAndErrorAttributes(t *testing.T) {
	redact := func(s string) string { return strings.ReplaceAll(s, "hunter3", "***") }
	var buf bytes.Buffer
	logger := New(&buf, "text", redact)
	logger.Log(context.Background(), LevelFatal, "Runtime terminated", "error", errors.New("hunter3 broke"))

	out := buf.String()
	if !strings.Contains(out, "level=FATAL") {
		t.Errorf("want level=FATAL in text output, got %s", out)
	}
	if strings.Contains(out, "hunter3") {
		t.Errorf("error attribute leaked the secret: %s", out)
	}
}

func TestWithTraceparent(t *testing.T) {
	tests := []struct {
		name   string
		header string
		trace  string
		span   string
	}{
		{"valid", goldenHeader, goldenTraceID, goldenSpanID},
		{"valid unsampled", "00-" + goldenTraceID + "-" + goldenSpanID + "-00", goldenTraceID, goldenSpanID},
		{"surrounding space", " " + goldenHeader + " ", goldenTraceID, goldenSpanID},
		{"future version with extra field", "01-" + goldenTraceID + "-" + goldenSpanID + "-01-extra", goldenTraceID, goldenSpanID},
		{"empty", "", "", ""},
		{"garbage", "not-a-traceparent", "", ""},
		{"zero trace id", "00-00000000000000000000000000000000-" + goldenSpanID + "-01", "", ""},
		{"zero span id", "00-" + goldenTraceID + "-0000000000000000-01", "", ""},
		{"version ff", "ff-" + goldenTraceID + "-" + goldenSpanID + "-01", "", ""},
		{"version 00 with extra field", "00-" + goldenTraceID + "-" + goldenSpanID + "-01-extra", "", ""},
		{"version 00 with reserved flags", "00-" + goldenTraceID + "-" + goldenSpanID + "-09", "", ""},
		{"uppercase hex", "00-" + strings.ToUpper(goldenTraceID) + "-" + goldenSpanID + "-01", "", ""},
		{"short trace id", "00-4bf92f3577b34da6-" + goldenSpanID + "-01", "", ""},
		{"non-hex trace id", "00-" + strings.Repeat("g", 32) + "-" + goldenSpanID + "-01", "", ""},
		{"non-hex flags", "00-" + goldenTraceID + "-" + goldenSpanID + "-zz", "", ""},
		{"missing flags", "00-" + goldenTraceID + "-" + goldenSpanID, "", ""},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			ctx := WithTraceparent(context.Background(), tc.header)
			if span := trace.SpanContextFromContext(ctx); span.IsRemote() != (tc.trace != "") {
				t.Errorf("remote span context = %v, want remote = %t", span, tc.trace != "")
			}
			var buf bytes.Buffer
			New(&buf, "json", nil).InfoContext(ctx, "m")
			got := decodeLine(t, buf.Bytes())

			gotTrace, hasTrace := got["trace_id"]
			gotSpan, hasSpan := got["span_id"]
			if tc.trace == "" {
				if hasTrace || hasSpan {
					t.Errorf("want no IDs, got trace_id=%v span_id=%v", gotTrace, gotSpan)
				}
				return
			}
			if gotTrace != tc.trace || gotSpan != tc.span {
				t.Errorf("got trace_id=%v span_id=%v, want %s %s", gotTrace, gotSpan, tc.trace, tc.span)
			}
		})
	}
}

func TestWithTraceparent_invalidKeepsParentContext(t *testing.T) {
	parent := WithTraceparent(context.Background(), goldenHeader)
	child := WithTraceparent(parent, "garbage")

	var buf bytes.Buffer
	New(&buf, "json", nil).InfoContext(child, "m")
	if got := decodeLine(t, buf.Bytes()); got["trace_id"] != goldenTraceID {
		t.Errorf("an invalid header must not erase the parent's trace: %v", got)
	}
}

func TestNew_noTraceWithoutTraceparent(t *testing.T) {
	var buf bytes.Buffer
	New(&buf, "json", nil).Info("no ctx") // slog.Info passes context.Background()
	if got := decodeLine(t, buf.Bytes()); got["trace_id"] != nil {
		t.Errorf("unexpected trace_id: %v", got)
	}
}

// Handlers are shared by every request goroutine; interleaved writes would
// corrupt lines.
func TestJSONHandler_concurrentWritesStayWholeLines(t *testing.T) {
	var buf bytes.Buffer
	logger := New(&buf, "json", nil)

	const goroutines, perGoroutine = 16, 50
	var wg sync.WaitGroup
	for g := 0; g < goroutines; g++ {
		wg.Add(1)
		go func(g int) {
			defer wg.Done()
			derived := logger.With("g", g) // derived handlers share the lock
			for i := 0; i < perGoroutine; i++ {
				derived.Error("concurrent", "i", i, "error", errors.New("boom"))
			}
		}(g)
	}
	wg.Wait()

	lines := bytes.Split(bytes.TrimSuffix(buf.Bytes(), []byte("\n")), []byte("\n"))
	if len(lines) != goroutines*perGoroutine {
		t.Fatalf("got %d lines, want %d", len(lines), goroutines*perGoroutine)
	}
	for _, line := range lines {
		decodeLine(t, line)
	}
}

type secretStringer struct{ value string }

func (s secretStringer) String() string { return "stringer:" + s.value }

type secretStruct struct {
	User  string
	Value string
}

// Text mode must redact every value JSON mode redacts: a caller's string
// attribute named "level", and non-string values through their String form.
func TestText_redactsEveryAttributeValue(t *testing.T) {
	redact := func(s string) string { return strings.ReplaceAll(s, "hunter3", "***") }
	tests := []struct {
		name string
		args []any
		want string
	}{
		{"string attribute named level", []any{"level", "password=hunter3"}, `level="password=***"`},
		{"Stringer", []any{"who", secretStringer{"hunter3"}}, "who=stringer:***"},
		{"struct", []any{"obj", secretStruct{"dws", "hunter3"}}, `obj="{dws ***}"`},
		{"pointer to Stringer", []any{"who", &secretStringer{"hunter3"}}, "who=stringer:***"},
		{"error", []any{"error", errors.New("bad hunter3")}, "error=\"bad ***\""},
		{"nested group", []any{slog.Group("g", slog.Any("o", secretStringer{"hunter3"}))}, "g.o=stringer:***"},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			var buf bytes.Buffer
			New(&buf, "text", redact).Info("m", tc.args...)

			out := buf.String()
			if strings.Contains(out, "hunter3") {
				t.Errorf("line leaks the secret: %s", out)
			}
			if !strings.Contains(out, tc.want) {
				t.Errorf("line %q lacks %q", out, tc.want)
			}
			if !strings.Contains(out, "level=INFO") {
				t.Errorf("slog's own level attribute was lost: %s", out)
			}
		})
	}
}

// Values with nothing to redact keep slog's own formatting.
func TestText_unchangedValuesKeepFormatting(t *testing.T) {
	var buf bytes.Buffer
	New(&buf, "text", func(s string) string { return s }).Info("m", "n", 3, "obj", secretStruct{"a", "b"})
	if out := buf.String(); !strings.Contains(out, "n=3") || !strings.Contains(out, `obj="{User:a Value:b}"`) {
		t.Errorf("unexpected formatting: %s", out)
	}
}
