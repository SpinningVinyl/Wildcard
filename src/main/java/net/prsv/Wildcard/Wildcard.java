package net.prsv.Wildcard;

import java.util.*;

/**
 * <p>Wildcard is my own implementation of glob pattern matching. Glob patterns specify sets of strings with wildcard
 * characters. The {@link #match(String, String)} method returns {@code true} if the specified string matches the
 * pattern.</p>
 *
 * <p>This implementation supports the following wildcards:</p>
 * <ul>
 *     <li><b>*</b> (star), which matches any number of any characters including none;</li>
 *     <li><b>?</b> (question mark), which matches any single character;</li>
 *     <li><b>[abc]</b> (brackets), which matches any single character given in the brackets. E.g., {@code [abc]}
 * would match either 'a', 'b' or 'c', but not 'd' or 'z';</li>
 *     <li><b>[a-z]</b> (brackets with ranges), which matches one character within the given range, e.g. {@code [a-z]}
 *     would match any character from 'a' to 'z', but not 'A' or '8'.</li>
 * </ul>
 *
 * <p>It is possible to combine single characters and ranges in one set of brackets, e.g. {@code [a-zZ0$]} would match
 * any of the following:</p>
 * <ul>
 *     <li>any character from 'a' to 'z';</li>
 *     <li>'Z' (capital z);</li>
 *     <li>'0' (zero);</li>
 *     <li>'$' (the dollar sign).</li>
 * </ul>
 *
 * <p>The bracket wildcard also supports <em>negation</em>. If the first character inside brackets is the exclamation
 * mark '!', the wildcard would match a single character that is <b>not</b> given in the bracket or is from the
 * given range.</p>
 *
 * <p>If special characters are escaped with a backslash '\', they are treated as literals. In other words, {@code "\?"}
 * matches a literal question mark, {@code "\-"} matches a literal dash, '\\' matches a literal backslash, etc.
 * Please note that '!' and '-' are not considered special characters outside of brackets. </p>
 *
 * <p>An unescaped backslash is always swallowed, both in ordinary patterns and inside bracket patterns. If it precedes
 * a character that does not form a recognized escape sequence, the backslash is discarded and the following character
 * is processed normally. Consequently, {@code \a} matches {@code a}, and a pattern consisting of a single trailing
 * backslash matches the empty string.</p>
 *
 * <p>Unlike Unix glob, which never matches the forward slash character '/', this implementation treats '/'
 * as a regular character.</p>
 *
 * <p>Wildcard matches Unicode code points rather than UTF-16 code units. Supplementary characters therefore count
 * as one character for question marks, bracket patterns, and ranges.</p>
 *
 * <p>Positions included in error messages are zero-based Unicode code-point offsets into the pattern, not UTF-16
 * code-unit indices. For example, a pattern element immediately following an emoji is at offset 1.</p>
 *
 * <p>Please take note that Wildcard will defensively replace all instances of the null terminator (U+0000) with
 * the Unicode replacement character (U+FFFD) in both text and pattern.</p>
 *
 * @author Pavel Urusov
 */
public final class Wildcard {

    private Wildcard() {
    }

    private enum GlobTokenType {
        LITERAL,
        ANY_CHAR,
        STAR,
        BRACKET
    }

    private static class CodePointRange {
        private final int from;
        private final int to;

        CodePointRange(int from, int to) {
            this.from = from;
            this.to = to;
        }

        boolean contains(int codePoint) {
            return codePoint >= from && codePoint <= to;
        }
    }

    private static class GlobToken {
        private final GlobTokenType type;
        private final int codePoint;
        private final Set<Integer> matchingCodePoints = new HashSet<>();
        private final List<CodePointRange> matchingRanges = new ArrayList<>();
        private final boolean negate;

        GlobToken(GlobTokenType tokenType, int codePoint, boolean negate) {
            this.type = tokenType;
            this.codePoint = codePoint;
            this.negate = negate;
        }

        GlobToken(int codePoint) {
            this(GlobTokenType.LITERAL, codePoint, false);
        }

        GlobToken(GlobTokenType tokenType) {
            this(tokenType, 0, false);
        }

        GlobToken(GlobTokenType tokenType, boolean negate) {
            this(tokenType, 0, negate);
        }

    }

    private static final Set<Integer> specialCodePoints = Set.of(
            (int) '[', (int) ']', (int) '*', (int) '-', (int) '!', (int) '?');


    private static Optional<GlobToken> parseBracketPattern(int[] bracketPattern) {

        if (bracketPattern.length == 0) {
            return Optional.empty();
        }

        int position = 0;
        boolean negate = false;

        if (bracketPattern[position] == '!') {
            negate = true;
            position++;
        }
        int contentStart = position;

        GlobToken result = new GlobToken(GlobTokenType.BRACKET, negate);

        while (position < bracketPattern.length) {
            int codePoint = bracketPattern[position];

            if (codePoint == '\\') {
                if (position + 1 < bracketPattern.length) {
                    int nextCodePoint = bracketPattern[position + 1];
                    // if the next character in the pattern is a special character or another slash,
                    if (specialCodePoints.contains(nextCodePoint) || nextCodePoint == codePoint) {
                        // add it to matching code points and skip to the next character
                        result.matchingCodePoints.add(nextCodePoint);
                        position += 1;
                    }
                    // otherwise do nothing -- swallow the backslash
                }
            } else if (specialCodePoints.contains(codePoint) && codePoint != '-') {
                // skip unescaped special characters EXCEPT dash
                position += 1;
            } else if (codePoint == '-' && position != contentStart && position + 1 != bracketPattern.length) {
                int fromCodePoint = bracketPattern[position - 1];
                int toCodePoint = bracketPattern[position + 1];
                // if the next character is escaped, skip the slash
                // this should work, because slashes on their own should always come in pairs
                if (toCodePoint == '\\' && position + 2 != bracketPattern.length) {
                    int nextNextCodePoint = bracketPattern[position + 2];
                    if (nextNextCodePoint != toCodePoint) {
                        toCodePoint = nextNextCodePoint;
                    }
                }
                if (toCodePoint < fromCodePoint) {
                    throw new IllegalArgumentException(String.format("Invalid pattern: '%c' higher than '%c' in [%s]",
                            fromCodePoint, toCodePoint,
                            new String(bracketPattern, contentStart, bracketPattern.length - contentStart)));
                }
                result.matchingRanges.add(new CodePointRange(fromCodePoint, toCodePoint));
                position += 1;
            } else {
                result.matchingCodePoints.add(codePoint);
            }
            position++;
        }

        return Optional.of(result);
    }

    private static ArrayList<GlobToken> tokenize(int[] pattern) {

        ArrayList<GlobToken> result = new ArrayList<>();

        if (pattern.length == 0) {
            return result;
        }

        int position = 0;
        while (position < pattern.length) {

            int codePoint = pattern[position];

            Optional<GlobToken> token = Optional.empty();

            if (codePoint == '\\') {
                if (position + 1 < pattern.length) {
                    int nextCodePoint = pattern[position + 1];
                    // if the next character in the pattern is a special character or another slash,
                    if (specialCodePoints.contains(nextCodePoint) || nextCodePoint == codePoint) {
                        // create a new literal token and skip the next character
                        token = Optional.of(new GlobToken(nextCodePoint));
                        position = position + 1;
                    }
                    // otherwise do nothing -- swallow the backslash
                }
            } else if (codePoint == '*') {
                if (result.isEmpty() || result.get(result.size() - 1).type != GlobTokenType.STAR) {
                    token = Optional.of(new GlobToken(GlobTokenType.STAR));
                }
            } else if (codePoint == '?') {
                token = Optional.of(new GlobToken(GlobTokenType.ANY_CHAR));
            } else if (codePoint == '[') {
                int closingPosition = -1;
                int i = 0;
                boolean escaped = false;
                // find the position of the next unescaped closing bracket
                while (position + i < pattern.length) {
                    int bracketCodePoint = pattern[position + i];
                    if (bracketCodePoint == ']' && !escaped) {
                        closingPosition = position + i;
                        break;
                    }
                    if (bracketCodePoint == '\\') {
                        escaped = !escaped;
                    } else {
                        escaped = false;
                    }
                    i += 1;
                }
                // if not found, the pattern is unbalanced -- throw an exception
                if (closingPosition == -1) {
                    throw new IllegalArgumentException(String.format("Invalid pattern: unbalanced '[' at %d", position));
                }

                int[] bracketPattern = Arrays.copyOfRange(pattern, position + 1, closingPosition);
                int bracketPatternPosition = position;

                // don't forget to move the cursor past the closing bracket
                position = closingPosition;

                token = parseBracketPattern(bracketPattern);
                if (token.isEmpty()) {
                    throw new IllegalArgumentException(
                            String.format("Invalid pattern: empty '[]' at %d", bracketPatternPosition));
                }
                if (token.get().matchingCodePoints.isEmpty()
                        && token.get().matchingRanges.isEmpty()
                        && token.get().negate) {
                    throw new IllegalArgumentException(
                            String.format("Invalid pattern: '[!]' at %d", bracketPatternPosition));
                }


            } else {
                token = Optional.of(new GlobToken(codePoint));
            }
            token.ifPresent(result::add);
            position += 1;
        }
        return result;
    }

    private static boolean tokenMatches(GlobToken token, int textCodePoint) {
        switch (token.type) {
            case ANY_CHAR:
                return true;
            case BRACKET:
                boolean matched = token.matchingCodePoints.contains(textCodePoint);
                if (!matched) {
                    for (CodePointRange range : token.matchingRanges) {
                        if (range.contains(textCodePoint)) {
                            matched = true;
                            break;
                        }
                    }
                }
                return token.negate ? !matched : matched;
            case LITERAL:
                return token.codePoint == textCodePoint;
            case STAR:
                throw new IllegalStateException("STAR tokens must be handled by the matching algorithm");
            default:
                throw new IllegalStateException("Unsupported token type: " + token.type);
        }
    }

    private static boolean match(List<GlobToken> tokenStream, int[] text) {
        boolean[] state = new boolean[text.length + 1];
        boolean[] nextState = new boolean[text.length + 1];
        state[0] = true;

        for (GlobToken token : tokenStream) {
            Arrays.fill(nextState, false);

            if (token.type == GlobTokenType.STAR) {
                nextState[0] = state[0];
                for (int textIndex = 1; textIndex <= text.length; textIndex++) {
                    nextState[textIndex] = state[textIndex] || nextState[textIndex - 1];
                }
            } else {
                for (int textIndex = 1; textIndex <= text.length; textIndex++) {
                    nextState[textIndex] = state[textIndex - 1]
                            && tokenMatches(token, text[textIndex - 1]);
                }
            }

            boolean[] oldState = state;
            state = nextState;
            nextState = oldState;
        }

        return state[text.length];
    }

    /**
     * Checks whether the specified string matches the specified glob pattern.
     * {@code null} arguments are treated as empty strings.
     * @param pattern pattern to match the specified string against
     * @param text string to match against the specified pattern
     * @return {@code true} if the specified string matches the specified pattern
     * @throws IllegalArgumentException if the pattern is malformed in such a way that it can't be sanitized
     */
    public static boolean match(String pattern, String text) throws IllegalArgumentException {
        if (pattern == null) {
            pattern = "";
        }
        if (text == null) {
            text = "";
        }
        pattern = pattern.replace('\u0000', '\ufffd');
        text = text.replace('\u0000', '\ufffd');
        int[] patternCodePoints = pattern.codePoints().toArray();
        int[] textCodePoints = text.codePoints().toArray();
        ArrayList<GlobToken> tokenStream = tokenize(patternCodePoints);
        return match(tokenStream, textCodePoints);
    }

    /**
     * Checks whether the specified string matches the specified glob pattern without exceeding the provided work limit.
     * The work estimate is the product of the pattern and text lengths in Unicode code points. {@code null} pattern and
     * text arguments are treated as empty strings.
     * @param pattern pattern to match the specified string against
     * @param text string to match against the specified pattern
     * @param workLimit maximum permitted work estimate
     * @return {@code true} if the specified string matches the specified pattern
     * @throws IllegalArgumentException if the work estimate exceeds the provided limit, or if the pattern is malformed
     */
    public static boolean matchWithLimit(String pattern, String text, long workLimit) throws IllegalArgumentException {
        long patternLength = pattern == null ? 0L : pattern.codePointCount(0, pattern.length());
        long textLength = text == null ? 0L : text.codePointCount(0, text.length());
        long workEstimate = patternLength * textLength;

        if (workEstimate > workLimit) {
            throw new IllegalArgumentException(String.format(
                    "Work estimate (%d) exceeds the provided work limit (%d)", workEstimate, workLimit));
        }

        return match(pattern, text);
    }


}
