package basicauth

import (
	"slices"
	"strings"
)

// Match reports whether uri, the request target as the host sees it, is on this config's
// endpoint: its path, ignoring any query string, equals /v1.0/invoke/<endpoint>/method or has
// that as a prefix followed by '/'. The endpoint name is compared exactly, so "pets" never
// matches "pets-v2".
//
// A path holding a "." or ".." segment, raw or percent-encoded in any case, never matches. If
// the sidecar cleaned such a path after this guest ran, the request could be routed to another
// endpoint carrying this credential; not matching sends it out unauthenticated instead, which
// is the safe direction. The zero Config, which has no endpoint, matches nothing.
func (c *Config) Match(uri string) bool {
	if c.methodPath == "" {
		return false
	}

	path := uri
	if i := strings.IndexByte(path, '?'); i >= 0 {
		path = path[:i]
	}

	if path != c.methodPath && !strings.HasPrefix(path, c.methodPath+"/") {
		return false
	}

	return !slices.ContainsFunc(strings.Split(path, "/"), isDotSegment)
}

// methodPath is the Dapr invocation path of endpoint, the path Match compares requests against.
func methodPath(endpoint string) string {
	return "/v1.0/invoke/" + endpoint + "/method"
}

// isDotSegment reports whether segment is "." or "..", where each dot may be written as %2e or
// %2E.
func isDotSegment(segment string) bool {
	var dots int
	for segment != "" {
		switch {
		case segment[0] == '.':
			segment = segment[1:]
		case len(segment) >= 3 && segment[0] == '%' && segment[1] == '2' && (segment[2] == 'e' || segment[2] == 'E'):
			segment = segment[3:]
		default:
			return false
		}
		dots++
	}
	return dots == 1 || dots == 2
}
