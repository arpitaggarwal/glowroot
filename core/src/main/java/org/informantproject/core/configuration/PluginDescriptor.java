/**
 * Copyright 2012 the original author or authors.
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
package org.informantproject.core.configuration;

import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.informantproject.api.weaving.Mixin;
import org.informantproject.core.weaving.Advice;
import org.informantproject.core.weaving.Weaver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * @author Trask Stalnaker
 * @since 0.5
 */
public class PluginDescriptor {

    private static final Logger logger = LoggerFactory.getLogger(PluginDescriptor.class);

    private final String name;
    private final String groupId;
    private final String artifactId;
    private final String version;
    private final ImmutableList<PropertyDescriptor> propertyDescriptors;
    // marked transient for gson serialization
    private final transient List<Mixin> mixins;
    // marked transient for gson serialization
    private final transient List<Advice> advisors;

    private final Map<String, PropertyDescriptor> propertyDescriptorsByName;

    public PluginDescriptor(String name, String groupId, String artifactId, String version,
            List<PropertyDescriptor> propertyDescriptors, List<String> aspects) {

        this.name = name;
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.propertyDescriptors = ImmutableList.copyOf(propertyDescriptors);
        List<Mixin> mixins = Lists.newArrayList();
        List<Advice> advisors = Lists.newArrayList();
        for (String aspect : aspects) {
            try {
                // don't initialize the aspect since that will trigger static initializers which
                // will probably call PluginServices.get()
                Class<?> aspectClass = Class.forName(aspect, false, PluginDescriptor.class
                        .getClassLoader());
                advisors.addAll(Weaver.getAdvisors(aspectClass));
                mixins.addAll(Weaver.getMixins(aspectClass));
            } catch (ClassNotFoundException e) {
                continue;
            }
        }
        this.mixins = ImmutableList.copyOf(mixins);
        this.advisors = ImmutableList.copyOf(advisors);
        propertyDescriptorsByName = Maps.newHashMap();
        for (PropertyDescriptor propertyDescriptor : propertyDescriptors) {
            propertyDescriptorsByName.put(propertyDescriptor.getName(), propertyDescriptor);
        }
    }

    public String getId() {
        return groupId + ":" + artifactId;
    }

    public String getName() {
        return name;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getVersion() {
        return version;
    }

    public List<PropertyDescriptor> getPropertyDescriptors() {
        return propertyDescriptors;
    }

    public List<Mixin> getMixins() {
        return mixins;
    }

    public List<Advice> getAdvisors() {
        return advisors;
    }

    @Nullable
    public PropertyDescriptor getPropertyDescriptor(String propertyName) {
        return propertyDescriptorsByName.get(propertyName);
    }

    // equality is defined only in terms of groupId and artifactId
    @Override
    public boolean equals(@Nullable Object o) {
        if (!(o instanceof PluginDescriptor)) {
            return false;
        }
        PluginDescriptor other = (PluginDescriptor) o;
        return groupId.equals(other.groupId) && artifactId.equals(other.artifactId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(groupId, artifactId);
    }

    public static class PropertyDescriptor {
        private final String prompt;
        private final String name;
        private final String type;
        private final Object defaultValue;
        private final boolean hidden;
        private final String description;
        public PropertyDescriptor(String prompt, String name, String type,
                @Nullable Object defaultValue, boolean hidden, @Nullable String description) {
            this.prompt = prompt;
            this.name = name;
            this.type = type;
            this.defaultValue = defaultValue;
            this.hidden = hidden;
            this.description = description;
        }
        public String getPrompt() {
            return prompt;
        }
        public String getName() {
            return name;
        }
        public String getType() {
            return type;
        }
        @Nullable
        public Object getDefault() {
            return defaultValue;
        }
        public boolean isHidden() {
            return hidden;
        }
        @Nullable
        public String getDescription() {
            return description;
        }
        public Class<?> getJavaClass() {
            if (type.equals("string")) {
                return String.class;
            } else if (type.equals("boolean")) {
                return Boolean.class;
            } else if (type.equals("double")) {
                return Double.class;
            } else {
                logger.error("unexpected type '" + type + "', this should have been caught by"
                        + " schema validation");
                return String.class;
            }
        }
    }
}
