import { Link } from "@tanstack/react-router";
import { Clock, List, ShieldCheck } from "lucide-react";
import type { ReactNode } from "react";
import { AuthControl } from "./auth-control";

export type NavKey = "workflows" | "instances" | "controller";

interface Crumb {
	label: string;
	to?: string;
	mono?: boolean;
	heading?: boolean;
}

interface AppLayoutProps {
	active: NavKey;
	crumbs: Crumb[];
	topRight?: ReactNode;
	children: ReactNode;
}

export function AppLayout({
	active,
	crumbs,
	topRight,
	children,
}: AppLayoutProps) {
	return (
		<div className="app">
			<aside className="side">
				<div className="side-brand">
					<span className="side-brand-dot" />
					dws
				</div>

				<div className="side-section">Catalog</div>
				<Link
					to="/workflows"
					className={`side-item${active === "workflows" ? " active" : ""}`}
				>
					<List /> Workflows
				</Link>
				<Link
					to="/instances"
					className={`side-item${active === "instances" ? " active" : ""}`}
				>
					<Clock /> Instances
				</Link>

				<div className="side-section">System</div>
				<span
					className={`side-item${active === "controller" ? " active" : ""}`}
				>
					<ShieldCheck /> Controller
				</span>

				<div className="side-foot">
					<span className="side-foot-dot" /> controller ok
				</div>
			</aside>

			<div className="main">
				<div className="topbar">
					<div className="crumbs">
						{crumbs.map((c, i) => {
							const cls = [
								c.mono ? "mono" : "",
								c.heading ? "here" : "",
								i === crumbs.length - 1 ? "here" : "",
							]
								.filter(Boolean)
								.join(" ");
							const style = c.heading
								? { fontFamily: "var(--font-heading)", fontSize: 15 }
								: undefined;
							return (
								// biome-ignore lint/suspicious/noArrayIndexKey: breadcrumb trail is a fixed positional list
								<span key={`crumb-${i}`} style={{ display: "contents" }}>
									{i > 0 && <span className="sep">/</span>}
									{c.to ? (
										<Link to={c.to}>{c.label}</Link>
									) : (
										<span className={cls} style={style}>
											{c.label}
										</span>
									)}
								</span>
							);
						})}
					</div>
					{/* Always rendered: the auth control belongs on every screen, so this
					    is no longer conditional on a route supplying `topRight`. */}
					<div className="top-right">
						{topRight}
						<AuthControl />
					</div>
				</div>

				{children}
			</div>
		</div>
	);
}
