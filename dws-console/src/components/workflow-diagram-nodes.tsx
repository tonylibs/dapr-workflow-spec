import { Handle, type Node, type NodeProps, Position } from "@xyflow/react";
import type { CSSProperties } from "react";
import { TaskTypeBadge } from "#/components/status";
import type { DefinitionGraphNode } from "#/lib/definition-graph-model";
import type { TaskType } from "#/lib/mock-data";

export type DiagramNodeData = DefinitionGraphNode & { errorCount: number };
export type DiagramNode = Node<DiagramNodeData>;

const HIDDEN_HANDLE: CSSProperties = { visibility: "hidden" };

const CONTAINER_STYLE: CSSProperties = {
	width: "100%",
	height: "100%",
	borderRadius: 18,
	border: "1.5px dashed var(--color-divider)",
	backgroundColor: "color-mix(in srgb, var(--color-surface) 40%, transparent)",
	padding: "10px 14px",
	boxSizing: "border-box",
};

const CONTAINER_HEADER_STYLE: CSSProperties = {
	display: "flex",
	alignItems: "center",
	gap: 6,
	fontSize: 11,
	fontWeight: 700,
	textTransform: "uppercase",
	letterSpacing: "0.05em",
	color: "color-mix(in srgb, var(--color-text) 60%, transparent)",
};

const TERMINAL_STYLE: CSSProperties = {
	width: "100%",
	height: "100%",
	borderRadius: 999,
	border: "1.5px solid var(--color-divider)",
	backgroundColor: "var(--color-surface)",
	display: "flex",
	alignItems: "center",
	justifyContent: "center",
	fontSize: 12,
	fontWeight: 600,
	letterSpacing: "0.05em",
	textTransform: "uppercase",
};

const ERROR_BADGE_STYLE: CSSProperties = {
	marginLeft: "auto",
	background: "var(--color-fail)",
	color: "#ffffff",
	borderRadius: 999,
	padding: "1px 6px",
	fontSize: 11,
	fontWeight: 700,
};

const NAME_STYLE: CSSProperties = {
	overflow: "hidden",
	textOverflow: "ellipsis",
	whiteSpace: "nowrap",
};

// The SDK emits task types beyond the mock `TaskType` union (`for`, `fork`, `try-catch`, ...);
// with `showIcon={false}` the badge only prints the string, so the cast is safe.
function TypeBadge({ taskType }: { taskType: string }) {
	return <TaskTypeBadge type={taskType as TaskType} showIcon={false} />;
}

function TaskNode({ id, data }: NodeProps<DiagramNode>) {
	return (
		<div
			className="wf-node-card"
			style={{ width: "100%", cursor: "default", position: "relative" }}
		>
			<Handle type="target" position={Position.Top} style={HIDDEN_HANDLE} />
			<div style={{ display: "flex", alignItems: "center", gap: 6 }}>
				<TypeBadge taskType={data.taskType} />
				{data.errorCount > 0 && (
					<span data-testid={`error-badge-${id}`} style={ERROR_BADGE_STYLE}>
						{data.errorCount} error{data.errorCount > 1 ? "s" : ""}
					</span>
				)}
			</div>
			<div className="name" style={NAME_STYLE}>
				{data.name}
			</div>
			<Handle type="source" position={Position.Bottom} style={HIDDEN_HANDLE} />
		</div>
	);
}

function ContainerNode({ data }: NodeProps<DiagramNode>) {
	return (
		<div style={CONTAINER_STYLE}>
			<Handle type="target" position={Position.Top} style={HIDDEN_HANDLE} />
			<div style={CONTAINER_HEADER_STYLE}>
				<TypeBadge taskType={data.taskType} />
				<span>{data.name}</span>
			</div>
			<Handle type="source" position={Position.Bottom} style={HIDDEN_HANDLE} />
		</div>
	);
}

function StartEndNode({ data }: NodeProps<DiagramNode>) {
	return (
		<div style={TERMINAL_STYLE}>
			{data.kind === "start" ? (
				<Handle
					type="source"
					position={Position.Bottom}
					style={HIDDEN_HANDLE}
				/>
			) : (
				<Handle type="target" position={Position.Top} style={HIDDEN_HANDLE} />
			)}
			{data.name}
		</div>
	);
}

export const nodeTypes = {
	taskNode: TaskNode,
	containerNode: ContainerNode,
	startEndNode: StartEndNode,
};
