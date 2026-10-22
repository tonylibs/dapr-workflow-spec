// Command dws-mid-basic is the Wasm guest behind daprd's middleware.http.wasm Component. It adds
// "Authorization: Basic ..." to requests on one Dapr HTTPEndpoint's invocation path and passes
// every other request through untouched.
//
// It is built with TinyGo 0.34.0 (see CLAUDE.md) and logs nothing, ever: the only text it emits
// is the field-and-rule message of a bad config, which names no value.
package main

import (
	"github.com/http-wasm/http-wasm-guest-tinygo/handler"
	"github.com/http-wasm/http-wasm-guest-tinygo/handler/api"

	"github.com/dws/dws-mid-basic/internal/basicauth"
)

func main() {
	cfg, err := basicauth.Parse(handler.Host.GetConfig())
	if err != nil {
		// Refuse to start. The handler is not registered, so even if the host keeps the module
		// loaded it fails requests instead of forwarding them without the credential.
		panic(err.Error())
	}
	handler.HandleRequestFn = handleRequest(cfg)
}

func handleRequest(cfg *basicauth.Config) api.HandleRequest {
	// Encode once: the credential is constant for the life of the module.
	header := cfg.HeaderValue()
	return func(req api.Request, _ api.Response) (bool, uint32) {
		if cfg.Match(req.GetURI()) {
			// Set, not Add: the forwarded request carries exactly one Authorization header.
			req.Headers().Set("Authorization", header)
		}
		return true, 0
	}
}
