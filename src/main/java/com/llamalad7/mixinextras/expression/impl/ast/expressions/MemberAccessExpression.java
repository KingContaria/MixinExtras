package com.llamalad7.mixinextras.expression.impl.ast.expressions;

import com.llamalad7.mixinextras.expression.impl.ast.identifiers.Identifier;
import com.llamalad7.mixinextras.expression.impl.flow.FlowValue;
import com.llamalad7.mixinextras.expression.impl.pool.IdentifierPool;
import com.llamalad7.mixinextras.expression.impl.serialization.ExpressionReader;
import com.llamalad7.mixinextras.expression.impl.serialization.ExpressionWriter;
import com.llamalad7.mixinextras.expression.impl.serialization.SerializedExpressionId;
import com.llamalad7.mixinextras.utils.Decorations;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;

import java.io.IOException;

@SerializedExpressionId(".")
public class MemberAccessExpression implements SimpleExpression {
    public final Expression receiver;
    public final Identifier name;

    public MemberAccessExpression(Expression receiver, Identifier name) {
        this.receiver = receiver;
        this.name = name;
    }

    @Override
    public boolean matches(FlowValue node, IdentifierPool pool, OutputSink sink) {
        AbstractInsnNode insn = node.getInsn();
        switch (insn.getOpcode()) {
            case Opcodes.GETFIELD:
            case Opcodes.ARRAYLENGTH:
                return name.matches(pool, insn) && inputsMatch(node, pool, sink, receiver);
        }
        return false;
    }

    @Override
    public void capture(FlowValue node, OutputSink sink) {
        if (node.getInsn().getOpcode() == Opcodes.ARRAYLENGTH) {
            sink.decorate(node.getInsn(), Decorations.SIMPLE_OPERATION_ARGS, new Type[]{node.getInput(0).getType()});
            sink.decorate(node.getInsn(), Decorations.SIMPLE_OPERATION_RETURN_TYPE, Type.INT_TYPE);
        }
        SimpleExpression.super.capture(node, sink);
    }

    @Override
    public void write(ExpressionWriter writer) throws IOException {
        writer.writeExpression(receiver);
        writer.writeIdentifier(name);
    }

    public static Expression read(ExpressionReader reader) throws IOException {
        return new MemberAccessExpression(reader.readExpression(), reader.readIdentifier());
    }
}
