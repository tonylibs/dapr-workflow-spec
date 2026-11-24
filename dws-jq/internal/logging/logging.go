// Package logging writes the DWS structured log format (docs/log-format.md).
//
// New selects the format from the raw DWS_LOG_FORMAT value: "json" (trimmed,
// case-insensitive) gives one JSON object per line with the contract keys;
// anything else gives slog's text output. In both modes a caller-supplied
// redact function runs over messages, string attributes and error text; in
// JSON mode the contract's regular-expression scrubber then runs on top.
package logging

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"math"
	"regexp"
	"runtime/debug"
	"strings"
	"sync"
	"time"
)

const (
	// LoggerName is the value of the "logger" key in JSON mode.
	LoggerName = "dws-jq"

	// LevelFatal is above slog.LevelError and maps to severity FATAL.
	LevelFatal = slog.Level(12)

	timestampLayout = "2006-01-02T15:04:05.000Z"
	placeholder     = "[REDACTED]"
	minLevel        = slog.LevelInfo
)

// Contract keys. An extra attribute that uses one of these names is kept under
// the extraKeyPrefix-ed name instead.
const (
	keyTimestamp  = "timestamp"
	keySeverity   = "severity"
	keyLogger     = "logger"
	keyMessage    = "message"
	keyTraceID    = "trace_id"
	keySpanID     = "span_id"
	keyExcType    = "exception.type"
	keyExcMessage = "exception.message"
	keyExcStack   = "exception.stacktrace"

	extraKeyPrefix = "attr."
)

var contractKeys = map[string]struct{}{
	keyTimestamp: {}, keySeverity: {}, keyLogger: {}, keyMessage: {},
	keyTraceID: {}, keySpanID: {},
	keyExcType: {}, keyExcMessage: {}, keyExcStack: {},
}

// The two scrubber expressions of docs/log-format.md, in order.
var (
	credentialPair = regexp.MustCompile(`(?i)([\w.-]*(?:authorization|password|passwd|secret|token|api[-_]?key)[\w.-]*)("?\s*[:=]\s*"?)(?:(?:bearer|basic)\s+)?[^\s"',;&}]+`)
	bareCredential = regexp.MustCompile(`(?i)\b(bearer|basic)\s+[A-Za-z0-9._~+/-]{8,}=*`)
)

// New returns a logger writing to w. format is the raw DWS_LOG_FORMAT value.
// redact, which may be nil, is applied to every message, string attribute and
// error text before anything else touches it.
func New(w io.Writer, format string, redact func(string) string) *slog.Logger {
	if redact == nil {
		redact = func(s string) string { return s }
	}
	if strings.EqualFold(strings.TrimSpace(format), "json") {
		return slog.New(&jsonHandler{out: &lockedWriter{w: w}, redact: redact})
	}
	return slog.New(slog.NewTextHandler(w, &slog.HandlerOptions{
		Level:       minLevel,
		ReplaceAttr: textReplaceAttr(redact),
	}))
}

// textReplaceAttr redacts the message and every string-like attribute value
// (strings, and anything else through its String form, as JSON mode does) and
// renames the fatal level, leaving the rest of slog's text output unchanged.
func textReplaceAttr(redact func(string) string) func([]string, slog.Attr) slog.Attr {
	return func(groups []string, a slog.Attr) slog.Attr {
		if len(groups) == 0 && a.Key == slog.LevelKey {
			// Only slog's own level attribute is exempt; a caller's string
			// attribute named "level" is redacted like any other.
			if level, ok := a.Value.Any().(slog.Level); ok {
				if level >= LevelFatal {
					return slog.String(a.Key, "FATAL")
				}
				return a
			}
		}
		switch a.Value.Kind() {
		case slog.KindString, slog.KindAny:
			text := a.Value.String()
			redacted := redact(text)
			if redacted == text {
				return a // keep slog's own formatting when nothing changed
			}
			return slog.String(a.Key, redacted)
		}
		return a
	}
}

// lockedWriter serialises whole-record writes: a handler and every handler
// derived from it by WithAttrs/WithGroup share one.
type lockedWriter struct {
	mu sync.Mutex
	w  io.Writer
}

func (l *lockedWriter) write(b []byte) error {
	l.mu.Lock()
	defer l.mu.Unlock()
	_, err := l.w.Write(b)
	return err
}

// jsonHandler writes the DWS contract record, one object per line.
type jsonHandler struct {
	out    *lockedWriter
	redact func(string) string
	attrs  []slog.Attr // flattened leaves with their group prefix applied
	prefix string      // "a.b." for the open groups
}

func (h *jsonHandler) Enabled(_ context.Context, level slog.Level) bool {
	return level >= minLevel
}

func (h *jsonHandler) WithAttrs(attrs []slog.Attr) slog.Handler {
	if len(attrs) == 0 {
		return h
	}
	next := *h
	next.attrs = append(append([]slog.Attr(nil), h.attrs...), flatten(h.prefix, attrs)...)
	return &next
}

func (h *jsonHandler) WithGroup(name string) slog.Handler {
	if name == "" {
		return h
	}
	next := *h
	next.prefix = h.prefix + name + "."
	return &next
}

// scrub applies the caller's redaction, then the contract's scrubber.
func (h *jsonHandler) scrub(s string) string {
	s = h.redact(s)
	s = credentialPair.ReplaceAllString(s, "${1}${2}"+placeholder)
	return bareCredential.ReplaceAllString(s, "${1} "+placeholder)
}

func (h *jsonHandler) Handle(ctx context.Context, r slog.Record) error {
	var buf bytes.Buffer
	buf.WriteByte('{')

	ts := r.Time
	if ts.IsZero() {
		ts = time.Now()
	}
	writeField(&buf, keyTimestamp, ts.UTC().Format(timestampLayout), true)
	writeField(&buf, keySeverity, severity(r.Level), false)
	writeField(&buf, keyLogger, LoggerName, false)
	writeField(&buf, keyMessage, h.scrub(r.Message), false)
	if tc, ok := traceFrom(ctx); ok {
		writeField(&buf, keyTraceID, tc.traceID, false)
		writeField(&buf, keySpanID, tc.spanID, false)
	}

	leaves := append(append([]slog.Attr(nil), h.attrs...), flatten(h.prefix, attrSlice(r))...)
	exception, extras := splitException(leaves)
	if exception != nil {
		h.writeException(&buf, exception)
	}
	for _, a := range extras {
		h.writeExtra(&buf, a)
	}

	buf.WriteString("}\n")
	return h.out.write(buf.Bytes())
}

// splitException pulls the first error-valued attribute out of attrs.
func splitException(attrs []slog.Attr) (error, []slog.Attr) {
	var exception error
	rest := make([]slog.Attr, 0, len(attrs))
	for _, a := range attrs {
		if err, ok := a.Value.Any().(error); ok && a.Value.Kind() == slog.KindAny && exception == nil {
			exception = err
			continue
		}
		rest = append(rest, a)
	}
	return exception, rest
}

// writeException emits exception.type/.message/.stacktrace. The stack is the
// logging goroutine's, which is where the record is being written.
func (h *jsonHandler) writeException(buf *bytes.Buffer, err error) {
	typeName := fmt.Sprintf("%T", err)
	message := h.scrub(err.Error())
	stack := h.scrub(typeName + ": " + err.Error() + "\n" + string(debug.Stack()))
	writeField(buf, keyExcType, typeName, false)
	writeField(buf, keyExcMessage, message, false)
	writeField(buf, keyExcStack, stack, false)
}

func (h *jsonHandler) writeExtra(buf *bytes.Buffer, a slog.Attr) {
	key := a.Key
	if _, reserved := contractKeys[key]; reserved {
		key = extraKeyPrefix + key
	}
	buf.WriteByte(',')
	buf.WriteString(encodeString(key))
	buf.WriteByte(':')
	buf.WriteString(h.encodeValue(a.Value))
}

// encodeValue renders a resolved, non-group value as JSON. Strings and
// anything stringified are redacted and scrubbed.
func (h *jsonHandler) encodeValue(v slog.Value) string {
	switch v.Kind() {
	case slog.KindBool:
		return fmt.Sprint(v.Bool())
	case slog.KindInt64:
		return fmt.Sprint(v.Int64())
	case slog.KindUint64:
		return fmt.Sprint(v.Uint64())
	case slog.KindFloat64:
		f := v.Float64()
		if math.IsNaN(f) || math.IsInf(f, 0) {
			return encodeString(fmt.Sprint(f))
		}
		b, _ := json.Marshal(f) // finite floats always encode
		return string(b)
	case slog.KindTime:
		return encodeString(v.Time().UTC().Format(timestampLayout))
	case slog.KindDuration:
		return encodeString(v.Duration().String())
	default: // KindString, KindAny
		return encodeString(h.scrub(v.String()))
	}
}

// severity maps a slog level onto the contract's severity names.
func severity(l slog.Level) string {
	switch {
	case l >= LevelFatal:
		return "FATAL"
	case l >= slog.LevelError:
		return "ERROR"
	case l >= slog.LevelWarn:
		return "WARN"
	case l >= slog.LevelInfo:
		return "INFO"
	case l >= slog.LevelDebug:
		return "DEBUG"
	default:
		return "TRACE"
	}
}

func attrSlice(r slog.Record) []slog.Attr {
	attrs := make([]slog.Attr, 0, r.NumAttrs())
	r.Attrs(func(a slog.Attr) bool {
		attrs = append(attrs, a)
		return true
	})
	return attrs
}

// flatten resolves attrs and expands groups into leaves keyed "prefix.group.key".
func flatten(prefix string, attrs []slog.Attr) []slog.Attr {
	var out []slog.Attr
	for _, a := range attrs {
		a.Value = a.Value.Resolve()
		if a.Value.Kind() == slog.KindGroup {
			sub := prefix
			if a.Key != "" {
				sub += a.Key + "."
			}
			out = append(out, flatten(sub, a.Value.Group())...)
			continue
		}
		if a.Equal(slog.Attr{}) {
			continue
		}
		a.Key = prefix + a.Key
		out = append(out, a)
	}
	return out
}

func writeField(buf *bytes.Buffer, key, value string, first bool) {
	if !first {
		buf.WriteByte(',')
	}
	buf.WriteString(encodeString(key))
	buf.WriteByte(':')
	buf.WriteString(encodeString(value))
}

// encodeString returns s as a JSON string literal without HTML escaping.
func encodeString(s string) string {
	var b bytes.Buffer
	enc := json.NewEncoder(&b)
	enc.SetEscapeHTML(false)
	_ = enc.Encode(s) // encoding a string cannot fail
	return strings.TrimSuffix(b.String(), "\n")
}
