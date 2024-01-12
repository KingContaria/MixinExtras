package com.llamalad7.mixinextras.expression.impl.ast.expressions;

import com.llamalad7.mixinextras.expression.impl.ast.identifiers.Identifier;
import com.llamalad7.mixinextras.expression.impl.flow.FlowValue;
import com.llamalad7.mixinextras.expression.impl.pool.IdentifierPool;
import com.llamalad7.mixinextras.expression.impl.serialization.ExpressionReader;
import com.llamalad7.mixinextras.expression.impl.serialization.ExpressionWriter;
import com.llamalad7.mixinextras.expression.impl.serialization.SerializedExpressionId;
import org.apache.commons.lang3.ArrayUtils;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.util.List;

@SerializedExpressionId(".()")
public class MethodCallExpression implements SimpleExpression {
    public final Expression receiver;
    public final Identifier name;
    public final List<Expression> arguments;

    public MethodCallExpression(Expression receiver, Identifier name, List<Expression> arguments) {
        this.receiver = receiver;
        this.name = name;
        this.arguments = arguments;
    }

    @Override
    public boolean matches(FlowValue node, IdentifierPool pool, OutputSink sink) {
        switch (node.getInsn().getOpcode()) {
            case Opcodes.INVOKEVIRTUAL:
            case Opcodes.INVOKESPECIAL:
            case Opcodes.INVOKEINTERFACE:
                if (!name.matches(pool, node.getInsn())) {
                    return false;
                }
                Expression[] inputs = ArrayUtils.add(arguments.toArray(new Expression[0]), 0, receiver);
                return inputsMatch(node, pool, sink, inputs);
        }
        return false;
    }

    @Override
    public void write(ExpressionWriter writer) throws IOException {
        writer.writeExpression(receiver);
        writer.writeIdentifier(name);
        writer.writeExpressions(arguments);
    }

    public static Expression read(ExpressionReader reader) throws IOException {
        return new MethodCallExpression(reader.readExpression(), reader.readIdentifier(), reader.readExpressions());
    }
}
