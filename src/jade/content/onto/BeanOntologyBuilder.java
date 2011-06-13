/*****************************************************************
JADE - Java Agent DEvelopment Framework is a framework to develop 
multi-agent systems in compliance with the FIPA specifications.
Copyright (C) 2000 CSELT S.p.A. 

GNU Lesser General Public License

This library is free software; you can redistribute it and/or
modify it under the terms of the GNU Lesser General Public
License as published by the Free Software Foundation, 
version 2.1 of the License. 

This library is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
Lesser General Public License for more details.

You should have received a copy of the GNU Lesser General Public
License along with this library; if not, write to the
Free Software Foundation, Inc., 59 Temple Place - Suite 330,
Boston, MA  02111-1307, USA.
*****************************************************************/

package jade.content.onto;

//#J2ME_EXCLUDE_FILE
//#APIDOC_EXCLUDE_FILE

import jade.content.AgentAction;
import jade.content.Concept;
import jade.content.Predicate;
import jade.content.onto.annotations.AggregateResult;
import jade.content.onto.annotations.AggregateSlot;
import jade.content.onto.annotations.Element;
import jade.content.onto.annotations.Result;
import jade.content.onto.annotations.Slot;
import jade.content.onto.annotations.SuppressSlot;
import jade.content.schema.AgentActionSchema;
import jade.content.schema.AggregateSchema;
import jade.content.schema.ConceptSchema;
import jade.content.schema.ObjectSchema;
import jade.content.schema.PredicateSchema;
import jade.content.schema.TermSchema;
import jade.content.schema.facets.DefaultValueFacet;
import jade.content.schema.facets.DocumentationFacet;
import jade.content.schema.facets.PermittedValuesFacet;
import jade.content.schema.facets.RegexFacet;
import jade.util.Logger;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;

class BeanOntologyBuilder {

	private final static Logger logger = Logger.getMyLogger(BeanOntologyBuilder.class.getName());

	public static final String ENUM_SLOT_NAME = "name";
	private static final String GETTER_PREFIX = "get";
	private static final String BOOLEAN_GETTER_PREFIX = "is";
	private static final String SETTER_PREFIX = "set";
	private static final Object GET_CLASS_METHOD = "getClass";

	private Ontology ontology;
	private BeanIntrospector introspector;

	BeanOntologyBuilder(Ontology ontology) {
		this.ontology = ontology;
		Introspector ontoIntrospector = ontology.getIntrospector();
		introspector = (BeanIntrospector)ontoIntrospector;
	}

	private static boolean isGetter(Method method) {
		/*
		 * a getter method
		 *   - takes no parameters
		 *   - has a return value
		 *   - its name starts with "get"
		 *   - its name starts with "is"
		 *   - its 4th char is uppercase or is "_"
		 *   - its name is not "getClass" :-)
		 */
		String methodName = method.getName();
		if (methodName.length() < 3) {
			// it is surely too short
			return false;
		}
		if (!methodName.startsWith(GETTER_PREFIX) && !methodName.startsWith(BOOLEAN_GETTER_PREFIX)) {
			// it does not start with "get" or "is"
			return false;
		}
		char c;
		if (methodName.startsWith(BOOLEAN_GETTER_PREFIX)) {
			c = methodName.charAt(2);
		} else {
			c = methodName.charAt(3);
		}
		if (!Character.isUpperCase(c) && '_' != c) {
			// its 3th (isXXX) or 4th (getXXX) char is not uppercase or is not '_'
			return false;
		}
		if (void.class.equals(method.getReturnType())) {
			// it does not have a return value
			return false;
		}
		if (method.getParameterTypes().length > 0) {
			// it takes some parameters
			return false;
		}
		if (methodName.equals(GET_CLASS_METHOD)) {
			// it is "getClass", to be discarded
			return false;
		}
		return true;
	}

	private static boolean isSetter(Method method) {
		/*
		 * a setter method takes one parameter, does not have a return value and its name starts with "set" and its 4th char is uppercase or is "_"
		 */
		String methodName = method.getName(); 
		if (methodName.length() < 4) {
			// it is surely too short
			return false;
		}
		if (!methodName.startsWith(SETTER_PREFIX)) {
			// it does not start with "set"
			return false;
		}
		if (!Character.isUpperCase(methodName.charAt(3)) && '_' != methodName.charAt(3)) {
			// its 4th char is not uppercase
			return false;
		}
		if (!void.class.equals(method.getReturnType())) {
			// it has a return value
			return false;
		}
		if (method.getParameterTypes().length != 1) {
			// it does not take exactly 1 parameter
			return false;
		}
		return true;
	}

	private static String buildPropertyNameFromGetter(Method getter) {
		/*
		 * 1) rip of the "get" or "is" prefix from method's name
		 * 2) make lower case the 1st char of the result
		 * 
		 * method name       slot name
		 * --------------    ---------
		 * getThatThing() -> thatThing
		 * isThatThing()  -> thatThing
		 */
		String getterName = getter.getName();
		StringBuilder sb = new StringBuilder();
		int pos = 3;
		if (getterName.startsWith(BOOLEAN_GETTER_PREFIX)) {
			pos = 2;
		}
		sb.append(Character.toLowerCase(getterName.charAt(pos)));
		sb.append(getterName.substring(pos+1));
		return sb.toString();
	}

	private static String buildSetterNameFromBeanPropertyName(String beanPropertyName) {
		StringBuilder sb = new StringBuilder(SETTER_PREFIX);
		sb.append(Character.toUpperCase(beanPropertyName.charAt(0)));
		sb.append(beanPropertyName.substring(1));
		return sb.toString();
	}

	private static boolean accessorsAreConsistent(Method getter, Method setter) {
		/*
		 *  we have for sure a getter and a setter, so we don't need
		 *  to check the number of parameters and the existence of the return value
		 *  but only their type consistency
		 */
		return getter.getReturnType().equals(setter.getParameterTypes()[0]);
	}

	private static String getSchemaNameFromClass(Class clazz) {
		String result = clazz.getSimpleName();
		Element annotationElement = (Element)clazz.getAnnotation(Element.class);
		if (annotationElement != null) {
			if (!Element.USE_CLASS_SIMPLE_NAME.equals(annotationElement.name())) {
				result = annotationElement.name();
			}
		}
		return result;
	}

	private static Map<SlotKey, SlotAccessData> buildAccessorsMap(String schemaName, Class clazz, Method[] methodsArray) throws BeanOntologyException {
		Map<SlotKey, SlotAccessData> result = new TreeMap<SlotKey, SlotAccessData>();
		List<Method> getters = new ArrayList<Method>();
		Map<String, Method> setters = new HashMap<String, Method>();
		for (Method method: methodsArray) {
			if (method.getAnnotation(SuppressSlot.class) == null) {
				if (isGetter(method)) {
					getters.add(method);
				} else if (isSetter(method)) {
					setters.put(method.getName(), method);
				}
			}
		}

		/*
		 * now that we have a list of getters and a map of setters, we iterate through getters
		 * searching for the matching setter; when we find it, we store the couple in a SlotAccessData
		 */
		Iterator<Method> gettersIter = getters.iterator();
		Method getter, setter;
		String setterName;
		Class<?> slotClazz;
		SlotAccessData sad;
		String propertyName;
		Slot slotAnnotation;
		Class aggregateType;
		AggregateSlot aggregateSlotAnnotation;
		boolean mandatory;
		int cardMin;
		int cardMax;
		String defaultValue;
		String regex;
		String[] permittedValues;
		int position;
		String documentation;
		boolean orderByPosition = false;

		while (gettersIter.hasNext()) {
			getter = gettersIter.next();
			slotClazz = getter.getReturnType();
			mandatory = false;
			cardMin = 0;
			cardMax = ObjectSchema.UNLIMITED;
			defaultValue = null;
			regex = null;
			permittedValues = null;
			documentation = null;
			aggregateType = null;
			position = -1;
			slotAnnotation = getter.getAnnotation(Slot.class);
			aggregateSlotAnnotation = getter.getAnnotation(AggregateSlot.class);

			// build the name of the bean property starting from the getter
			propertyName = buildPropertyNameFromGetter(getter);
			// build the name of the setter name coherent with bean rules
			setterName = buildSetterNameFromBeanPropertyName(propertyName);
			setter = setters.get(setterName);
			if (setter != null) {
				// ok, we have getter and setter, we need to check parameters consistency and we are done
				if (accessorsAreConsistent(getter, setter)) {
					/*
					 * if getter @Slot annotation provides a name, use it; otherwise
					 * use the bean property name
					 */
					String slotName = propertyName;
					if (slotAnnotation != null) {
						/*
						 * if there's a @Slot annotation which specifies the name of the slot, use it
						 */
						if (!Slot.USE_METHOD_NAME.equals(slotAnnotation.name())) {
							slotName = slotAnnotation.name();
						}
						if (slotAnnotation.position() != -1) {
							position = slotAnnotation.position();
							orderByPosition = true;
						}
						if (!Slot.NULL.equals(slotAnnotation.defaultValue())) {
							defaultValue = slotAnnotation.defaultValue();
						}
						if (!Slot.NULL.equals(slotAnnotation.regex())) {
							regex = slotAnnotation.regex();
						}
						if (slotAnnotation.permittedValues().length > 0) {
							permittedValues = slotAnnotation.permittedValues();
						}
						if (!Slot.NULL.equals(slotAnnotation.documentation())) {
							documentation = slotAnnotation.documentation();
						}
						
						mandatory = slotAnnotation.mandatory();
					}
					// if present, use getter @AggregateSlot annotation data
					if (SlotAccessData.isAggregate(slotClazz)) {
						if (slotClazz.isArray()) {
							// extract the type of array elements
							aggregateType = slotClazz.getComponentType();
						}
						Type slotType = getter.getGenericReturnType(); 
						if (slotType instanceof ParameterizedType) {
							ParameterizedType slotParameterizedType = (ParameterizedType)slotType;
							Type[] actuals = slotParameterizedType.getActualTypeArguments();
							// slotType must be an array or a Collection => we expect only 1 item in actuals
							// get first element
							if (actuals.length > 0) {
								aggregateType = (Class)actuals[0];
							}
						}
//						if (slotType has generics) {
//							aggregateType = type from generics;
//						}
						if (aggregateSlotAnnotation != null) {
							cardMin = aggregateSlotAnnotation.cardMin();
							cardMax = aggregateSlotAnnotation.cardMax();
							if (!Object.class.equals(aggregateSlotAnnotation.type())) {
								aggregateType = aggregateSlotAnnotation.type();
							}
						}
					}
					sad = new SlotAccessData(slotClazz, getter, setter, mandatory, aggregateType, cardMin, cardMax, defaultValue, regex, permittedValues, documentation);
					result.put(new SlotKey(schemaName, slotName, position), sad);
				} else {
					// TODO it's not a bean property, maybe we could generate a warning...
				}
			}
		}
		
		// If exists at least one annotation with position setted
		// entire map should be sorted by position.
		// Slots without position (but in alphabetical order) are used to fill holes.
		// Position are zero-based.
		// In a class the positions are unique.
		// If a class extends another and ontology is flat the positions must be global and unique.
		// If a class extends another and ontology is hierarchical the positions must be local to the single classes.
		if (orderByPosition) {
			
			SlotKey[] positionedSK = new SlotKey[result.size()];
			List<SlotKey> nonPositionedSAD = new ArrayList<SlotKey>();  
			for (SlotKey key : result.keySet()) {
				position = key.position;
				if (position != -1) {
					
					// Check position validity
					if (position < 0 || position >= result.size()) {
						throw new BeanOntologyException("not valid position #" + position + " in slot " + key.slotName);
					}
					
					// Check position duplication
					if (positionedSK[position] != null) {
						throw new BeanOntologyException("duplicated position #" + position + " in slot " + key.slotName);
					}
					
					positionedSK[position] = key;
				} else {
					nonPositionedSAD.add(key);
				}
			}
			
			int nonPositionedSADIndex = 0;
			Map<SlotKey, SlotAccessData> orderedMap = new LinkedHashMap<SlotKey, SlotAccessData>();
			for (int i=0; i<result.size(); i++) {
				
				SlotKey key;
				if (positionedSK[i] != null) {
					key = positionedSK[i];
				} else {
					key = nonPositionedSAD.get(nonPositionedSADIndex);
					nonPositionedSADIndex++;
				}
				orderedMap.put(key, result.get(key));
			}
			
			result = orderedMap;
		}
		return result;
	}

	private static String getAggregateSchemaName(Class clazz) {
		String result = null;
		if (SlotAccessData.isSequence(clazz)) {
			result = BasicOntology.SEQUENCE;
		} else if (SlotAccessData.isSet(clazz)) {
			result = BasicOntology.SET;
		}
		return result;
	}

	private static AggregateSchema tryToGetAggregateSchema(Class clazz) {
		AggregateSchema result = null;

		try {
			String schemaName = getAggregateSchemaName(clazz);
			if (schemaName != null) {
				result = (AggregateSchema)BasicOntology.getInstance().getSchema(schemaName);
			}
		} catch (OntologyException oe) {
			// we should never arrive here
			oe.printStackTrace();
		}
		return result;
	}

	private TermSchema supplySchemaForClassFlat(Class clazz, boolean skipClassChecking) throws OntologyException, BeanOntologyException {
		TermSchema ts;
		ObjectSchema os;
		if (java.util.Calendar.class.isAssignableFrom(clazz)) {
			// ontologically, Calendar is translated into a Date
			os = ontology.getSchema(java.util.Date.class);
		} else if (clazz == Object.class) {
			os = TermSchema.getBaseSchema();
		} else {
			os = ontology.getSchema(clazz);
		}
		if (os == null) {
			if (!skipClassChecking && !Concept.class.isAssignableFrom(clazz)) {
				throw new BeanOntologyException("cannot add a slot of class "+clazz.getName()+" since it does not implement Concept");
			}
			os = doAddFlatSchema(clazz, skipClassChecking);
		}
		if (os != null && os instanceof TermSchema) {
			ts = (TermSchema)os;
		} else {
			throw new BeanOntologyException("cannot add a slot of class "+clazz.getName()+" since it does not extend TermSchema");
		}
		return ts;
	}

	private TermSchema supplySchemaForClassRecursive(Class clazz, boolean skipClassChecking) throws OntologyException, BeanOntologyException {
		TermSchema ts;
		ObjectSchema os;
		if (java.util.Calendar.class.isAssignableFrom(clazz)) {
			// ontologically, Calendar is translated into a Date
			os = ontology.getSchema(java.util.Date.class);
		} else if (clazz == Object.class) {
			os = TermSchema.getBaseSchema();
		} else {
			os = ontology.getSchema(clazz);
		}
		if (os == null) {
			if (!skipClassChecking && !Concept.class.isAssignableFrom(clazz)) {
				throw new BeanOntologyException("cannot add a slot of class "+clazz.getName()+" since it does not implement Concept");
			}
			ts = (ConceptSchema)doAddHierarchicalSchema(clazz, skipClassChecking);
		}
		if (os != null && os instanceof TermSchema) {
			ts = (TermSchema)os;
		} else {
			throw new BeanOntologyException("cannot add a slot of class "+clazz.getName()+" since it does not extend TermSchema");
		}
		return ts;
	}

	private void addTermSlotToConcept(ConceptSchema schema, String slotName, String schemaName, SlotAccessData sad, boolean skipClassChecking) throws OntologyException {
		if (logger.isLoggable(Logger.FINE)) {
			logger.log(Logger.FINE, "concept "+schemaName+": adding slot "+slotName);
		}
		try {
			if (!sad.aggregate) {
				TermSchema ts = supplySchemaForClassFlat(sad.type, skipClassChecking);
				schema.add(slotName, ts, sad.mandatory ? ObjectSchema.MANDATORY : ObjectSchema.OPTIONAL);
				
				if (sad.defaultValue != null) {
					schema.addFacet(slotName, new DefaultValueFacet(sad.defaultValue));
				}
				if (sad.regex != null) {
					schema.addFacet(slotName, new RegexFacet(sad.regex));
				}
				if (sad.documentation != null) {
					schema.addFacet(slotName, new DocumentationFacet(sad.documentation));
				}
				if (sad.permittedValues != null) {
					// Adjust permitted values in correct class type 
					// This is necessary because in the annotation the permitted values are string
					Object[] typizedPermittedValues = new Object[sad.permittedValues.length]; 
					if (sad.type != null) {
						for(int i=0; i<sad.permittedValues.length; i++) {
							typizedPermittedValues[i] = BasicOntology.adjustPrimitiveValue(sad.permittedValues[i], sad.type);
						}
					}
					schema.addFacet(slotName, new PermittedValuesFacet(typizedPermittedValues));
				}
			} else {
				TermSchema ats = null;
				if (sad.aggregateClass != null) {
					// try to get a schema for the contained type
					ats = supplySchemaForClassFlat(sad.aggregateClass, skipClassChecking);
				}
				schema.add(slotName, ats, sad.cardMin, sad.cardMax, getAggregateSchemaName(sad.type));
			}
		} catch (BeanOntologyException bobe) {
			throw new BeanOntologyException("error adding slot "+slotName+" to schema "+schemaName, bobe);
		}
	}

	private void addTermSlotToPredicate(PredicateSchema schema, String slotName, String schemaName, SlotAccessData sad, boolean skipClassChecking) throws OntologyException, BeanOntologyException {
		if (logger.isLoggable(Logger.FINE)) {
			logger.log(Logger.FINE, "predicate "+schemaName+": adding slot "+slotName);
		}
		try {
			if (!sad.aggregate) {
				TermSchema ts = supplySchemaForClassFlat(sad.type, skipClassChecking);
				schema.add(slotName, ts, sad.mandatory ? ObjectSchema.MANDATORY : ObjectSchema.OPTIONAL);
			} else {
				TermSchema ats = null;
				if (sad.aggregateClass != null) {
					// try to get a schema for the contained type
					ats = supplySchemaForClassFlat(sad.aggregateClass, skipClassChecking);
				}
				if (ats == null) {
					schema.add(slotName, tryToGetAggregateSchema(sad.type), sad.cardMin > 0 ? ObjectSchema.MANDATORY : ObjectSchema.OPTIONAL);
				} else {
					schema.add(slotName, ats, sad.cardMin, sad.cardMax, getAggregateSchemaName(sad.type));
				}
			}
		} catch (BeanOntologyException bobe) {
			throw new BeanOntologyException("error adding slot "+slotName+" to predicate "+schemaName, bobe);
		}
	}

	private ObjectSchema doAddFlatSchema(Class clazz, boolean skipClassChecking) throws BeanOntologyException {

		String schemaName = getSchemaNameFromClass(clazz);
		if (logger.isLoggable(Logger.FINE)) {
			logger.log(Logger.FINE, "building concept "+schemaName);
		}
		ObjectSchema schema;
		boolean isAction = AgentAction.class.isAssignableFrom(clazz); 
		if (isAction) {
			schema = new AgentActionSchema(schemaName);
		} else {
			if (Predicate.class.isAssignableFrom(clazz)) {
				schema = new PredicateSchema(schemaName);
			} else {
				schema = new ConceptSchema(schemaName);
				
				// If the class is a enum type add a string slot for the enum value 
				if (clazz.isEnum()) {
					doAddEnumSlotSchema(clazz, schema, schemaName);
				}
			}
		}

		try {
			ontology.add(schema, clazz);
		} catch (OntologyException oe) {
			throw new BeanOntologyException("error adding empty schema for class "+clazz);
		}

		// If the class is a enum type skip the accessors-map building (emun not have fields)
		if (!clazz.isEnum()) {
			Map<SlotKey, SlotAccessData> slotAccessorsMap = buildAccessorsMap(schemaName, clazz, clazz.getMethods());
			String slotName = null;
			SlotAccessData sad;
			try {
				for (Entry<SlotKey, SlotAccessData> entry: slotAccessorsMap.entrySet()) {
					slotName = entry.getKey().slotName;
					sad = entry.getValue();
					if (schema instanceof ConceptSchema) {
						addTermSlotToConcept((ConceptSchema)schema, slotName, schemaName, sad, true);
					} else {
						addTermSlotToPredicate((PredicateSchema)schema, slotName, schemaName, sad, true);
					}
				}
				introspector.addAccessors(slotAccessorsMap);
				if (isAction) {
					Annotation annotation;
					if ((annotation = clazz.getAnnotation(Result.class)) != null) {
						TermSchema ts = supplySchemaForClassFlat(((Result)annotation).type(), true);
						((AgentActionSchema)schema).setResult(ts);
					} else if ((annotation = clazz.getAnnotation(AggregateResult.class)) != null) {
						AggregateResult ar = (AggregateResult)annotation;
						TermSchema ts = supplySchemaForClassFlat(ar.type(), true);
						((AgentActionSchema)schema).setResult(ts, ar.cardMin(), ar.cardMax());
					}
				}
			} catch (OntologyException e) {
				throw new BeanOntologyException("error getting schema for slot "+slotName);
			}
		}
		
		return schema;
	}

	private ObjectSchema doAddHierarchicalSchema(Class clazz, boolean skipClassChecking) throws BeanOntologyException {
		Class superClazz = clazz.getSuperclass();
		ObjectSchema superSchema = null;
		if (superClazz != null) {
			if ((skipClassChecking && !Object.class.equals(superClazz)) || Concept.class.isAssignableFrom(superClazz) || Predicate.class.isAssignableFrom(superClazz)) {
				int scms = superClazz.getModifiers();
				if (!Modifier.isPrivate(scms)) {
					// classes referenced as slots do not need to be Concept, Predicate or AgentAction
					doAddHierarchicalSchema(superClazz, true);
					try {
						superSchema = ontology.getSchema(superClazz);
					} catch (OntologyException oe) {
						throw new BeanOntologyException("error getting schema for superclass "+superClazz);
					}
				}
			}
		}

		Method[] declaredMethods = clazz.getDeclaredMethods();
		List<Method> publicDeclaredMethodsList = new ArrayList<Method>();
		int modifiers;
		for (Method m: declaredMethods) {
			modifiers = m.getModifiers();
			if (Modifier.isPublic(modifiers) && !Modifier.isStatic(modifiers) && !Modifier.isAbstract(modifiers)) {
				publicDeclaredMethodsList.add(m);
			}
		}

		Method[] publicDeclaredMethods = new Method[publicDeclaredMethodsList.size()];
		for (int i = 0; i < publicDeclaredMethods.length; i++) {
			publicDeclaredMethods[i] = publicDeclaredMethodsList.get(i);
		}

		String schemaName = getSchemaNameFromClass(clazz);
		if (logger.isLoggable(Logger.FINE)) {
			logger.log(Logger.FINE, "building concept "+schemaName);
		}
		ObjectSchema schema;
		boolean isAction = AgentAction.class.isAssignableFrom(clazz); 
		if (isAction) {
			schema = new AgentActionSchema(schemaName);
		} else {
			if (Predicate.class.isAssignableFrom(clazz)) {
				schema = new PredicateSchema(schemaName);
			} else {
				schema = new ConceptSchema(schemaName);
				
				// If the class is a enum type add a string slot for the enum value 
				if (clazz.isEnum()) {
					doAddEnumSlotSchema(clazz, schema, schemaName);
				}
			}
		}
		if (superSchema != null) {
			if (logger.isLoggable(Logger.FINE)) {
				logger.log(Logger.FINE, "adding superschema from class "+superClazz);
			}
			if (schema instanceof ConceptSchema) {
				((ConceptSchema)schema).addSuperSchema((ConceptSchema)superSchema);
			} else {
				((PredicateSchema)schema).addSuperSchema((PredicateSchema)superSchema);
			}
		}

		try {
			ontology.add(schema, clazz);
		} catch (OntologyException oe) {
			throw new BeanOntologyException("error adding empty schema for class "+clazz);
		}

		
		// If the class is a enum type skip the accessors-map building (emun not have fields)
		if (!clazz.isEnum()) {
			Map<SlotKey, SlotAccessData> slotAccessorsMap = buildAccessorsMap(schemaName, clazz, publicDeclaredMethods);
			String slotName = null;
			SlotAccessData sad;
			try {
				for (Entry<SlotKey, SlotAccessData> entry: slotAccessorsMap.entrySet()) {
					slotName = entry.getKey().slotName;
					
					if (!schema.containsSlot(slotName)) {
						sad = entry.getValue();
						if (schema instanceof ConceptSchema) {
							addTermSlotToConcept((ConceptSchema)schema, slotName, schemaName, sad, true);
						} else {
							addTermSlotToPredicate((PredicateSchema)schema, slotName, schemaName, sad, true);
						}
					}
				}
				introspector.addAccessors(slotAccessorsMap);
				if (isAction) {
					Annotation annotation;
					if ((annotation = clazz.getAnnotation(Result.class)) != null) {
						TermSchema ts = supplySchemaForClassRecursive(((Result)annotation).type(), true);
						((AgentActionSchema)schema).setResult(ts);
					} else if ((annotation = clazz.getAnnotation(AggregateResult.class)) != null) {
						AggregateResult ar = (AggregateResult)annotation;
						TermSchema ts = supplySchemaForClassRecursive(ar.type(), true);
						((AgentActionSchema)schema).setResult(ts, ar.cardMin(), ar.cardMax());
					}
				}
			} catch (OntologyException e) {
				throw new BeanOntologyException("error getting schema for slot "+slotName, e);
			}
		}
		
		return schema;
	}

	private void doAddEnumSlotSchema(Class clazz, ObjectSchema schema, String schemaName) throws BeanOntologyException {
		try {
			ConceptSchema cs = (ConceptSchema)schema;
			
			cs.add(ENUM_SLOT_NAME, (TermSchema)ontology.getSchema(String.class));

			// Add enum permitted values
			Enum[] enumValues = ((Class<? extends Enum>)clazz).getEnumConstants();
			String[] enumStrValues = new String[enumValues.length]; 
			for(int i=0; i<enumValues.length; i++) {
				enumStrValues[i] = enumValues[i].toString(); 
			}
			cs.addFacet(ENUM_SLOT_NAME, new PermittedValuesFacet(enumStrValues));
			
		} catch (OntologyException e) {
			throw new BeanOntologyException("error adding slot value to enum-schema "+schemaName, e);
		}
	}
	
	private void doAddSchema(Class clazz, boolean buildHierarchy, boolean skipClassChecking) throws BeanOntologyException {
		// Skip all interfaces
		if (!clazz.isInterface()) {
			boolean classIsValid = skipClassChecking || (Concept.class.isAssignableFrom(clazz) || Predicate.class.isAssignableFrom(clazz));
			if (classIsValid) {
				ObjectSchema schema = null;
				try {
					schema = BasicOntology.getInstance().getSchema(clazz);
				} catch (OntologyException oe) {
					// this should never happen
					oe.printStackTrace();
				}
				if (schema != null) {
					throw new BeanOntologyException("cannot add schema for class "+clazz+" since it is included in BasicOntology");
				}
				if (buildHierarchy) {
					doAddHierarchicalSchema(clazz, skipClassChecking);
				} else {
					doAddFlatSchema(clazz, skipClassChecking);
				}
			}
		}
	}

	void addSchema(Class clazz, boolean buildHierarchy) throws BeanOntologyException {
		doAddSchema(clazz, buildHierarchy, true);
	}

	void addSchemas(String pkgname, boolean buildHierarchy) throws BeanOntologyException {
		try {
			List<Class> classesForPackage = ClassDiscover.getClassesForPackage(pkgname);
			if (classesForPackage.size() < 1) {
				throw new BeanOntologyException("no suitable classes found");
			}
			for (Class clazz: classesForPackage) {
				doAddSchema(clazz, buildHierarchy, false);
			}
		} catch (ClassNotFoundException cnfe) {
			throw new BeanOntologyException("Class not found", cnfe);
		}
	}
}
