import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";

const { useAuthMock } = vi.hoisted(() => ({ useAuthMock: vi.fn() }));

vi.mock("#/lib/oidc", () => ({ useAuth: useAuthMock }));

import { AuthControl } from "./auth-control";

describe("AuthControl", () => {
	it("reports an OIDC initialization failure without hiding the console", () => {
		useAuthMock.mockReturnValue({ status: "unavailable" });

		const html = renderToStaticMarkup(<AuthControl />);

		expect(html).toContain("<output");
		expect(html).toContain("Authentication unavailable");
	});
});
