module github.com/dws/dws-mid-bearer

// 1.23 is the newest language version TinyGo 0.34.0 builds; keep it there
// (the other Go packages use 1.26.x, which the pinned TinyGo cannot compile).
go 1.23

require github.com/http-wasm/http-wasm-guest-tinygo v0.4.0
