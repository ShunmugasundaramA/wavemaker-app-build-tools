package com.wavemaker.app.build.swaggerdoc.handler;

import java.util.*;

import com.wavemaker.tools.apidocs.tools.core.model.ComposedModel;
import com.wavemaker.tools.apidocs.tools.core.model.Model;
import com.wavemaker.tools.apidocs.tools.core.model.ModelImpl;
import com.wavemaker.tools.apidocs.tools.core.model.RefModel;
import com.wavemaker.tools.apidocs.tools.core.model.properties.ArrayProperty;
import com.wavemaker.tools.apidocs.tools.core.model.properties.Property;
import com.wavemaker.tools.apidocs.tools.core.model.properties.RefProperty;

/**
 * Created by sunilp on 12/8/15.
 */
public class ModelHandler {

    private final Model model;
    private final Map<String, Model> definitions;

    public ModelHandler(Model model, Map<String, Model> definitions) {
        this.model = model;
        this.definitions = definitions;
    }

    public Map<String, Property> getProperties() {
        Map<String, Property> properties = new HashMap<>();
        return buildProperties(model, properties);
    }

    // recursive api to build all properties from model,parent model and so on.
    private Map<String, Property> buildProperties(Model model, Map<String, Property> properties) {

        if (model != null) {
            //some times model can be composed,Composed means if a class inherits another class or interface.
            if (model instanceof ComposedModel) {
                List<Model> allModels = ((ComposedModel) model).getAllOf();
                for (Model eachModel : allModels) {
                    properties.putAll(buildProperties(eachModel, properties));
                }
            } else if (model instanceof RefModel) {
                buildProperties(definitions.get(((RefModel) model).getSimpleRef()), properties);
            } else {
                // in other models such as array,modelImpl,abstract getting properties are straight forward.
                if (model.getProperties() != null) {
                    properties.putAll(model.getProperties());
                }
            }
        }

        return properties;
    }


    public Map<String,Property> listProperties(Model model, int level) {
        Map<String,Property> propertiesMap = new HashMap<>();
        listProperties(model, level, propertiesMap);
        return propertiesMap;
    }

    private void listProperties(Model model, int level, Map<String,Property> propertiesMap) {
        ModelImpl actualModel = (ModelImpl) model;
        if (level > 0) {
            final Map<String, Property> properties = actualModel.getProperties();
            for (String propertyName : properties.keySet()) {
                final Property property = properties.get(propertyName);
                if(actualModel.getRequired().contains(propertyName)) {
                    final PropertyHandler propertyHandler = new PropertyHandler(property, definitions);
                    if (propertyHandler.isPrimitive()) {
                        propertiesMap.put(propertyName,property);
                    } else if (property instanceof ArrayProperty) {
                        //this case occurs what property is List<int> || Set<Emp> || List<User> || Set<String> || ......
                        ArrayProperty arrayProperty = (ArrayProperty) property;
                        boolean isList = arrayProperty.isList();
                        if (isList) {
                            Property argProperty = arrayProperty.getItems();
                            if (argProperty instanceof RefProperty) {
                                // case : List<someObject> or Set<someObject>
                                RefProperty refProperty = (RefProperty) argProperty;
                                PropertyHandler refPropertyHandler = new PropertyHandler(refProperty, definitions);
                                final Model refModel = definitions.get(refProperty.getName());
                                propertiesMap.put(propertyName,refProperty);
                                listProperties(refModel, level - 1, propertiesMap);
                            } else {
                                //case : List<primitive> or Set<primitive>
                                propertiesMap.put(propertyName,argProperty);
                            }
                        }
                    }
                    else if (property instanceof RefProperty) {
                        //this case occurs when property is Object<Object,Object....> eq : Page<Employee>
                        RefProperty refProperty = (RefProperty) property;
                        List<Property> argProperties = refProperty.getTypeArguments();
                        for (Property argProperty : argProperties) {
                            PropertyHandler argPropertyHandler = new PropertyHandler(argProperty, definitions);
                            if (argPropertyHandler.isPrimitive()) {
                                propertiesMap.put(propertyName,argProperty);
                            } else {
                                final Model argModel = definitions.get(argProperty.getName());
                                propertiesMap.put(propertyName,argProperty);
                                listProperties(argModel, level - 1, propertiesMap);
                            }
                        }
                    }
                }
            }
        }
    }
}