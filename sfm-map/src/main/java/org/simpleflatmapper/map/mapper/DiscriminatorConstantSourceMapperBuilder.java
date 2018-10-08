package org.simpleflatmapper.map.mapper;

import org.simpleflatmapper.converter.Context;
import org.simpleflatmapper.map.FieldKey;
import org.simpleflatmapper.map.FieldMapper;
import org.simpleflatmapper.map.FieldMapperErrorHandler;
import org.simpleflatmapper.map.MapperBuilderErrorHandler;
import org.simpleflatmapper.map.MapperBuildingException;
import org.simpleflatmapper.map.MapperConfig;
import org.simpleflatmapper.map.MappingContext;
import org.simpleflatmapper.map.MappingException;
import org.simpleflatmapper.map.SourceFieldMapper;
import org.simpleflatmapper.map.context.MappingContextFactory;
import org.simpleflatmapper.map.context.MappingContextFactoryBuilder;
import org.simpleflatmapper.map.impl.GenericBuilder;
import org.simpleflatmapper.reflect.BiInstantiator;
import org.simpleflatmapper.reflect.meta.ClassMeta;
import org.simpleflatmapper.reflect.meta.PropertyFinder;
import org.simpleflatmapper.reflect.meta.PropertyMeta;
import org.simpleflatmapper.util.ForEachCallBack;
import org.simpleflatmapper.util.Predicate;
import org.simpleflatmapper.util.TypeHelper;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;


public class DiscriminatorConstantSourceMapperBuilder<S, T, K extends FieldKey<K>>  extends ConstantSourceMapperBuilder<S, T, K> {
    
    private final DiscriminatedBuilder<S, T, K>[] builders;
    private final MappingContextFactoryBuilder<? super S, K> mappingContextFactoryBuilder;
    private final CaptureError mapperBuilderErrorHandler = new CaptureError();

    @SuppressWarnings("unchecked")
    public DiscriminatorConstantSourceMapperBuilder(
            MapperConfig.Discriminator<? super S, T> discriminator,
            final MapperSource<? super S, K> mapperSource,
            final ClassMeta<T> classMeta,
            final MapperConfig<K> mapperConfig,
            MappingContextFactoryBuilder<? super S, K> mappingContextFactoryBuilder,
            KeyFactory<K> keyFactory,
            PropertyFinder<T> propertyFinder) throws MapperBuildingException {
        this.mappingContextFactoryBuilder = mappingContextFactoryBuilder;
        
        builders = new DiscriminatedBuilder[discriminator.cases.length];

        MapperConfig<K> kMapperConfig = mapperConfig.mapperBuilderErrorHandler(mapperBuilderErrorHandler);
        
        for(int i = 0; i < discriminator.cases.length; i++) {
            MapperConfig.DiscrimnatorCase<? super S, ? extends T> discrimnatorCase = discriminator.cases[i];
            builders[i] = getDiscriminatedBuilder(mapperSource, mappingContextFactoryBuilder, keyFactory, propertyFinder, kMapperConfig, discrimnatorCase);
        }
    }

    private <T> DiscriminatedBuilder<S, T, K> getDiscriminatedBuilder(MapperSource<? super S, K> mapperSource, MappingContextFactoryBuilder<? super S, K> mappingContextFactoryBuilder, KeyFactory<K> keyFactory, PropertyFinder<T> propertyFinder, MapperConfig<K> kMapperConfig, MapperConfig.DiscrimnatorCase<? super S, ? extends T> discrimnatorCase) {
        return new DiscriminatedBuilder<S, T, K>((MapperConfig.DiscrimnatorCase<? super S, T>) discrimnatorCase, 
                new DefaultConstantSourceMapperBuilder<S, T, K>(mapperSource, (ClassMeta<T>) discrimnatorCase.classMeta, kMapperConfig, mappingContextFactoryBuilder, keyFactory, propertyFinder));
    }

    @Override
    public ConstantSourceMapperBuilder<S, T, K> addMapping(K key, ColumnDefinition<K, ?> columnDefinition) {
        for(DiscriminatedBuilder<S, T, K> builder : builders) {
            builder.builder.addMapping(key, columnDefinition);
        }
        return this;
    }

    @Override
    protected <P> void addMapping(K columnKey, ColumnDefinition<K, ?> columnDefinition, PropertyMeta<T, P> prop) {

        int i = 0;
        for(DiscriminatedBuilder<S, T, K> builder : builders) {
            if (TypeHelper.isAssignable(prop.getOwnerType(), builder.builder.getTargetType())) {
                builder.builder.addMapping(columnKey, columnDefinition, prop);
                i++;
            }
        }
        if (i == 0) {
            throw new IllegalStateException("No builder compatible with " + prop);
        }
        
    }
    @Override
    public List<K> getKeys() {
        HashSet<K> keys = new HashSet<K>();
        for(DiscriminatedBuilder<S, T, K> builder : builders) {
            keys.addAll(builder.builder.getKeys());
        }
        return new ArrayList<K>(keys);
    }

    @Override
    public <H extends ForEachCallBack<PropertyMapping<T, ?, K>>> H forEachProperties(H handler) {
        for(DiscriminatedBuilder<S, T, K> builder : builders) {
            builder.builder.forEachProperties(handler);
        }
        return handler;
    }

    @Override
    public ContextualSourceFieldMapperImpl<S, T> mapper() {
        SourceFieldMapper<S, T> mapper = sourceFieldMapper();
        return new ContextualSourceFieldMapperImpl<S, T>(mappingContextFactoryBuilder.build(), mapper);
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public SourceFieldMapper<S, T> sourceFieldMapper() {
        PredicatedInstantiator<S, T>[] predicatedInstantiator = new PredicatedInstantiator[builders.length];
        
        for(int i = 0; i < builders.length; i++) {
            DiscriminatedBuilder<S, T, K> builder = builders[i];
            DefaultConstantSourceMapperBuilder.GenericBuilderMapping genericBuilderMapping = builder.builder.getGenericBuilderMapping();
            predicatedInstantiator[i] = new PredicatedInstantiator<S, T>(builder.discrimnatorCase.predicate, genericBuilderMapping.genericBuilderInstantiator);
        }
        GenericBuildBiInstantiator gbi = new GenericBuildBiInstantiator(predicatedInstantiator);

        DiscriminatorGenericBuilderMapper<S, T> mapper = new DiscriminatorGenericBuilderMapper<S, T>(gbi);
        
        return new TransformSourceFieldMapper<S, GenericBuilder<S, T>, T>(mapper, new FieldMapper[0], GenericBuilder.<S, T>buildFunction());
    }

    @Override
    public boolean isRootAggregate() {
        return builders[0].builder.isRootAggregate();
    }

    @Override
    public MappingContextFactory<? super S> contextFactory() {
        return builders[0].builder.contextFactory();
    }

    @Override
    public void addMapper(FieldMapper<S, T> mapper) {
        for(DiscriminatedBuilder<S, T, K> builder : builders) {
            builder.builder.addMapper(mapper);
        }
    }


    private static class DiscriminatedBuilder<S, T, K extends FieldKey<K>> {
        private final MapperConfig.DiscrimnatorCase<? super S, T> discrimnatorCase;
        private final DefaultConstantSourceMapperBuilder<S, T, K> builder;

        private DiscriminatedBuilder(MapperConfig.DiscrimnatorCase<? super S, T> discrimnatorCase, DefaultConstantSourceMapperBuilder<S, T, K> builder) {
            this.discrimnatorCase = discrimnatorCase;
            this.builder = builder;
        }
    }

    private static class GenericBuildBiInstantiator<S, T> implements BiInstantiator<S, MappingContext<S>, GenericBuilder<S, T>> {
        private final PredicatedInstantiator<S, T>[] predicatedInstantiators;

        public GenericBuildBiInstantiator(PredicatedInstantiator<S, T>[] predicatedInstantiators) {
            this.predicatedInstantiators = predicatedInstantiators;
        }

        @SuppressWarnings("unchecked")
        @Override
        public GenericBuilder<S, T> newInstance(S o, MappingContext<S> o2) throws Exception {
            for(PredicatedInstantiator<S, T> pi : predicatedInstantiators) {
                //noinspection unchecked
                if (pi.predicate.test(o)) {
                    return pi.instantiator.newInstance(o, o2);
                }
            }
            throw new IllegalArgumentException("No discrimator matched " + o); 
        }

        private BiInstantiator<S, MappingContext<? super S>, GenericBuilder> getInstantiator(Object o) {
            for(PredicatedInstantiator pi : predicatedInstantiators) {
                //noinspection unchecked
                if (pi.predicate.test(o)) {
                    return pi.instantiator;
                }
            }
            throw new IllegalArgumentException("No discrimator matched " + o);
        }
    }

    private static class PredicatedInstantiator<S, T> {
        private final Predicate predicate;
        private final BiInstantiator<S, MappingContext<? super S>, GenericBuilder<S, T>> instantiator;

        private PredicatedInstantiator(Predicate predicate, BiInstantiator<S, MappingContext<? super S>, GenericBuilder<S, T>> instantiator) {
            this.predicate = predicate;
            this.instantiator = instantiator;
        }
    }

    private class DiscriminatorGenericBuilderMapper<S, T> extends AbstractMapper<S, GenericBuilder<S, T>> {
        public DiscriminatorGenericBuilderMapper(GenericBuildBiInstantiator gbi) {
            super(gbi);
        }

        @Override
        protected void mapFields(S source, GenericBuilder<S, T> target, MappingContext<? super S> mappingContext) throws Exception {
            target.mapFrom(source, mappingContext);
        }

        @Override
        protected void mapToFields(S source, GenericBuilder<S, T> target, MappingContext<? super S> mappingContext) throws Exception {
            target.mapFrom(source, mappingContext);
        }
    }

    private class CaptureError implements MapperBuilderErrorHandler {
        @Override
        public void accessorNotFound(String msg) {
            
        }

        @Override
        public void propertyNotFound(Type target, String property) {

        }

        @Override
        public void customFieldError(FieldKey<?> key, String message) {

        }
    }
}