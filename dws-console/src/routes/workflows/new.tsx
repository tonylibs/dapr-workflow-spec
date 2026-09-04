import { json } from "@codemirror/lang-json";
import { yaml } from "@codemirror/lang-yaml";
import { EditorView } from "@codemirror/view";
import { createFileRoute, Link } from "@tanstack/react-router";
import CodeMirror from "@uiw/react-codemirror";
import { type ChangeEvent, useEffect, useMemo, useState } from "react";
import { AppLayout } from "#/components/app-layout";
import { Banner } from "#/components/states";
import {
	ApiError,
	AuthenticationError,
	type DefinitionSubmission,
	submitDefinition,
} from "#/lib/admin-client";
import {
	readDefinitionFile,
	useDefinitionDraftStore,
} from "#/lib/definition-draft-store";
import { useOidc } from "#/lib/oidc";

export const Route = createFileRoute("/workflows/new")({
	component: DefinitionEditor,
});

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

function DefinitionEditor() {
	const oidc = useOidc();
	const definition = useDefinitionDraftStore((state) => state.definition);
	const format = useDefinitionDraftStore((state) => state.format);
	const setDefinition = useDefinitionDraftStore(
		(state) => state.setDefinition,
	);
	const setFormat = useDefinitionDraftStore((state) => state.setFormat);
	const setDraft = useDefinitionDraftStore((state) => state.setDraft);
	const [outcome, setOutcome] = useState<DefinitionSubmission | undefined>();
	const [requestError, setRequestError] = useState<string | undefined>();
	const [importError, setImportError] = useState<string | undefined>();
	const [isSubmitting, setIsSubmitting] = useState(false);

	useEffect(() => {
		void useDefinitionDraftStore.persist.rehydrate();
	}, []);

	// Only the highlighting extension follows the format selector — the buffer is
	// never rewritten on toggle, so flipping YAML/JSON cannot mangle a draft
	// (design D3). Do not reformat here.
	const extensions = useMemo(
		() => [format === "yaml" ? yaml() : json(), editorTheme, editorLabel],
		[format],
	);

	const importDefinition = async (event: ChangeEvent<HTMLInputElement>) => {
		const input = event.currentTarget;
		const file = input.files?.[0];
		if (!file) return;

		setImportError(undefined);
		try {
			const draft = await readDefinitionFile(file);
			setDraft(draft.definition, draft.format);
		} catch (error) {
			setImportError(
				error instanceof Error
					? `Could not read the selected file: ${error.message}`
					: "Could not read the selected file.",
			);
		} finally {
			// Allow selecting the same file again after its contents have changed.
			input.value = "";
		}
	};

	const selectFormat = (event: ChangeEvent<HTMLSelectElement>) => {
		setFormat(event.target.value === "json" ? "json" : "yaml");
	};

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
						onChange={selectFormat}
					>
						<option value="yaml">YAML</option>
						<option value="json">JSON</option>
					</select>
					<label className="muted" htmlFor="definition-file">
						Import definition
					</label>
					<input
						id="definition-file"
						type="file"
						accept=".yaml,.yml,.json"
						onChange={importDefinition}
					/>
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
				{importError && <Banner>{importError}</Banner>}
				<CodeMirror
					value={definition}
					height="480px"
					extensions={extensions}
					onChange={setDefinition}
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
				<Link to="/workflows" className="btn-sm">
					← Back to workflows
				</Link>
			</div>
		</AppLayout>
	);
}
