/*
	Milyn - Copyright (C) 2006 - 2010

	This library is free software; you can redistribute it and/or
	modify it under the terms of the GNU Lesser General Public
	License (version 2.1) as published by the Free Software
	Foundation.

	This library is distributed in the hope that it will be useful,
	but WITHOUT ANY WARRANTY; without even the implied warranty of
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

	See the GNU Lesser General Public License for more details:
	http://www.gnu.org/licenses/lgpl.txt
*/
package org.milyn.ejc;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.milyn.ect.EdiSpecificationReader;
import org.milyn.edisax.model.EdifactModel;
import org.milyn.edisax.model.internal.Description;
import org.milyn.edisax.util.EDIUtils;
import org.milyn.edisax.util.IllegalNameException;
import org.milyn.io.FileUtils;
import org.milyn.javabean.pojogen.JClass;
import org.milyn.resource.URIResourceLocator;
import org.milyn.util.CollectionsUtil;
import org.milyn.util.FreeMarkerTemplate;
import org.xml.sax.SAXException;

/**
 * {@link EJC} Executor.
 *
 * @author <a href="mailto:tom.fennelly@jboss.com">tom.fennelly@jboss.com</a>
 */
public class EJCExecutor {

	private String ediMappingModel;
	private Set<String> messages;
	private File destDir;
	private String packageName;
	private static FreeMarkerTemplate messageBindingTemplate = new FreeMarkerTemplate(
		"templates/interchange-message-bindingConfig.ftl.xml", EJCExecutor.class);
	private static FreeMarkerTemplate interchangeBindingTemplate = new FreeMarkerTemplate(
		"templates/interchange-bindingConfig.ftl.xml", EJCExecutor.class);

	public void execute() throws EJCException, IOException, SAXException, IllegalNameException, ClassNotFoundException {
		this.assertMandatoryProperty(this.ediMappingModel, "ediMappingModel");
		this.assertMandatoryProperty(this.destDir, "destDir");
		this.assertMandatoryProperty(this.packageName, "packageName");

		if (this.destDir.exists() && !this.destDir.isDirectory()) {
			throw new EJCException("Specified EJC destination directory '" + this.destDir.getAbsoluteFile()
				+ "' exists, but is not a directory.");
		}

		Properties interchangeProperties = EDIUtils.getInterchangeProperties(this.ediMappingModel);

		Map<String, EdifactModel> mappingModels = new LinkedHashMap<String, EdifactModel>();
		EDIUtils.loadMappingModels(this.ediMappingModel, mappingModels, URIResourceLocator.DEFAULT_BASE_URI);

		EdifactModel definitionsModel = mappingModels.get(EDIUtils.MODEL_SET_DEFINITIONS_DESCRIPTION_LOOKUP_NAME);
		String commonsPackageName = this.packageName + ".common";
		ClassModel definitionsClassModel = null;

		if (definitionsModel != null) {
			EJC ejc = new EJC();
			definitionsClassModel = ejc.compile(definitionsModel.getEdimap(), commonsPackageName,
												this.destDir.getAbsolutePath());

			// Get rid of the binding and edi mapping model configs for the commons...
			this.deleteFile(commonsPackageName, EJC.BINDINGCONFIG_XML);
			this.deleteFile(commonsPackageName, EJC.EDIMAPPINGCONFIG_XML);
		}

		List<MessageDefinition> messageSetDefinitions = new ArrayList<MessageDefinition>();
		Set<Map.Entry<String, EdifactModel>> modelSet = mappingModels.entrySet();
		StringBuilder rootClassesListFileBuilder = new StringBuilder();
		for (Map.Entry<String, EdifactModel> model : modelSet) {
			Description description = model.getValue().getDescription();

			if (description != null) {
				String descriptionStr = description.getName() + ":" + description.getVersion();
				if (descriptionStr.equals(EDIUtils.MODEL_SET_DEFINITIONS_DESCRIPTION_LOOKUP_NAME)) {
					// Already done (above). Skip it...
					continue;
				}
			}

			if ((this.messages == null) || this.messages.contains(description.getName())) {
				EJC ejc = new EJC();

				ejc.include(commonsPackageName);
				ejc.addEDIMessageAnnotation(true);
				if (definitionsClassModel != null) {
					String messagePackageName = this.packageName + "." + description.getName();
					ClassModel classModel = ejc.compile(model.getValue().getEdimap(), messagePackageName,
														this.destDir.getAbsolutePath(),
														definitionsClassModel.getClassesByNode());

					// If this is an interchange, get rid of the edi mapping model config and the
					// Factory class for the message folder...
					if (interchangeProperties != null) {
						MessageDefinition messageDef = new MessageDefinition(description.getName(),
							"/" + messagePackageName.replace('.', '/') + "/" + EJC.BINDINGCONFIG_XML);
						messageSetDefinitions.add(messageDef);

						this.deleteFile(messagePackageName, EJC.EDIMAPPINGCONFIG_XML);
						this.deleteFile(messagePackageName,
										EDIUtils.encodeClassName(description.getName()) + "Factory.java");

						JClass beanClass = classModel.getRootBeanConfig().getBeanClass();
						rootClassesListFileBuilder.append(beanClass.getPackageName()).append(".")
							.append(beanClass.getClassName()).append("\n");
					}
				} else {
					ejc.compile(model.getValue().getEdimap(), this.packageName, this.destDir.getAbsolutePath());
				}
			}
		}

		// Write the list of class names into the jar file as a list file. Can be used
		// for testing etc...
		FileUtils.writeFile(rootClassesListFileBuilder.toString().getBytes("UTF-8"),
							new File(this.destDir, this.packageName.replace('.', '/') + "/ejc-classes.lst"));

		if ((interchangeProperties != null) && !messageSetDefinitions.isEmpty()) {
			this.applyTemplate(	"message-bindingconfig.xml", messageBindingTemplate, interchangeProperties,
								messageSetDefinitions);
			this.applyTemplate(	"interchange-bindingconfig.xml", interchangeBindingTemplate, interchangeProperties,
								messageSetDefinitions);
			this.generateFactoryClass(interchangeProperties);
		}
	}

	private void deleteFile(String packageName, String fileName) {
		File file = new File(this.destDir, packageName.replace('.', '/') + "/" + fileName);
		file.delete();
	}

	private void applyTemplate(String outFile, FreeMarkerTemplate template, Properties interchangeProperties,
		List<MessageDefinition> messageSetDefinitions) throws IOException {
		File messageBindingConfigFile = new File(this.destDir, this.packageName.replace('.', '/') + "/" + outFile);
		FileWriter interchangeBindingConfigWriter = new FileWriter(messageBindingConfigFile);

		try {
			Map<String, Object> contextObj = new HashMap<String, Object>();

			contextObj.put("interchangeProperties", interchangeProperties);
			contextObj.put("messageSetDef", messageSetDefinitions);

			template.apply(contextObj, interchangeBindingConfigWriter);
		} finally {
			try {
				interchangeBindingConfigWriter.flush();
			} finally {
				interchangeBindingConfigWriter.close();
			}
		}
	}

	private void generateFactoryClass(Properties interchangeProperties) throws IOException {
		FreeMarkerTemplate factoryTemplate = new FreeMarkerTemplate(
			"templates/" + interchangeProperties.getProperty(EdiSpecificationReader.INTERCHANGE_TYPE)
				+ "-interchange-factoryClass.ftl.xml",
			EJCExecutor.class);
		Map<String, Object> contextObj = new HashMap<String, Object>();
		String packageTokens[] = this.packageName.split("\\.");
		String messageSetName = packageTokens[packageTokens.length - 1].toUpperCase();

		contextObj.put("mappingModel", this.ediMappingModel);
		contextObj.put("package", this.packageName);
		contextObj.put("messageSetName", messageSetName);
		contextObj.put("bindingConfig", "/" + this.packageName.replace('.', '/') + "/interchange-bindingconfig.xml");

		File interchangeFactoryFile = new File(this.destDir,
			this.packageName.replace('.', '/') + "/" + messageSetName + "InterchangeFactory.java");
		FileWriter interchangeBindingConfigWriter = new FileWriter(interchangeFactoryFile);
		try {
			factoryTemplate.apply(contextObj, interchangeBindingConfigWriter);
		} finally {
			try {
				interchangeBindingConfigWriter.flush();
			} finally {
				interchangeBindingConfigWriter.close();
			}
		}
	}

	public void setEdiMappingModel(String ediMappingModel) {
		this.ediMappingModel = ediMappingModel;
	}

	public void setMessages(String messages) {
		if (messages != null) {
			this.messages = CollectionsUtil.toSet(messages.split(","));
		}
	}

	public void setMessages(Set<String> messages) {
		this.messages = messages;
	}

	public void setDestDir(File destDir) {
		this.destDir = destDir;
	}

	public void setPackageName(String packageName) {
		this.packageName = packageName;
	}

	private void assertMandatoryProperty(Object obj, String name) {
		if (obj == null) {
			throw new EJCException("Mandatory EJC property '" + name + "' + not specified.");
		}
	}

	public static class MessageDefinition {
		private String messageName;
		private String bindingConfigPath;

		public MessageDefinition(String messageName, String bindingConfigPath) {
			this.messageName = messageName;
			this.bindingConfigPath = bindingConfigPath;
		}

		public String getMessageName() {
			return this.messageName;
		}

		public String getBindingConfigPath() {
			return this.bindingConfigPath;
		}
	}
}