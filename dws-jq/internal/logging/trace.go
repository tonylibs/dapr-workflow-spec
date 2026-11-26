package logging

import (
	"context"
	"strings"

	"go.opentelemetry.io/otel/propagation"
)

// WithTraceparent extracts the remote span context for request log correlation.
// An invalid or missing header leaves ctx unchanged.
func WithTraceparent(ctx context.Context, header string) context.Context {
	return (propagation.TraceContext{}).Extract(ctx, propagation.MapCarrier{
		"traceparent": strings.TrimSpace(header),
	})
}
