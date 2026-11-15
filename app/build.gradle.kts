// `java` 在这个脚本里被 Gradle 的 java 插件扩展占用，用别名 import 绕开，
// 否则 `java.util.Properties` 会被解析成 `JavaPluginExtension.util`。
import java.util.Properties as JavaProperties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

// 发布签名：机密值不进版本库，改从仓库外的 keystore.properties 读取。
// 没有该文件时 release 退回 debug 签名 —— 保证 clone 下来就能构建（见 README）。
val keystoreProps = rootProject.file("keystore.properties").takeIf { it.exists() }?.let { f ->
    JavaProperties().apply { f.inputStream().use(::load) }
}
// Explicit spare-device UI probe only; production support remains Android 13+.
val legacyUiSmoke = providers.gradleProperty("legacyUiSmoke").orNull == "true"
if (legacyUiSmoke && gradle.startParameter.taskNames.any { it.contains("release", ignoreCase = true) })
    error("legacyUiSmoke is a debug-only UI probe, not a supported release")
if (legacyUiSmoke) gradle.taskGraph.whenReady {
    check(allTasks.none { it.project == project && it.name.contains("release", ignoreCase = true) }) {
        "legacyUiSmoke cannot build release tasks, including through aggregate tasks"
    }
}

android {
    namespace = "com.frederic22.hyperextreme"
    compileSdk = 37
    buildToolsVersion = "37.0.0"
    ndkVersion = "28.2.13676358"

    // 桌面桥（home_bridge）：native 子工程（libxposed API 102 native 入口），仅 arm64-v8a；
    // libc++ 静态链接，避免运行期依赖 libc++_shared。
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    defaultConfig {
        applicationId = "com.xtg.hyperextremealive"
        minSdk = if (legacyUiSmoke) 31 else 33
        targetSdk = 37
        versionCode = 47
        versionName = "0.5-alive.7-ui.32-aod-slider" + if (legacyUiSmoke) "-ui-smoke-api31" else ""

        ndk {
            abiFilters += "arm64-v8a"
        }
        externalNativeBuild {
            cmake {
                arguments.add("-DANDROID_STL=c++_static")
            }
        }
    }

    signingConfigs {
        // 发布签名（本地模块项目，没有对外服务）：keystore/release.jks
        // 生成方式（口令只写进 keystore.properties，不要进版本库）：
        //   keytool -genkeypair -keystore keystore/release.jks -alias <别名> \
        //     -keyalg RSA -keysize 2048 -validity 10000
        // 没有 keystore.properties 时不创建这个配置，release 退回 debug 签名。
        if (keystoreProps != null) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8 全量裁剪 + 资源收缩。
            //
            // 【为什么以前是 false】原判断是"Xposed 入口按类名找，混淆会弄丢" —— 这是误判：
            // 按类名加载的入口只有两个（HookEntry / XposedProvider），都在
            // app/proguard-rules.pro 里显式 keep 住了。R8 剪掉的是**没被引用到的库代码**：
            // 本次打包前 dex 里有 17,803 个类，工程自己的不到 300 个，其余全是
            // androidx.compose.*（text / lazy / gestures / animation…）和 miuix 的 316 个
            // basic 组件 —— 一行都没执行过。
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = if (keystoreProps != null) {
                signingConfigs.getByName("release")
            } else {
                logger.warn("未找到 keystore.properties —— release 将使用 debug 签名")
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        // Host JVM access for the ART capability-field regression. Android uses its own runtime.
        unitTests.all { it.jvmArgs("--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED") }
    }

    // Only the generated tree is packaged: never include both original and compressed resources.
    sourceSets.getByName("main").assets.setSrcDirs(listOf(layout.buildDirectory.dir("generated/packedAliveAssets").get().asFile))
    sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/aliveAssets").get().asFile)
    sourceSets.getByName("main").jniLibs.srcDir(layout.buildDirectory.dir("generated/aliveJni").get().asFile)
    // Videos require openFd; gzip streams must not be compressed a second time.
    // Do not exempt "dex": that also disables compression of the top-level classes.dex.
    // LSPosed searches the module APK directly for its native entry; keep this small bridge mmap-ready.
    androidResources { noCompress += listOf("mp4", "agz", "libhome_bridge.so") }

    packaging {
        // Let PackageInstaller extract native libraries once, instead of shipping mmap-ready ELF.
        jniLibs { useLegacyPackaging = true }
        // dex 压缩进 APK（minSdk >= 28 时 AGP 默认是不压缩、安装时直接 mmap，
        // 体积会大 20MB 左右）。这里要的是包小，所以打开 legacy 打包。
        dex {
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

val preparePackedAliveAssets by tasks.registering(Exec::class) {
    inputs.dir(rootProject.file("app/src/main/assets"))
    inputs.file(rootProject.file("tools/pack_alive_assets.py"))
    inputs.file(rootProject.file("tools/dynamic_resource_pack.py"))
    outputs.dir(rootProject.layout.buildDirectory.dir("dynamic-wallpapers"))
    outputs.dir(layout.buildDirectory.dir("generated/packedAliveAssets"))
    workingDir(rootProject.projectDir)
    commandLine(System.getenv("PYTHON") ?: "python", "tools/pack_alive_assets.py")
}
tasks.named("preBuild") { dependsOn(preparePackedAliveAssets) }

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("androidx.compose.ui:ui-test-junit4:1.12.0")
    // libxposed API 102（由 io.github.libxposed:api:102.0.0 的 AAR 解出的 classes.jar）。
    // 只参与编译，运行时由框架提供，绝不打包进 APK。
    compileOnly(files("libs/libxposed-api-102.jar"))
    testImplementation(files("libs/libxposed-api-102.jar"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.jetbrains.compose.foundation)
    implementation(libs.miuix.core)
    implementation(libs.miuix.ui)
    implementation(libs.miuix.icons)
    // 底栏玻璃：改用 miuix 官方的 miuix-blur（AGSL 手写可分离高斯 + 自适应降采样），
    // 不再用 kyant backdrop 的 RenderEffect.createBlurEffect 那条链。
    implementation(libs.miuix.blur)
    // 悬浮底栏（从 HyperChanger 移植：Apache-2.0）用到的液态玻璃与形状库
    implementation(libs.kyant.backdrop)
    implementation(libs.kyant.shapes)
    // 空间壁纸的深度推理（Depth Anything V2 ViT-S int8）。
    // **完整版**：mobile（minimal build）缺 ConvInteger 等算子，实测跑不动这个模型，
    // 2026-10-22 用户决定退回完整版（so 17.8MB），模型直接用 `.onnx`。
    implementation(libs.onnxruntime.android)
}

val prepareAliveHelper by tasks.registering(Exec::class) {
    inputs.dir(rootProject.file("alive-root/src"))
    inputs.file(rootProject.file("app/src/main/java/com/frederic22/hyperextreme/alive/core/ModuleIdentity.java"))
    inputs.file(rootProject.file("app/src/main/java/com/frederic22/hyperextreme/alive/core/MusicLockRecovery.java"))
    inputs.file(rootProject.file("tools/build_alive_helper.py"))
    outputs.dir(layout.buildDirectory.dir("generated/aliveAssets"))
    workingDir(rootProject.projectDir)
    commandLine(
        System.getenv("PYTHON") ?: "python", "tools/build_alive_helper.py",
        "--sdk", android.sdkDirectory.absolutePath,
        "--java-home", System.getProperty("java.home"),
        "--compile-sdk", android.compileSdk.toString(),
        "--build-tools", android.buildToolsVersion,
    )
}
tasks.named("preBuild") { dependsOn(prepareAliveHelper) }

val prepareAlivePhoto by tasks.registering(Exec::class) {
    inputs.dir(rootProject.file("alive-photo"))
    inputs.file(rootProject.file("tools/build_alive_photo.py"))
    outputs.dir(layout.buildDirectory.dir("generated/aliveJni"))
    workingDir(rootProject.projectDir)
    commandLine(
        System.getenv("PYTHON") ?: "python", "tools/build_alive_photo.py",
        "--ndk", android.sdkDirectory.resolve("ndk/${android.ndkVersion}").absolutePath,
    )
}
tasks.named("preBuild") { dependsOn(prepareAlivePhoto) }



// AGP's legacy JNI mode compresses libraries but aligns explicitly stored ones to only 4 bytes.
// Realign the tiny LSPosed entry and sign again with the SAME variant key, before any APK consumer.
android.applicationVariants.all {
    val variant = this
    val signing = requireNotNull(signingConfig)
    packageApplicationProvider.configure {
        inputs.file(rootProject.file("tools/finalize_packed_apk.py"))
        doLast {
            variant.outputs.forEach { output ->
                val process = ProcessBuilder(
                    System.getenv("PYTHON") ?: "python", "tools/finalize_packed_apk.py",
                    "--apk", output.outputFile.absolutePath,
                    "--sdk", android.sdkDirectory.absolutePath,
                    "--build-tools", android.buildToolsVersion,
                    "--java-home", System.getProperty("java.home"),
                    "--keystore", requireNotNull(signing.storeFile).absolutePath,
                    "--alias", requireNotNull(signing.keyAlias),
                    "--min-sdk", if (legacyUiSmoke) "31" else "33",
                ).directory(rootProject.projectDir).redirectErrorStream(true)
                process.environment()["ALIVE_STORE_PASSWORD"] = requireNotNull(signing.storePassword)
                process.environment()["ALIVE_KEY_PASSWORD"] = signing.keyPassword ?: requireNotNull(signing.storePassword)
                val running = process.start()
                running.inputStream.bufferedReader().useLines { lines -> lines.forEach { logger.lifecycle(it) } }
                check(running.waitFor() == 0) { "APK alignment/signature verification failed" }
            }
        }
    }
}
