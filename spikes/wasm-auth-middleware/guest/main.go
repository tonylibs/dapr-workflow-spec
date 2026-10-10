package main

import (
	"encoding/base64"
	"encoding/json"
	"strings"

	"github.com/http-wasm/http-wasm-guest-tinygo/handler"
	"github.com/http-wasm/http-wasm-guest-tinygo/handler/api"
)

type config struct {
	Scheme   string `json:"scheme"`
	Endpoint string `json:"endpoint"`
	Path     string `json:"path"`
	Token    string `json:"token"`
	Username string `json:"username"`
	Password string `json:"password"`
}

var policy config

func main() {
	if err := json.Unmarshal(handler.Host.GetConfig(), &policy); err != nil {
		panic("invalid guestConfig")
	}
	handler.HandleRequestFn = handleRequest
}

func handleRequest(req api.Request, _ api.Response) (bool, uint32) {
	uri := req.GetURI()
	want := "/v1.0/invoke/" + policy.Endpoint + "/method" + policy.Path
	handler.Host.Log(api.LogLevelInfo, "auth-spike uri="+uri+" target="+want)
	if uri != want && !strings.HasPrefix(uri, want+"?") {
		return true, 0
	}
	switch policy.Scheme {
	case "bearer":
		req.Headers().Set("Authorization", "Bearer "+policy.Token)
	case "basic":
		req.Headers().Set("Authorization", "Basic "+base64.StdEncoding.EncodeToString([]byte(policy.Username+":"+policy.Password)))
	}
	return true, 0
}
