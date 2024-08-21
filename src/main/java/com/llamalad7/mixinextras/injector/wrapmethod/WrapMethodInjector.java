package com.llamalad7.mixinextras.injector.wrapmethod;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.service.MixinExtrasService;
import com.llamalad7.mixinextras.sugar.impl.ShareInfo;
import com.llamalad7.mixinextras.utils.CompatibilityHelper;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.injection.code.Injector;
import org.spongepowered.asm.mixin.injection.struct.InjectionInfo;
import org.spongepowered.asm.mixin.injection.struct.InjectionNodes.InjectionNode;
import org.spongepowered.asm.mixin.injection.struct.Target;
import org.spongepowered.asm.util.Annotations;

import java.util.ArrayList;
import java.util.List;

public class WrapMethodInjector extends Injector {
    private final Type operationType = MixinExtrasService.getInstance().changePackage(Operation.class, Type.getType(CompatibilityHelper.getAnnotation(info).desc), WrapMethod.class);
    private final List<ShareInfo> shares = new ArrayList<>();

    public WrapMethodInjector(InjectionInfo info) {
        super(info, "@WrapMethod");
    }

    @Override
    protected void inject(Target target, InjectionNode node) {
        this.checkTargetModifiers(target, true);
        this.checkSignature(target);
        info.addCallbackInvocation(methodNode);
        boolean captureTargetArgs = !(methodArgs.length > 0 && methodArgs[0].equals(operationType));
        System.out.println(captureTargetArgs);
        WrapMethodApplicatorExtension.offerWrapper(target, methodNode, operationType, shares, captureTargetArgs);
    }

    private void checkSignature(Target target) {
        InjectorData handler = new InjectorData(target, "method wrapper");
        String description = String.format("%s %s %s from %s", this.annotationType, handler, this, CompatibilityHelper.getMixin(info));

        if (!returnType.equals(target.returnType)) {
            throw CompatibilityHelper.makeInvalidInjectionException(
                    info,
                    String.format(
                            "%s targeting %s has an incorrect return type Expected %s but got %s!",
                            description, target, target.returnType, returnType
                    )
            );
        }
        int argIndex = 0;
        boolean captureTargetArgs = !(methodArgs.length > 0 && methodArgs[0].equals(operationType));
        int expectedTargetArgs = captureTargetArgs ? target.arguments.length : 0;
        for (; argIndex < expectedTargetArgs; argIndex++) {
            Type theirType = target.arguments[argIndex];
            if (argIndex >= methodArgs.length) {
                throw CompatibilityHelper.makeInvalidInjectionException(
                        info,
                        String.format(
                                "%s targeting %s doesn't have enough parameters!",
                                description, target
                        )
                );
            }
            Type ourType = methodArgs[argIndex];

            if (!ourType.equals(theirType)) {
                throw CompatibilityHelper.makeInvalidInjectionException(
                        info,
                        String.format(
                                "%s targeting %s has a mismatching param at index %s! Expected %s but got %s",
                                description, target, argIndex, theirType, ourType
                        )
                );
            }
        }
        if (argIndex >= methodArgs.length || !methodArgs[argIndex++].equals(operationType)) {
            throw CompatibilityHelper.makeInvalidInjectionException(
                    info,
                    String.format(
                            "%s targeting %s is missing Operation parameter!",
                            description, target
                    )
            );
        }
        List<AnnotationNode> sugars = Annotations.getValue(CompatibilityHelper.getAnnotation(info), "sugars");
        if (sugars != null) {
            for (int i = 0; i < argIndex; i++) {
                AnnotationNode sugar = sugars.get(i);
                if (MixinExtrasService.getInstance().isClassOwned(Type.getType(sugar.desc).getClassName())) {
                    throw CompatibilityHelper.makeInvalidInjectionException(
                            info,
                            String.format(
                                    "%s targeting %s has sugar on a non-trailing param which is not allowed!",
                                    description, target
                            )
                    );
                }
            }
        }
        for (; argIndex < methodArgs.length; argIndex++) {
            ShareInfo share;
            if (sugars == null ||
                    (share = ShareInfo.getOrCreate(
                            target,
                            sugars.get(argIndex),
                            methodArgs[argIndex],
                            CompatibilityHelper.getMixin(info).getMixin(),
                            null
                    )) == null) {
                throw CompatibilityHelper.makeInvalidInjectionException(
                        info,
                        String.format(
                                "%s targeting %s has an excess parameter at index %s!",
                                description, target, argIndex
                        )
                );
            }
            shares.add(share);
        }
    }
}
