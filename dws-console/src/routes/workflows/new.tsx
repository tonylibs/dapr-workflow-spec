import { json } from "@codemirror/lang-json";
import { yaml } from "@codemirror/lang-yaml";
import { EditorView } from "@codemirror/view";
import { createFileRoute, Link } from "@tanstack/react-router";
import CodeMirror from "@uiw/react-codemirror";
import { useMemo, useState } from "react";
import { AppLayout } from "#/components/app-layout";
import { DeploymentPlanView } from "#/components/deployment-plan-view";
import { Banner } from "#/components/states";
import {
	ApiError,
	AuthenticationError,
	type DefinitionPreview,
	type DefinitionSubmission,
	previewDefinition,
	type SpecError,
	submitDefinition,
	validateDefinitionSpec,
} from "#/lib/admin-client";
import { useOidc } from "#/lib/oidc";

export const Route = createFileRoute("/workflows/new")({
	component: DefinitionEditor,
});

type Format = "yaml" | "json";

const editorTheme = EditorView.theme({
	"&": {
		backgroundColor: "var(--color-bg)",
		color: "var(--color-text)",
		border: "1px solid var(--color-divider)",
		borderRadius: "8px",
		fontSize: "14px",
	},
	".cm-gutters": {
		backgroundColor: "var(--color-surface)",
		color: "var(--color-neutral-600)",
		borderRight: "1px solid var(--color-divider)",
	},
	".cm-activeLine, .cm-activeLineGutter": {
		backgroundColor: "color-mix(in srgb, var(--color-accent) 10%, transparent)",
	},
	".cm-cursor": { borderLeftColor: "var(--color-accent)" },
	".cm-selectionBackground, &.cm-focused .cm-selectionBackground": {
		backgroundColor: "color-mix(in srgb, var(--color-accent) 25%, transparent)",
	},
});

/**
 * Names the editable region for assistive tech.
 *
 * An `aria-label` prop on `<CodeMirror>` lands on the outer wrapper div; the
 * element that actually takes focus is the contentEditable inside, reachable
 * only through `contentAttributes`.
 */
const editorLabel = EditorView.contentAttributes.of({
	"aria-label": "Workflow definition",
});

/**
 * Exported for its own tests: the route registration above owns how this
 * component is reached, not what it does, and the preview behaviour is worth
 * asserting without standing up a router.
 */
export function DefinitionEditor() {
	const oidc = useOidc();
	const [definition, setDefinition] = useState("");
	const [format, setFormat] = useState<Format>("yaml");
	const [outcome, setOutcome] = useState<DefinitionSubmission | undefined>();
	const [requestError, setRequestError] = useState<string | undefined>();
	const [isSubmitting, setIsSubmitting] = useState(false);
	const [preview, setPreview] = useState<DefinitionPreview | undefined>();
	const [specErrors, setSpecErrors] = useState<SpecError[] | undefined>();
	const [isPreviewing, setIsPreviewing] = useState(false);
	// Only the highlighting extension follows the format selector — the buffer is
	// never rewritten on toggle, so flipping YAML/JSON cannot mangle a draft
	// (design D3). Do not reformat here.
	const extensions = useMemo(
		() => [format === "yaml" ? yaml() : json(), editorTheme, editorLabel],
		[format],
	);

	const submit = async () => {
		if (!oidc.isUserLoggedIn || !definition.trim()) return;
		setIsSubmitting(true);
		setOutcome(undefined);
		setRequestError(undefined);
		try {
			// The centralized transport acquires the current bearer token itself
			// (design D6); this route no longer touches the OIDC client directly.
			setOutcome(await submitDefinition(definition));
		} catch (error) {
			if (error instanceof AuthenticationError) {
				setRequestError("Your session has expired. Sign in again to submit.");
			} else {
				setRequestError(
					error instanceof ApiError
						? error.message
						: "Could not reach dws-admin.",
				);
			}
		} finally {
			setIsSubmitting(false);
		}
	};

	/**
	 * Two layers, in order: spec conformance in dws-admin (fast, local, gives
	 * field paths), then — only if that passes — dws-controller's compile-only
	 * dry run for deployability. A document that fails layer 1 would fail layer 2
	 * too, with strictly worse feedback, so the controller is never asked.
	 */
	const runPreview = async () => {
		if (!oidc.isUserLoggedIn || !definition.trim()) return;
		setIsPreviewing(true);
		setPreview(undefined);
		setSpecErrors(undefined);
		setRequestError(undefined);
		try {
			const report = await validateDefinitionSpec(definition, format);
			if (!report.valid) {
				setSpecErrors(report.errors);
				return;
			}
			setPreview(await previewDefinition(definition));
		} catch (error) {
			if (error instanceof AuthenticationError) {
				setRequestError("Your session has expired. Sign in again to preview.");
			} else {
				setRequestError(
					error instanceof ApiError
						? error.message
						: "Could not reach dws-admin.",
				);
			}
		} finally {
			setIsPreviewing(false);
		}
	};

	// A plan describes the buffer that produced it; once the text moves, showing
	// it would assert something no longer true.
	const onDefinitionChange = (next: string) => {
		setDefinition(next);
		setPreview(undefined);
		setSpecErrors(undefined);
	};

	const canSubmit = oidc.isUserLoggedIn && definition.trim().length > 0;

	return (
		<AppLayout
			active="workflows"
			crumbs={[
				{ label: "Workflows", to: "/workflows" },
				{ label: "New definition", heading: true },
			]}
		>
			<div className="pane" style={{ gap: 16 }}>
				<div>
					<h2 className="pane-title">Definition editor</h2>
					<p className="pane-lede">
						Write or paste a DSL 1.0 YAML or JSON definition.
					</p>
				</div>
				<div style={{ display: "flex", alignItems: "center", gap: 10 }}>
					<label className="muted" htmlFor="definition-format">
						Format
					</label>
					<select
						id="definition-format"
						value={format}
						onChange={(event) => setFormat(event.target.value as Format)}
					>
						<option value="yaml">YAML</option>
						<option value="json">JSON</option>
					</select>
					<button
						type="button"
						className="btn-sm"
						disabled={!canSubmit || isPreviewing || isSubmitting}
						onClick={runPreview}
					>
						{isPreviewing ? "Checking…" : "Preview"}
					</button>
					<button
						type="button"
						className="btn-sm primary"
						disabled={!canSubmit || isSubmitting}
						onClick={submit}
					>
						{isSubmitting ? "Submitting…" : "Submit definition"}
					</button>
				</div>
				{!oidc.isUserLoggedIn && (
					<Banner variant="warn">Sign in to submit a definition.</Banner>
				)}
				<CodeMirror
					value={definition}
					height="480px"
					extensions={extensions}
					onChange={onDefinitionChange}
				/>
				{outcome?.kind === "applied" && (
					<Banner variant="success" role="status">
						{outcome.result.created
							? `Applied ${outcome.result.versionId}.`
							: `${outcome.result.versionId} is already applied.`}
					</Banner>
				)}
				{outcome?.kind === "validation-error" && (
					<Banner>
						<ul>
							{outcome.errors.map((error) => (
								<li key={error}>{error}</li>
							))}
						</ul>
					</Banner>
				)}
				{requestError && <Banner>{requestError}</Banner>}
				{specErrors && (
					<Banner>
						<p>This is not a valid DSL 1.0 definition.</p>
						<ul>
							{specErrors.map((error) => (
								<li key={`${error.path}:${error.message}:${error.line ?? ""}`}>
									<code>{error.path || "(document)"}</code> — {error.message}
									{error.line !== undefined &&
										` (line ${error.line}, column ${error.column})`}
								</li>
							))}
						</ul>
					</Banner>
				)}
				{preview?.kind === "deploy-error" && (
					<Banner variant="warn">
						<p>Valid DSL, but this cluster cannot deploy it.</p>
						<ul>
							{preview.errors.map((error) => (
								<li key={error}>{error}</li>
							))}
						</ul>
					</Banner>
				)}
				{preview?.kind === "plan" && <DeploymentPlanView plan={preview.plan} />}
				<Link to="/workflows" className="btn-sm">
					← Back to workflows
				</Link>
			</div>
		</AppLayout>
	);
}
