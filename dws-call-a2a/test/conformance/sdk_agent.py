"""Tier (c): a tiny real agent built with the *official* a2a-sdk server
components (`AgentExecutor`, `TaskUpdater`, `DefaultRequestHandlerV2`,
`create_jsonrpc_routes`, `create_agent_card_routes`). This is the oracle tier
(ADR 0004 Consequences): unlike `test/fake_server/server.py` (hand-rolled JSON
dicts, which encodes this project's own understanding of the wire format),
every response here is serialized by the SDK's own server-side code, so a
misreading in this runner's client-side code cannot also be baked into the
thing it's tested against.
"""

from __future__ import annotations

from a2a.server.agent_execution import AgentExecutor, RequestContext
from a2a.server.events import EventQueue
from a2a.server.request_handlers.default_request_handler_v2 import DefaultRequestHandlerV2
from a2a.server.routes.agent_card_routes import create_agent_card_routes
from a2a.server.routes.jsonrpc_routes import create_jsonrpc_routes
from a2a.server.tasks.inmemory_task_store import InMemoryTaskStore
from a2a.server.tasks.task_updater import TaskUpdater
from a2a.types import (
    AgentCapabilities,
    AgentCard,
    AgentInterface,
    Part,
    Task,
    TaskState,
    TaskStatus,
)
from fastapi import FastAPI

_RPC_PATH = "/rpc"


class ScriptedAgentExecutor(AgentExecutor):
    """Multi-turn: the first message on a task asks a clarifying question
    (`input-required`); any resuming message completes the task, echoing the
    resumed text into an artifact. A special trigger text produces
    `auth-required` instead."""

    async def execute(self, context: RequestContext, event_queue: EventQueue) -> None:
        assert context.task_id is not None
        assert context.context_id is not None
        updater = TaskUpdater(event_queue, context.task_id, context.context_id)
        text = _first_text(context)

        if context.current_task is None:
            # The framework requires the *first* event for a brand-new task
            # to be an actual `Task` object -- `TaskUpdater.submit()`/
            # `.start_work()` only ever enqueue `TaskStatusUpdateEvent`s, so
            # without this the framework rejects the first status update
            # with "Agent should enqueue Task before TaskStatusUpdateEvent
            # event". Not documented anywhere obvious; found by running this
            # conformance tier against the real SDK.
            initial_task = Task(id=context.task_id, context_id=context.context_id)
            initial_task.status.CopyFrom(TaskStatus(state=TaskState.TASK_STATE_SUBMITTED))
            await event_queue.enqueue_event(initial_task)

            if text == "trigger-auth-required":
                await updater.requires_auth(
                    updater.new_agent_message([Part(text="please authenticate")])
                )
                return
            await updater.start_work()
            await updater.requires_input(
                updater.new_agent_message([Part(text="what is your favorite color?")])
            )
            return

        await updater.add_artifact([Part(text=f"received: {text}")], name="answer")
        await updater.complete()

    async def cancel(self, context: RequestContext, event_queue: EventQueue) -> None:
        assert context.task_id is not None
        assert context.context_id is not None
        updater = TaskUpdater(event_queue, context.task_id, context.context_id)
        await updater.cancel()


def _first_text(context: RequestContext) -> str | None:
    message = context.message
    if message is None:
        return None
    for part in message.parts:
        if part.WhichOneof("content") == "text":
            return part.text
    return None


def build_sdk_agent_app(
    *, base_url: str = "https://conformance-agent.example.com", enable_v0_3_compat: bool = True
) -> FastAPI:
    """`enable_v0_3_compat=True` (the default here) makes the JSON-RPC route
    speak the legacy v0.3-compatible dialect -- matching what this runner's
    `is_legacy_version` dialect selection expects for a `protocolVersion:
    "0.3.0"` interface, and matching the predominant real-world A2A wire
    convention. Set `False` to conformance-test the modern protobuf-JSON
    dialect instead (see `test_conformance.py`).
    """
    rpc_url = f"{base_url}{_RPC_PATH}"
    protocol_version = "0.3.0" if enable_v0_3_compat else "1.0"

    card = AgentCard(
        name="sdk-conformance-agent",
        description="tiny real agent built from official a2a-sdk server components",
        version="1.0.0",
        default_input_modes=["text"],
        default_output_modes=["text"],
        capabilities=AgentCapabilities(),
        skills=[],
        supported_interfaces=[
            AgentInterface(
                url=rpc_url, protocol_binding="JSONRPC", protocol_version=protocol_version
            ),
        ],
    )

    handler = DefaultRequestHandlerV2(
        agent_executor=ScriptedAgentExecutor(),
        task_store=InMemoryTaskStore(),
        agent_card=card,
    )

    app = FastAPI()
    for route in create_agent_card_routes(card):
        app.router.routes.append(route)
    for route in create_jsonrpc_routes(
        handler, rpc_url=_RPC_PATH, enable_v0_3_compat=enable_v0_3_compat
    ):
        app.router.routes.append(route)
    return app
