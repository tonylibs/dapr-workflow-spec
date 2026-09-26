"""Offline regression checks; no Docker, credentials, or cluster access required."""

import contextlib
import io
import subprocess
import unittest
from types import SimpleNamespace
from unittest.mock import Mock, patch

import new_ssh_sandbox as helper


class CredentialSetupTests(unittest.TestCase):
    def test_reports_docker_stdout_when_stderr_is_empty(self):
        result = subprocess.CompletedProcess([], 127, stdout="exec: agent-auth-setup: not found", stderr="")
        with patch.dict(helper.os.environ, {"OPENAI_API_KEY": "test-secret"}, clear=True), patch.object(
            helper.subprocess, "run", return_value=result
        ):
            with self.assertRaisesRegex(RuntimeError, "agent-auth-setup: not found") as caught:
                helper.forward_agent_tokens("sandbox-test")
            self.assertIn("--pull-image", str(caught.exception))

    def test_reports_exit_code_without_output(self):
        result = subprocess.CompletedProcess([], 137, stdout="", stderr="")
        with patch.dict(helper.os.environ, {"OPENAI_API_KEY": "test-secret"}, clear=True), patch.object(
            helper.subprocess, "run", return_value=result
        ):
            with self.assertRaisesRegex(RuntimeError, "exit code 137"):
                helper.forward_agent_tokens("sandbox-test")

    def test_does_not_expose_tokens_in_failure_output(self):
        result = subprocess.CompletedProcess([], 1, stdout="", stderr="rejected test-secret")
        with patch.dict(helper.os.environ, {"OPENAI_API_KEY": "test-secret"}, clear=True), patch.object(
            helper.subprocess, "run", return_value=result
        ):
            with self.assertRaises(RuntimeError) as caught:
                helper.forward_agent_tokens("sandbox-test")
            self.assertNotIn("test-secret", str(caught.exception))


class StartupTests(unittest.TestCase):
    def test_waits_for_execd_before_provisioning_keys(self):
        ready = False

        def create(*args, **kwargs):
            nonlocal ready
            # SandboxSync.create only waits for execd when health checks are enabled.
            ready = not kwargs.get("skip_health_check", False)
            return sandbox

        def command_run(command):
            if not ready:
                raise ConnectionError("execd is still starting")
            return SimpleNamespace(exit_code=0)

        sandbox = SimpleNamespace(
            id="test-startup",
            commands=SimpleNamespace(run=command_run),
            files=SimpleNamespace(write_file=Mock()),
            kill=Mock(),
            close=Mock(),
        )
        with contextlib.ExitStack() as stack:
            stack.enter_context(patch.object(helper.sys, "argv", ["new_ssh_sandbox.py"]))
            stack.enter_context(patch.object(helper, "ensure_key_pair"))
            stack.enter_context(patch.object(helper.Path, "read_text", side_effect=[
                helper.DEFAULT_CONFIG.read_text(encoding="utf-8"),
                "ssh-ed25519 test-public-key",
            ]))
            stack.enter_context(patch.object(helper.SandboxSync, "create", side_effect=create))
            stack.enter_context(patch.object(helper, "forward_agent_tokens"))
            stack.enter_context(patch.object(helper, "allocate_ssh_port", return_value=22222))
            stack.enter_context(patch.object(helper, "docker_ip", return_value="172.17.0.2"))
            stack.enter_context(patch.object(helper, "run"))
            stack.enter_context(patch.object(helper, "wait_for_ssh"))
            output = stack.enter_context(contextlib.redirect_stdout(io.StringIO()))
            self.assertEqual(helper.main(), 0)
            self.assertIn("Sandbox created: test-startup", output.getvalue())


if __name__ == "__main__":
    unittest.main()
