/*
 * Copyright 2014 the original author or authors.
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
package org.glowroot.weaving;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.common.collect.Lists;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.objectweb.asm.Type;

import org.glowroot.common.Reflections;
import org.glowroot.common.Reflections.ReflectiveException;
import org.glowroot.markers.UsedByGeneratedBytecode;

// can't generate classes in bootstrap class loader, so this is needed for storing meta holders
// similar technique is not good for non-bootstrap class loaders anyways since then weak references
// would need to be used to prevent retention of meta holders
@UsedByGeneratedBytecode
public class BootstrapMetaHolders {

    private static final Map<String, Integer> classMetaHolderIndexes =
            new ConcurrentHashMap<String, Integer>();
    private static final Map<String, Integer> methodMetaHolderIndexes =
            new ConcurrentHashMap<String, Integer>();
    private static final List</*@Nullable*/ClassMetaHolder> classMetaHolders =
            Lists.newCopyOnWriteArrayList();
    private static final List</*@Nullable*/MethodMetaHolder> methodMetaHolders =
            Lists.newCopyOnWriteArrayList();

    private BootstrapMetaHolders() {}

    public static int reserveClassMetaHolderIndex(String metaHolderInternalName,
            String classMetaFieldName) {
        synchronized (classMetaHolders) {
            String key = metaHolderInternalName + '.' + classMetaFieldName;
            Integer index = classMetaHolderIndexes.get(key);
            if (index == null) {
                classMetaHolders.add(null);
                index = classMetaHolders.size() - 1;
                classMetaHolderIndexes.put(key, index);
            }
            return index;
        }
    }

    public static int reserveMethodMetaHolderIndex(String metaHolderInternalName,
            String methodMetaFieldName) {
        synchronized (methodMetaHolders) {
            methodMetaHolders.add(null);
            int index = methodMetaHolders.size() - 1;
            methodMetaHolderIndexes.put(metaHolderInternalName + '.' + methodMetaFieldName, index);
            return index;
        }
    }

    public static void createClassMetaHolder(String metaHolderInternalName,
            String classMetaFieldName, Type classMetaType, Type type) {
        String key = metaHolderInternalName + '.' + classMetaFieldName;
        Integer index = classMetaHolderIndexes.get(key);
        if (index == null) {
            throw new AssertionError("ClassMetaHolder was not reserved for key: " + key);
        }
        ClassMetaHolder classMetaHolder = new ClassMetaHolder(classMetaType, type);
        classMetaHolders.set(index, classMetaHolder);
    }

    public static void createMethodMetaHolder(String metaHolderInternalName,
            String methodMetaFieldName, Type methodMetaType, Type type, Type returnType,
            List<Type> parameterTypes) {
        String key = metaHolderInternalName + '.' + methodMetaFieldName;
        Integer index = methodMetaHolderIndexes.get(key);
        if (index == null) {
            throw new AssertionError("MethodMetaHolder was not reserved for key: " + key);
        }
        MethodMetaHolder methodMetaHolder =
                new MethodMetaHolder(methodMetaType, type, returnType, parameterTypes);
        methodMetaHolders.set(index, methodMetaHolder);
    }

    public static Object getClassMeta(int index) {
        ClassMetaHolder classMetaHolder = classMetaHolders.get(index);
        if (classMetaHolder == null) {
            throw new AssertionError("ClassMetaHolder was not instantiated for index: " + index);
        }
        return classMetaHolder.getClassMeta();
    }

    public static Object getMethodMeta(int index) {
        MethodMetaHolder methodMetaHolder = methodMetaHolders.get(index);
        if (methodMetaHolder == null) {
            throw new AssertionError("MethodMetaHolder was not instantiated for index: " + index);
        }
        return methodMetaHolder.getMethodMeta();
    }

    private static Class<?> getType(Type type) throws ClassNotFoundException {
        switch (type.getSort()) {
            case Type.VOID:
                return void.class;
            case Type.BOOLEAN:
                return boolean.class;
            case Type.CHAR:
                return char.class;
            case Type.BYTE:
                return byte.class;
            case Type.SHORT:
                return short.class;
            case Type.INT:
                return int.class;
            case Type.FLOAT:
                return float.class;
            case Type.LONG:
                return long.class;
            case Type.DOUBLE:
                return double.class;
            case Type.ARRAY:
                return GeneratedBytecodeUtil.getArrayClass(getType(type.getElementType()),
                        type.getDimensions());
            default:
                return Class.forName(type.getClassName(), false, null);
        }
    }

    private static class ClassMetaHolder {

        private final Type classMetaType;
        private final Type type;
        private volatile @MonotonicNonNull Object classMeta;

        private ClassMetaHolder(Type classMetaType, Type type) {
            this.classMetaType = classMetaType;
            this.type = type;
        }

        private Object getClassMeta() {
            Object classMetaLocal = classMeta;
            if (classMetaLocal != null) {
                return classMetaLocal;
            }
            synchronized (this) {
                if (classMeta == null) {
                    Class<?> classMetaClass;
                    Class<?> wovenClass;
                    try {
                        classMetaClass = getType(classMetaType);
                        wovenClass = getType(type);
                    } catch (ClassNotFoundException e) {
                        throw new IllegalStateException("Error instantiating @BindClassMeta class",
                                e);
                    }
                    try {
                        Constructor<?> constructor =
                                Reflections.getConstructor(classMetaClass, Class.class);
                        classMeta = Reflections.invoke(constructor, wovenClass);
                    } catch (ReflectiveException e) {
                        throw new IllegalStateException("Error instantiating @BindClassMeta class",
                                e);
                    }
                }
            }
            return classMeta;
        }
    }

    private static class MethodMetaHolder {

        private final Type methodMetaType;
        private final Type type;
        private final Type returnType;
        private final List<Type> parameterTypes;
        private volatile @MonotonicNonNull Object methodMeta;

        private MethodMetaHolder(Type methodMetaType, Type type, Type returnType,
                List<Type> parameterTypes) {
            this.methodMetaType = methodMetaType;
            this.type = type;
            this.returnType = returnType;
            this.parameterTypes = parameterTypes;
        }

        private Object getMethodMeta() {
            Object methodMetaLocal = methodMeta;
            if (methodMetaLocal != null) {
                return methodMetaLocal;
            }
            synchronized (this) {
                if (methodMeta == null) {
                    Class<?> classMetaClass;
                    Class<?> wovenClass;
                    Class<?> returnClass;
                    Class<?>[] parameterClasses;
                    try {
                        classMetaClass = getType(methodMetaType);
                        wovenClass = getType(type);
                        returnClass = getType(returnType);
                        parameterClasses = new Class[parameterTypes.size()];
                        for (int i = 0; i < parameterTypes.size(); i++) {
                            parameterClasses[i] = getType(parameterTypes.get(i));
                        }
                    } catch (ClassNotFoundException e) {
                        throw new IllegalStateException(
                                "Error instantiating @BindMethodMeta class", e);
                    }
                    try {
                        Constructor<?> constructor = Reflections.getConstructor(classMetaClass,
                                Class.class, Class.class, Class[].class);
                        methodMeta = Reflections.invoke(constructor, wovenClass, returnClass,
                                parameterClasses);
                    } catch (ReflectiveException e) {
                        throw new IllegalStateException(
                                "Error instantiating @BindMethodMeta class", e);
                    }
                }
            }
            return methodMeta;
        }
    }
}