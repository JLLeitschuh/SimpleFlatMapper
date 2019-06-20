package org.simpleflatmapper.map.property;


import org.simpleflatmapper.converter.Context;
import org.simpleflatmapper.map.FieldKey;
import org.simpleflatmapper.map.context.MappingContextFactoryBuilder;
import org.simpleflatmapper.map.getter.ContextualGetter;
import org.simpleflatmapper.map.getter.ContextualGetterFactory;
import org.simpleflatmapper.map.getter.ContextualGetterFactoryAdapter;
import org.simpleflatmapper.reflect.getter.GetterFactory;
import org.simpleflatmapper.util.TypeHelper;

import java.lang.reflect.Type;
import java.sql.ResultSet;
import java.sql.SQLException;

public class GetterFactoryProperty {
    private final ContextualGetterFactory<?, ?> getterFactory;
    private final Type sourceType;

    public GetterFactoryProperty(ContextualGetterFactory<?, ?> getterFactory) {
        this(getterFactory, getSourceType(getterFactory));
    }

    public GetterFactoryProperty(ContextualGetterFactory<?, ?> getterFactory, Type sourceType) {
        this.getterFactory = getterFactory;
        this.sourceType = sourceType;
    }

    public GetterFactoryProperty(GetterFactory<?, ?> getterFactory) {
        this(getterFactory, getSourceType(getterFactory));
    }

    public GetterFactoryProperty(GetterFactory<?, ?> getterFactory, Type sourceType) {
        this.getterFactory = new ContextualGetterFactoryAdapter(getterFactory);
        this.sourceType = sourceType;
    }

    public ContextualGetterFactory<?, ?> getGetterFactory() {
        return getterFactory;
    }

    public Type getSourceType() {
        return sourceType;
    }

    @Override
    public String toString() {
        return "GetterFactory{" + getterFactory + "}";
    }

    private static Type getSourceType(ContextualGetterFactory<?, ?> getterFactory) {
        Type[] types = TypeHelper.getGenericParameterForClass(getterFactory.getClass(), ContextualGetterFactory.class);
        return types != null ? types[0] : null;
    }
    private static Type getSourceType(GetterFactory<?, ?> getterFactory) {
        Type[] types = TypeHelper.getGenericParameterForClass(getterFactory.getClass(), GetterFactory.class);
        return types != null ? types[0] : null;
    }


    public static <T, K extends FieldKey<K>, S> GetterFactoryProperty forType(final Class<T> type, final IndexedGetter<S, T> getter) {
        ContextualGetterFactory<ResultSet, K> getterFactory = new ContextualGetterFactory<ResultSet, K>() {
            @Override
            public <P> ContextualGetter<ResultSet, P> newGetter(Type target, K key, MappingContextFactoryBuilder<?, K> mappingContextFactoryBuilder, Object... properties) {
                if (TypeHelper.areEquals(type, target)) {
                    final int index = key.getIndex();
                    return (ContextualGetter<ResultSet, P>) new IndexedGetterAdapter<S, T>(getter, index);
                }
                return null;
            }
        };

        return new GetterFactoryProperty(getterFactory);
    }


    public interface IndexedGetter<S, T> {
        T get(S s, int i) throws Exception;
    }

    private static class IndexedGetterAdapter<S, T> implements ContextualGetter<S, T> {
        private final IndexedGetter<S, T> getter;
        private final int index;

        public IndexedGetterAdapter(IndexedGetter<S, T> getter, int index) {
            this.getter = getter;
            this.index = index;
        }

        @Override
        public T get(S target, Context context) throws Exception {
            return getter.get(target, index);
        }
    }
}
