/*
 * Copyright (c) 2016 Arthur Naseef
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 */

package com.amlinv;

import org.apache.maven.enforcer.rule.api.EnforcerRule;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.enforcer.rule.api.EnforcerRuleHelper;
import org.apache.maven.plugin.logging.Log;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluationException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Validate operation of the ParentVersionRule.
 *
 * Created by art on 2/17/16.
 */
public class ParentVersionRuleTest {

    private ParentVersionRule rule;

    private EnforcerRuleHelper mockEnforcerRuleHelper;
    private Log mockLog;

    private List<String> ignoreList;
    private ExpressionEvaluationException expressionEvaluationException;

    /**
     * Setup common test data and interactions.
     *
     * @throws Exception
     */
    @Before
    public void setupTest() throws Exception {
        this.rule = new ParentVersionRule();

        this.mockEnforcerRuleHelper = Mockito.mock(EnforcerRuleHelper.class);
        this.mockLog = Mockito.mock(Log.class);

        Mockito.when(this.mockEnforcerRuleHelper.getLog()).thenReturn(this.mockLog);

        this.ignoreList = Arrays.asList("x-ignore-artifact1-x", "x-ignore-group2-x:x-ignore-artifact2-x");
        this.expressionEvaluationException = new ExpressionEvaluationException("x-expression-evaluation-failed-x");
    }

    /**
     * Verify the getter and setter for ignored.
     *
     * @throws Exception
     */
    @Test
    public void testGetSetIgnored() throws Exception {
        assertTrue(rule.getIgnore().isEmpty());

        this.rule.setIgnore(this.ignoreList);
        assertEquals(this.ignoreList, rule.getIgnore());
    }

    /**
     * Verify the getter and setter for ignoreMissingParent.
     *
     * @throws Exception
     */
    @Test
    public void testGetSetIgnoreMissingParent() throws Exception {
        assertTrue(rule.isIgnoreMissingParent());

        this.rule.setIgnoreMissingParent(false);
        assertFalse(rule.isIgnoreMissingParent());
    }

    /**
     * Verify the rule does not support cached values.
     *
     * @throws Exception
     */
    @Test
    public void testIsCacheable() throws Exception {
        assertFalse(this.rule.isCacheable());
    }

    /**
     * Verify the result of isResultValid().
     *
     * @throws Exception
     */
    @Test
    public void testIsResultValid() throws Exception {
        EnforcerRule rule1 = Mockito.mock(EnforcerRule.class);

        assertFalse(this.rule.isResultValid(rule1));

        // Make sure it's just a constant false regardless of what is passed in.
        Mockito.verifyZeroInteractions(rule1);
    }

    /**
     * Verify the result of getCachedId().
     *
     * @throws Exception
     */
    @Test
    public void testGetCachedId() throws Exception {
        assertNull(this.rule.getCacheId());
    }

    /**
     * Verify pass when the parent version matches the artifact, and the parent version in the pom also matches.
     *
     * @throws Exception
     */
    @Test
    public void testExecutePassWithParent() throws Exception {
        this.initProject("x-group-x", "x-artifact-x", "1.0.0", "1.0.0", "1.0.0");
        this.rule.execute(this.mockEnforcerRuleHelper);
    }

    /**
     * Verify failure when the version of the parent does not match the version of the artifact.
     *
     * @throws Exception
     */
    @Test
    public void testExecuteFailWithParentVersionDifferent() throws Exception {
        this.initProject("x-group-x", "x-artifact-x", "1.0.0", "1.1.0", "1.1.0");

        try {
            this.rule.execute(this.mockEnforcerRuleHelper);
            fail("Missing expected exception");
        } catch ( EnforcerRuleException exc ) {
            assertEquals("parent and project version mismatch: project=1.0.0; parent=1.1.0", exc.getMessage());
        }
    }

    /**
     * Verify failure when the version of the parent from the POM does not match the version of the parent in the build.
     *
     * @throws Exception
     */
    @Test
    public void testExecuteFailWithParentPomVersionDifferent() throws Exception {
        this.initProject("x-group-x", "x-artifact-x", "1.0.0", "1.0.0", "1.1.0");

        try {
            this.rule.execute(this.mockEnforcerRuleHelper);
            fail("Missing expected exception");
        } catch ( EnforcerRuleException exc ) {
            assertEquals("actual parent version does not match the one listed in the pom: actual parent " +
                    "version=1.0.0; version from pom=1.1.0", exc.getMessage());
        }
    }

    /**
     * Verify pass when no parent exists, and the ignore-missing-parent setting is true.
     * @throws Exception
     */
    @Test
    public void testExecutePassWithNoParentVersion() throws Exception {
        this.initProject("x-group-x", "x-artifact-x", "1.0.0", null, null);
        this.rule.setIgnoreMissingParent(true);
        this.rule.execute(this.mockEnforcerRuleHelper);

        Mockito.verify(this.mockLog).debug("ignoring this artifact due to no/missing parent");
    }

    /**
     * Verify failure when no parent exists, and the ignore-missing-parent setting is false.
     *
     * @throws Exception
     */
    @Test
    public void testExecuteFailWithNoParentVersion() throws Exception {
        this.initProject("x-group-x", "x-artifact-x", "1.0.0", null, null);
        this.rule.setIgnoreMissingParent(false);

        try {
            this.rule.execute(this.mockEnforcerRuleHelper);
            fail("Missing expected exception");
        } catch ( EnforcerRuleException exc ) {
            assertEquals("parent and project version mismatch: project=1.0.0; parent=unknown-project-parent-version",
                    exc.getMessage());
        }
    }

    /**
     * Verify ignoring of an artifact by artifact-id only.
     *
     * @throws Exception
     */
    @Test
    public void testExecutePassWithIgnoredArtifact1() throws Exception {
        this.initProject("x-ignore-group1-x", "x-ignore-artifact1-x", "1.0.0", "1.1.0", "1.2.0");
        this.rule.setIgnore(this.ignoreList);
        this.rule.execute(this.mockEnforcerRuleHelper);

        Mockito.verify(this.mockLog).debug("ignoring this artifact; it matches the ignore list");
    }

    /**
     * Verify ignoring of an artifact by &lt;group-id&gt;:&lt;artifact-id&gt; combination.
     *
     * @throws Exception
     */
    @Test
    public void testExecutePassWithIgnoredArtifact2() throws Exception {
        this.initProject("x-ignore-group2-x", "x-ignore-artifact2-x", "1.0.0", "1.1.0", "1.2.0");
        this.rule.setIgnore(this.ignoreList);
        this.rule.execute(this.mockEnforcerRuleHelper);

        Mockito.verify(this.mockLog).debug("ignoring this artifact; it matches the ignore list");
    }

    /**
     * Verify processing of an artifact that does not match the ignore list.
     *
     * @throws Exception
     */
    @Test
    public void testExecutePassUnignoredArtifact() throws Exception {
        this.initProject("x-group1-x", "x-artifact1-x", "1.0.0", "1.0.0", "1.0.0");

        this.rule.setIgnore(this.ignoreList);
        this.rule.execute(this.mockEnforcerRuleHelper);

        Mockito.verify(this.mockLog, Mockito.times(0)).debug("ignoring this artifact; it matches the ignore list");
    }

    /**
     * Verify handling of an evaluation exception on the ${project.artifact.artifactId} value during checking of the
     * ignore list.
     *
     * @throws Exception
     */
    @Test
    public void testExceptionOnEvaluateIgnoredList() throws Exception {
        Mockito.when(this.mockEnforcerRuleHelper.evaluate("${project.artifact.artifactId}"))
                .thenThrow(this.expressionEvaluationException);

        try {
            this.rule.execute(this.mockEnforcerRuleHelper);
            fail("missing expected exception");
        } catch ( EnforcerRuleException enforcerRuleExc ) {
            assertEquals("error while checking the ignore list", enforcerRuleExc.getMessage());
            assertSame(this.expressionEvaluationException, enforcerRuleExc.getCause());
        }
    }

    /**
     * Verify handling of an evaluation exception on the ${project.version} value.
     *
     * @throws Exception
     */
    @Test
    public void testExceptionOnReadProjectVersion() throws Exception {
        this.initProject("x-group1-x", "x-artifact1-x", "1.0.0", "1.0.0", "1.0.0");

        Mockito.when(this.mockEnforcerRuleHelper.evaluate("${project.version}"))
                .thenThrow(this.expressionEvaluationException);

        try {
            this.rule.execute(this.mockEnforcerRuleHelper);
            fail("missing expected exception");
        } catch ( EnforcerRuleException enforcerRuleExc ) {
            assertEquals("unable to determine the project version", enforcerRuleExc.getMessage());
            assertSame(this.expressionEvaluationException, enforcerRuleExc.getCause());
        }
    }

    /**
     * Verify handling of an evaluation exception on the ${project.parent.version} value.
     *
     * @throws Exception
     */
    @Test
    public void testExceptionOnReadProjectParentVersion() throws Exception {
        this.initProject("x-group1-x", "x-artifact1-x", "1.0.0", "1.0.0", "1.0.0");

        Mockito.when(this.mockEnforcerRuleHelper.evaluate("${project.parent.version}"))
                .thenThrow(this.expressionEvaluationException);

        try {
            this.rule.execute(this.mockEnforcerRuleHelper);
            fail("missing expected exception");
        } catch ( EnforcerRuleException enforcerRuleExc ) {
            assertEquals("unable to determine the parent version", enforcerRuleExc.getMessage());
            assertSame(this.expressionEvaluationException, enforcerRuleExc.getCause());
        }
    }

    /**
     * Verify handling of an evaluation exception on the ${project.parentArtifact.version} value.
     *
     * @throws Exception
     */
    @Test
    public void testExceptionOnReadProjectParentPomVersion() throws Exception {
        this.initProject("x-group1-x", "x-artifact1-x", "1.0.0", "1.0.0", "1.0.0");

        Mockito.when(this.mockEnforcerRuleHelper.evaluate("${project.parentArtifact.version}"))
                .thenThrow(this.expressionEvaluationException);

        try {
            this.rule.execute(this.mockEnforcerRuleHelper);
            fail("missing expected exception");
        } catch ( EnforcerRuleException enforcerRuleExc ) {
            assertEquals("unable to determine the version of the parent specified in the pom",
                    enforcerRuleExc.getMessage());
            assertSame(this.expressionEvaluationException, enforcerRuleExc.getCause());
        }
    }

    /**
     * Initialize test interactions to mimic a project with the given settings.
     *
     * @param groupId groupId of the artifact being built.
     * @param artifactId artifactId of the artifact being built.
     * @param version version number of the artifact being built.
     * @param parentVersion version number of the parent being built (null = none).
     * @param parentVersionInPom version number of the parent specified in the artifact's pom file (null = none).
     * @throws Exception
     */
    private void initProject(String groupId, String artifactId, String version, String parentVersion,
                             String parentVersionInPom) throws Exception {

        Mockito.when(this.mockEnforcerRuleHelper.evaluate("${project.version}")).thenReturn(version);
        Mockito.when(this.mockEnforcerRuleHelper.evaluate("${project.parent.version}")).thenReturn(parentVersion);
        Mockito.when(this.mockEnforcerRuleHelper.evaluate("${project.parentArtifact.version}"))
                .thenReturn(parentVersionInPom);
        Mockito.when(this.mockEnforcerRuleHelper.evaluate("${project.artifact.artifactId}")).thenReturn(artifactId);
        Mockito.when(this.mockEnforcerRuleHelper.evaluate("${project.artifact.groupId}")).thenReturn(groupId);
    }
}