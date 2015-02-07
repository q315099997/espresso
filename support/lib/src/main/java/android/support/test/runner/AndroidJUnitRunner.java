/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.support.test.runner;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Build;
import android.os.Bundle;
import android.support.test.internal.runner.RunnerArgs;
import android.support.test.internal.runner.TestExecutor;
import android.support.test.internal.runner.TestRequest;
import android.support.test.internal.runner.TestRequestBuilder;
import android.support.test.internal.runner.listener.ActivityFinisherRunListener;
import android.support.test.internal.runner.listener.CoverageListener;
import android.support.test.internal.runner.listener.DelayInjector;
import android.support.test.internal.runner.listener.InstrumentationResultPrinter;
import android.support.test.internal.runner.listener.LogRunListener;
import android.support.test.internal.runner.listener.SuiteAssignmentPrinter;
import android.support.test.internal.runner.tracker.AnalyticsBasedUsageTracker;
import android.support.test.internal.runner.tracker.UsageTracker;
import android.support.test.internal.runner.tracker.UsageTrackerRegistry;
import android.test.suitebuilder.annotation.LargeTest;
import android.util.Log;

import org.junit.runner.notification.RunListener;

/**
 * An {@link Instrumentation} that runs JUnit3 and JUnit4 tests against
 * an Android package (application).
 * <p/>
 * Based on and replacement for {@link android.test.InstrumentationTestRunner}. Supports a superset
 * of {@link android.test.InstrumentationTestRunner} features,
 * while maintaining command/output format compatibility with that class.
 *
 * <h3>Typical Usage</h3>
 * <p/>
 * Write JUnit3 style {@link junit.framework.TestCase}s and/or JUnit4 style
 * {@link org.junit.Test}s that perform tests against the classes in your package.
 * Make use of the {@link android.support.test.InstrumentationRegistry} if needed.
 * <p/>
 * In an appropriate AndroidManifest.xml, define an instrumentation with android:name set to
 * {@link android.support.test.runner.AndroidJUnitRunner} and the appropriate android:targetPackage
 * set.
 * <p/>
 * Execution options:
 * <p/>
 * <b>Running all tests:</b> adb shell am instrument -w
 * com.android.foo/android.support.test.runner.AndroidJUnitRunner
 * <p/>
 * <b>Running all tests in a class:</b> adb shell am instrument -w
 * -e class com.android.foo.FooTest
 * com.android.foo/android.support.test.runner.AndroidJUnitRunner
 * <p/>
 * <b>Running a single test:</b> adb shell am instrument -w
 * -e class com.android.foo.FooTest#testFoo
 * com.android.foo/android.support.test.runner.AndroidJUnitRunner
 * <p/>
 * <b>Running all tests in multiple classes:</b> adb shell am instrument -w
 * -e class com.android.foo.FooTest,com.android.foo.TooTest
 * com.android.foo/android.support.test.runner.AndroidJUnitRunner
 * <p/>
 * <b>Running all tests listed in a file:</b> adb shell am instrument -w
 * -e testFile /sdcard/tmp/testFile.txt com.android.foo/com.android.test.runner.AndroidJUnitRunner
 * The file should contain a list of line separated test classes and optionally methods (expected
 * format: com.android.foo.FooClassName#testMethodName).
 * <p/>
 * <b>Running all tests in a java package:</b> adb shell am instrument -w
 * -e package com.android.foo.bar
 * com.android.foo/android.support.test.runner.AndroidJUnitRunner
 * <b>To debug your tests, set a break point in your code and pass:</b>
 * -e debug true
 * <p/>
 * <b>Running a specific test size i.e. annotated with
 * {@link android.test.suitebuilder.annotation.SmallTest} or
 * {@link android.test.suitebuilder.annotation.MediumTest} or
 * {@link android.test.suitebuilder.annotation.LargeTest}:</b>
 * adb shell am instrument -w -e size [small|medium|large]
 * com.android.foo/android.support.test.runner.AndroidJUnitRunner
 * <p/>
 * <b>Filter test run to tests with given annotation:</b> adb shell am instrument -w
 * -e annotation com.android.foo.MyAnnotation
 * com.android.foo/android.support.test.runner.AndroidJUnitRunner
 * <p/>
 * If used with other options, the resulting test run will contain the intersection of the two
 * options.
 * e.g. "-e size large -e annotation com.android.foo.MyAnnotation" will run only tests with both
 * the {@link LargeTest} and "com.android.foo.MyAnnotation" annotations.
 * <p/>
 * <b>Filter test run to tests <i>without</i> given annotation:</b> adb shell am instrument -w
 * -e notAnnotation com.android.foo.MyAnnotation
 * com.android.foo/android.support.test.runner.AndroidJUnitRunner
 * <p/>
 * As above, if used with other options, the resulting test run will contain the intersection of
 * the two options.
 * e.g. "-e size large -e notAnnotation com.android.foo.MyAnnotation" will run tests with
 * the {@link LargeTest} annotation that do NOT have the "com.android.foo.MyAnnotation" annotations.
 * <p/>
 * <b>Filter test run to tests <i>without any</i> of a list of annotations:</b> adb shell am
 * instrument -w -e notAnnotation com.android.foo.MyAnnotation,com.android.foo.AnotherAnnotation
 * com.android.foo/android.support.test.runner.AndroidJUnitRunner
 * <p/>
 * <b>Filter test run to a shard of all tests, where numShards is an integer greater than 0 and
 * shardIndex is an integer between 0 (inclusive) and numShards (exclusive):</b> adb shell am
 * instrument -w -e numShards 4 -e shardIndex 1
 * com.android.foo/android.support.test.runner.AndroidJUnitRunner
 * <p/>
 * <b>To run in 'log only' mode</b>
 * -e log true
 * This option will load and iterate through all test classes and methods, but will bypass actual
 * test execution. Useful for quickly obtaining info on the tests to be executed by an
 * instrumentation command.
 * <p/>
 * <b>To generate EMMA code coverage:</b>
 * -e coverage true
 * Note: this requires an emma instrumented build. By default, the code coverage results file
 * will be saved in a /data/<app>/coverage.ec file, unless overridden by coverageFile flag (see
 * below)
 * <p/>
 * <b> To specify EMMA code coverage results file path:</b>
 * -e coverageFile /sdcard/myFile.ec
 * <p/>
 * <b> To specify one or more {@link RunListener}s to observe the test run:</b>
 * -e listener com.foo.Listener,com.foo.Listener2
 * <p/>
 * <b>Set timeout (in milliseconds) that will be applied to each test:</b>
 * -e timeout_msec 5000
 * <p/>
 * Supported for both JUnit3 and JUnit4 style tests. For JUnit3 tests, this flag is the only way
 * to specify timeouts. For JUnit4 tests, this flag overrides timeouts specified via
 * {@link org.junit.rules.Timeout}. Please note that in JUnit4 {@link org.junit.Test#timeout()}
 * annotation take precedence over both, this flag and {@link org.junit.Test#timeout()} annotation.
 * <p/>
 * <b>To disable Google Analytics:</b>
 * -e disableAnalytics true
 * <p/>
 * <b/>All arguments can also be specified in the in the AndroidManifest via a meta-data tag:</b>
 * eg. using listeners:
 * instrumentation android:name="android.support.test.runner.AndroidJUnitRunner" ...
 *    meta-data android:name="listener"
 *              android:value="com.foo.Listener,com.foo.Listener2"
 * Arguments specified via shell will take override manifest specified arguments.
 */
public class AndroidJUnitRunner extends MonitoringInstrumentation {

    private static final String LOG_TAG = "AndroidJUnitRunner";

    private Bundle mArguments;
    private InstrumentationResultPrinter mInstrumentationResultPrinter = null;

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        mArguments = arguments;

        start();
    }

    /**
     * Get the Bundle object that contains the arguments passed to the instrumentation
     *
     * @return the Bundle object
     */
    private Bundle getArguments(){
        return mArguments;
    }

    /**
     * Exposed for unit testing
     */
    InstrumentationResultPrinter getInstrumentationResultPrinter() {
        return mInstrumentationResultPrinter;
    }

    @Override
    public void onStart() {
        super.onStart();

        Bundle results = new Bundle();
        try {
            // build the arguments. Read from manifest first so manifest-provided args can be overridden
            // with command line arguments
            RunnerArgs runnerArgs = new RunnerArgs.Builder()
                    .fromManifest(this)
                    .fromBundle(getArguments())
                    .build();

            TestExecutor.Builder executorBuilder = new TestExecutor.Builder(this);
            if (runnerArgs.debug) {
                executorBuilder.setWaitForDebugger(true);
            }

            addListeners(runnerArgs, executorBuilder);

            TestRequest testRequest = buildRequest(runnerArgs, getArguments());

            results = executorBuilder.build().execute(testRequest);

        } catch (RuntimeException e) {
            final String msg = "Fatal exception when running tests";
            Log.e(LOG_TAG, msg, e);
            // report the exception to instrumentation out
            results.putString(Instrumentation.REPORT_KEY_STREAMRESULT,
                    msg + "\n" + Log.getStackTraceString(e));
        }
        finish(Activity.RESULT_OK, results);
    }

    @Override
    public void finish(int resultCode, Bundle results) {
        try {
            UsageTrackerRegistry.getInstance().trackUsage("AndroidJUnitRunner");
            UsageTrackerRegistry.getInstance().sendUsages();
        } catch (RuntimeException re) {
            Log.w(LOG_TAG, "Failed to send analytics.", re);
        }
        super.finish(resultCode, results);
    }

    private void addListeners(RunnerArgs args, TestExecutor.Builder builder) {
        if (args.suiteAssignment) {
            builder.addRunListener(new SuiteAssignmentPrinter());
        } else {
            builder.addRunListener(new LogRunListener());
            mInstrumentationResultPrinter = new InstrumentationResultPrinter();
            builder.addRunListener(mInstrumentationResultPrinter);
            builder.addRunListener(new ActivityFinisherRunListener(this,
                    new MonitoringInstrumentation.ActivityFinisher()));
            addDelayListener(args, builder);
            addCoverageListener(args, builder);
        }

        addListenersFromArg(args, builder);
    }

    private void addCoverageListener(RunnerArgs args, TestExecutor.Builder builder) {
        if (args.codeCoverage) {
            builder.addRunListener(new CoverageListener(args.codeCoveragePath));
        }
    }

    /**
     * Sets up listener to inject a delay between each test, if specified.
     */
    private void addDelayListener(RunnerArgs args, TestExecutor.Builder builder) {
        if (args.delayMsec > 0) {
            builder.addRunListener(new DelayInjector(args.delayMsec));
        } else if (args.logOnly && Build.VERSION.SDK_INT < 16) {
            // On older platforms, collecting tests can fail for large volume of tests.
            // Insert a small delay between each test to prevent this
            builder.addRunListener(new DelayInjector(15 /* msec */));
        }
    }

    private void addListenersFromArg(RunnerArgs args, TestExecutor.Builder builder) {
        for (RunListener listener : args.listeners) {
            builder.addRunListener(listener);
        }
    }

    @Override
    public boolean onException(Object obj, Throwable e) {
        InstrumentationResultPrinter instResultPrinter = getInstrumentationResultPrinter();
        if (instResultPrinter != null) {
            // report better error message back to Instrumentation results.
            instResultPrinter.reportProcessCrash(e);
        }
        return super.onException(obj, e);
    }

    /**
     * Builds a {@link TestRequest} based on given input arguments.
     * <p/>
     * Exposed for unit testing.
     */
    TestRequest buildRequest(RunnerArgs runnerArgs, Bundle bundleArgs) {

        TestRequestBuilder builder = createTestRequestBuilder(this, bundleArgs);

        // only scan for tests for current apk aka testContext
        // Note that this represents a change from InstrumentationTestRunner where
        // getTargetContext().getPackageCodePath() aka app under test was also scanned
        builder.addApkToScan(getContext().getPackageCodePath());

        builder.addFromRunnerArgs(runnerArgs);

        if (!runnerArgs.disableAnalytics) {
            if (null != getTargetContext()) {
                UsageTracker tracker = new AnalyticsBasedUsageTracker.Builder(
                        getTargetContext()).buildIfPossible();

                if (null != tracker) {
                    UsageTrackerRegistry.registerInstance(tracker);
                }
            }
        }

        return builder.build();
    }

    /**
     * Factory method for {@link TestRequestBuilder}.
     * <p/>
     * Exposed for unit testing.
     */
    TestRequestBuilder createTestRequestBuilder(Instrumentation instr, Bundle arguments) {
        return new TestRequestBuilder(instr, arguments);
    }
}
