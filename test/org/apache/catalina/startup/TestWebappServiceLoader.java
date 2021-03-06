/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.catalina.startup;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.servlet.ServletContainerInitializer;
import javax.servlet.ServletContext;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.easymock.EasyMock;
import org.easymock.IMocksControl;

public class TestWebappServiceLoader {
    private static final String CONFIG_FILE =
            "META-INF/services/javax.servlet.ServletContainerInitializer";
    private IMocksControl control;
    private ClassLoader cl;
    private ClassLoader parent;
    private ServletContext context;
    private WebappServiceLoader<ServletContainerInitializer> loader;

    @Before
    public void init() {
        control = EasyMock.createStrictControl();
        parent = control.createMock(ClassLoader.class);
        cl = EasyMock.createMockBuilder(ClassLoader.class)
                .withConstructor(parent)
                .addMockedMethod("loadClass", String.class)
                .createMock(control);
        context = control.createMock(ServletContext.class);
        EasyMock.expect(context.getClassLoader()).andStubReturn(cl);
    }

    @Test
    public void testNoInitializersFound() throws IOException {
        loader = new WebappServiceLoader<>(context);
        EasyMock.expect(context.getAttribute(ServletContext.ORDERED_LIBS))
                .andReturn(null);
        EasyMock.expect(cl.getResources(CONFIG_FILE))
                .andReturn(Collections.<URL>emptyEnumeration());
        control.replay();
        Assert.assertTrue(loader.load(ServletContainerInitializer.class).isEmpty());
        control.verify();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testInitializerFromClasspath() throws IOException {
        URL url = new URL("file://test");
        loader = EasyMock.createMockBuilder(WebappServiceLoader.class)
                .addMockedMethod("parseConfigFile", Set.class, URL.class)
                .withConstructor(context).createMock(control);
        EasyMock.expect(context.getAttribute(ServletContext.ORDERED_LIBS))
                .andReturn(null);
        EasyMock.expect(cl.getResources(CONFIG_FILE))
                .andReturn(Collections.enumeration(Collections.singleton(url)));
        loader.parseConfigFile(EasyMock.isA(Set.class), EasyMock.same(url));
        control.replay();
        Assert.assertTrue(loader.load(ServletContainerInitializer.class).isEmpty());
        control.verify();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testWithOrdering() throws IOException {
        URL url1 = new URL("file://jar1.jar");
        URL sci1 = new URL("jar:file://jar1.jar!/" + CONFIG_FILE);
        URL url2 = new URL("file://dir/");
        URL sci2 = new URL("file://dir/" + CONFIG_FILE);
        loader = EasyMock.createMockBuilder(WebappServiceLoader.class)
                .addMockedMethod("parseConfigFile", Set.class, URL.class)
                .withConstructor(context).createMock(control);
        List<String> jars = Arrays.asList("jar1.jar", "dir/");
        EasyMock.expect(context.getAttribute(ServletContext.ORDERED_LIBS))
                .andReturn(jars);
        EasyMock.expect(context.getResource("/WEB-INF/lib/jar1.jar"))
                .andReturn(url1);
        loader.parseConfigFile(EasyMock.isA(Set.class), EasyMock.eq(sci1));
        EasyMock.expect(context.getResource("/WEB-INF/lib/dir/"))
                .andReturn(url2);
        loader.parseConfigFile(EasyMock.isA(Set.class), EasyMock.eq(sci2));
        EasyMock.expect(parent.getResources(CONFIG_FILE))
                .andReturn(Collections.<URL>emptyEnumeration());

        control.replay();
        Assert.assertTrue(loader.load(ServletContainerInitializer.class).isEmpty());
        control.verify();
    }

    @Test
    public void testParseConfigFile() throws IOException {
        Set<String> found = new HashSet<>();
        loader = new WebappServiceLoader<>(context);
        loader.parseConfigFile(found, getClass().getResource("service-config.txt"));
        Assert.assertEquals(Collections.singleton("provider1"), found);
    }

    @Test
    public void testLoadServices() throws Exception {
        Class<?> sci = TesterServletContainerInitializer1.class;
        loader = new WebappServiceLoader<>(context);
        cl.loadClass(sci.getName());
        EasyMock.expectLastCall()
                .andReturn(sci);
        Set<String> names = Collections.singleton(sci.getName());
        control.replay();
        Collection<ServletContainerInitializer> initializers =
                loader.loadServices(ServletContainerInitializer.class, names);
        Assert.assertEquals(1, initializers.size());
        Assert.assertTrue(sci.isInstance(initializers.iterator().next()));
        control.verify();
    }

    @Test
    public void testServiceIsNotExpectedType() throws Exception {
        Class<?> sci = Object.class;
        loader = new WebappServiceLoader<>(context);
        cl.loadClass(sci.getName());
        EasyMock.expectLastCall()
                .andReturn(sci);
        Set<String> names = Collections.singleton(sci.getName());
        control.replay();
        try {
            loader.loadServices(ServletContainerInitializer.class, names);
        } catch (IOException e) {
            Assert.assertTrue(e.getCause() instanceof ClassCastException);
        } finally {
            control.verify();
        }
    }

    @Test
    public void testServiceCannotBeConstructed() throws Exception {
        Class<?> sci = Integer.class;
        loader = new WebappServiceLoader<>(context);
        cl.loadClass(sci.getName());
        EasyMock.expectLastCall()
                .andReturn(sci);
        Set<String> names = Collections.singleton(sci.getName());
        control.replay();
        try {
            loader.loadServices(ServletContainerInitializer.class, names);
        } catch (IOException e) {
            Assert.assertTrue(e.getCause() instanceof InstantiationException);
        } finally {
            control.verify();
        }
    }
}
