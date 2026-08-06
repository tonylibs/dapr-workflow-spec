import {
	Equal,
	GitBranch,
	Phone,
	Radio,
	RotateCcw,
	Send,
	ShieldAlert,
	Terminal,
	Timer,
} from "lucide-react";
import type { ComponentType } from "react";
import {
	type DeploymentStatus,
	type InstanceStatus,
	statusClass,
	type TaskStatus,
	type TaskType,
	type WorkflowStatus,
} from "#/lib/mock-data";

/** Workflow status — filled pill. */
export function WorkflowTag({ status }: { status: WorkflowStatus }) {
	return <span className={`wf-tag ${statusClass(status)}`}>{status}</span>;
}

/** Deployment status — outline pill. */
export function DeploymentTag({ status }: { status: DeploymentStatus }) {
	return <span className={`dep-tag ${statusClass(status)}`}>{status}</span>;
}

/** Instance status — dot + text. */
export function InstanceStatusBadge({
	status,
	style,
}: {
	status: InstanceStatus;
	style?: React.CSSProperties;
}) {
	return (
		<span className={`inst-st ${statusClass(status)}`} style={style}>
			{status}
		</span>
	);
}

/** Task status — small dot + label. */
export function TaskStatusBadge({
	status,
	label,
}: {
	status: TaskStatus;
	label: string;
}) {
	return (
		<span className={`task-status ${statusClass(status)}`}>
			<span className="task-dot" />
			<span className="lbl">{label}</span>
		</span>
	);
}

const TYPE_ICON: Record<TaskType, ComponentType<{ className?: string }>> = {
	call: Phone,
	run: Terminal,
	switch: GitBranch,
	set: Equal,
	wait: Timer,
	listen: Radio,
	emit: Send,
	try: RotateCcw,
	catch: ShieldAlert,
};

/** Task-type chip with a lucide type icon. */
export function TaskTypeBadge({
	type,
	showIcon = true,
}: {
	type: TaskType;
	showIcon?: boolean;
}) {
	const Icon = TYPE_ICON[type];
	const extra =
		type === "switch" ? " type-switch" : type === "try" ? " type-try" : "";
	return (
		<span className={`task-type${extra}`}>
			{showIcon && <Icon />}
			{type}
		</span>
	);
}
