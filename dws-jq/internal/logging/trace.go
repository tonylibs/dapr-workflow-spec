package logging

import (
	"context"
	"strings"
)

type traceContextKey struct{}

// traceContext carries the IDs of an inbound W3C traceparent header.
type traceContext struct {
	traceID string
	spanID  string
}

const (
	versionLen = 2
	traceIDLen = 32
	spanIDLen  = 16
	flagsLen   = 2

	traceIDStart = versionLen + 1
	spanIDStart  = traceIDStart + traceIDLen + 1
	flagsStart   = spanIDStart + spanIDLen + 1
	// traceparentLen is the length of a version-00 header:
	// version "-" trace-id "-" span-id "-" flags.
	traceparentLen = flagsStart + flagsLen
)

// WithTraceparent returns a context that makes records logged with it carry
// the trace and span IDs of a W3C traceparent header. A header that is empty,
// malformed or has an all-zero ID is ignored and ctx is returned unchanged, so
// the record then omits trace_id and span_id.
func WithTraceparent(ctx context.Context, header string) context.Context {
	tc, ok := parseTraceparent(header)
	if !ok {
		return ctx
	}
	return context.WithValue(ctx, traceContextKey{}, tc)
}

func traceFrom(ctx context.Context) (traceContext, bool) {
	if ctx == nil {
		return traceContext{}, false
	}
	tc, ok := ctx.Value(traceContextKey{}).(traceContext)
	return tc, ok
}

func parseTraceparent(header string) (traceContext, bool) {
	header = strings.TrimSpace(header)
	if len(header) < traceparentLen {
		return traceContext{}, false
	}
	version := header[:versionLen]
	traceID := header[traceIDStart : traceIDStart+traceIDLen]
	spanID := header[spanIDStart : spanIDStart+spanIDLen]
	flags := header[flagsStart : flagsStart+flagsLen]

	if header[versionLen] != '-' || header[traceIDStart+traceIDLen] != '-' || header[spanIDStart+spanIDLen] != '-' {
		return traceContext{}, false
	}
	if version == "ff" || !isLowerHex(version) || !isLowerHex(traceID) || !isLowerHex(spanID) || !isLowerHex(flags) {
		return traceContext{}, false
	}
	if isAllZero(traceID) || isAllZero(spanID) {
		return traceContext{}, false
	}
	// Version 00 has exactly four fields; later versions may append more,
	// each introduced by a dash.
	rest := header[traceparentLen:]
	if rest != "" && (version == "00" || rest[0] != '-') {
		return traceContext{}, false
	}
	return traceContext{traceID: traceID, spanID: spanID}, true
}

func isLowerHex(s string) bool {
	for i := 0; i < len(s); i++ {
		c := s[i]
		if (c < '0' || c > '9') && (c < 'a' || c > 'f') {
			return false
		}
	}
	return true
}

func isAllZero(s string) bool {
	return strings.Trim(s, "0") == ""
}
