// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
	DEFINITION_DRAFT_STORAGE_KEY,
	definitionDraftDefaults,
	inferDefinitionFormat,
	readDefinitionFile,
	useDefinitionDraftStore,
} from "./definition-draft-store";

function resetDraft() {
	localStorage.clear();
	useDefinitionDraftStore.setState(definitionDraftDefaults);
}

beforeEach(resetDraft);
afterEach(resetDraft);

describe("definition draft store", () => {
	it("hydrates to an empty YAML draft when browser storage has no draft", async () => {
		await useDefinitionDraftStore.persist.rehydrate();

		expect(useDefinitionDraftStore.getState()).toMatchObject(
			definitionDraftDefaults,
		);
	});

	it("loads selected file text and infers JSON case-insensitively", async () => {
		const file = {
			name: "shipping-workflow.JSON",
			text: vi.fn().mockResolvedValue('{"document":{"dsl":"1.0"}}'),
		};

		const draft = await readDefinitionFile(file);
		useDefinitionDraftStore.getState().setDraft(draft.definition, draft.format);

		expect(file.text).toHaveBeenCalledOnce();
		expect(useDefinitionDraftStore.getState()).toMatchObject({
			definition: '{"document":{"dsl":"1.0"}}',
			format: "json",
		});
	});

	it("uses YAML for YAML and every non-JSON filename", () => {
		expect(inferDefinitionFormat("shipping-workflow.yml")).toBe("yaml");
		expect(inferDefinitionFormat("shipping-workflow.yaml")).toBe("yaml");
		expect(inferDefinitionFormat("shipping-workflow.txt")).toBe("yaml");
	});

	it("keeps an imported draft editable and persists the edited values", async () => {
		const imported = await readDefinitionFile({
			name: "shipping-workflow.json",
			text: vi.fn().mockResolvedValue('{"name":"shipping"}'),
		});
		const store = useDefinitionDraftStore.getState();
		store.setDraft(imported.definition, imported.format);
		store.setDefinition('{"name":"shipping","version":"2"}');
		store.setFormat("yaml");

		expect(useDefinitionDraftStore.getState()).toMatchObject({
			definition: '{"name":"shipping","version":"2"}',
			format: "yaml",
		});
		expect(JSON.parse(localStorage.getItem(DEFINITION_DRAFT_STORAGE_KEY) ?? "{}"))
			.toMatchObject({
				state: {
					definition: '{"name":"shipping","version":"2"}',
					format: "yaml",
				},
			});

		const savedDraft = localStorage.getItem(DEFINITION_DRAFT_STORAGE_KEY);
		useDefinitionDraftStore.setState(definitionDraftDefaults);
		localStorage.setItem(DEFINITION_DRAFT_STORAGE_KEY, savedDraft ?? "");
		await useDefinitionDraftStore.persist.rehydrate();

		expect(useDefinitionDraftStore.getState()).toMatchObject({
			definition: '{"name":"shipping","version":"2"}',
			format: "yaml",
		});
	});

	it("hydrates a persisted imported draft", async () => {
		localStorage.setItem(
			DEFINITION_DRAFT_STORAGE_KEY,
			JSON.stringify({
				state: {
					definition: '{"name":"shipping"}',
					format: "json",
				},
				version: 0,
			}),
		);

		await useDefinitionDraftStore.persist.rehydrate();

		expect(useDefinitionDraftStore.getState()).toMatchObject({
			definition: '{"name":"shipping"}',
			format: "json",
		});
	});
});
