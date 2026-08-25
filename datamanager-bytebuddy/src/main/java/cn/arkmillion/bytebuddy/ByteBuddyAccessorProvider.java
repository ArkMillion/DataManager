package cn.arkmillion.bytebuddy;

import cn.arkmillion.core.accessor.PropertyAccessor;
import cn.arkmillion.core.accessor.PropertyAccessorFactory;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.implementation.MethodCall;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatchers;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class ByteBuddyAccessorProvider implements PropertyAccessorFactory {

    private static final ByteBuddy BYTE_BUDDY = new ByteBuddy();

    private final Map<Field, Optional<PropertyAccessor>> cache = new ConcurrentHashMap<>();

    @Override
    public PropertyAccessor create(Field field) {
        if (Modifier.isStatic(field.getModifiers())) {
            return null;
        }
        return cache.computeIfAbsent(field, ByteBuddyAccessorProvider::generate).orElse(null);
    }

    private static Optional<PropertyAccessor> generate(Field field) {
        Method getter = findGetter(field);
        Method setter = findSetter(field);
        if (getter == null || setter == null) {
            return Optional.empty();
        }
        try {
            AbstractPropertyReader reader = instantiate(generateReader(getter));
            AbstractPropertyWriter writer = instantiate(generateWriter(setter));
            return Optional.of(new GeneratedPropertyAccessor(reader, writer));
        } catch (ReflectiveOperationException e) {
            return Optional.empty();
        }
    }

    private static Class<? extends AbstractPropertyReader> generateReader(Method getter) {
        DynamicType.Unloaded<AbstractPropertyReader> unloaded = BYTE_BUDDY
                .subclass(AbstractPropertyReader.class)
                .method(ElementMatchers.<MethodDescription>named("read"))
                .intercept(MethodCall.invoke(getter)
                        .onArgument(0)
                        .withAssigner(Assigner.DEFAULT, Assigner.Typing.DYNAMIC))
                .make();
        return unloaded
                .load(AbstractPropertyReader.class.getClassLoader(), ClassLoadingStrategy.Default.WRAPPER)
                .getLoaded();
    }

    private static Class<? extends AbstractPropertyWriter> generateWriter(Method setter) {
        DynamicType.Unloaded<AbstractPropertyWriter> unloaded = BYTE_BUDDY
                .subclass(AbstractPropertyWriter.class)
                .method(ElementMatchers.<MethodDescription>named("write"))
                .intercept(MethodCall.invoke(setter)
                        .onArgument(0)
                        .withArgument(1)
                        .withAssigner(Assigner.DEFAULT, Assigner.Typing.DYNAMIC))
                .make();
        return unloaded
                .load(AbstractPropertyWriter.class.getClassLoader(), ClassLoadingStrategy.Default.WRAPPER)
                .getLoaded();
    }

    private static <T> T instantiate(Class<? extends T> clazz) throws ReflectiveOperationException {
        Constructor<? extends T> constructor = clazz.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static Method findGetter(Field field) {
        String base = capitalize(field.getName());
        boolean booleanType = field.getType() == boolean.class || field.getType() == Boolean.class;
        Method candidate = lookupMethod(field.getDeclaringClass(), (booleanType ? "is" : "get") + base);
        if (candidate != null && candidate.getParameterCount() == 0 && Modifier.isPublic(candidate.getModifiers())) {
            return candidate;
        }
        return null;
    }

    private static Method findSetter(Field field) {
        String name = "set" + capitalize(field.getName());
        for (Class<?> current = field.getDeclaringClass(); current != null && current != Object.class;
             current = current.getSuperclass()) {
            for (Method m : current.getDeclaredMethods()) {
                if (m.getName().equals(name)
                        && m.getParameterCount() == 1
                        && Modifier.isPublic(m.getModifiers())
                        && m.getParameterTypes()[0].isAssignableFrom(field.getType())) {
                    return m;
                }
            }
        }
        return null;
    }

    private static Method lookupMethod(Class<?> clazz, String name) {
        for (Class<?> current = clazz; current != null && current != Object.class;
             current = current.getSuperclass()) {
            for (Method m : current.getDeclaredMethods()) {
                if (m.getName().equals(name)) {
                    return m;
                }
            }
        }
        return null;
    }

    private static String capitalize(String name) {
        if (name.isEmpty()) {
            return name;
        }
        if (name.length() == 1) {
            return Character.toUpperCase(name.charAt(0)) + "";
        }
        char first = Character.toUpperCase(name.charAt(0));
        if (Character.isUpperCase(name.charAt(1))) {
            return name;
        }
        return first + name.substring(1);
    }

    static final class GeneratedPropertyAccessor implements PropertyAccessor {

        private final AbstractPropertyReader reader;
        private final AbstractPropertyWriter writer;

        GeneratedPropertyAccessor(AbstractPropertyReader reader, AbstractPropertyWriter writer) {
            this.reader = reader;
            this.writer = writer;
        }

        @Override
        public Object get(Object entity) {
            return reader.read(entity);
        }

        @Override
        public void set(Object entity, Object value) {
            writer.write(entity, value);
        }
    }
}
