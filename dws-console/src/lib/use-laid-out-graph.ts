import { useEffect, useMemo, useState } from "react";
import type { DefinitionFormat } from "#/lib/definition-draft-store";
import { buildDefinitionGraph } from "./definition-graph-model";
import { type LaidOutGraph, layoutWorkflowGraph } from "./workflow-layout";

/** Quiet period after the last keystroke before the draft is rebuilt. */
const DIAGRAM_DEBOUNCE_MS = 300;

export type LaidOutGraphState = {
	/** The last graph that built and laid out successfully; kept while `error` is set (stale). */
	graph?: LaidOutGraph;
	/** Why the current draft could not be built, if it could not. */
	error?: string;
};

function useDebouncedValue<T>(value: T, delayMs: number): T {
	const [debounced, setDebounced] = useState(value);
	useEffect(() => {
		const timer = setTimeout(() => setDebounced(value), delayMs);
		return () => clearTimeout(timer);
	}, [value, delayMs]);
	return debounced;
}

/**
 * Builds and lays out the draft's graph after it stops changing.
 *
 * A failed build keeps the previous graph and reports the error (stale); a layout that finishes
 * after newer input arrived, or after unmount, is discarded.
 */
export function useLaidOutGraph(
	definition: string,
	format: DefinitionFormat,
): LaidOutGraphState {
	const input = useMemo(() => ({ definition, format }), [definition, format]);
	const debounced = useDebouncedValue(input, DIAGRAM_DEBOUNCE_MS);
	const [state, setState] = useState<LaidOutGraphState>({});

	useEffect(() => {
		if (!debounced.definition.trim()) {
			setState({});
			return;
		}

		const fail = (error: string) => setState((prev) => ({ ...prev, error }));
		const result = buildDefinitionGraph(debounced.definition, debounced.format);
		if (!result.ok) {
			fail(result.error);
			return;
		}

		let discarded = false;
		layoutWorkflowGraph(result.graph).then(
			(graph) => {
				if (!discarded) setState({ graph });
			},
			(error) => {
				if (!discarded) {
					fail(error instanceof Error ? error.message : String(error));
				}
			},
		);
		return () => {
			discarded = true;
		};
	}, [debounced]);

	return state;
}
