package io.github.qifan777.server.shared.markdown;

/** Markdown 代码块提取工具(对应原 MarkdownParserUtil)。 */
public final class MarkdownParserUtil {
    private MarkdownParserUtil() {}

    public static String extractText(String markdownCode) {
        String code = extractRawText(markdownCode);
        return code.replace("\r\n", " ").replace("\n", " ").replace("\r", " ");
    }

    public static String extractRawText(String markdownCode) {
        int startIndex = -1;
        int delimiterLength = 0;
        for (int i = 0; i <= markdownCode.length() - 3; i++) {
            if (markdownCode.regionMatches(i, "```", 0, 3)) {
                startIndex = i;
                delimiterLength = 3;
                while (i + delimiterLength < markdownCode.length()
                    && markdownCode.charAt(i + delimiterLength) == '`') {
                    delimiterLength++;
                }
                break;
            }
        }
        if (startIndex == -1) {
            return markdownCode;
        }
        int contentStart = startIndex + delimiterLength;
        while (contentStart < markdownCode.length() && markdownCode.charAt(contentStart) != '\n') {
            contentStart++;
        }
        if (contentStart < markdownCode.length() && markdownCode.charAt(contentStart) == '\n') {
            contentStart++;
        }
        String closing = "`".repeat(delimiterLength);
        int endIndex = markdownCode.indexOf(closing, contentStart);
        if (endIndex == -1) {
            return markdownCode.substring(contentStart);
        }
        return markdownCode.substring(contentStart, endIndex);
    }
}
