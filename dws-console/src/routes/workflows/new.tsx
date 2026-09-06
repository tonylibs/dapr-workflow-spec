import { createFileRoute } from "@tanstack/react-router";
import { DefinitionEditor } from "#/components/definition-editor";

export const Route = createFileRoute("/workflows/new")({
	component: DefinitionEditor,
});
