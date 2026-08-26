import { json } from "@codemirror/lang-json";
import { yaml } from "@codemirror/lang-yaml";
import { EditorView } from "@codemirror/view";
import { createFileRoute, Link } from "@tanstack/react-router";
import CodeMirror from "@uiw/react-codemirror";
import { useMemo, useState } from "react";
import { AppLayout } from "#/components/app-layout";
import { Banner } from "#/components/states";
import { ApiError, type DefinitionSubmission, submitDefinition } from "#/lib/admin-client";
import { getOidc, useOidc } from "#/lib/oidc";

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

function DefinitionEditor() {
	const oidc = useOidc();
	const [definition, setDefinition] = useState("");
	const [format, setFormat] = useState<Format>("yaml");
	const [outcome, setOutcome] = useState<DefinitionSubmission | undefined>();
	const [requestError, setRequestError] = useState<string | undefined>();
	const [isSubmitting, setIsSubmitting] = useState(false);
	const extensions = useMemo(
		() => [format === "yaml" ? yaml() : json(), editorTheme],
		[format],
	);

	const submit = async () => {
		if (!oidc.isUserLoggedIn || !definition.trim()) return;
		setIsSubmitting(true);
		setOutcome(undefined);
		setRequestError(undefined);
		try {
			const authenticatedOidc = await getOidc({ assert: "user logged in" });
			setOutcome(
				await submitDefinition(definition, await authenticatedOidc.getAccessToken()),
			);
		} catch (error) {
			setRequestError(
				error instanceof ApiError ? error.message : "Could not reach dws-admin.",
			);
		} finally {
			setIsSubmitting(false);
		}
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
					<p className="pane-lede">Write or paste a DSL 1.0 YAML or JSON definition.</p>
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
					onChange={setDefinition}
					aria-label="Workflow definition"
				/>
				{outcome?.kind === "applied" && (
					<output className="banner" style={{ display: "block" }}>
						{outcome.result.created
							? `Applied ${outcome.result.workflow} (${outcome.result.version}).`
							: `${outcome.result.workflow} (${outcome.result.version}) is already applied.`}
					</output>
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
				<Link to="/workflows" className="btn-sm">
					← Back to workflows
				</Link>
			</div>
		</AppLayout>
	);
}
