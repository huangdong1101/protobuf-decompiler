package com.myth;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumDescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumValueDescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldOptions;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileOptions;
import com.google.protobuf.DescriptorProtos.MethodDescriptorProto;
import com.google.protobuf.DescriptorProtos.ServiceDescriptorProto;
import com.google.protobuf.Descriptors.FileDescriptor;

/**
 * Protobuf Decompiler
 *
 * @author huangdong
 * Created on 2023-10-23
 */
public class ProtobufDecompiler {

    public static void decompile(FileDescriptor descriptor, String outDir) throws IOException {
        decompile(descriptor, outDir, Collections.emptySet());
    }

    public static void decompile(FileDescriptor descriptor, String outDir, Set<String> excludeDependencies)
            throws IOException {
        String dirPath = new File(outDir).getAbsolutePath();
        LinkedList<FileDescriptor> queue = new LinkedList<>();
        queue.addLast(descriptor);
        do {
            FileDescriptor item = queue.removeFirst();
            if (item.getFullName().startsWith("google/protobuf")) {
                continue;
            }
            if (excludeDependencies.stream().anyMatch(item.getFullName()::startsWith)) {
                continue;
            }
            String content = decompile(item.toProto());
            File file = new File(dirPath + "/" + item.getFullName());
            FileUtils.write(file, content, StandardCharsets.UTF_8);
            item.getDependencies().forEach(queue::addLast);
        } while (!queue.isEmpty());
    }

    public static String decompile(FileDescriptorProto descriptor) {
        StringBuilder builder = new StringBuilder();

        String syntax = descriptor.getSyntax();
        if (StringUtils.isNotBlank(syntax)) {
            builder.append("syntax = \"").append(syntax).append("\";");
        }

        String pkg = descriptor.getPackage();
        if (StringUtils.isNotBlank(pkg)) {
            builder.append('\n');
            builder.append("package ").append(pkg).append(";");
        }

        FileOptions options = descriptor.getOptions();
        String javaPackage = options.getJavaPackage();
        if (StringUtils.isNotBlank(javaPackage)) {
            builder.append('\n');
            builder.append("option java_package = \"").append(javaPackage).append("\";");
        }
        String javaOuterClassname = options.getJavaOuterClassname();
        if (StringUtils.isNotBlank(javaOuterClassname)) {
            builder.append('\n');
            builder.append("option java_outer_classname = \"").append(javaOuterClassname).append("\";");
        }
        boolean javaMultipleFiles = options.getJavaMultipleFiles();
        if (javaMultipleFiles) {
            builder.append('\n');
            builder.append("option java_multiple_files = true;");
        }

        if (descriptor.getDependencyCount() > 0) {
            builder.append('\n'); //空一行
            for (String dependency : descriptor.getDependencyList()) {
                builder.append('\n');
                builder.append("import \"").append(dependency).append("\";");
            }
        }

        for (EnumDescriptorProto enumType : descriptor.getEnumTypeList()) {
            builder.append('\n'); //空一行
            builder.append('\n');
            appendEnum(enumType, builder, "");
        }

        for (DescriptorProto messageType : descriptor.getMessageTypeList()) {
            builder.append('\n'); //空一行
            builder.append('\n');
            appendMessage(pkg, messageType, builder, "");
        }

        for (ServiceDescriptorProto service : descriptor.getServiceList()) {
            builder.append('\n'); //空一行
            builder.append('\n');
            appendService(service, builder, "");
        }
        return builder.toString();
    }

    private static void appendEnum(EnumDescriptorProto enumType, StringBuilder builder, String prefix) {
        builder.append(prefix).append("enum ").append(enumType.getName()).append(" {");
        for (EnumValueDescriptorProto enumValue : enumType.getValueList()) {
            builder.append('\n');
            appendEnumValue(enumValue, builder, prefix + "    ");
        }
        builder.append('\n');
        builder.append(prefix).append("}");
    }

    private static void appendEnumValue(EnumValueDescriptorProto enumValue, StringBuilder builder, String prefix) {
        builder.append(prefix)
                .append(enumValue.getName())
                .append(" = ")
                .append(enumValue.getNumber())
                .append(";");
    }

    private static void appendMessage(String pkg, DescriptorProto messageType, StringBuilder builder, String prefix) {
        builder.append(prefix).append("message ").append(messageType.getName()).append(" {");
        for (EnumDescriptorProto enumType : messageType.getEnumTypeList()) {
            builder.append('\n');
            appendEnum(enumType, builder, prefix + "    ");
        }
        Map<String, String> nestedMapTypes = new HashMap<>();
        if (messageType.getNestedTypeCount() > 0) {
            String messageFullName;
            if (StringUtils.isBlank(pkg)) {
                messageFullName = messageType.getName();
            } else {
                messageFullName = pkg + "." + messageType.getName();
            }
            for (DescriptorProto nestedType : messageType.getNestedTypeList()) {
                if (nestedType.getOptions().getMapEntry()) {
                    String keyType = getTypeName(nestedType.getField(0), nestedMapTypes);
                    String valueType = getTypeName(nestedType.getField(1), nestedMapTypes);
                    StringUtils.defaultIfBlank(pkg, "");
                    String name = "." + messageFullName + "." + nestedType.getName();
                    String type = "map<" + keyType + ", " + valueType + ">";
                    nestedMapTypes.put(name, type);
                    continue;
                }
                builder.append('\n');
                appendMessage(messageFullName, nestedType, builder, prefix + "    ");
            }
        }
        int oneofIndex = -1;
        List<FieldDescriptorProto> oneofFields = new ArrayList<>();
        for (FieldDescriptorProto field : messageType.getFieldList()) {
            if (field.hasOneofIndex()) {
                if (field.getOneofIndex() != oneofIndex) {
                    if (!oneofFields.isEmpty()) {
                        String oneofName = messageType.getOneofDecl(oneofIndex).getName();
                        builder.append('\n');
                        appendOneofField(oneofName, oneofFields, builder, prefix + "    ", nestedMapTypes);
                        oneofFields = new ArrayList<>();
                    }
                    oneofIndex = field.getOneofIndex();
                }
                oneofFields.add(field);
                continue;
            }
            if (!oneofFields.isEmpty()) {
                String oneofName = messageType.getOneofDecl(oneofIndex).getName();
                builder.append('\n');
                appendOneofField(oneofName, oneofFields, builder, prefix + "    ", nestedMapTypes);
                oneofFields = new ArrayList<>();
            }
            builder.append('\n');
            builder.append(prefix).append("    ");
            appendField(field, builder, nestedMapTypes);
        }
        if (!oneofFields.isEmpty()) {
            String oneofName = messageType.getOneofDecl(oneofIndex).getName();
            builder.append('\n');
            appendOneofField(oneofName, oneofFields, builder, prefix + "    ", nestedMapTypes);
        }
        builder.append('\n');
        builder.append(prefix).append("}");
    }

    private static void appendField(FieldDescriptorProto field, StringBuilder builder,
            Map<String, String> nestedMapTypes) {
        String type = getTypeName(field, nestedMapTypes);
        switch (field.getLabel()) {
            case LABEL_OPTIONAL:
                break;
            case LABEL_REPEATED:
                if (type.startsWith("map<")) {
                    break;
                }
            default:
                builder.append(field.getLabel().name().substring(6).toLowerCase())
                        .append(" ");
                break;
        }
        builder.append(type)
                .append(" ")
                .append(field.getName())
                .append(" = ")
                .append(field.getNumber());

        FieldOptions options = field.getOptions();
        String prefix = " [";
        if (options.getDeprecated()) {
            builder.append(prefix).append("deprecated = true");
            prefix = ", ";
        }
        if (options.getPacked()) {
            builder.append(prefix).append("packed = true");
            prefix = ", ";
        }
        if (options.getLazy()) {
            builder.append(prefix).append("lazy = true");
            prefix = ", ";
        }
        if (prefix.equals(", ")) {
            builder.append("]");
        }
        builder.append(";");
    }

    private static void appendOneofField(String oneofName, List<FieldDescriptorProto> oneofFields,
            StringBuilder builder, String prefix, Map<String, String> nestedMapTypes) {
        if (oneofFields.isEmpty()) {
            return;
        }
        builder.append(prefix).append("oneof ").append(oneofName).append(" {");
        for (FieldDescriptorProto field : oneofFields) {
            builder.append('\n');
            builder.append(prefix).append("    ");
            appendField(field, builder, nestedMapTypes);
        }
        builder.append('\n');
        builder.append(prefix).append("}");
    }

    private static String getTypeName(FieldDescriptorProto field, Map<String, String> mapTypes) {
        switch (field.getType()) {
            case TYPE_MESSAGE:
                String mapType = mapTypes.get(field.getTypeName());
                if (mapType != null) {
                    return mapType;
                }
            case TYPE_ENUM:
                return field.getTypeName();
            default:
                return field.getType().name().substring(5).toLowerCase();
        }
    }

    private static void appendService(ServiceDescriptorProto service, StringBuilder builder, String prefix) {
        builder.append(prefix).append("service ").append(service.getName()).append(" {");
        for (MethodDescriptorProto method : service.getMethodList()) {
            builder.append('\n');
            builder.append(prefix)
                    .append("    ")
                    .append("rpc ")
                    .append(method.getName())
                    .append(" (")
                    .append(method.getInputType())
                    .append(") returns (")
                    .append(method.getOutputType())
                    .append(");");
        }
        builder.append('\n');
        builder.append(prefix).append("}");
    }
}
