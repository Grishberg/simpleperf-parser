/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.profilers.cpu.simpleperf;

import com.android.tools.adtui.model.Range;
import com.android.tools.profiler.proto.SimpleperfReport;
import com.android.tools.profilers.cpu.CaptureNode;
import com.android.tools.profilers.cpu.CpuCapture;
import com.android.tools.profilers.cpu.CpuThreadInfo;
import com.android.tools.profilers.cpu.nodemodel.CppFunctionModel;
import com.google.common.collect.Lists;
import com.google.protobuf.ByteString;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.android.tools.profilers.cpu.CpuProfilerTestUtils.traceFileToByteString;
import static org.junit.Assert.*;

public class SimpleperfTraceParserTest {

    private SimpleperfTraceParser myParser;

    private File myTraceFile;

    @Before
    public void setUp() throws IOException {
        ByteString traceBytes = traceFileToByteString("simpleperf.trace");
        File trace = createTempFile("cpu_trace", ".trace");
        try (FileOutputStream out = new FileOutputStream(trace)) {
            out.write(traceBytes.toByteArray());
        }
        myTraceFile = trace;
        myParser = new SimpleperfTraceParser();
    }

    @Test
    public void samplesAndLostCountShouldMatchSimpleperfReport() throws IOException {
        myParser.parseTraceFile(myTraceFile);
        assertEquals(23487, myParser.getSampleCount());
        assertEquals(93, myParser.getLostSampleCount());
    }

    @Test
    public void allTreesShouldStartWithThreadName() throws IOException {
        CpuCapture capture = myParser.parse(myTraceFile, 0);

        for (CpuThreadInfo thread : capture.getThreads()) {
            CaptureNode tree = capture.getCaptureNode(thread.getId());
            assertNotNull(tree);
            assertEquals(thread.getName(), tree.getData().getName());
        }
    }

    @Test
    public void checkKnownThreadsPresenceAndCount() throws IOException {
        CpuCapture capture = myParser.parse(myTraceFile, 0);

        assertFalse(capture.getCaptureNodes().isEmpty());

        // Studio:Heartbeat
        int studioHeartbeatCount = 0;
        // Studio:MemoryAgent
        int studioMemoryAgentCount = 0;
        // Studio:Socket
        int studioSocketCount = 0;
        // JVMTI Agent thread
        int jvmtiAgentCount = 0;

        for (CpuThreadInfo thread : capture.getThreads()) {
            String threadName = thread.getName();
            CaptureNode tree = capture.getCaptureNode(thread.getId());
            // Using contains instead of equals because native thread names are limited to 15 characters
            // and there is no way to predict where they are going to be trimmed.
            if ("Studio:Heartbeat".contains(threadName)) {
                studioHeartbeatCount++;
                // libjvmtiagent should be the entry point
                // TODO: Update file name along with the trace files
                validateRootNodesAndGetEntryPoint(tree,"libperfa_arm64.so");
            }
            else if ("Studio:MemoryAgent".contains(threadName)) {
                studioMemoryAgentCount++;
                // libjvmtiagent should be the entry point
                // TODO: Update file name along with the trace files
                validateRootNodesAndGetEntryPoint(tree,"libperfa_arm64.so");
            }
            else if ("Studio:Socket".contains(threadName)) {
                studioSocketCount++;
                // libjvmtiagent should be the entry point
                // TODO: Update file name along with the trace files
                validateRootNodesAndGetEntryPoint(tree,"libperfa_arm64.so");
            }
            else if ("JVMTI Agent thread".contains(threadName)) {
                jvmtiAgentCount++;
                // openjdkjvmti should be the entry point
                validateRootNodesAndGetEntryPoint(tree,"openjdkjvmti::AgentCallback");
            }
        }

        assertEquals(1, studioHeartbeatCount);
        assertEquals(1, studioMemoryAgentCount);
        assertEquals(1, studioSocketCount);
        assertEquals(2, jvmtiAgentCount);
    }

    @Test
    public void nodeDepthsShouldBeCoherent() throws IOException {
        CpuCapture capture = myParser.parse(myTraceFile, 0);
        CaptureNode anyTree = capture.getCaptureNodes().iterator().next();
        assertEquals(0, anyTree.getDepth());

        // Just go as deep as possible in one branch per child and check the depths of each node in the branch
        for (CaptureNode child : anyTree.getChildren()) {
            int depth = 1;
            CaptureNode node = child;
            while (node != null) {
                assertEquals(depth++, node.getDepth());
                node = node.getFirstChild();
            }
        }
    }

    @Test
    public void cppModelVAddressComesFromParentInCallChain() throws IOException {
        ByteString traceBytes = traceFileToByteString("simpleperf_callchain.trace");
        File trace = createTempFile("native_trace", ".trace", true);
        try (FileOutputStream out = new FileOutputStream(trace)) {
            out.write(traceBytes.toByteArray());
        }
        CpuCapture capture = myParser.parse(trace, 1);

        int mainThread = 7056;
        SimpleperfReport.Sample mainFirstSample =
                myParser.mySamples.stream().filter((sample -> sample.getThreadId() == mainThread)).findFirst().orElse(null);
        assertNotNull(mainFirstSample);

        CaptureNode mainThreadTree = capture.getCaptureNode(mainFirstSample.getThreadId());
        assertNotNull(mainThreadTree);

        List<SimpleperfReport.Sample.CallChainEntry> firstCallChain = Lists.reverse(mainFirstSample.getCallchainList());
        List<CaptureNode> leftMostMainTreeBranch = new ArrayList<>();
        while (mainThreadTree != null) {
            leftMostMainTreeBranch.add(mainThreadTree);
            mainThreadTree = mainThreadTree.getChildCount() > 0 ? mainThreadTree.getChildAt(0) : null;
        }

        String mainThreadName = "e.sample.tunnel";
        // tree branch = callchain + special node representing the thread name
        assertEquals(firstCallChain.size() + 1, leftMostMainTreeBranch.size());
        assertEquals(mainThreadName, leftMostMainTreeBranch.get(0).getData().getName());

        int cppModelCount = 0;
        for (int i = 1; i < firstCallChain.size(); i++) {
            CaptureNode correspondingNode = leftMostMainTreeBranch.get(i + 1);
            if (correspondingNode.getData() instanceof CppFunctionModel) {
                cppModelCount++;
                long parentVAddress = firstCallChain.get(i - 1).getVaddrInFile();
                assertEquals(parentVAddress, ((CppFunctionModel)correspondingNode.getData()).getVAddress());
            }

        }
        // Verify we indeed checked for some addresses
        assertTrue(cppModelCount > 0);
    }

    @Test
    public void mainProcessShouldBePresent() throws IOException {
        CpuCapture capture = myParser.parse(myTraceFile, 0);
        int appPid = 8589;
        CaptureNode mainThread = capture.getCaptureNode(appPid);
        assertNotNull(mainThread);
        assertEquals(appPid, capture.getMainThreadId());
    }

    @Test
    public void invalidFileShouldFailDueToMagicNumberMismatch() throws IOException {
        ByteString traceBytes = traceFileToByteString("simpleperf_malformed.trace");
        File trace = createTempFile("cpu_trace", ".trace");
        try (FileOutputStream out = new FileOutputStream(trace)) {
            out.write(traceBytes.toByteArray());
        }
        myParser = new SimpleperfTraceParser();

        try {
            myParser.parse(trace, 0);
            fail("IllegalStateException should have been thrown due to missing file.");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("magic number mismatch"));
            // Do nothing. Expected exception.
        }
    }

    @Test
    public void rangeShouldBeFromFirstToLastTimestamp() throws IOException {
        CpuCapture capture = myParser.parse(myTraceFile, 0);
        long startTimeUs = TimeUnit.NANOSECONDS.toMicros(myParser.mySamples.get(0).getTime());
        long endTimeUs = TimeUnit.NANOSECONDS.toMicros(myParser.mySamples.get(myParser.mySamples.size() - 1).getTime());
        Range expected = new Range(startTimeUs, endTimeUs);
        assertEquals(expected.getMin(), capture.getRange().getMin(), 0);
        assertEquals(expected.getMax(), capture.getRange().getMax(), 0);
    }

    @Test
    public void tagsSortedByExpectedOrder() {
        List<String> tags = Arrays.asList("/a/b/c", "/c/d/e", "[java]", "/a/*");
        Collections.shuffle(tags);
        tags.sort(SimpleperfTraceParser.TAG_COMPARATOR);

        //assertThat(tags).isEqualTo(Arrays.asList("/a/b/c", "/c/d/e", "[java]", "/a/*"));
    }

    /**
     * Checks that a {@link CaptureNode} tree starts with "__start_thread -> __pthread_start", then verifies the node just after then.
     */
    private static void validateRootNodesAndGetEntryPoint(CaptureNode tree, String entryPoint) {
        CaptureNode startThread = tree.getChildAt(0);
        assertTrue(startThread.getData().getFullName().startsWith("__start_thread"));
        CaptureNode pThreadStart = startThread.getChildAt(0);
        assertTrue(pThreadStart.getData().getFullName().startsWith("__pthread_start"));
        assertTrue(pThreadStart.getChildAt(0).getData().getFullName().startsWith(entryPoint));
    }

    private static File createTempFile(String prefix, String suffix) throws IOException {
        return Files.createTempFile(prefix, suffix).toFile();
    }

    private static File createTempFile(String prefix, String suffix, boolean b) throws IOException {
        return Files.createTempFile(prefix, suffix).toFile();
    }
}
