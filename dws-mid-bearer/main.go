// Command dws-mid-bearer is a Dapr middleware.http.wasm guest that sets
// "Authorization: Bearer <token>" on service invocations of one HTTPEndpoint.
//
// Build it with TinyGo 0.34.0 only (see the Dockerfile): 0.35 and later break
// http-wasm-guest-tinygo v0.4.0 with "module closed with exit_code(0)".
package main

import (
	"github.com/http-wasm/http-wasm-guest-tinygo/handler"

	"github.com/dws/dws-mid-bearer/internal/bearer"
)

func main() {
	cfg, err := bearer.Parse(handler.Host.GetConfig())
	if err != nil {
		// Fail closed: aborting init makes daprd refuse to load the component,
		// so a call is never sent without credentials. The message is a fixed
		// string and never contains the config.
		panic("dws-mid-bearer: " + err.Error())
	}
	handler.HandleRequestFn = cfg.Handle
}
