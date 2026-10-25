package bearer

import (
	"strings"

	"github.com/http-wasm/http-wasm-guest-tinygo/handler/api"
)

// Matches reports whether uri is on this endpoint's Dapr service-invocation
// path: /v1.0/invoke/<endpoint>/method, then "/", "?" or the end. The boundary
// check keeps "pets-api-2" and "/methods" from matching "pets-api" and
// "/method".
//
// A Config that did not come from Parse has no prefix and matches nothing:
// without this guard an empty prefix would match every URI and stamp an empty
// Authorization header.
func (c Config) Matches(uri string) bool {
	if c.prefix == "" {
		return false
	}
	rest, ok := strings.CutPrefix(uri, c.prefix)
	return ok && (rest == "" || rest[0] == '/' || rest[0] == '?')
}

// Handle is the http-wasm request handler. It sets the Authorization header on
// requests for its own endpoint (replacing any caller-supplied value, so
// exactly one is forwarded) and leaves every other request untouched. It always
// continues to the next handler and never logs.
func (c Config) Handle(req api.Request, _ api.Response) (next bool, reqCtx uint32) {
	if c.Matches(req.GetURI()) {
		req.Headers().Set("Authorization", c.header)
	}
	return true, 0
}
