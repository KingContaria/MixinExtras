package com.llamalad7.mixinextras.utils;

import com.llamalad7.mixinextras.service.MixinExtrasService;
import org.apache.commons.lang3.StringUtils;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.injection.struct.Target;
import org.spongepowered.asm.util.Bytecode;
import org.spongepowered.asm.util.Constants;

import java.lang.annotation.Annotation;
import java.lang.annotation.Repeatable;
import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class ASMUtils {
    public static final Type OBJECT_TYPE = Type.getType(Object.class);

    public static final Handle LMF_HANDLE = new Handle(
            Opcodes.H_INVOKESTATIC,
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            Bytecode.generateDescriptor(CallSite.class, MethodHandles.Lookup.class, String.class, MethodType.class, MethodType.class, MethodHandle.class, MethodType.class),
            false
    );

    public static String annotationToString(AnnotationNode annotation) {
        StringBuilder builder = new StringBuilder("@").append(typeToString(Type.getType(annotation.desc)));
        List<Object> values = annotation.values;
        if (values == null || values.isEmpty()) {
            return builder.toString();
        }
        builder.append('(');
        for (int i = 0; i < values.size(); i += 2) {
            if (i != 0) {
                builder.append(", ");
            }
            String name = (String) values.get(i);
            Object value = values.get(i + 1);
            builder.append(name).append(" = ").append(valueToString(value));
        }
        builder.append(')');
        return builder.toString();
    }

    public static String typeToString(Type type) {
        String name = type.getClassName();
        return name.substring(name.lastIndexOf('.') + 1).replace('$', '.');
    }

    private static String valueToString(Object value) {
        if (value instanceof String) {
            return '"' + value.toString() + '"';
        }
        if (value instanceof Type) {
            Type type = (Type) value;
            return typeToString(type) + ".class";
        }
        if (value instanceof String[]) {
            String[] enumInfo = (String[]) value;
            return typeToString(Type.getType(enumInfo[0])) + '.' + enumInfo[1];
        }
        if (value instanceof AnnotationNode) {
            return annotationToString((AnnotationNode) value);
        }
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            if (list.size() == 1) {
                return valueToString(list.get(0));
            }
            return '{' + list.stream().map(ASMUtils::valueToString).collect(Collectors.joining(", ")) + '}';
        }
        return value.toString();
    }

    public static boolean isPrimitive(Type type) {
        return type.getDescriptor().length() == 1;
    }

    public static MethodInsnNode getInvokeInstruction(ClassNode owner, MethodNode method) {
        boolean isInterface = (owner.access & Opcodes.ACC_INTERFACE) != 0;
        int opcode = (method.access & Opcodes.ACC_STATIC) != 0 ? Opcodes.INVOKESTATIC
                : (method.access & Opcodes.ACC_PRIVATE) != 0 ? Opcodes.INVOKESPECIAL
                : isInterface ? Opcodes.INVOKEINTERFACE
                : Opcodes.INVOKEVIRTUAL;
        return new MethodInsnNode(
                opcode, owner.name, method.name, method.desc, isInterface
        );
    }

    public static int getDummyOpcodeForType(Type type) {
        switch (type.getSort()) {
            case Type.BOOLEAN:
            case Type.CHAR:
            case Type.BYTE:
            case Type.SHORT:
            case Type.INT:
                return Opcodes.ICONST_0;
            case Type.FLOAT:
                return Opcodes.FCONST_0;
            case Type.LONG:
                return Opcodes.LCONST_0;
            case Type.DOUBLE:
                return Opcodes.DCONST_0;
            case Type.ARRAY:
            case Type.OBJECT:
                return Opcodes.ACONST_NULL;
            default:
                throw new UnsupportedOperationException();
        }
    }

    /**
     * Mixin already has this method in {@link Target} but it's wrong.
     */
    public static MethodInsnNode findInitNodeFor(Target target, TypeInsnNode newNode) {
        int start = target.indexOf(newNode);
        int depth = 0;
        for (Iterator<AbstractInsnNode> it = target.insns.iterator(start); it.hasNext(); ) {
            AbstractInsnNode insn = it.next();
            if (insn instanceof TypeInsnNode && insn.getOpcode() == Opcodes.NEW) {
                TypeInsnNode typeNode = (TypeInsnNode) insn;
                if (typeNode.desc.equals(newNode.desc)) {
                    depth++;
                }
            } else if (insn instanceof MethodInsnNode && insn.getOpcode() == Opcodes.INVOKESPECIAL) {
                MethodInsnNode methodNode = (MethodInsnNode) insn;
                if (Constants.CTOR.equals(methodNode.name) && methodNode.owner.equals(newNode.desc)) {
                    depth--;
                    if (depth == 0) {
                        return methodNode;
                    }
                }
            }
        }
        return null;
    }

    public static void ifElse(InsnList insns, int jumpToSecond, Runnable first, Runnable second) {
        ifElse(insns, label -> insns.add(new JumpInsnNode(jumpToSecond, label)), first, second);
    }

    public static void ifElse(InsnList insns, Consumer<LabelNode> addJump, Runnable first, Runnable second) {
        LabelNode secondLabel = new LabelNode();
        LabelNode end = new LabelNode();
        addJump.accept(secondLabel);
        first.run();
        insns.add(new JumpInsnNode(Opcodes.GOTO, end));
        insns.add(secondLabel);
        second.run();
        insns.add(end);
    }

    public static AnnotationNode getRepeatedMEAnnotation(MethodNode method, Class<? extends Annotation> single) {
        Class<? extends Annotation> container = single.getAnnotation(Repeatable.class).value();
        AnnotationNode repeated = getInvisibleMEAnnotation(method, container);
        if (repeated != null) {
            return repeated;
        }
        AnnotationNode individual = getInvisibleMEAnnotation(method, single);
        if (individual == null) {
            return null;
        }
        AnnotationNode result = new AnnotationNode(Type.getDescriptor(container));
        result.visit("value", individual);
        return result;
    }

    public static AnnotationNode getInvisibleMEAnnotation(MethodNode method, Class<? extends Annotation> annotation) {
        return getMEAnnotation(method.invisibleAnnotations, Type.getInternalName(annotation));
    }

    private static AnnotationNode getMEAnnotation(List<AnnotationNode> annotations, String internalAnnotationName) {
        String annotationName = "." + StringUtils.substringAfterLast(internalAnnotationName, "/");
        if (annotations == null) {
            return null;
        }
        for (AnnotationNode annotation : annotations) {
            String binaryName = Type.getType(annotation.desc).getClassName();
            if (MixinExtrasService.getInstance().isClassOwned(binaryName) && binaryName.endsWith(annotationName)) {
                return annotation;
            }
        }
        return null;
    }

    public static Type getConstantType(AbstractInsnNode insn) {
        if (insn instanceof TypeInsnNode) {
            // Mixin for some reason decides these are `Class<?>` constants
            return null;
        }
        return Bytecode.getConstantType(insn);
    }
}
