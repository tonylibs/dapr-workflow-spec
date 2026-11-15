# Changelog

## [0.3.0](https://github.com/tonylibs/dapr-workflow-spec/compare/dws-orchestrator-v0.2.0...dws-orchestrator-v0.3.0) (2026-11-14)


### Features

* **orchestrator:** idempotent start with caller-supplied instanceId ([6dfdbaa](https://github.com/tonylibs/dapr-workflow-spec/commit/6dfdbaabc69e37e458ac07adc692976248df1062))
* scheduling Phase 6a — time triggers (every/cron/after) via dws-schedule-time ([6046db6](https://github.com/tonylibs/dapr-workflow-spec/commit/6046db6664781aa02e5326730c8c09a038c7674b))

## [0.2.0](https://github.com/tonylibs/dapr-workflow-spec/compare/dws-orchestrator-v0.1.1...dws-orchestrator-v0.2.0) (2026-11-01)


### Features

* **orchestrator:** emit DWS structured JSON logs with trace correlation when DWS_LOG_FORMAT=json ([2a305d7](https://github.com/tonylibs/dapr-workflow-spec/commit/2a305d7deab418066533cc1ab5592cbffceffeb4))


### Documentation

* **openspec:** propose observability-phase4-log-trace-correlation ([1488723](https://github.com/tonylibs/dapr-workflow-spec/commit/14887238840cba7cb9c32a30cfe5feabdba241b5))
* **orchestrator:** record workflow-code logging rule and DWS_LOG_FORMAT contract ([60d3952](https://github.com/tonylibs/dapr-workflow-spec/commit/60d39522c2fab10aa44b417566c7eeaa57ec2068))

## [0.1.1](https://github.com/tonylibs/dapr-workflow-spec/compare/dws-orchestrator-v0.1.0...dws-orchestrator-v0.1.1) (2026-10-10)


### Bug Fixes

* **orchestrator:** rethrow SDK interruptions before catch-block side effects ([13b2d86](https://github.com/tonylibs/dapr-workflow-spec/commit/13b2d86b93a5ce2eb551a9044158080becf294cb))
