package com.avago.macrobenchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startupColdNoLogin() = benchmarkRule.measureRepeated(
        packageName = "com.avago",
        metrics = listOf(StartupTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.COLD,
    ) {
        pressHome()
        startActivityAndWait()
    }

    @Test
    fun startupWarm() = benchmarkRule.measureRepeated(
        packageName = "com.avago",
        metrics = listOf(StartupTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.WARM,
    ) {
        pressHome()
        startActivityAndWait()
    }

    @Test
    fun startupHot() = benchmarkRule.measureRepeated(
        packageName = "com.avago",
        metrics = listOf(StartupTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.HOT,
    ) {
        pressHome()
        startActivityAndWait()
    }

    @Test
    fun scrollAssetList() = benchmarkRule.measureRepeated(
        packageName = "com.avago",
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.WARM,
        setupBlock = {
            pressHome()
            startActivityAndWait()
        }
    ) {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val list = device.findObject(UiSelector().resourceId("com.avago:id/asset_list"))
        repeat(5) {
            list.swipeUp(10)
            list.swipeDown(10)
        }
    }

    @Test
    fun scrollWorkOrderList() = benchmarkRule.measureRepeated(
        packageName = "com.avago",
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.WARM,
        setupBlock = {
            pressHome()
            startActivityAndWait()
        }
    ) {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.findObject(UiSelector().text("Work Orders")).clickAndWaitForNewWindow()
        val list = device.findObject(UiSelector().resourceId("com.avago:id/workorder_list"))
        repeat(5) {
            list.swipeUp(10)
            list.swipeDown(10)
        }
    }
}

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generateBaselineProfile() = rule.collect(
        packageName = "com.avago",
        profileBlock = {
            startActivityAndWait()
            // Navigate to key screens
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            device.findObject(UiSelector().text("Assets")).clickAndWaitForNewWindow()
            device.pressBack()
            device.findObject(UiSelector().text("Work Orders")).clickAndWaitForNewWindow()
            device.pressBack()
            device.findObject(UiSelector().text("Inventory")).clickAndWaitForNewWindow()
            device.pressBack()
            device.findObject(UiSelector().text("Schedule")).clickAndWaitForNewWindow()
            device.pressBack()
        }
    )
}
