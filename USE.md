# OpenVA 使用文档

本文档详细讲解 OpenVA 项目的使用方式,包括:作为虚拟引擎 App 直接使用、作为 SDK 集成 Bcore 到你自己的工程、以及单独使用 BlackReflection 反射框架(支持 kapt 与 KSP 两种代码生成路径)。

> 项目版本: `5.0.0`
> Maven 坐标 group: `rj.openva`
> 发布方式: `mavenLocal`(本地 Maven 仓库)

---

## 目录

1. [项目结构总览](#1-项目结构总览)
2. [环境要求](#2-环境要求)
3. [作为应用构建运行](#3-作为应用构建运行)
4. [作为 SDK 集成 Bcore](#4-作为-sdk-集成-bcore)
5. [核心 API:BlackBoxCore](#5-核心-apiblackboxcore)
6. [BlackReflection 反射框架](#6-blackreflection-反射框架)
7. [BlackReflection 进阶用法](#7-blackreflection-进阶用法)
8. [编译器:JSR-269 与 KSP](#8-编译器jsr-269-与-ksp)
9. [发布到 mavenLocal](#9-发布到-mavenlocal)
10. [下游深度定制](#10-下游深度定制)
11. [常见问题](#11-常见问题)

---

## 1. 项目结构总览

```
OpenVA/
├── app/                  # 虚拟引擎宿主 App(可直接安装使用)
├── Bcore/                # 虚拟化核心 SDK(AAR,对外发布)
├── black-reflection/      # 反射框架 runtime(注解 + BlackReflection + Reflector)
├── compiler/             # JSR-269 注解处理器(配 kapt 使用)
├── compiler-ksp/         # KSP2 符号处理器(Kotlin 2.3.21 + KSP 2.3.9,配 ksp 使用)
├── settings.gradle       # 仓库镜像配置(阿里云)
└── gradle/libs.versions.toml
```

各模块 Maven 坐标:

| 模块 | groupId | artifactId | version | 产物 |
|------|---------|-----------|---------|------|
| black-reflection | rj.openva | black-reflection | 5.0.0 | jar |
| compiler | rj.openva | compiler | 5.0.0 | jar(kapt 处理器) |
| compiler-ksp | rj.openva | compiler-ksp | 5.0.0 | jar(KSP 处理器) |
| Bcore | rj.openva | Bcore | 5.0.0 | aar |

依赖关系:`Bcore` → `api: black-reflection`、`annotationProcessor: compiler`;`compiler` / `compiler-ksp` → `implementation: black-reflection`(拿注解定义)。

---

## 2. 环境要求

- **JDK**: 17+(Bcore 编译用 21,处理器模块用 17)
- **Android Gradle Plugin**: 8.13.2
- **Gradle**: 8.13
- **compileSdk**: 34+
- **minSdk**: 21(Android 5.0)
- **NDK**: 28.2.13676358(Bcore 含 native hook,仅 `arm64-v8a` / `armeabi-v7a`)
- **Kotlin**(仅当你启用 KSP 路径): 2.3.21 + KSP 2.3.9

> 仓库已默认走阿里云镜像(`maven.aliyun.com`),国内网络可直接拉取,无需翻墙。`mavenLocal()` 已加入仓库列表,本地发布的产物可被下游直接引用。

---

## 3. 作为应用构建运行

最简单的用法:把 `app` 模块编译成 APK 装到设备上。

```bash
# 克隆
git clone https://github.com/RJMultiDev/OpenVA.git
cd OpenVA

# Debug APK(产物在 app/build/outputs/apk/debug/)
./gradlew :app:assembleDebug

# Release APK
./gradlew :app:assembleRelease
```

安装到设备:

```bash
adb install app/build/outputs/apk/debug/OpenVA_5.0.0_*.apk
```

打开 App 后,在主界面「添加应用」即可把已安装的 APK 克隆进虚拟环境运行,支持多开(多 userId)。

---

## 4. 作为 SDK 集成 Bcore

如果你要写自己的虚拟化宿主,不想用 `app` 这个壳,可以把 `Bcore` 作为 AAR 集成进你的工程。

### 4.1 先把产物发布到 mavenLocal

在 OpenVA 根目录执行(顺序很重要,因为 Bcore 依赖 black-reflection,而 compiler 又依赖 black-reflection):

```bash
./gradlew :black-reflection:publishToMavenLocal
./gradlew :compiler:publishToMavenLocal
./gradlew :compiler-ksp:publishToMavenLocal
./gradlew :Bcore:publishReleasePublicationToMavenLocal
```

完成后 `~/.m2/repository/rj/openva/` 下应有 4 个子目录(`black-reflection`、`compiler`、`compiler-ksp`、`Bcore`),每个含 `*-5.0.0.jar` / `*.aar` + `.pom` + `.module`。

### 4.2 在你的工程里引用

你的 `settings.gradle` 必须包含 `mavenLocal()`:

```groovy
dependencyResolutionManagement {
    repositories {
        mavenLocal()
        maven { url "https://maven.aliyun.com/repository/google" }
        maven { url "https://maven.aliyun.com/repository/central" }
        google()
        mavenCentral()
    }
}
```

你的 `app/build.gradle`:

```groovy
plugins {
    id 'com.android.application'
}

android {
    compileSdk 34
    defaultConfig {
        minSdk 21
        ndk { abiFilters 'arm64-v8a', 'armeabi-v7a' }
    }
    // Bcore 内部使用 Java 21 反射 API,建议对齐
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
}

dependencies {
    // Bcore 已把 black-reflection 声明为 api 依赖,会传递过来
    implementation 'rj.openva:Bcore:5.0.0'

    // Bcore 自身用 kapt(JSR-269)生成 BR* 反射代理类
    // 你的宿主工程也需要配 kapt,否则编译时不会触发处理器
    // (如果你不用 Bcore 内置的反射代理,可不加;但 Bcore 运行时依赖这些生成的类)
    annotationProcessor 'rj.openva:compiler:5.0.0'
}
```

> 如果你用 Kotlin 工程,把 `annotationProcessor` 换成 `kapt`,并应用 `kotlin-kapt` 插件。详见 [第 8 节](#8-编译器jsr-269-与-ksp)。

---

## 5. 核心 API:BlackBoxCore

`BlackBoxCore` 是虚拟化引擎的入口,所有虚拟应用操作都从它走。

### 5.1 获取实例

```java
BlackBoxCore core = BlackBoxCore.get();
```

### 5.2 安装虚拟应用

三种入参,任选其一:

```java
// 方式 1:从宿主已安装的包名克隆(自动取宿主的 sourceDir)
InstallResult r1 = core.installPackageAsUser("com.example.target", 0);

// 方式 2:从 APK 文件
InstallResult r2 = core.installPackageAsUser(new File("/sdcard/app.apk"), 0);

// 方式 3:从 content Uri
InstallResult r3 = core.installPackageAsUser(uri, 0);

if (r1.isSuccess()) {
    // 安装成功,可启动
} else {
    Log.e("OpenVA", "install failed: " + r1.getError());
}
```

> userId 从 0 开始,代表一个虚拟用户(多开分身)。userId=0 是第一个分身,userId=1 是第二个,以此类推。

### 5.3 启动 / 卸载 / 查询

```java
// 启动虚拟应用
boolean ok = core.launchApk("com.example.target", 0);

// 卸载(指定分身)
core.uninstallPackageAsUser("com.example.target", 0);
// 卸载(所有分身)
core.uninstallPackage("com.example.target");

// 是否已安装
boolean installed = core.isInstalled("com.example.target", 0);

// 列出某分身下所有已安装包
List<PackageInfo> packages = core.getInstalledPackages(0, 0);
List<ApplicationInfo> apps = core.getInstalledApplications(0, 0);
```

### 5.4 多用户(多开)管理

`userId` 即虚拟用户。每个 userId 拥有独立的数据目录、包列表、账号。要在第 N 个分身安装,直接传 `userId = N` 即可,无需预先创建。

### 5.5 进程上下文

在虚拟应用进程内拿当前信息:

```java
// 当前虚拟 userId
int userId = BActivityThread.getUserId();
// 当前虚拟包名
String pkg = BActivityThread.getAppPackageName();
// 宿主主线程对象(用于反射 ActivityThread)
Object mainThread = BlackBoxCore.mainThread();
```

---

## 6. BlackReflection 反射框架

BlackReflection 是 OpenVA 内置的「类型安全反射」工具。你写一个 Java 接口描述要反射的目标类,注解标明字段/方法/构造,编译期会生成 `BR*` 代理类,运行时直接调用,像普通方法一样访问隐藏 API。

### 6.1 为什么用它

- 解决 Android P+ 对隐藏 API 的反射限制(Bcore 内部配合 FreeReflection)
- 类型安全:接口方法签名即反射签名,编译期检查
- 零运行时开销查找:方法/字段名在编译期固化
- 自动回退:目标不存在时返回默认值(不抛异常,除非用 `getWithException`)

### 6.2 注解一览

| 注解 | 作用位置 | 含义 |
|------|----------|------|
| `@BClass(Xxx.class)` | 接口 | 反射目标类(编译期已知 Class) |
| `@BClassName("a.b.Xxx")` | 接口 | 反射目标类(全限定名字符串,适合隐藏类) |
| `@BMethod` | 方法 | 实例方法 |
| `@BStaticMethod` | 方法 | 静态方法 |
| `@BField` | 方法 | 实例字段(方法名 = 字段名) |
| `@BStaticField` | 方法 | 静态字段 |
| `@BConstructor` | 方法 | 构造方法(方法名随意,惯例 `_new`) |
| `@BParamClass(X.class)` | 参数 | 指定参数的真实 Class(反射用) |
| `@BParamClassName("a.b.X")` | 参数 | 同上,字符串形式 |

### 6.3 三步用法

#### 第 1 步:写接口

以反射 `android.app.AlarmManager` 的隐藏字段为例:

```java
package black.android.app;

import android.os.IInterface;
import top.niunaijun.blackreflection.annotation.BClassName;
import top.niunaijun.blackreflection.annotation.BField;

@BClassName("android.app.AlarmManager")
public interface AlarmManager {
    @BField
    IInterface mService();

    @BField
    int mTargetSdkVersion();
}
```

#### 第 2 步:编译触发处理器

kapt 路径(Java/Kotlin 工程均可):

```groovy
plugins {
    id 'com.android.library' // 或 application
    id 'kotlin-kapt' // Kotlin 工程需要;纯 Java 工程用 annotationProcessor
}

dependencies {
    implementation 'rj.openva:black-reflection:5.0.0'
    // 二选一:
    kapt 'rj.openva:compiler:5.0.0'           // kapt 路径
    // 或
    annotationProcessor 'rj.openva:compiler:5.0.0' // 纯 Java 路径
}
```

KSP 路径(Kotlin 工程,更快):

```groovy
plugins {
    id 'com.google.devtools.ksp'
}

dependencies {
    implementation 'rj.openva:black-reflection:5.0.0'
    ksp 'rj.openva:compiler-ksp:5.0.0'
}
```

编译后会在接口同包下生成:
- `BRAlarmManager.java` — 代理入口类
- `AlarmManagerContext.java` — 实例访问接口
- `AlarmManagerStatic.java` — 静态访问接口

#### 第 3 步:调用

```java
// 拿到一个真实的 AlarmManager 实例(比如从 Context 拿)
android.app.AlarmManager real = ctx.getSystemService(android.app.AlarmManager.class);

// 包一层反射代理
AlarmManagerContext proxy = BRAlarmManager.get(real);
IInterface service = proxy.mService();
int sdk = proxy.mTargetSdkVersion();

// 静态字段/方法用无参 get()
// ActivityThreadStatic at = BRActivityThread.get();
// Object thread = at.currentActivityThread();
```

### 6.4 生成代码长什么样

以 `@BClassName("android.app.AlarmManager")` 为例,生成的 `BRAlarmManager`:

```java
package black.android.app;

public final class BRAlarmManager {
    // 静态访问(无 caller)
    public static AlarmManagerStatic get() {
        return BlackReflection.create(AlarmManagerStatic.class, null, false);
    }
    public static AlarmManagerStatic getWithException() {
        return BlackReflection.create(AlarmManagerStatic.class, null, true);
    }
    // 实例访问(带 caller)
    public static AlarmManagerContext get(Object caller) {
        return BlackReflection.create(AlarmManagerContext.class, caller, false);
    }
    public static AlarmManagerContext getWithException(Object caller) {
        return BlackReflection.create(AlarmManagerContext.class, caller, true);
    }
    // 真实 Class(用于 ClassUtil.classReady 检查是否可加载)
    public static Class<?> getRealClass() {
        return top.niunaijun.blackreflection.utils.ClassUtil.classReady(AlarmManagerContext.class);
    }
}
```

`AlarmManagerContext` / `AlarmManagerStatic` 是与原接口结构相同的子接口,但被 `BlackReflection.create` 用 `Proxy.newProxyInstance` 动态代理,每次方法调用都会走 `Reflector` 反射。

---

## 7. BlackReflection 进阶用法

### 7.1 完整示例:反射 ActivityThread

```java
package black.android.app;

import android.app.Application;
import android.os.Handler;
import android.os.IBinder;

import top.niunaijun.blackreflection.annotation.BClassName;
import top.niunaijun.blackreflection.annotation.BField;
import top.niunaijun.blackreflection.annotation.BMethod;
import top.niunaijun.blackreflection.annotation.BStaticMethod;

@BClassName("android.app.ActivityThread")
public interface ActivityThread {
    @BField
    Application mInitialApplication();

    @BField
    Handler mH();

    @BStaticMethod
    Object currentActivityThread();

    @BStaticMethod
    String currentPackageName();

    @BMethod
    IBinder getApplicationThread();

    @BMethod
    String getProcessName();
}
```

调用:

```java
// 静态:不带 caller
Object thread = BRActivityThread.get().currentActivityThread();
String pkg = BRActivityThread.get().currentPackageName();

// 实例:带 caller(必须是 ActivityThread 的真实实例)
ActivityThreadContext at = BRActivityThread.get(thread);
Handler h = at.mH();
Application app = at.mInitialApplication();
IBinder binder = at.getApplicationThread();
```

### 7.2 内嵌类型(`$Stub`、`$H`)

隐藏 API 里常见内部类,用嵌套接口表达:

```java
@BClassName("android.accounts.IAccountManager")
public interface IAccountManager {

    @BClassName("android.accounts.IAccountManager$Stub")
    interface Stub {
        @BStaticMethod
        IInterface asInterface(IBinder binder);
    }
}
```

调用:`IAccountManager.Stub` → 生成 `BRIAccountManagerStub.get()`。

### 7.3 构造方法(`@BConstructor`)

方法名随意,惯例 `_new`:

```java
@BClassName("android.app.ActivityThread$ProviderKey")
public interface ProviderKeyJBMR1 {
    @BConstructor
    ProviderKeyJBMR1 _new(String name, int uid);
}
```

调用:

```java
ProviderKeyJBMR1 key = BRProviderKeyJBMR1.get()._new("com.example", 10001);
// 注意:构造方法只能用 .get()(无 caller),返回的是真实对象代理
```

### 7.4 参数真实类型(`@BParamClass` / `@BParamClassName`)

反射方法参数类型在编译期无法直接引用(隐藏类)时用:

```java
@BMethod
Object getPackageInfo(
    ApplicationInfo ai,
    @BParamClassName("android.content.res.CompatibilityInfo") Object compatInfo,
    int flags
);
```

`@BParamClass(X.class)` 用于能拿到 Class 的情况,`@BParamClassName("a.b.X")` 用于完全隐藏的类。处理器会把这些信息写进生成的方法签名,运行时 `BlackReflection.getParamClass` 据此 `Class.forName`。

### 7.5 写字段(`_set_`)与探针(`_check_`)

生成器会为每个 `@BField` 自动多生成两个方法:
- `_set_<字段名>(Object value)` — 写入字段
- `_check_<字段名>()` — 返回 `java.lang.reflect.Field`,可用于判断字段是否存在

```java
// 写
BRActivityThread.get(mainThread)._set_mInitialApplication(newApp);
// 探针(字段不存在时返回 null,不抛异常)
Field f = BRActivityThread.get(thread)._check_mH();
if (f != null) { /* 字段存在,可安全访问 */ }
```

对 `@BMethod` 同理生成 `_check_<方法名>(...)` 返回 `java.lang.reflect.Method`,用于多版本兼容判断(Bcore 大量用此模式做 API 降级)。

### 7.6 异常模式

默认 `get()` 在反射失败时返回默认值(对象返 null,基本类型抛 `BlackNullPointerException`)。如需捕获真实异常,用 `getWithException()`:

```java
try {
    BRActivityThread.getWithException(thread).getProcessName();
} catch (Throwable e) {
    // 真实反射异常
}
```

### 7.7 调试与缓存

```java
BlackReflection.DEBUG = true;  // 打开会打印反射异常堆栈
BlackReflection.CACHE = true;  // 缓存静态代理(同 clazz 复用)
```

---

## 8. 编译器:JSR-269 与 KSP

OpenVA 提供两个**功能等价**的代码生成器,产出字节级一致的 Java 源文件。二选一即可。

### 8.1 对照

| 维度 | `compiler`(JSR-269) | `compiler-ksp`(KSP2) |
|------|----------------------|---------------------|
| 适用工程 | Java / Kotlin(配 kapt) | Kotlin(配 ksp) |
| 工具链 | javac AP / kotlin-kapt | Kotlin 2.3.21 + KSP 2.3.9 |
| 速度 | 慢(kapt 走 javac stub) | 快(KSP2 直跑 analysis API) |
| 输出 | `build/generated/source/kapt/` 或 `apt/` | `build/generated/ksp/` |
| 依赖 | `rj.openva:black-reflection` | `rj.openva:black-reflection` |
| Maven | `rj.openva:compiler:5.0.0` | `rj.openva:compiler-ksp:5.0.0` |

### 8.2 选哪个

- **纯 Java 工程** → 只能选 `compiler`(配 `annotationProcessor`)
- **Kotlin 工程 + kapt 已存在** → 用 `compiler`(配 `kapt`),不引入新工具链
- **Kotlin 工程 + 想要更快编译** → 用 `compiler-ksp`(配 `ksp` 插件)。这是 KSP2 唯一可走路径,Kotlin 必须升到 2.3.21

> 注意:KSP 2.4.0 尚未发布,Kotlin 2.4.0 暂无对应 KSP tag。当前 KSP2 唯一可用组合是 **Kotlin 2.3.21 + KSP 2.3.9**。

### 8.3 KSP 工程配置示例

```groovy
// settings.gradle
pluginManagement {
    repositories {
        mavenLocal()
        maven { url "https://maven.aliyun.com/repository/google" }
        maven { url "https://maven.aliyun.com/repository/gradle-plugin" }
        gradlePluginPortal()
    }
}

// build.gradle (root)
plugins {
    id 'com.android.application' version '8.13.2' apply false
    id 'org.jetbrains.kotlin.android' version '2.3.21' apply false
    id 'com.google.devtools.ksp' version '2.3.9' apply false
}

// app/build.gradle
plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
    id 'com.google.devtools.ksp'
}

dependencies {
    implementation 'rj.openva:black-reflection:5.0.0'
    ksp 'rj.openva:compiler-ksp:5.0.0'
}
```

### 8.4 验证生成的代码

```bash
# 编译后检查产物
find build/generated/ksp -name "*.java" | head
# 或 kapt 路径
find build/generated/source/kapt -name "BR*.java" | head
```

每个 `@BClassName` 接口应生成 3 个文件:`BR<Name>.java`、`<Name>Context.java`、`<Name>Static.java`。

---

## 9. 发布到 mavenLocal

各模块的 `build.gradle` 已配好 `maven-publish`。完整发布流程:

```bash
# 1. 先发 runtime(被其它模块依赖)
./gradlew :black-reflection:publishToMavenLocal

# 2. 发处理器(依赖 black-reflection 的注解)
./gradlew :compiler:publishToMavenLocal
./gradlew :compiler-ksp:publishToMavenLocal

# 3. 发 AAR
./gradlew :Bcore:publishReleasePublicationToMavenLocal
```

验证:

```bash
ls ~/.m2/repository/rj/openva/
# 应输出: Bcore  black-reflection  compiler  compiler-ksp
```

> Bcore 的 pom 里通过 `withXml` 手动声明了 `black-reflection` 和 `appcompat` 为 `compile` 依赖,下游 `implementation 'rj.openva:Bcore:5.0.0'` 会传递拉到。但 `annotationProcessor` 不会传递,下游工程需自行声明(见 4.2)。

### 9.1 修改 group / version

在根 `build.gradle` 的 `ext` 块和各模块的 `group` / `version` 处同步修改。例如换 group 为 `com.yourcompany`:

- `build.gradle`(root)`ext { groupId = "com.yourcompany" ... }`
- 各模块 `group = "com.yourcompany"`、`version = "5.0.0"`
- Bcore pom 的 `withXml` 里的 `rj.openva` 也要改成新 group

---

## 10. 下游深度定制

### 10.1 自定义反射目标

你的工程里加自己的 `@BClassName` 接口,只要把处理器配进 `dependencies`,编译时会和 Bcore 的反射代理一起生成。

```java
// 你的工程里:反射某个厂商私有类
@BClassName("com.miui.foo.Bar")
public interface MiuiBar {
    @BStaticMethod
    String getVersion();

    @BMethod
    void doSomething(int mode);
}
```

### 10.2 替换 Bcore 的服务代理

Bcore 通过 `proxy/` 包下的类伪造系统服务(ActivityManager、PackageManager、AccountManager 等)。你可以:
- 继承 `BPackageManager` 等 fake framework,覆盖方法
- 通过 `BlackBoxCore` 的钩子在虚拟应用启动前后注入自定义逻辑

### 10.3 网络层 / 系统行为拦截

如需在虚拟应用进程里拦截网络请求或系统行为调用,可在你的宿主工程里:
- 用 `@BMethod` 反射 hook 目标系统的隐藏方法
- 配合 Bcore 的 `BActivityThread` 进程上下文判断「当前调用是否来自虚拟应用」,再做拦截判定

> Bcore 本身不内置网络拦截能力,这属于下游宿主的定制范畴。

---

## 11. 常见问题

### Q1: 编译报 `Could not resolve rj.openva:Bcore:5.0.0`

确认:
1. 已执行 `publishToMavenLocal` 发布了对应模块
2. 你的 `settings.gradle` 里有 `mavenLocal()`
3. 检查 `~/.m2/repository/rj/openva/Bcore/5.0.0/` 下有 `.aar` 文件

### Q2: 编译时没有生成 `BR*.java`

处理器没被触发。检查:
- Java 工程:`annotationProcessor 'rj.openva:compiler:5.0.0'`
- Kotlin + kapt:`kapt 'rj.openva:compiler:5.0.0'` + `id 'kotlin-kapt'`
- Kotlin + ksp:`ksp 'rj.openva:compiler-ksp:5.0.0'` + `id 'com.google.devtools.ksp'`

### Q3: 运行时 `NullPointerException: value is null!`

反射的目标字段/方法返回了 null 但声明的是基本类型(如 `int`)。这是 `BlackReflection.generateNullValue` 在基本类型收到 null 时抛的 `BlackNullPointerException`。要么确认目标真实存在,要么改用对象类型(如 `Integer`)。

### Q4: KSP 报 `Kotlin 2.4.0` 不支持

KSP 还没发布对应 2.4.0 的版本。把 Kotlin 降到 `2.3.21`,KSP 用 `2.3.9`(见 `gradle/libs.versions.toml`)。

### Q5: 多开分身数据互相串

确认每次操作都传了正确的 `userId`。Bcore 按 `userId` 隔离数据目录,跨 userId 操作不会自动路由。

### Q6: APK 安装失败提示 `Cannot clone BlackBox app`

`installPackageAsUser` 检测到要克隆的是宿主自身包名(`getHostPkg()`),会拒绝(防止无限递归)。这是 Bcore 的安全保护,不要尝试克隆宿主。

---

## 附:相关文件索引

| 内容 | 文件 |
|------|------|
| 反射入口 | `black-reflection/src/main/java/top/niunaijun/blackreflection/BlackReflection.java` |
| 反射底层 | `black-reflection/src/main/java/top/niunaijun/blackreflection/utils/Reflector.java` |
| 注解定义 | `black-reflection/src/main/java/top/niunaijun/blackreflection/annotation/` |
| JSR-269 处理器 | `compiler/src/main/java/top/niunaijun/blackreflection/BlackReflectionProcessor.java` |
| KSP 处理器 | `compiler-ksp/src/main/kotlin/top/niunaijun/blackreflection/ksp/BlackReflectionKspProcessor.kt` |
| 虚拟化入口 | `Bcore/src/main/java/top/niunaijun/blackbox/BlackBoxCore.java` |
| 真实使用示例 | `Bcore/src/main/java/black/android/app/ActivityThread.java` 等所有 `black.android.*` 接口 |
| 调用示例 | `Bcore/src/main/java/top/niunaijun/blackbox/app/BActivityThread.java`(用 `BRActivityThread`) |
