/*
 * Tai-e: A Program Analysis Framework for Java
 *
 * Copyright (C) 2020 Tian Tan <tiantan@nju.edu.cn>
 * Copyright (C) 2020 Yue Li <yueli@nju.edu.cn>
 * All rights reserved.
 *
 * This software is designed for the "Static Program Analysis" course at
 * Nanjing University, and it supports a subset of Java features.
 * Tai-e is only for educational and academic purposes, and any form of
 * commercial use is disallowed.
 */

package pascal.taie.language.classes;

import pascal.taie.World;
import pascal.taie.ir.IR;
import pascal.taie.ir.proginfo.MethodRef;
import pascal.taie.language.types.Type;
import pascal.taie.oldpta.ir.PTAIR;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class JMethod extends ClassMember {

    private final List<Type> paramTypes;

    private final Type returnType;

    private final Subsignature subsignature;

    /**
     * Source of the body (and/or other information) of this method.
     * IRBuilder can use this to build method IR.
     */
    private final Object methodSource;

    private PTAIR ptair;

    private IR ir;

    public JMethod(JClass declaringClass, String name, Set<Modifier> modifiers,
                   List<Type> paramTypes, Type returnType,
                   Object methodSource) {
        super(declaringClass, name, modifiers);
        this.paramTypes = Collections.unmodifiableList(paramTypes);
        this.returnType = returnType;
        this.signature = StringReps.getSignatureOf(this);
        this.subsignature = Subsignature.get(name, paramTypes, returnType);
        this.methodSource = methodSource;
    }

    public boolean isAbstract() {
        return Modifier.hasAbstract(modifiers);
    }

    public boolean isNative() {
        return Modifier.hasNative(modifiers);
    }

    public int getParamCount() {
        return paramTypes.size();
    }

    public Type getParamType(int i) {
        return paramTypes.get(i);
    }

    public List<Type> getParamTypes() {
        return paramTypes;
    }

    public Type getReturnType() {
        return returnType;
    }

    public Subsignature getSubsignature() {
        return subsignature;
    }

    public Object getMethodSource() {
        return methodSource;
    }

    public PTAIR getPTAIR() {
        assert !isAbstract();
        if (ptair == null) {
            ptair = World.getIRBuilder().buildPTAIR(this);
        }
        return ptair;
    }

    public IR getIR() {
        assert !isAbstract();
        if (ir == null) {
            if (isNative()) {
                ir = World.getNativeModel().buildNativeIR(this);
            } else {
                ir = World.getIRBuilder().buildIR(this);
            }
        }
        return ir;
    }

    /**
     * @return the {@link MethodRef} pointing to this method.
     */
    public MethodRef getRef() {
        return MethodRef.get(declaringClass, name,
                paramTypes, returnType, isStatic());
    }
}
