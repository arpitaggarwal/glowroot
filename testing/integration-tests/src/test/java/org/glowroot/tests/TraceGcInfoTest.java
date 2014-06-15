/*
 * Copyright 2013-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.glowroot.tests;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.List;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import org.glowroot.Containers;
import org.glowroot.container.AppUnderTest;
import org.glowroot.container.Container;
import org.glowroot.container.TraceMarker;
import org.glowroot.container.config.GeneralConfig;
import org.glowroot.container.trace.Trace;
import org.glowroot.container.trace.TraceGcInfo;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Trask Stalnaker
 * @since 0.5
 */
public class TraceGcInfoTest {

    private static Container container;

    @BeforeClass
    public static void setUp() throws Exception {
        container = Containers.getSharedContainer();
    }

    @AfterClass
    public static void tearDown() throws Exception {
        container.close();
    }

    @Before
    public void beforeEachTest() throws Exception {
        GeneralConfig config = container.getConfigService().getGeneralConfig();
        config.setGcInfoEnabled(true);
        container.getConfigService().updateGeneralConfig(config);
    }

    @After
    public void afterEachTest() throws Exception {
        container.checkAndReset();
    }

    @Test
    public void shouldTestGarbageCollection() throws Exception {
        // given
        // when
        container.executeAppUnderTest(ShouldGenerateGarbage.class);
        // then
        Trace trace = container.getTraceService().getLastTrace();
        List<TraceGcInfo> garbageCollectorInfos = trace.getGcInfos();
        long collectionCount = 0;
        long collectionTime = 0;
        for (TraceGcInfo garbageCollectionInfo : garbageCollectorInfos) {
            collectionCount += garbageCollectionInfo.getCollectionCount();
            collectionTime += garbageCollectionInfo.getCollectionTime();
        }
        assertThat(collectionCount).isGreaterThanOrEqualTo(5);
        assertThat(collectionTime).isGreaterThanOrEqualTo(5);
    }

    public static class ShouldGenerateGarbage implements AppUnderTest, TraceMarker {
        @Override
        public void executeApp() throws Exception {
            traceMarker();
        }
        @Override
        public void traceMarker() throws Exception {
            long collectionCountStart = collectionCount();
            long collectionTimeStart = collectionTime();
            while (collectionCount() - collectionCountStart < 5
                    || collectionTime() - collectionTimeStart < 5) {
                for (int i = 0; i < 1000; i++) {
                    createGarbage();
                }
            }
        }
        private static long collectionCount() {
            List<GarbageCollectorMXBean> garbageCollectorBeans =
                    ManagementFactory.getGarbageCollectorMXBeans();
            long total = 0;
            for (GarbageCollectorMXBean garbageCollectorBean : garbageCollectorBeans) {
                total += garbageCollectorBean.getCollectionCount();
            }
            return total;
        }
        private static long collectionTime() {
            List<GarbageCollectorMXBean> garbageCollectorBeans =
                    ManagementFactory.getGarbageCollectorMXBeans();
            long total = 0;
            for (GarbageCollectorMXBean garbageCollectorBean : garbageCollectorBeans) {
                total += garbageCollectorBean.getCollectionTime();
            }
            return total;
        }
        private Object createGarbage() {
            return new char[10000];
        }
    }
}