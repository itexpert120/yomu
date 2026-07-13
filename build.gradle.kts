// Top-level build file where you can add configuration options common to all sub-projects/modules.
import com.diffplug.spotless.LineEnding

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.spotless)
}

spotless {
    // The repo had mixed CRLF/LF; pin to LF (matches .gitattributes) so formatting is the only diff.
    lineEndings = LineEnding.UNIX
    kotlin {
        // Restrict traversal to source roots. Scanning `**` can race Android's asset compression
        // task over transient build directories when formatting and assemble run together in CI.
        target("app/src/**/*.kt")
        ktlint(libs.versions.ktlint.get())
            .editorConfigOverride(
                mapOf(
                    // Match the existing IntelliJ/Android Studio style rather than ktlint_official, to
                    // keep the formatting diff to genuine fixes (no opinionated wrapping churn).
                    "ktlint_code_style" to "intellij_idea",
                    // The codebase has long explanatory comments and injected JS/CSS string literals;
                    // don't hard-wrap on a column limit.
                    "max_line_length" to "off",
                    // @Composable functions are intentionally PascalCase; ktlint's function-naming rule
                    // can't reliably distinguish them, so disable it (standard for Compose codebases).
                    "ktlint_standard_function-naming" to "disabled",
                    // The design system deliberately names tokens/constants in PascalCase
                    // (YomuMotion.EmphasizedDecel, etc.); keep that convention rather than churn to
                    // SCREAMING_SNAKE_CASE and rewrite call sites.
                    "ktlint_standard_property-naming" to "disabled",
                ),
            )
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        ktlint(libs.versions.ktlint.get())
        trimTrailingWhitespace()
        endWithNewline()
    }
}
