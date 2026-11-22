# SPDX-License-Identifier: GPL-3.0-or-later
# Rhino's optional Java bean converter targets desktop JVMs. NewPipe evaluates
# JavaScript in an interpreted sandbox and only passes strings into its scope.
-dontwarn org.mozilla.javascript.JavaToJSONConverters
