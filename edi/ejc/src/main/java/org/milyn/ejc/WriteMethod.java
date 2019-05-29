/*
 * Milyn - Copyright (C) 2006 - 2010
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License (version 2.1) as published by the Free Software
 * Foundation.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * See the GNU Lesser General Public License for more details:
 * http://www.gnu.org/licenses/lgpl.txt
 */
package org.milyn.ejc;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.milyn.config.Configurable;
import org.milyn.edisax.model.internal.Component;
import org.milyn.edisax.model.internal.ContainerNode;
import org.milyn.edisax.model.internal.DelimiterType;
import org.milyn.edisax.model.internal.Delimiters;
import org.milyn.edisax.model.internal.Field;
import org.milyn.edisax.model.internal.MappingNode;
import org.milyn.edisax.model.internal.Segment;
import org.milyn.edisax.model.internal.SegmentGroup;
import org.milyn.edisax.model.internal.ValueNode;
import org.milyn.edisax.util.EDIUtils;
import org.milyn.javabean.DataDecoder;
import org.milyn.javabean.DataEncoder;
import org.milyn.javabean.decoders.DABigDecimalDecoder;
import org.milyn.javabean.pojogen.JClass;
import org.milyn.javabean.pojogen.JMethod;
import org.milyn.javabean.pojogen.JNamedType;
import org.milyn.javabean.pojogen.JType;
import org.milyn.smooks.edi.EDIWritable;

/**
 * EDIWritable bean serialization class.
 *
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
class WriteMethod extends JMethod {

	private JClass jClass;
	private MappingNode mappingNode;
	private boolean appendFlush = false;
	private boolean trunacate;
	private DelimiterType terminatingDelimiter;

	WriteMethod(JClass jClass, MappingNode mappingNode) {
		super("write");
		this.addParameter(new JType(Writer.class), "writer");
		this.addParameter(new JType(Delimiters.class), "delimiters");
		this.getExceptions().add(new JType(IOException.class));
		jClass.getImplementTypes().add(new JType(EDIWritable.class));
		jClass.getMethods().add(this);
		this.jClass = jClass;
		this.mappingNode = mappingNode;
		this.trunacate = ((mappingNode instanceof ContainerNode) && ((ContainerNode) mappingNode).isTruncatable());

		if (this.trunacate) {
			jClass.getRawImports().add(new JType(StringWriter.class));
			jClass.getRawImports().add(new JType(List.class));
			jClass.getRawImports().add(new JType(ArrayList.class));
			jClass.getRawImports().add(new JType(EDIUtils.class));
			jClass.getRawImports().add(new JType(DelimiterType.class));
		}
	}

	public void writeObject(JNamedType property, DelimiterType delimiterType, BindingConfig bindingConfig,
		MappingNode mappingNode) {
		this.writeDelimiter(delimiterType);
		this.writeObject(property, bindingConfig, mappingNode);
	}

	public void writeFieldCollection(JNamedType property, DelimiterType delimiterType, BindingConfig bindingConfig,
		int maxOccurs) {
		boolean first = this.getBodyBuilder().length() == 0;
		this.appendToBody("\n        if(" + property.getName() + " != null && !" + property.getName()
			+ ".isEmpty()) {");
		this.appendToBody("\n            for(" + property.getType().getGenericType().getSimpleName() + " item : "
			+ property.getName() + ") {");
		if (first) {
			this.appendToBody("\n                if(!nodeTokens.isEmpty()) {");
			this.writeDelimiter(delimiterType, "\n                    ");
			this.appendToBody("\n                }");
		} else {
			this.writeDelimiter(delimiterType, "\n                ");
		}
		this.appendToBody("\n                item.write(nodeWriter, delimiters);");
		if (this.trunacate) {
			this.appendToBody("\n                nodeTokens.add(nodeWriter.toString());");
			this.appendToBody("\n                ((StringWriter)nodeWriter).getBuffer().setLength(0);");
		}
		this.appendToBody("\n            }");
		this.appendToBody("\n        }");
		if (first) {
			this.appendToBody("\n        for(int i = " + property.getName() + " == null ? 1 : " + property.getName()
				+ ".size() + 1; i < " + maxOccurs + "; i++) {");
			this.writeDelimiter(delimiterType, "\n            ");
			this.appendToBody("\n        }");
		} else {
			this.appendToBody("\n        for(int i = " + property.getName() + " == null ? 0 : " + property.getName()
				+ ".size(); i < " + maxOccurs + "; i++) {");
			this.writeDelimiter(delimiterType, "\n            ");
			this.appendToBody("\n        }");
		}
	}

	public void writeObject(JNamedType property, BindingConfig bindingConfig, MappingNode mappingNode) {
		this.appendToBody("\n        if(" + property.getName() + " != null) {");
		if (mappingNode instanceof Segment) {
			if (!((Segment) mappingNode).getFields().isEmpty()
				&& ((bindingConfig.getParent() == null) || (this.bodyLength() > 0))) {
				this.appendToBody("\n            nodeWriter.write(\"" + ((Segment) mappingNode).getSegcode() + "\");");
				// this.appendToBody("\n nodeWriter.write(delimiters.getField());");
			}
		}
		this.appendToBody("\n            " + property.getName() + ".write(nodeWriter, delimiters);");
		if (this.trunacate) {
			this.appendToBody("\n            nodeTokens.add(nodeWriter.toString());");
			this.appendToBody("\n            ((StringWriter)nodeWriter).getBuffer().setLength(0);");
		}
		this.appendToBody("\n        }");
	}

	public void writeValue(JNamedType property, ValueNode modelNode, DelimiterType delimiterType) {
		this.writeDelimiter(delimiterType);
		this.writeValue(property, modelNode);
	}

	public void writeValue(JNamedType property, ValueNode modelNode) {
		this.appendToBody("\n        if(" + property.getName() + " != null) {");

		DataDecoder dataDecoder = modelNode.getDecoder();
		if (dataDecoder instanceof DataEncoder) {
			String encoderName = property.getName() + "Encoder";
			Class<? extends DataDecoder> decoderClass = dataDecoder.getClass();

			// Add the property for the encoder instance...
			this.jClass.getProperties().add(new JNamedType(new JType(decoderClass), encoderName));

			// Create the encoder in the constructor...
			JMethod defaultConstructor = this.jClass.getDefaultConstructor();
			defaultConstructor
				.appendToBody("\n        " + encoderName + " = new " + decoderClass.getSimpleName() + "();");

			// Configure the encoder in the constructor (if needed)....
			if (dataDecoder instanceof Configurable) {
				Properties configuration = ((Configurable) dataDecoder).getConfiguration();

				if (configuration != null) {
					Set<Map.Entry<Object, Object>> encoderConfig = configuration.entrySet();
					String encoderPropertiesName = encoderName + "Properties";

					this.jClass.getRawImports().add(new JType(Properties.class));
					defaultConstructor
						.appendToBody("\n        Properties " + encoderPropertiesName + " = new Properties();");
					for (Map.Entry<Object, Object> entry : encoderConfig) {
						defaultConstructor.appendToBody("\n        " + encoderPropertiesName + ".setProperty(\""
							+ entry.getKey() + "\", \"" + entry.getValue() + "\");");
					}
					defaultConstructor
						.appendToBody("\n        " + encoderName + ".setConfiguration(" + encoderPropertiesName + ");");
				}
			}

			// Add the encoder encode instruction to te write method...
			if (decoderClass == DABigDecimalDecoder.class) {
				this.appendToBody("\n            nodeWriter.write(delimiters.escape(" + encoderName + ".encode("
					+ property.getName() + ", delimiters)));");
			} else {
				this.appendToBody("\n            nodeWriter.write(delimiters.escape(" + encoderName + ".encode("
					+ property.getName() + ")));");
			}
		} else {
			this.appendToBody("\n            nodeWriter.write(delimiters.escape(" + property.getName()
				+ ".toString()));");
		}

		if (this.trunacate) {
			this.appendToBody("\n            nodeTokens.add(nodeWriter.toString());");
			this.appendToBody("\n            ((StringWriter)nodeWriter).getBuffer().setLength(0);");
		}

		this.appendToBody("\n        }");
	}

	public void writeSegmentCollection(JNamedType property, SegmentGroup segmentGroup) {
		this.appendToBody("\n        if(" + property.getName() + " != null && !" + property.getName()
			+ ".isEmpty()) {");
		this.appendToBody("\n            for(" + property.getType().getGenericType().getSimpleName() + " "
			+ property.getName() + "Inst : " + property.getName() + ") {");

		if ((segmentGroup instanceof Segment) && !((Segment) segmentGroup).getFields().isEmpty()) {
			this.appendToBody("\n                nodeWriter.write(\"" + segmentGroup.getSegcode() + "\");");
			// this.appendToBody("\n nodeWriter.write(delimiters.getField());");
			if (this.trunacate) {
				this.appendToBody("\n                nodeTokens.add(nodeWriter.toString());");
				this.appendToBody("\n                ((StringWriter)nodeWriter).getBuffer().setLength(0);");
			}
		}

		this.appendToBody("\n                " + property.getName() + "Inst.write(nodeWriter, delimiters);");
		this.appendToBody("\n            }");
		this.appendToBody("\n        }");
	}

	@Override
	public String getBody() {
		StringBuilder builder = new StringBuilder();

		if (this.trunacate) {
			builder.append("\n        Writer nodeWriter = new StringWriter();\n");
			builder.append("\n        List<String> nodeTokens = new ArrayList<String>();\n");
		} else {
			builder.append("\n        Writer nodeWriter = writer;\n");
		}

		builder.append(super.getBody());

		if (this.trunacate) {
			builder.append("\n        nodeTokens.add(nodeWriter.toString());");
			if (this.mappingNode instanceof Segment) {
				builder
					.append("\n        String nodeTokensStr = EDIUtils.concatAndTruncate(nodeTokens, DelimiterType.FIELD, delimiters);");
				builder.append("\n        if (!nodeTokensStr.isEmpty()) {");
				builder.append("\n            writer.write(delimiters.getField());");
				builder.append("\n            writer.write(nodeTokensStr);");
				builder.append("\n        }");
			} else if (this.mappingNode instanceof Field) {
				builder
					.append("\n        writer.write(EDIUtils.concatAndTruncate(nodeTokens, DelimiterType.COMPONENT, delimiters));");
			} else if (this.mappingNode instanceof Component) {
				builder
					.append("\n        writer.write(EDIUtils.concatAndTruncate(nodeTokens, DelimiterType.SUB_COMPONENT, delimiters));");
			}
		}

		if (this.terminatingDelimiter != null) {
			this.writeDelimiter(this.terminatingDelimiter, "writer", builder);
		}
		if (this.appendFlush) {
			builder.append("\n        writer.flush();");
		}

		return builder.toString();
	}

	public void writeDelimiter(DelimiterType delimiterType) {
		this.writeDelimiter(delimiterType, "\n        ");
	}

	public void writeDelimiter(DelimiterType delimiterType, String indent) {
		this.writeDelimiter(delimiterType, "nodeWriter", this.getBodyBuilder(), indent);
	}

	private void writeDelimiter(DelimiterType delimiterType, String writerVariableName, StringBuilder builder) {
		this.writeDelimiter(delimiterType, writerVariableName, builder, "\n        ");
	}

	private void writeDelimiter(DelimiterType delimiterType, String writerVariableName, StringBuilder builder,
		String indent) {
		if (this.bodyLength() == 0) {
			return;
		}

		switch (delimiterType) {
		case SEGMENT:
			builder.append(indent + writerVariableName + ".write(delimiters.getSegmentDelimiter());");
			break;
		case FIELD:
			builder.append(indent + writerVariableName + ".write(delimiters.getField());");
			break;
		case FIELD_REPEAT:
			builder.append(indent + writerVariableName + ".write(delimiters.getFieldRepeat());");
			break;
		case COMPONENT:
			builder.append(indent + writerVariableName + ".write(delimiters.getComponent());");
			break;
		case SUB_COMPONENT:
			builder.append(indent + writerVariableName + ".write(delimiters.getSubComponent());");
			break;
		case DECIMAL_SEPARATOR:
			builder.append(indent + writerVariableName + ".write(delimiters.getDecimalSeparator());");
			break;
		default:
			throw new UnsupportedOperationException("Unsupported '" + DelimiterType.class.getName()
				+ "' enum conversion.  Enum '" + delimiterType + "' not specified in switch statement.");
		}
	}

	public void addFlush() {
		this.appendFlush = true;
	}

	public void addTerminatingDelimiter(DelimiterType delimiterType) {
		this.terminatingDelimiter = delimiterType;
	}
}
