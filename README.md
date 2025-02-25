# Wildcard

Wildcard is my own implementation of glob pattern matching. Glob patterns specify sets of strings with wildcard
characters. This class provides a single static public method, `match(String, String)`, which returns
`true` if the specified string matches the pattern.

<p>This implementation supports the following wildcards:</p>

 - <b>*</b> (star), which matches any number of any characters including none;
 - <b>?</b> (question mark), which matches any single character;
 - <b>[abc]</b> (brackets), which matches any single character given in the brackets. E.g., `[abc]`
would match either 'a', 'b' or 'c', but not 'd' or 'z';
 - <b>[a-z]</b> (brackets with ranges), which matches one character within the given range, e.g. `[a-z]`
would match any character from 'a' to 'z', but not 'A' or '8'.


It is possible to combine single characters and ranges in one set of brackets, e.g. `[a-zZ0$]` would match
any of the following:

 - any character from 'a' to 'z';
 - 'Z' (capital z);
 - '0' (zero);
 - '$' (the dollar sign).

The bracket wildcard also supports *negation*. If the first character inside brackets is the exclamation
mark '!', the wildcard would match a single character that is **not** given in the bracket or is from the
given range.

If special characters are escaped with a backslash '\\', they are treated as literals. In other words, `\?`
matches a literal question mark, `\-` matches a literal dash, `\\` matches a literal backslash, etc.
Please note that '!' and '-' are not considered special characters outside of brackets. </p>

Unlike Unix glob, which never matches the forward slash character '/', this implementation treats '/'
as a regular character.

Please take note that Wildcard will defensively replace all instances of the null terminator (U+0000) with
the Unicode replacement character (U+FFFD) in both text and pattern.