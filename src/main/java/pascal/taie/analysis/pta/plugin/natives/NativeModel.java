/*
 * Tai-e: A Static Analysis Framework for Java
 *
 * Copyright (C) 2022 Tian Tan <tiantan@nju.edu.cn>
 * Copyright (C) 2022 Yue Li <yueli@nju.edu.cn>
 *
 * This file is part of Tai-e.
 *
 * Tai-e is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * Tai-e is distributed in the hope that it will be useful,but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Tai-e. If not, see <https://www.gnu.org/licenses/>.
 */

package pascal.taie.analysis.pta.plugin.natives;

import pascal.taie.analysis.pta.core.cs.context.Context;
import pascal.taie.analysis.pta.core.cs.element.ArrayIndex;
import pascal.taie.analysis.pta.core.cs.element.CSCallSite;
import pascal.taie.analysis.pta.core.cs.element.CSMethod;
import pascal.taie.analysis.pta.core.cs.element.CSObj;
import pascal.taie.analysis.pta.core.cs.element.CSVar;
import pascal.taie.analysis.pta.core.solver.PointerFlowEdge;
import pascal.taie.analysis.pta.core.solver.Solver;
import pascal.taie.analysis.pta.plugin.util.AbstractModel;
import pascal.taie.analysis.pta.plugin.util.CSObjs;
import pascal.taie.analysis.pta.pts.PointsToSet;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.proginfo.MethodRef;
import pascal.taie.ir.stmt.Invoke;
import pascal.taie.language.classes.ClassNames;
import pascal.taie.language.classes.JMethod;
import pascal.taie.language.type.ClassType;
import pascal.taie.language.type.PrimitiveType;
import pascal.taie.util.collection.Maps;
import pascal.taie.util.collection.Sets;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

class NativeModel extends AbstractModel {

    private final MethodRef arrayCopy;

    private final ClassType object;

    private final Map<Invoke, Var> arrayCopyVars = Maps.newMap();

    private final Map<Var, Var> arrayCopySrcs = Maps.newMap();

    private final Map<Var, Var> arrayCopyDests = Maps.newMap();

    private final MethodRef privilegedActionRun;

    private final MethodRef privilegedExceptionActionRun;

    private Set<JMethod> nativeMethods;

    NativeModel(Solver solver) {
        super(solver);
        arrayCopy = Objects.requireNonNull(
            hierarchy.getJREMethod("<java.lang.System: void arraycopy(java.lang.Object,int,java.lang.Object,int,int)>"))
            .getRef();
        object = Objects.requireNonNull(hierarchy.getJREClass(ClassNames.OBJECT))
            .getType();
        privilegedActionRun = Objects.requireNonNull(
            hierarchy.getJREMethod("<java.security.PrivilegedAction: java.lang.Object run()>"))
            .getRef();
        privilegedExceptionActionRun = Objects.requireNonNull(
            hierarchy.getJREMethod("<java.security.PrivilegedExceptionAction: java.lang.Object run()>"))
            .getRef();
    }

    Collection<JMethod> getNativeMethods() {
        return Collections.unmodifiableSet(nativeMethods);
    }

    @Override
    protected void registerVarAndHandler() {
        nativeMethods = Sets.newSet();

        nativeMethods.add(
            hierarchy.getJREMethod("<java.lang.System: void arraycopy(java.lang.Object,int,java.lang.Object,int,int)>"));

        JMethod doPrivileged1 = hierarchy.getJREMethod("<java.security.AccessController: java.lang.Object doPrivileged(java.security.PrivilegedAction)>");
        registerRelevantVarIndexes(doPrivileged1, 0);
        registerAPIHandler(doPrivileged1, this::doPrivileged);
        nativeMethods.add(doPrivileged1);

        JMethod doPrivileged2 = hierarchy.getJREMethod("<java.security.AccessController: java.lang.Object doPrivileged(java.security.PrivilegedAction,java.security.AccessControlContext)>");
        registerRelevantVarIndexes(doPrivileged2, 0);
        registerAPIHandler(doPrivileged2, this::doPrivileged);
        nativeMethods.add(doPrivileged2);

        JMethod doPrivileged3 = hierarchy.getJREMethod("<java.security.AccessController: java.lang.Object doPrivileged(java.security.PrivilegedExceptionAction)>");
        registerRelevantVarIndexes(doPrivileged3, 0);
        registerAPIHandler(doPrivileged3, this::doPrivilegedException);
        nativeMethods.add(doPrivileged3);

        JMethod doPrivileged4 = hierarchy.getJREMethod("<java.security.AccessController: java.lang.Object doPrivileged(java.security.PrivilegedExceptionAction,java.security.AccessControlContext)>");
        registerRelevantVarIndexes(doPrivileged4, 0);
        registerAPIHandler(doPrivileged4, this::doPrivilegedException);
        nativeMethods.add(doPrivileged4);
    }

    int arraycopyEdges = 0;

    int primitivearraycopy = 0;

    int typematches = 0;

    @Override
    public void handleNewInvoke(Invoke invoke) {
        super.handleNewInvoke(invoke);
        // handle System.arraycopy(...)
        if (invoke.getMethodRef().equals(arrayCopy)) {
            Var src = invoke.getInvokeExp().getArg(0);
            Var dest = invoke.getInvokeExp().getArg(2);
            Var tmp = getNewArrayCopyVar(invoke);
            arrayCopySrcs.put(src, tmp);
            arrayCopyDests.put(dest, tmp);
        }
    }

    private Var getNewArrayCopyVar(Invoke invoke) {
        String name = "%native-arraycopy-tmp" + arrayCopyVars.size();
        return arrayCopyVars.computeIfAbsent(invoke,
            i -> new Var(i.getContainer(), name, object, -1));
    }

    @Override
    public void handleNewPointsToSet(CSVar csVar, PointsToSet pts) {
        super.handleNewPointsToSet(csVar, pts);
        // handle System.arraycopy(...)
        if (arrayCopySrcs.containsKey(csVar.getVar())) {
            Var tmp = arrayCopySrcs.get(csVar.getVar());
            CSVar csTmp = csManager.getCSVar(csVar.getContext(), tmp);
            pts.objects()
                .filter(CSObjs::isArray)
                .map(csManager::getArrayIndex)
                .forEach(srcIndex -> {
                    solver.addPFGEdge(srcIndex, csTmp,
                        PointerFlowEdge.Kind.ARRAY_LOAD);
                    ++arraycopyEdges;
                });
        }
        if (arrayCopyDests.containsKey(csVar.getVar())) {
            Var tmp = arrayCopyDests.get(csVar.getVar());
            CSVar csTmp = csManager.getCSVar(csVar.getContext(), tmp);
            pts.objects()
                .filter(CSObjs::isArray)
                .map(csManager::getArrayIndex)
                .forEach(destIndex -> {
                    solver.addPFGEdge(csTmp, destIndex,
                        destIndex.getType(), PointerFlowEdge.Kind.ARRAY_STORE);
                    ++arraycopyEdges;
                });
        }
    }

    /**
     * Model for System.arraycopy(...).
     */
    private void arrayCopy(CSVar csVar, PointsToSet pts, Invoke invoke) {
        List<PointsToSet> args = getArgs(csVar, pts, invoke, 0, 2);
        PointsToSet srcObjs = args.get(0);
        PointsToSet destObjs = args.get(1);
        srcObjs.objects()
            .filter(CSObjs::isArray)
            .forEach(srcArray -> {
                ArrayIndex src = csManager.getArrayIndex(srcArray);
                destObjs.objects()
                    .filter(CSObjs::isArray)
                    .forEach(destArray -> {
                        ArrayIndex dest = csManager.getArrayIndex(destArray);
                        if (typeSystem.isSubtype(src.getType(), dest.getType()) ||
                            typeSystem.isSubtype(dest.getType(), src.getType())) {
                            ++typematches;
//                        System.out.println(src + " -> " + dest);
                            solver.addPFGEdge(src, dest, dest.getType(),
                                PointerFlowEdge.Kind.ARRAY_STORE);
                        }
                        ++arraycopyEdges;
                        if (src.getType() instanceof PrimitiveType
                            && dest.getType() instanceof PrimitiveType) {
                            ++primitivearraycopy;
                        }
                    });
            });
    }

    private void doPrivileged(CSVar csVar, PointsToSet pts, Invoke invoke) {
        doPrivileged(csVar, pts, invoke, privilegedActionRun);
    }

    private void doPrivilegedException(CSVar csVar, PointsToSet pts, Invoke invoke) {
        doPrivileged(csVar, pts, invoke, privilegedExceptionActionRun);
    }

    /**
     * Model for AccessController.doPrivileged(...).
     */
    private void doPrivileged(CSVar csVar, PointsToSet pts, Invoke invoke, MethodRef run) {
        Context callerCtx = csVar.getContext();
        CSCallSite csCallSite = csManager.getCSCallSite(callerCtx, invoke);
        for (CSObj recvObj : pts) {
            JMethod callee = hierarchy.dispatch(recvObj.getObject().getType(), run);
            if (callee == null) {
                return;
            }
            // select callee context
            Context calleeCtx = selector.selectContext(
                csCallSite, recvObj, callee);
            // pass receiver object
            solver.addVarPointsTo(calleeCtx, callee.getIR().getThis(), recvObj);
            // pass return value
            Var lhs = invoke.getResult();
            if (lhs != null) {
                CSVar csLhs = csManager.getCSVar(callerCtx, lhs);
                for (Var ret : callee.getIR().getReturnVars()) {
                    CSVar csRet = csManager.getCSVar(calleeCtx, ret);
                    solver.addPFGEdge(csRet, csLhs, PointerFlowEdge.Kind.RETURN);
                }
            }
            // add call edge
            CSMethod csCallee = csManager.getCSMethod(calleeCtx, callee);
            solver.addCallEdge(new DoPrivilegedCallEdge(csCallSite, csCallee));
        }
    }
}
