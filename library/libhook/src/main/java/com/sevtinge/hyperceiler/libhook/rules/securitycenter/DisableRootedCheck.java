/*
 * This file is part of HyperCeiler.
 *
 * HyperCeiler is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (C) 2023-2026 HyperCeiler Contributions
 */

package com.sevtinge.hyperceiler.libhook.rules.securitycenter;

import com.sevtinge.hyperceiler.common.log.XposedLog;
import com.sevtinge.hyperceiler.libhook.base.BaseHook;
import io.github.lingqiqi5211.ezhooktool.xposed.java.IMethodHook;
import com.sevtinge.hyperceiler.libhook.utils.hookapi.dexkit.IDexKit;
import com.sevtinge.hyperceiler.libhook.utils.hookapi.dexkit.IDexKitList;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindField;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.FieldMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.BaseDataList;
import org.luckypray.dexkit.result.ClassData;
import org.luckypray.dexkit.result.FieldData;
import org.luckypray.dexkit.result.MethodData;
import org.luckypray.dexkit.result.MethodDataList;
import org.luckypray.dexkit.result.base.BaseData;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

import io.github.lingqiqi5211.ezhooktool.xposed.common.HookParam;

public class DisableRootedCheck extends BaseHook {
    private Method mReturnEnvironmentMethod;
    private Class<?> mClientApiRequestClass;
    private Method mEnvironmentPutMethod;
    private Class<?> mRiskAppClass;
    private Field mRiskAppField;
    private Method mUnsetEnvironmentMethod;
    private List<Method> mSetEnvironmentMethods;
    private List<Method> mCheckRootMethods;

    @Override
    protected boolean useDexKit() {
        return true;
    }

    @Override
    protected boolean initDexKit() {
        mReturnEnvironmentMethod = optionalMember("ReturnEnvironment", new IDexKit() {
            @Override
            public BaseData dexkit(DexKitBridge bridge) throws ReflectiveOperationException {
                MethodData methodData = bridge.findMethod(FindMethod.create()
                    .matcher(MethodMatcher.create()
                        .usingStrings("Invalid Task , reason:  [canceled, ")
                    )).firstOrNull();
                return methodData;
            }
        });
        mClientApiRequestClass = optionalMember("ClientApiRequest", new IDexKit() {
            @Override
            public BaseData dexkit(DexKitBridge bridge) throws ReflectiveOperationException {
                ClassData classData = bridge.findClass(FindClass.create()
                    .matcher(ClassMatcher.create()
                        .usingStrings("ClientApiRequest")
                    )).firstOrNull();
                return classData;
            }
        });
        if (mClientApiRequestClass != null) {
            mEnvironmentPutMethod = optionalMember("EnvironmentPut", new IDexKit() {
                @Override
                public BaseData dexkit(DexKitBridge bridge) throws ReflectiveOperationException {
                    MethodData methodData = bridge.findMethod(FindMethod.create()
                        .matcher(MethodMatcher.create()
                            .declaredClass(mClientApiRequestClass)
                            .returnType(boolean.class)
                        )).firstOrNull();
                    return methodData;
                }
            });
        }
        mRiskAppClass = optionalMember("RiskAppClass", new IDexKit() {
            @Override
            public BaseData dexkit(DexKitBridge bridge) throws ReflectiveOperationException {
                ClassData classData = bridge.findClass(FindClass.create()
                    .matcher(ClassMatcher.create()
                        .usingStrings("RiskAppManager")
                    )).firstOrNull();
                return classData;
            }
        });
        if (mRiskAppClass != null) {
            mRiskAppField = optionalMember("RiskApp", new IDexKit() {
                @Override
                public BaseData dexkit(DexKitBridge bridge) throws ReflectiveOperationException {
                    FieldData fieldData = bridge.findField(FindField.create()
                        .matcher(FieldMatcher.create()
                            .type(mRiskAppClass)
                            .modifiers(Modifier.PRIVATE)
                        )).firstOrNull();
                    return fieldData;
                }
            });
            mUnsetEnvironmentMethod = optionalMember("UnsetEnvironment", new IDexKit() {
                @Override
                public BaseData dexkit(DexKitBridge bridge) throws ReflectiveOperationException {
                    MethodData methodData = bridge.findMethod(FindMethod.create()
                        .matcher(MethodMatcher.create()
                            .usingStrings("RiskAppManager")
                        )).firstOrNull();
                    return methodData;
                }
            });
            if (mClientApiRequestClass != null) {
                mSetEnvironmentMethods = optionalMemberList("SetEnvironment", new IDexKitList() {
                    @Override
                    public BaseDataList<MethodData> dexkit(DexKitBridge bridge) throws ReflectiveOperationException {
                        MethodDataList methodData = bridge.findMethod(FindMethod.create()
                            .matcher(MethodMatcher.create()
                                .returnType(void.class)
                                .paramTypes(mClientApiRequestClass)
                                .declaredClass(mRiskAppClass)
                            )
                        );
                        return methodData;
                    }
                });
            }
        }
        mCheckRootMethods = optionalMemberList("CheckRoot", new IDexKitList() {
            @Override
            public BaseDataList<MethodData> dexkit(DexKitBridge bridge) throws ReflectiveOperationException {
                MethodDataList methodData = bridge.findMethod(FindMethod.create()
                        .matcher(MethodMatcher.create()
                                .usingStrings("/system/bin/")
                                .returnType(boolean.class)
                        )
                );
                return methodData;
            }
        });
        return true;
    }

    @Override
    public void init() {
        if (mReturnEnvironmentMethod != null && mClientApiRequestClass != null && mEnvironmentPutMethod != null && mRiskAppField != null) {
            hookMethod(mReturnEnvironmentMethod, new IMethodHook() {
                @Override
                public void before(HookParam param) {
                    Object obj = param.getArgs()[0];
                    if (mClientApiRequestClass.isInstance(obj)) {
                        callMethod(obj, mEnvironmentPutMethod.getName());
                        Object thisObj = param.getThisObject();
                        Object cField = getObjectField(thisObj, mRiskAppField.getName());
                        if (mSetEnvironmentMethods != null) {
                            for (Method method : mSetEnvironmentMethods) {
                                if (method != mUnsetEnvironmentMethod) {
                                    callMethod(cField, method.getName(), obj);
                                }
                            }
                        }
                    }
                }
            });
        }
        if (mCheckRootMethods != null) {
            for (Method method : mCheckRootMethods) {
                XposedLog.d(TAG, getPackageName(), "Current hooking method is " + method);
                hookMethod(method, new IMethodHook() {
                    @Override
                    public void before(HookParam param) {
                        param.setResult(false);
                    }
                });
            }
        }
    }
}
