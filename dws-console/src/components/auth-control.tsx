import { LogIn, LogOut } from "lucide-react";
import { useAuth } from "#/lib/oidc";
import { Button } from "./ui/button";

/**
 * Sign-in / signed-in-identity control for the topbar (auth roadmap Phase 1).
 *
 * Rendered by `AppLayout` so it appears on every screen without each route
 * wiring it up. Login is purely additive at this phase: nothing here gates a
 * route or a read, so when the IdP is unreachable the console reports that
 * authentication is unavailable while the rest of the UI carries on unchanged.
 */
export function AuthControl() {
	const auth = useAuth();

	if (auth.status === "initializing") {
		return null;
	}

	if (auth.status === "unavailable") {
		return (
			<output
				className="auth-identity"
				title="The configured identity provider could not be initialized"
			>
				Authentication unavailable
			</output>
		);
	}

	if (auth.status === "signed-out") {
		return (
			<Button size="sm" variant="outline" onClick={auth.signIn}>
				<LogIn /> Sign in
			</Button>
		);
	}

	return (
		<>
			<span className="auth-identity" title={auth.email ?? auth.subject}>
				{auth.displayName}
			</span>
			<Button
				size="sm"
				variant="ghost"
				onClick={auth.signOut}
				aria-label="Sign out"
			>
				<LogOut /> Sign out
			</Button>
		</>
	);
}
