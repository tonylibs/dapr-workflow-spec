"""Production entrypoint: `python -m dws_call_a2a`.

Config errors raised while building the app (a `ConfigError` from
`load_config`/boot-time card validation) propagate out of `create_app()`
before uvicorn starts serving, so the process exits non-zero -- the
fail-fast behavior the shared step-service contract expects.
"""

from __future__ import annotations

import logging

import uvicorn

from dws_call_a2a.config import load_config
from dws_call_a2a.main import create_app

logging.basicConfig(level=logging.INFO)

config = load_config()
app = create_app(config)

if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=config.port, log_level="info")  # noqa: S104 -- bind-all is required inside the container
