package ma.glasnost.orika.impl;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import ma.glasnost.orika.Converter;
import ma.glasnost.orika.Mapper;
import ma.glasnost.orika.MapperFacade;
import ma.glasnost.orika.MapperFactory;
import ma.glasnost.orika.MappingException;
import ma.glasnost.orika.ObjectFactory;
import ma.glasnost.orika.metadata.ClassMap;
import ma.glasnost.orika.metadata.ClassMapBuilder;
import ma.glasnost.orika.metadata.ConverterKey;
import ma.glasnost.orika.metadata.MapperKey;

public class DefaultMapperFactory implements MapperFactory {

	private final MapperFacade mapperFacade;
	private final MapperGenerator mapperGenerator;
	private final Map<MapperKey, GeneratedMapperBase> mappersRegistry;
	private final Map<ConverterKey, Converter<?, ?>> convertersRegistry;
	private final Map<Class<?>, ObjectFactory<?>> objectFactoryRegistry;
	private final Map<Class<?>, Set<Class<?>>> aToBRegistry;

	public DefaultMapperFactory(Set<ClassMap<?, ?>> classMaps, Set<Converter<?, ?>> converters,
			Set<ObjectFactory<?>> objectFactories) {
		this.mapperGenerator = new MapperGenerator(this);
		this.mappersRegistry = new ConcurrentHashMap<MapperKey, GeneratedMapperBase>();
		this.mapperFacade = new MapperFacadeImpl(this);
		this.convertersRegistry = new ConcurrentHashMap<ConverterKey, Converter<?, ?>>();
		this.aToBRegistry = new ConcurrentHashMap<Class<?>, Set<Class<?>>>();

		if (classMaps != null) {
			for (ClassMap<?, ?> classMap : classMaps) {
				registerClassMap(classMap);
			}
		}

		if (converters == null) {
			// add builtin converter
		}

		objectFactoryRegistry = new ConcurrentHashMap<Class<?>, ObjectFactory<?>>();
		if (objectFactories != null) {
			for (ObjectFactory<?> objectFactory : objectFactories) {
				objectFactoryRegistry.put(objectFactory.getTargetClass(), objectFactory);
			}
		}
	}

	public DefaultMapperFactory() {
		this(null, null, null);
	}

	public GeneratedMapperBase get(MapperKey mapperKey) {
		if (!mappersRegistry.containsKey(mapperKey)) {
			ClassMap<?, ?> classMap = ClassMapBuilder.map(mapperKey.getAType(), mapperKey.getBType()).byDefault().toClassMap();
			registerClassMap(classMap);
		}
		return mappersRegistry.get(mapperKey);
	}

	public <S, D> void registerConverter(String converterId, Converter<S, D> converter) {
		convertersRegistry.put(new ConverterKey(converter.getSource(), converter.getDestination()), converter);
	}

	@SuppressWarnings("unchecked")
	public <S, D> Converter<S, D> lookupConverter(Class<S> source, Class<D> destination) {
		return (Converter<S, D>) convertersRegistry.get(new ConverterKey(source, destination));
	}

	public MapperFacade getMapperFacade() {
		return mapperFacade;
	}

	public <T> void registerObjectFactory(ObjectFactory<T> objectFactory, Class<T> targetClass) {
		objectFactoryRegistry.put(targetClass, objectFactory);
	}

	@SuppressWarnings("unchecked")
	public <T> ObjectFactory<T> lookupObjectFactory(Class<T> targetClass) {
		return (ObjectFactory<T>) objectFactoryRegistry.get(targetClass);
	}

	public <S, D> Class<? extends D> lookupConcreteDestinationClass(Class<S> sourceClass, Class<D> destinationClass,
			MappingContext context) {
		Class<? extends D> concreteClass = context.getConcreteClass(sourceClass, destinationClass);

		if (concreteClass != null) {
			return concreteClass;
		}

		Set<Class<?>> destinationSet = aToBRegistry.get(sourceClass);
		if (destinationSet == null || destinationSet.isEmpty()) {
			return null;
		}

		for (Class<?> clazz : destinationSet) {
			if (destinationClass.isAssignableFrom(clazz)) {
				if (concreteClass != null) {
					throw new MappingException("Can not decide which concrete destination class to pick for class: "
							+ sourceClass.getName());
				} else {
					@SuppressWarnings("unchecked")
					Class<? extends D> cls = (Class<? extends D>) clazz;
					concreteClass = cls;
				}
			}
		}
		return concreteClass;
	}

	@SuppressWarnings("unchecked")
	public <S, D> void registerClassMap(ClassMap<S, D> classMap) {
		register(classMap.getAType(), classMap.getBType());
		register(classMap.getBType(), classMap.getAType());

		MapperKey mapperKey = new MapperKey(classMap.getAType(), classMap.getBType());
		GeneratedMapperBase mapper = this.mapperGenerator.build(classMap);
		mapper.setMapperFacade(mapperFacade);
		mapper.setCustomMapper((Mapper<Object, Object>) classMap.getCustomizedMapper());
		mappersRegistry.put(mapperKey, mapper);
	}

	private <S, D> void register(Class<S> sourceClass, Class<D> destinationClass) {
		Set<Class<?>> destinationSet = aToBRegistry.get(sourceClass);
		if (destinationSet == null) {
			destinationSet = new HashSet<Class<?>>();
			aToBRegistry.put(sourceClass, destinationSet);
		}
		destinationSet.add(destinationClass);
	}

}
