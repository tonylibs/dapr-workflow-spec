import { create } from "zustand";
import { persist } from "zustand/middleware";

export type DefinitionFormat = "yaml" | "json";

export interface DefinitionDraft {
	definition: string;
	format: DefinitionFormat;
}

export type DefinitionSourceFile = Pick<File, "name" | "text">;

interface DefinitionDraftStore extends DefinitionDraft {
	setDefinition: (definition: string) => void;
	setFormat: (format: DefinitionFormat) => void;
	setDraft: (definition: string, format: DefinitionFormat) => void;
}

export const DEFINITION_DRAFT_STORAGE_KEY = "dws:draft";

export const definitionDraftDefaults: DefinitionDraft = {
	definition: "",
	format: "yaml",
};

export function inferDefinitionFormat(filename: string): DefinitionFormat {
	return filename.toLowerCase().endsWith(".json") ? "json" : "yaml";
}

export async function readDefinitionFile(
	file: DefinitionSourceFile,
): Promise<DefinitionDraft> {
	return {
		definition: await file.text(),
		format: inferDefinitionFormat(file.name),
	};
}

export const useDefinitionDraftStore = create<DefinitionDraftStore>()(
	persist(
		(set) => ({
			...definitionDraftDefaults,
			setDefinition: (definition) => set({ definition }),
			setFormat: (format) => set({ format }),
			setDraft: (definition, format) => set({ definition, format }),
		}),
		{
			name: DEFINITION_DRAFT_STORAGE_KEY,
			// Server rendering must begin with the same defaults as the browser.
			// The editor explicitly hydrates after mounting.
			skipHydration: true,
		},
	),
);
