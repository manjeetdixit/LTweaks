package li.lingfeng.ltweaks.xposed;

import android.app.Activity;
import android.app.Service;
import android.text.TextUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import li.lingfeng.ltweaks.MyApplication;
import li.lingfeng.ltweaks.lib.XposedLoad;
import li.lingfeng.ltweaks.prefs.PrefKeys;
import li.lingfeng.ltweaks.prefs.Prefs;
import li.lingfeng.ltweaks.utils.Callback;
import li.lingfeng.ltweaks.utils.Logger;
import li.lingfeng.ltweaks.utils.XposedUtils;

/**
 * Created by smallville on 2017/1/23.
 */

public abstract class XposedBase implements IXposedHookLoadPackage {

    protected XC_LoadPackage.LoadPackageParam lpparam;
    private Set<XC_MethodHook.Unhook> performCreateHooks;
    private Map<Class, List<Callback.C1<Activity>>> activityCreateCallbacks;

    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        this.lpparam = lpparam;
        final XposedLoad xposedLoad = getClass().getAnnotation(XposedLoad.class);
        if (!xposedLoad.loadAtActivityCreate().isEmpty()) {
            final Class cls = findClass(xposedLoad.loadAtActivityCreate());
            performCreateHooks = hookAllMethods(Activity.class, "attach", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (performCreateHooks == null || !cls.isAssignableFrom(param.thisObject.getClass())) {
                        return;
                    }
                    XposedUtils.unhookAll(performCreateHooks);
                    performCreateHooks = null;

                    if (xposedLoad.useRemotePreferences()) {
                        Activity activity = (Activity) param.thisObject;
                        Prefs.createRemotePreferences(activity.getApplicationContext());
                    }
                    List<String> enabledPrefs = new ArrayList<>();
                    for (int pref : xposedLoad.prefs()) {
                        if (Prefs.instance().getBoolean(pref, false)) {
                            enabledPrefs.add(PrefKeys.getById(pref));
                        }
                    }

                    if (enabledPrefs.size() > 0 || xposedLoad.prefs().length == 0) {
                        try {
                            if (xposedLoad.packages().length > 0) {
                                Logger.i("Load " + XposedBase.this.getClass().getName() + " for " + lpparam.packageName
                                        + ", with prefs [" + TextUtils.join(", ", enabledPrefs) + "]");
                            }
                            handleLoadPackage();
                        } catch (Throwable e) {
                            Logger.e("Can't handleLoadPackage, " + e.getMessage());
                            Logger.stackTrace(e);
                        }
                    }
                }
            });
        } else {
            handleLoadPackage();
        }
    }

    protected abstract void handleLoadPackage() throws Throwable;

    protected void hookAtActivityCreate(final Callback.C1<Activity> callback) {
        hookAtActivityCreate(Activity.class, callback);
    }

    protected void hookAtActivityCreate(String strActivity, final Callback.C1<Activity> callback) {
        final Class cls = findClass(strActivity);
        hookAtActivityCreate(cls, callback);
    }

    protected void hookAtActivityCreate(Class<? extends Activity> clsActivity, final Callback.C1<Activity> callback) {
        if (activityCreateCallbacks == null) {
            activityCreateCallbacks = new HashMap<>();
            hookAllMethods(Activity.class, "attach", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    for (Class cls : activityCreateCallbacks.keySet()) {
                        if (cls.isAssignableFrom(param.thisObject.getClass())) {
                            for (Callback.C1<Activity> callback : activityCreateCallbacks.get(cls)) {
                                try {
                                    callback.onResult((Activity) param.thisObject);
                                } catch (Throwable e) {
                                    Logger.e("hookAtActivityCreate callback invoke exception, " + e);
                                    Logger.stackTrace(e);
                                }
                            }
                            activityCreateCallbacks.remove(cls);
                        }
                    }
                }
            });
        }
        List<Callback.C1<Activity>> callbacks = activityCreateCallbacks.get(clsActivity);
        if (callbacks == null) {
            callbacks = new ArrayList<>();
            activityCreateCallbacks.put(clsActivity, callbacks);
        }
        callbacks.add(callback);
    }

    protected Class<?> findClass(String name) {
        return XposedHelpers.findClass(name, lpparam.classLoader);
    }

    protected XC_MethodHook.Unhook findAndHookMethod(Class<?> clazz, String methodName, Object... parameterTypesAndCallback) {
        return XposedHelpers.findAndHookMethod(clazz, methodName, parameterTypesAndCallback);
    }

    protected XC_MethodHook.Unhook findAndHookMethod(String className, String methodName, Object... parameterTypesAndCallback) {
        return XposedHelpers.findAndHookMethod(className, lpparam.classLoader, methodName, parameterTypesAndCallback);
    }

    protected XC_MethodHook.Unhook findAndHookMethodByParameterAndReturnTypes(String cls, Class<?> returnType, Object... parameterTypesAndCallback) {
        return findAndHookMethodByParameterAndReturnTypes(findClass(cls), returnType, parameterTypesAndCallback);
    }

    protected XC_MethodHook.Unhook findAndHookMethodByParameterAndReturnTypes(Class<?> cls, Class<?> returnType, Object... parameterTypesAndCallback) {
        if(parameterTypesAndCallback.length != 0 && parameterTypesAndCallback[parameterTypesAndCallback.length - 1] instanceof XC_MethodHook) {
            Class<?>[] parameterTypes = new Class<?>[parameterTypesAndCallback.length - 1];
            System.arraycopy(parameterTypesAndCallback, 0, parameterTypes, 0, parameterTypes.length);
            Method[] methods = XposedHelpers.findMethodsByExactParameters(cls, returnType, parameterTypes);
            if (methods.length == 1) {
                Logger.v("Hook P&R method " + methods[0]);
                return XposedBridge.hookMethod(methods[0], (XC_MethodHook) parameterTypesAndCallback[parameterTypesAndCallback.length - 1]);
            } else {
                for (Method method : methods) {
                    Logger.e("P&R method " + method);
                }
                throw new AssertionError("Can't hook P&R method in cls " + cls + ", " + methods.length + " methods.");
            }
        } else {
            throw new IllegalArgumentException("no callback defined");
        }
    }

    protected XC_MethodHook.Unhook findAndHookConstructor(String className, Object... parameterTypesAndCallback) {
        return XposedHelpers.findAndHookConstructor(className, lpparam.classLoader, parameterTypesAndCallback);
    }

    protected XC_MethodHook.Unhook findAndHookConstructor(Class<?> clazz, Object... parameterTypesAndCallback) {
        return XposedHelpers.findAndHookConstructor(clazz, parameterTypesAndCallback);
    }

    protected Set<XC_MethodHook.Unhook> hookAllMethods(Class<?> hookClass, String methodName, XC_MethodHook callback) {
        return XposedBridge.hookAllMethods(hookClass, methodName, callback);
    }

    protected Set<XC_MethodHook.Unhook> hookAllMethods(String className, String methodName, XC_MethodHook callback) {
        return XposedBridge.hookAllMethods(findClass(className), methodName, callback);
    }

    protected Set<XC_MethodHook.Unhook> hookAllConstructors(Class<?> hookClass, XC_MethodHook callback) {
        return XposedBridge.hookAllConstructors(hookClass, callback);
    }

    protected Set<XC_MethodHook.Unhook> hookAllConstructors(String className, XC_MethodHook callback) {
        return XposedBridge.hookAllConstructors(findClass(className), callback);
    }

    protected XC_MethodHook.Unhook findAndHookActivity(final String className, String methodName, Object... parameterTypesAndCallback) {
        return findAndHookWithParent(className, Activity.class, methodName, parameterTypesAndCallback);
    }

    protected XC_MethodHook.Unhook findAndHookService(final String className, String methodName, Object... parameterTypesAndCallback) {
        return findAndHookWithParent(className, Service.class, methodName, parameterTypesAndCallback);
    }

    protected XC_MethodHook.Unhook findAndHookWithParent(final String className, final Class clsBase, String methodName, Object... parameterTypesAndCallback) {
        if (clsBase == null) {
            throw new AssertionError("clsBase is null.");
        }

        if(parameterTypesAndCallback.length != 0 && parameterTypesAndCallback[parameterTypesAndCallback.length - 1] instanceof XC_MethodHook) {
            Class<?>[] parameterTypes = new Class<?>[parameterTypesAndCallback.length - 1];
            System.arraycopy(parameterTypesAndCallback, 0, parameterTypes, 0, parameterTypes.length);

            // If method is overridden by extended class, then hook it directly.
            Class<?> cls = null;
            Method method = null;
            try {
                cls = findClass(className);
                if (!clsBase.isAssignableFrom(cls)) {
                    throw new AssertionError("Parent of cls " + cls + " is not " + clsBase);
                }
                method = XposedHelpers.findMethodExact(cls, methodName, parameterTypes);
                Logger.v("Hook " + className + " " + methodName);
                return XposedBridge.hookMethod(method, (XC_MethodHook) parameterTypesAndCallback[parameterTypes.length]);
            } catch (AssertionError e) {
                throw e;
            } catch (Throwable e) {
                method = null;
            }

            // Try find from parent class.
            if (cls == null) {
                cls = clsBase;
            }
            while (true) {
                if (cls != clsBase) {
                    cls = cls.getSuperclass();
                }
                try {
                    method = XposedHelpers.findMethodExact(cls, methodName, parameterTypes);
                    if (Modifier.isAbstract(method.getModifiers()) || cls.isInterface()) {
                        throw new Exception();
                    }
                    break;
                } catch (Throwable e) {
                    method = null;
                }
                if (cls == clsBase) {
                    break;
                }
            }
            if (method == null) {
                throw new AssertionError("Method " + methodName + " even can't be found in clsBase " + clsBase
                        + ", or it's abstract.");
            }

            // Hook parent class or clsBase.
            final XC_MethodHook hook = (XC_MethodHook) parameterTypesAndCallback[parameterTypes.length];
            XC_MethodHook middleHook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.thisObject.getClass().getName().equals(className)) {
                        try {
                            XposedHelpers.callMethod(hook, "beforeHookedMethod", param);
                        } catch (XposedHelpers.InvocationTargetError e) {
                            throw e.getCause();
                        }
                    }
                }
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.thisObject.getClass().getName().equals(className)) {
                        try {
                            XposedHelpers.callMethod(hook, "afterHookedMethod", param);
                        } catch (XposedHelpers.InvocationTargetError e) {
                            throw e.getCause();
                        }
                    }
                }
            };
            Logger.v("Hook " + cls.getName() + " " + methodName + " for " + className);
            return XposedBridge.hookMethod(method, middleHook);
        } else {
            throw new IllegalArgumentException("no callback defined");
        }
    }

    protected Method findMethodStartsWith(String clsName, String methodNameStarts) {
        return findMethodStartsWith(findClass(clsName), methodNameStarts);
    }

    protected Method findMethodStartsWith(Class cls, String methodNameStarts) {
        Method[] methods = cls.getDeclaredMethods();
        for (Method method : methods) {
            if (method.getName().startsWith(methodNameStarts)) {
                return method;
            }
        }
        return null;
    }
}
