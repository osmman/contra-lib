/**
 * Wrapper for the validation stage of a build trigger
 * Example Usage:
 *
 * def valid = buildTrigger {
 *     return runValidation(env.CI_MESSAGE)
 * }
 *
 * @param body
 * @return
 */
def call(Closure body = {}) {
    def result = false

    handlePipelineStep {
        result = body()
    }

    if (!result) {
        echo "CI_MESSAGE was invalid. Skipping..."
        currentBuild.description = "*Build Skipped*"
    }

    return result
}
