package io.dws.controller.compile;

/** Naming helpers shared by the compile and apply passes. */
public final class Names {

    private Names() {}

    /** RFC-1123-ish kebab-case: camelCase boundaries and non-alphanumerics become single dashes. */
    public static String kebab(String input) {
        StringBuilder out = new StringBuilder(input.length() + 8);
        char[] chars = input.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (Character.isUpperCase(c)) {
                appendDash(out);
                out.append(Character.toLowerCase(c));
            } else if (Character.isLetterOrDigit(c)) {
                out.append(c);
            } else {
                appendDash(out);
            }
        }
        int end = out.length();
        while (end > 0 && out.charAt(end - 1) == '-') {
            end--;
        }
        return out.substring(0, end);
    }

    private static void appendDash(StringBuilder out) {
        if (out.length() > 0 && out.charAt(out.length() - 1) != '-') {
            out.append('-');
        }
    }

    public static String definitionResource(String workflow, String versionId) {
        return "dws-def-" + workflow + "-" + versionId;
    }

    public static String orchestrator(String workflow, String versionId) {
        return workflow + "-" + versionId;
    }
}
