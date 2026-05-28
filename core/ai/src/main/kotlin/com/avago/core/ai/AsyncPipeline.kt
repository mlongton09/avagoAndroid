package com.avago.core.ai

/**
 * Generic async pipeline for chaining independent processing steps.
 * Mirrors iOS AsyncPipeline.swift — provides type-safe step composition
 * with compile-time type checking and runtime error propagation.
 *
 * Usage:
 * ```kotlin
 * val pipeline = Pipeline.identity<String>()
 *     .then(SomeStep())
 *     .then(AnotherStep())
 * val result: Result<FinalOutput> = pipeline.execute(input)
 * ```
 *
 * Each step's successful output feeds as input to the next step.
 * The first failure short-circuits execution and propagates as-is.
 */
interface AsyncPipelineStep<Input, Output> {
    suspend fun process(input: Input): Result<Output>
}

/**
 * Composable async pipeline built from [AsyncPipelineStep] instances.
 */
class Pipeline<Input, Output> private constructor(
    private val runner: suspend (Input) -> Result<Output>,
) {
    companion object {
        /** Create an identity pipeline that passes input through unchanged. */
        fun <T> identity(): Pipeline<T, T> = Pipeline { Result.success(it) }
    }

    /** Append [step], producing a new [Pipeline] with [NextOutput] as the result type. */
    fun <NextOutput> then(step: AsyncPipelineStep<Output, NextOutput>): Pipeline<Input, NextOutput> =
        Pipeline { input ->
            runner(input).fold(
                onSuccess = { intermediate -> step.process(intermediate) },
                onFailure = { Result.failure(it) },
            )
        }

    /** Run the pipeline with [input] and return the final [Result]. */
    suspend fun execute(input: Input): Result<Output> = runner(input)
}
