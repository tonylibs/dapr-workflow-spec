package bearer

import "github.com/http-wasm/http-wasm-guest-tinygo/handler/api"

// Matches reports whether uri is on this endpoint's Dapr service-invocation
// path: /v1.0/invoke/<endpoint>/method, then "/", "?" or the end. The boundary
// check keeps "pets-api-2" and "/methods" from matching "pets-api" and
// "/method".
func (c Config) Matches(uri string) bool {
	prefix := "/v1.0/invoke/" + c.Endpoint + "/method"
	if len(uri) < len(prefix) || uri[:len(prefix)] != prefix {
		return false
	}
	if len(uri) == len(prefix) {
		return true
	}
	return uri[len(prefix)] == '/' || uri[len(prefix)] == '?'
}

// Handle is the http-wasm request handler. It sets the Authorization header on
// requests for its own endpoint (replacing any caller-supplied value, so
// exactly one is forwarded) and leaves every other request untouched. It always
// continues to the next handler and never logs.
func (c Config) Handle(req api.Request, _ api.Response) (next bool, reqCtx uint32) {
	if c.Matches(req.GetURI()) {
		req.Headers().Set("Authorization", "Bearer "+c.Token)
	}
	return true, 0
}
