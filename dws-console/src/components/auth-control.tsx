import { LogIn, LogOut } from "lucide-react";
import { useAuth } from "#/lib/oidc";
import { Button } from "./ui/button";

/**
 * Sign-in / signed-in-identity control for the topbar (auth roadmap Phase 1).
 *
 * Rendered by `AppLayout` so it appears on every screen without each route
 * wiring it up. Login is purely additive at this phase: nothing here gates a
 * route or a read, so when the IdP is unreachable this collapses to nothing and
 * the rest of the console carries on unchanged.
 */
export function AuthControl() {
	const auth = useAuth();

	// Both states are non-actionable, and a dead "Sign in" button that can only
	// fail is worse than no button: render nothing.
	if (auth.status === "initializing" || auth.status === "unavailable") {
		return null;
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
