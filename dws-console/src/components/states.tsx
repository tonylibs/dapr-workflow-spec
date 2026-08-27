import { Inbox } from "lucide-react";
import type { ComponentPropsWithoutRef, ReactNode } from "react";

/** The four render states each list/detail screen can demonstrate. */
export type ViewState = "data" | "loading" | "empty" | "error";
export const VIEW_STATES: ViewState[] = ["data", "loading", "empty", "error"];

/** Soft-circle empty state, shared across zero-workflow / zero-instance / zero-task views. */
export function EmptyState({
	title,
	children,
}: {
	title: string;
	children?: ReactNode;
}) {
	return (
		<div className="empty">
			<span className="empty-mark">
				<Inbox />
			</span>
			<h3>{title}</h3>
			{children && <p>{children}</p>}
		</div>
	);
}

/**
 * Inline banner shown above a table or below a form.
 *
 * Defaults to the error skin (400 bad filter / limit); `warn` and `success`
 * carry their own tint so an operator can tell the three outcomes apart at a
 * glance.
 */
export function Banner({
	variant = "error",
	children,
	action,
	...rest
}: {
	variant?: "error" | "warn" | "success";
	children: ReactNode;
	action?: ReactNode;
} & Omit<ComponentPropsWithoutRef<"div">, "children">) {
	return (
		<div
			className={`banner${variant === "error" ? "" : ` ${variant}`}`}
			{...rest}
		>
			<span>{children}</span>
			{action && <span className="banner-action">{action}</span>}
		</div>
	);
}

/**
 * Demo-only control: lets a reviewer flip a screen between its data / loading /
 * empty / error states. In the live console these states are driven by
 * TanStack Query (isPending / empty result / error), not a manual toggle.
 */
export function StateSwitch({
	value,
	onChange,
}: {
	value: ViewState;
	onChange: (s: ViewState) => void;
}) {
	return (
		<div className="filters" style={{ gap: 4 }}>
			<span className="muted" style={{ fontSize: 11, marginRight: 2 }}>
				state:
			</span>
			{VIEW_STATES.map((s) => (
				<button
					key={s}
					type="button"
					className={`fchip${value === s ? " active" : ""}`}
					style={{ fontSize: 11, padding: "4px 10px" }}
					onClick={() => onChange(s)}
				>
					{s}
				</button>
			))}
		</div>
	);
}
