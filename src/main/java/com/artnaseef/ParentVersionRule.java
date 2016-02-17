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

package com.artnaseef;

import org.apache.maven.enforcer.rule.api.EnforcerRule;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.enforcer.rule.api.EnforcerRuleHelper;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluationException;

import java.util.LinkedList;
import java.util.List;

/**
 * Maven Enforcer Plugin rule that verifies the parent version of artifacts matches the version of the artifact itself
 * and the version of the parent defined in the POM equals the effective version of the parent in the build.  In order
 * to use with a multi-module project in which the top-level pom has a parent with a different version, which is very
 * common, use the &lt;ignore&gt; configuration setting to specific the artifact, or group and artifact, of the pom
 * for which to skip the validation.
 *
 * <p>
 *  &lt;ignore&gt;
 *      &lt;value&gt;cxp-parent&lt;/value&gt;
 *  &lt;/ignore&gt;
 * </p>
 *
 * Created by art on 2/17/16.
 */
public class ParentVersionRule implements EnforcerRule {

    /**
     * List of artifacts to ignore, each specified as either
     * (a) simply the artifact ID, or
     * (b) &lt;group-id&gt;:&lt;artifact-id&gt;
     */
    private List<String> ignore = new LinkedList<>();

    /**
     * Whether to ignore artifacts which have no parent.
     */
    private boolean ignoreMissingParent = true;

    public List<String> getIgnore() {
        return ignore;
    }

    public void setIgnore(List<String> ignore) {
        this.ignore = ignore;
    }

    public boolean isIgnoreMissingParent() {
        return ignoreMissingParent;
    }

    public void setIgnoreMissingParent(boolean ignoreMissingParent) {
        this.ignoreMissingParent = ignoreMissingParent;
    }

    /**
     * Validate the parent version; this is the main method for this rule.
     *
     * @param enforcerRuleHelper enforcer interface to the build.
     * @throws EnforcerRuleException on any validation failure.
     */
    public void execute(EnforcerRuleHelper enforcerRuleHelper) throws EnforcerRuleException {
        String projectVersion;
        String parentVersion;
        String parentArtifactVersion;

        //
        // Check whether to ignore this artifact.  A commonly ignored artifact will be the parent of the module
        //  hierarchy.
        //
        try {
            if (this.checkIgnore(enforcerRuleHelper)) {
                enforcerRuleHelper.getLog().debug("ignoring this artifact; it matches the ignore list");
                return;
            }
        } catch (ExpressionEvaluationException evalExc) {
            throw new EnforcerRuleException("error while checking the ignore list", evalExc);
        }


        //
        // Read the project version.
        //
        try {
            projectVersion = this.getProperty(enforcerRuleHelper, "project.version", "unknown-project-version");
        } catch (ExpressionEvaluationException evalExc) {
            throw new EnforcerRuleException("unable to determine the project version", evalExc);
        }


        //
        // Read the parent project version - the effective one in-use for this build.
        //
        try {
            parentVersion = this.getProperty(enforcerRuleHelper, "project.parent.version", "unknown-project-parent-version");

            //
            // If the parent version is missing, ignore this artifact if configured to do so.
            //
            if ((ignoreMissingParent) && (parentVersion.equals("unknown-project-parent-version"))) {
                enforcerRuleHelper.getLog().debug("ignoring this artifact due to no/missing parent");
                return;
            }
        } catch (ExpressionEvaluationException evalExc) {
            throw new EnforcerRuleException("unable to determine the parent version", evalExc);
        }


        //
        // Read the parent version from the POM.
        //
        try {
            parentArtifactVersion = this.getProperty(enforcerRuleHelper, "project.parentArtifact.version",
                    "unknown-parent-version-from-pom");
        } catch (ExpressionEvaluationException evalExc) {
            throw new EnforcerRuleException("unable to determine the version of the parent specified in the pom",
                    evalExc);
        }


        //
        // Verify the project version and the parent version match.
        //
        if (!(projectVersion.equals(parentVersion))) {
            throw new EnforcerRuleException("parent and project version mismatch: project=" +
                    projectVersion + "; parent=" + parentVersion);
        }


        //
        // Also verify the effective parent version for this build is the same as the one specified in the POM.
        //
        if (!(parentArtifactVersion.equals(parentVersion))) {
            throw new EnforcerRuleException("actual parent version does not match the one listed in the pom: " +
                    "actual parent version=" + parentVersion + "; version from pom=" + parentArtifactVersion);
        }
    }

    public boolean isCacheable() {
        return false;
    }

    public boolean isResultValid(EnforcerRule enforcerRule) {
        return false;
    }

    public String getCacheId() {
        return null;
    }

    /**
     * Check whether the artifact being validated is in the ignore list.
     *
     * @param enforcerRuleHelper enforcer interface to the build.
     * @return true => the artifact should be ignored; false => the artifact should be processed normally.
     * @throws ExpressionEvaluationException - when a problem is encountered evaluating project properties.
     */
    private boolean checkIgnore(EnforcerRuleHelper enforcerRuleHelper) throws ExpressionEvaluationException {
        String artifact;
        String group;

        //
        // Determine the group ID and artifact ID for the current artifact being built.
        //
        artifact = this.getProperty(enforcerRuleHelper, "project.artifact.artifactId", "unknown-artifact");
        group = this.getProperty(enforcerRuleHelper, "project.artifact.groupId", "unknown-artifact");

        String combined = group + ":" + artifact;

        enforcerRuleHelper.getLog().debug("checking ignore of <group>:<artifact>=" + combined);


        //
        // Loop over the list of artifacts to ignore and compare against each.
        //
        for (String toIgnore : this.ignore) {
            enforcerRuleHelper.getLog().debug("checking ignore of " + combined + " against " + toIgnore);

            //
            // Check if the ignore specification is in <group>:<artifact> format, or just <artifact> format.
            //
            if (toIgnore.contains(":")) {
                if (toIgnore.equals(combined)) {
                    return true;
                }
            } else {
                if (toIgnore.equals(artifact)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Read the named property for the maven build and return its value, if defined, or the specified default value
     * otherwise.
     *
     * @param enforcerRuleHelper enforcer interface to the build.
     * @param name name of the property to return.
     * @param defaultValue value to return if the property is not defined.
     * @return the value of the property, if defined, or the default value otherwise.
     * @throws ExpressionEvaluationException - when a problem is encountered evaluating project properties.
     */
    private String getProperty(EnforcerRuleHelper enforcerRuleHelper, String name, String defaultValue) throws ExpressionEvaluationException {
        String result;
        Object propValue;

        //
        // Evaluate the property.
        //
        propValue = enforcerRuleHelper.evaluate("${" + name + "}");
        if (propValue != null) {
            // Found it; return it as a string.
            result = propValue.toString();
            enforcerRuleHelper.getLog().debug("property '" + name + "='" + result + "'");
        } else {
            // Not found; return the default value.
            result = defaultValue;
            enforcerRuleHelper.getLog().debug("property '" + name + "' not found; using default value '" +
                    defaultValue + "'");
        }

        return result;
    }
}
