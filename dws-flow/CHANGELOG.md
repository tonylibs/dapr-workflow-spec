# Changelog

## [0.3.0](https://github.com/tonylibs/dapr-workflow-spec/compare/dws-flow-v0.2.0...dws-flow-v0.3.0) (2026-10-19)


### Features

* **flow:** collect the use names a node references ([c743451](https://github.com/tonylibs/dapr-workflow-spec/commit/c74345188392effa08ff40eaa9cdf28b81f31a63))
* **flow:** load referenced use entries at boot and fail on a bad one ([5111b0a](https://github.com/tonylibs/dapr-workflow-spec/commit/5111b0a16b45b63b42bb048af7d8653b29d77b63))
* **flow:** load use entries from the Dapr configuration store at boot ([f83b28f](https://github.com/tonylibs/dapr-workflow-spec/commit/f83b28f544e358f3f2222843b9c65ecb25ac8df7))
* **flow:** resolve a named task timeout through the use resolver ([93650b9](https://github.com/tonylibs/dapr-workflow-spec/commit/93650b904557b138efb55c8e9c5698b1cbc30ad4))
* **flow:** resolve named use entries at boot through a .NET UseResolver (phase 3i) ([963b8dc](https://github.com/tonylibs/dapr-workflow-spec/commit/963b8dcb1fd8aaf45636a0a18ee7238c8bef2d14))
* **flow:** resolve use entries inline or by name in one place ([99a2eb8](https://github.com/tonylibs/dapr-workflow-spec/commit/99a2eb8992b2302b31f9a0bafe05b204c0e681cc))


### Bug Fixes

* **flow:** reject duplicate-key use entries at boot ([0c4be4b](https://github.com/tonylibs/dapr-workflow-spec/commit/0c4be4bacc4d7324094473e0e987f45f032ec302))
* **flow:** reject newline-suffixed use names and overflowing timeout entries ([1646c7e](https://github.com/tonylibs/dapr-workflow-spec/commit/1646c7ef6532cf7b702437942901f366d2d38036))

## [0.2.0](https://github.com/tonylibs/dapr-workflow-spec/compare/dws-flow-v0.1.0...dws-flow-v0.2.0) (2026-10-05)


### Features

* **flow:** dws-flow core ([d722370](https://github.com/tonylibs/dapr-workflow-spec/commit/d7223706ae82e51784f09925fd1bb4f5e35dc579))


### Bug Fixes

* **flow:** publish dws-flow.csproj explicitly in Dockerfile ([0058c56](https://github.com/tonylibs/dapr-workflow-spec/commit/0058c56c3af21abae7508653f0ef21ccf32cded8))
