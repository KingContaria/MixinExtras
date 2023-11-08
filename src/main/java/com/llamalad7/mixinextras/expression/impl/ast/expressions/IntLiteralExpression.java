package com.llamalad7.mixinextras.expression.impl.ast.expressions;

import com.google.gson.annotations.SerializedName;
import com.llamalad7.mixinextras.expression.impl.flow.FlowValue;
import com.llamalad7.mixinextras.expression.impl.pool.IdentifierPool;
import com.llamalad7.mixinextras.expression.impl.serialization.SerializedTypeName;
import org.spongepowered.asm.util.Bytecode;

@SerializedTypeName("int")
public class IntLiteralExpression implements Expression {
    @SerializedName("v")
    public final long value;

    public IntLiteralExpression(long value) {
        this.value = value;
    }

    @Override
    public boolean matches(FlowValue node, IdentifierPool pool, CaptureSink sink) {
        Object cst = Bytecode.getConstant(node.getInsn());
        if (cst == null) {
            return false;
        }
        return (cst instanceof Integer || cst instanceof Long) && ((Number) cst).longValue() == value;
    }
}
