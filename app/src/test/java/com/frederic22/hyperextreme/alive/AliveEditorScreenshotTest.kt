package com.frederic22.hyperextreme.alive

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Alignment
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import com.frederic22.hyperextreme.ui.HyperTextStyles
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import top.yukonga.miuix.kmp.theme.*
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk=[35],application=Application::class,qualifiers="zh-rCN-w393dp-h873dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AliveEditorScreenshotTest {
    @get:Rule val compose=createEmptyComposeRule()
    private fun screenshot(name:String, node: SemanticsNodeInteraction = compose.onRoot()) {
        val out=File("build/reports/alive-ui").apply { mkdirs() }
        node.captureToImage().asAndroidBitmap().let { bitmap -> File(out,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) } }
    }
    @Test fun rasterControlsEditWithoutApplyingOrReplacingOtherSlots() {
        val host=Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            var current=VivoOptions(2);var imported=-1
            host.get().setContent {
                MiuixTheme(controller=remember { ThemeController(ColorSchemeMode.Dark) },textStyles=HyperTextStyles) {
                    var options by remember { mutableStateOf(current) }
                    Column(Modifier.fillMaxSize().background(Color.Black).verticalScroll(rememberScrollState()).padding(16.dp)) {
                        VivoControls(options,onChange={options=it;current=it},onImport={imported=it},onSwap={_,_->},onMask={})
                    }
                }
            }
            compose.onNodeWithText("效果 3").performClick();assertEquals(3,current.style)
            compose.onNodeWithText("光栅 2").performScrollTo().performClick()
            compose.onNodeWithText("选择视频").performScrollTo().performClick()
            assertEquals(VivoMediaSelection.SLOT_VIDEO+2,imported)
            compose.onNodeWithText("旋转 90°").performScrollTo().performClick()
            assertEquals(1,current.slots[2].rotation);assertEquals(0,current.slots[0].rotation)
            screenshot("vivo-raster-controls")
        } finally { host.pause().stop().destroy() }
    }
    @Test fun photoEditorRetainsDistinctReferencesAcrossTargetChanges() {
        val host=Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            var current=PhotoOptions.defaults().media(0,"photo-00000000000000000000000000000001.png")
                .media(1,"photo-00000000000000000000000000000002.png").media(2,"photo-00000000000000000000000000000003.png")
            var selected=0;var done=false
            host.get().setContent {
                MiuixTheme(controller=remember { ThemeController(ColorSchemeMode.Dark) },textStyles=HyperTextStyles) {
                    var photo by remember { mutableStateOf(current) };var target by remember { mutableIntStateOf(0) }
                    Box(Modifier.fillMaxSize().background(Color(0xff264552))) {
                        PhotoCropEditor(602,photo,target,onTarget={target=it;selected=it},onChange={photo=it;current=it},onDone={done=true})
                    }
                }
            }
            compose.onNodeWithText("相框").performClick();assertEquals(2,selected)
            compose.onNodeWithText("复位").performClick()
            assertEquals("photo-00000000000000000000000000000003.png",current.framePhoto)
            assertEquals("photo-00000000000000000000000000000002.png",current.homePhoto)
            screenshot("photo-crop-editor")
            compose.onNodeWithText("完成").performClick();assertTrue(done)
        } finally { host.pause().stop().destroy() }
    }
    @Test fun texturePanelEditsOnlyTheCurrentlyPreviewedSlot() {
        val host=Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            var current=PhotoOptions.defaults();var mode by mutableIntStateOf(1)
            host.get().setContent {
                MiuixTheme(controller=remember { ThemeController(ColorSchemeMode.Dark) },textStyles=HyperTextStyles) {
                    var options by remember { mutableStateOf(current) }
                    Column(Modifier.fillMaxSize().background(Color.Black).padding(20.dp)) {
                        PhotoControls(603,options,"texture",mode,onPreviewMode={error("Texture must not switch slots")}) { options=it;current=it }
                    }
                }
            }
            compose.onNodeWithText("磨砂").performClick()
            assertEquals(3,current.lockStyle);assertEquals(0,current.homeStyle)
            compose.onNodeWithText("仅调整桌面纹理，不改变锁屏。").assertDoesNotExist()
            screenshot("photo-lock-texture")
            compose.runOnIdle { mode=2 }
            compose.onNodeWithText("纯色").assertDoesNotExist()
            compose.onNodeWithText("条纹").performClick()
            assertEquals(3,current.lockStyle);assertEquals(1,current.homeStyle)
            compose.onNodeWithText("仅调整锁屏纹理，不改变桌面。").assertDoesNotExist()
            screenshot("photo-home-texture")
        } finally { host.pause().stop().destroy() }
    }
    @Test fun aodStylesSwitchInPlaceAndRemainAvailableInLockPreview() = verifyAodStylePicker("photo-aod-picker")
    @Test @org.robolectric.annotation.Config(qualifiers="zh-rCN-w320dp-h640dp-xhdpi")
    fun aodStylesFitCompactPreview() = verifyAodStylePicker("photo-aod-picker-compact")

    private fun verifyAodStylePicker(name: String) {
        val host=Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            val still=javaClass.getResourceAsStream("/alive/tile-603.png").use { BitmapFactory.decodeStream(it).asImageBitmap() }
            val shapes=(601..606).map { id -> javaClass.getResourceAsStream("/alive/tile-$id.png").use { BitmapFactory.decodeStream(it).asImageBitmap() } }
            var variant by mutableIntStateOf(603);var aodMode by mutableIntStateOf(0);var mode by mutableIntStateOf(0)
            var panel by mutableStateOf(false)
            var applied=false
            host.get().setContent {
                MiuixTheme(controller=remember { ThemeController(ColorSchemeMode.Dark) },textStyles=HyperTextStyles) {
                    Box(Modifier.fillMaxSize().background(Color.Black)) {
                        Image(still,null,Modifier.align(Alignment.TopCenter).padding(top=160.dp).fillMaxWidth(.65f),contentScale=ContentScale.Fit)
                        AliveEditorChrome("星月",mode,false,true,"仅预览壁纸 · 时钟以系统实际样式为准",
                            listOf(AliveEditorTool("样式",AliveToolIcon.STYLE){panel=true}) +
                            listOf("照片" to AliveToolIcon.PHOTO,"位置" to AliveToolIcon.POSITION,"取景" to AliveToolIcon.CROP,
                                "选项" to AliveToolIcon.OPTIONS,"演示" to AliveToolIcon.PLAY).map { AliveEditorTool(it.first,it.second) {} },
                            onCancel={},onApply={applied=true},onMode={mode=it})
                    }
                    WindowBottomSheet(show=panel,title="息屏样式",enableWindowDim=false,onDismissRequest={panel=false},
                        endAction={TextButton("完成",onClick={panel=false})}) {
                        Column(Modifier.fillMaxWidth().heightIn(max=340.dp).verticalScroll(rememberScrollState())) {
                            PhotoAodStylePicker(variant,aodMode,true,shapes) { v -> variant=v;aodMode=0;panel=false }
                        }
                    }
                }
            }
            compose.onNodeWithTag("photo-aod-styles").assertDoesNotExist()
            screenshot(name)
            for(index in 0..5) {
                compose.onNodeWithTag("alive-tool-样式").performClick()
                compose.onNodeWithTag("photo-aod-style-${variant-601}").assertIsSelected()
                if(index==0) screenshot("$name-panel",compose.onNodeWithTag("photo-aod-styles"))
                compose.onNodeWithTag("photo-aod-style-$index").performScrollTo().performClick()
                compose.onNodeWithTag("photo-aod-styles").assertDoesNotExist()
                assertEquals(601+index,variant)
                assertEquals(0,aodMode)
                assertFalse(applied)
            }
            compose.onNodeWithText("锁屏").performClick()
            compose.onNodeWithTag("alive-tool-样式").assertExists()
            compose.onNodeWithTag("photo-aod-styles").assertDoesNotExist()
            compose.onNodeWithText("息屏").performClick()
            compose.onNodeWithTag("alive-tool-样式").performClick()
            compose.onNodeWithTag("photo-aod-style-5").assertIsSelected()
            compose.onNodeWithText("完成").performClick()
        } finally { host.pause().stop().destroy() }
    }

    @Test fun dynamicPickerReusesCatalogAndSwitchesEveryFamily() = verifyDynamicPicker("dynamic-picker")
    @Test @org.robolectric.annotation.Config(qualifiers="zh-rCN-w320dp-h640dp-xhdpi")
    fun dynamicPickerFitsCompactSheet() = verifyDynamicPicker("dynamic-picker-compact")

    private fun verifyDynamicPicker(name: String) {
        val host=Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            val scenes=AliveCatalog.load(host.get())
            var variant by mutableIntStateOf(603);var mode by mutableIntStateOf(0);var panel by mutableStateOf(false)
            var applied=false
            val shapes=(601..606).map { id -> javaClass.getResourceAsStream("/alive/tile-$id.png").use { BitmapFactory.decodeStream(it).asImageBitmap() } }
            host.get().setContent {
                MiuixTheme(controller=remember { ThemeController(ColorSchemeMode.Dark) },textStyles=HyperTextStyles) {
                    Box(Modifier.fillMaxSize().background(Color.Black)) {
                        AliveEditorChrome(scenes.first { it.id==variant }.name,mode,false,true,"",
                            listOf(AliveEditorTool("样式",AliveToolIcon.STYLE){panel=true}),
                            onCancel={},onApply={applied=true},onMode={mode=it})
                    }
                    WindowBottomSheet(show=panel,title="壁纸样式",enableWindowDim=false,onDismissRequest={panel=false},
                        endAction={TextButton("完成",onClick={panel=false})}) {
                        AliveWallpaperPickerContent(variant,scenes,true,onSelect={variant=it.id;panel=false}) {
                            PhotoAodStylePicker(variant,0,true,shapes) { variant=it;panel=false }
                        }
                    }
                }
            }
            compose.onNodeWithTag("alive-tool-样式").performClick()
            compose.onNodeWithTag("photo-aod-styles").assertExists()
            compose.onNodeWithText("动态壁纸").performClick()
            compose.onNodeWithTag("alive-picker-scene-601").assertDoesNotExist()
            // All dynamic families, including each Vivo runtime, are selectable without navigation.
            for((category,id) in listOf(1 to 1,2 to 301,3 to 201,4 to 401,5 to 501,6 to 701,6 to 702,6 to 703)) {
                compose.onNodeWithTag("alive-picker-category-$category").performScrollTo().performClick()
                val itemIndex=scenes.filter { it.group==aliveDynamicGroups[category] }.indexOfFirst { it.id==id }
                compose.onNodeWithTag("alive-picker-grid").performScrollToIndex(itemIndex)
                compose.waitUntil(10_000) { compose.onAllNodes(hasContentDescription(scenes.first { it.id==id }.name)).fetchSemanticsNodes().isNotEmpty() }
                if(id==301) screenshot(name,compose.onNodeWithTag("alive-wallpaper-picker"))
                compose.onNodeWithTag("alive-picker-scene-$id").performClick()
                assertEquals(id,variant);assertEquals(0,mode);assertFalse(applied)
                compose.onNodeWithTag("alive-wallpaper-picker").assertDoesNotExist()
                compose.onNodeWithTag("alive-tool-样式").performClick()
                compose.onNodeWithTag("alive-segment-动态壁纸").assertIsSelected()
            }
            compose.onNodeWithTag("alive-picker-category-6").performScrollTo().performClick()
            compose.onNodeWithTag("alive-picker-grid").performScrollToIndex(2)
            compose.onNodeWithTag("alive-picker-scene-703").assertIsSelected()
            compose.onNodeWithText("照片壁纸").performClick()
            compose.onNodeWithTag("photo-aod-style-4").performScrollTo().performClick()
            assertEquals(605,variant);assertFalse(applied)
            compose.onNodeWithText("桌面").performClick()
            compose.onNodeWithTag("alive-tool-样式").performClick()
            compose.onNodeWithText("动态壁纸").performClick()
            compose.onNodeWithTag("alive-picker-grid").performScrollToIndex(0)
            compose.onNodeWithTag("alive-picker-scene-1").performClick()
            assertEquals(1,variant);assertEquals(2,mode);assertFalse(applied)
        } finally { host.pause().stop().destroy() }
    }

    @Test fun switchingPreviewFamiliesPreservesDraftsAndAppliedSettings() {
        val app=org.robolectric.RuntimeEnvironment.getApplication()
        val before=HashMap(Config.prefs(app).all)
        val liquid=VivoOptions(1).apply { style=3;photos.add("vivo-liquid-test.png");slots[0].zoom=2.4f }
        val raster=switchPreviewVivo(app,701,702,liquid)
        assertEquals(2,raster.family)
        raster.style=4;raster.slots[1].rotation=3;raster.photos.add("vivo-raster-test.png")
        val flash=switchPreviewVivo(app,702,703,raster)
        flash.style=5;flash.pattern="d5_pattern_star"
        val remembered=switchPreviewVivo(app,703,301,flash)
        val restored=switchPreviewVivo(app,301,701,remembered)
        assertEquals(liquid.json(),restored.json())
        assertEquals(raster.json(),switchPreviewVivo(app,701,702,restored).json())
        assertEquals(flash.json(),VivoOptions.load(app,703,true).json())
        assertEquals(before,Config.prefs(app).all)
    }

    @Test fun photoSourceSelectionUsesOnlyTheCurrentPreviewSlot() = verifyPhotoSourcePanel()

    @Test @org.robolectric.annotation.Config(qualifiers="zh-rCN-w320dp-h640dp-xhdpi")
    fun compactPhotoSourcePanelNeedsNoNotesOrOversizedButtons() = verifyPhotoSourcePanel("-compact")

    private fun verifyPhotoSourcePanel(suffix:String="") {
        val host=Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            var mode by mutableIntStateOf(1);var variant by mutableIntStateOf(602)
            var options by mutableStateOf(PhotoOptions.defaults().media(0,"photo-00000000000000000000000000000001.png"))
            var picked=-1
            host.get().setContent {
                MiuixTheme(controller=remember { ThemeController(ColorSchemeMode.Dark) },textStyles=HyperTextStyles) {
                    WindowBottomSheet(show=true,title=photoSourceTitle(photoSourceSlot(mode,variant,options)),
                        enableWindowDim=false,onDismissRequest={},endAction={TextButton("完成",onClick={})}) {
                        Column(Modifier.fillMaxWidth().heightIn(max=340.dp).verticalScroll(rememberScrollState()).testTag("photo-source-panel")) {
                            PhotoSourcePanel(photoSourceSlot(mode,variant,options),options,true,onChange={options=it},onPick={picked=it})
                        }
                    }
                }
            }
            compose.onNodeWithTag("photo-source-pick-0").performClick();assertEquals(0,picked)
            compose.onNodeWithTag("photo-source-pick-1").assertDoesNotExist()
            compose.onNodeWithText("跟随锁屏").assertDoesNotExist()
            screenshot("photo-source-lock$suffix",compose.onNodeWithTag("photo-source-panel"))
            compose.runOnIdle { mode=2 }
            compose.onNodeWithTag("photo-source-pick-0").assertDoesNotExist()
            compose.onNodeWithTag("photo-source-pick-2").assertDoesNotExist()
            compose.onNodeWithTag("photo-source-pick-1").performClick();assertEquals(1,picked)
            assertTrue("Opening the system picker must not disable follow",options.followLock)
            compose.onNodeWithText("跟随锁屏").performClick();assertFalse(options.followLock)
            compose.onNodeWithText("跟随锁屏").performClick();assertTrue(options.followLock)
            assertEquals("photo-00000000000000000000000000000001.png",options.lockPhoto)
            compose.onAllNodes(hasText("",substring=true) and hasAnyAncestor(hasTestTag("photo-source-panel")))
                .assertCountEquals(2)
            compose.onNodeWithTag("photo-source-pick-1").assertIsDisplayed()
            screenshot("photo-source-home$suffix",compose.onNodeWithTag("photo-source-panel"))
            compose.runOnIdle { mode=0 }
            compose.onNodeWithTag("photo-source-pick-2").performClick();assertEquals(2,picked)
            compose.onNodeWithTag("photo-source-pick-0").assertDoesNotExist()
            compose.runOnIdle { variant=603 }
            compose.onNodeWithTag("photo-source-pick-0").performClick();assertEquals(0,picked)
            compose.runOnIdle { variant=602;options=options.edit(15,1f) }
            compose.onNodeWithTag("photo-source-pick-0").assertExists()
        } finally { host.pause().stop().destroy() }
    }

    @Test fun cropHeaderReusesPreviewPillGeometry() {
        val host=Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            host.get().setContent {
                MiuixTheme(controller=remember { ThemeController(ColorSchemeMode.Dark) },textStyles=HyperTextStyles) {
                    Column(Modifier.fillMaxSize().background(Color.Black)) {
                        AliveEditorTopBar("星月","取消","应用",true,true,{},{})
                        AliveEditorTopBar("照片取景","返回","完成",true,true,{},{})
                    }
                }
            }
            fun bounds(label:String)=compose.onNodeWithTag("alive-header-$label").fetchSemanticsNode().boundsInRoot
            for((a,b) in listOf("取消" to "返回","应用" to "完成")) {
                assertEquals(bounds(a).width,bounds(b).width,.1f)
                assertEquals(bounds(a).height,bounds(b).height,.1f)
                assertEquals(bounds(a).left,bounds(b).left,.1f)
            }
            screenshot("shared-editor-pill-headers")
        } finally { host.pause().stop().destroy() }
    }

    @Test fun wallpaperSectionsAnimateInsteadOfReplacingImmediately() {
        val host=Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            val scenes=AliveCatalog.load(host.get())
            host.get().setContent {
                MiuixTheme(controller=remember { ThemeController(ColorSchemeMode.Dark) },textStyles=HyperTextStyles) {
                    Column(Modifier.fillMaxSize().background(Color(0xff232323)).padding(20.dp)) {
                        AliveWallpaperPickerContent(603,scenes,true,{}) {
                            PhotoAodStylePicker(603,0,true) {}
                        }
                    }
                }
            }
            compose.mainClock.autoAdvance=false
            compose.onNodeWithText("动态壁纸").performClick()
            compose.mainClock.advanceTimeBy(100)
            compose.onNodeWithTag("photo-aod-styles").assertExists()
            compose.onNodeWithTag("alive-picker-grid").assertExists()
            screenshot("picker-transition-middle")
            compose.mainClock.advanceTimeBy(400)
            compose.onNodeWithTag("photo-aod-styles").assertDoesNotExist()
            compose.onNodeWithTag("alive-picker-grid").assertExists()
            compose.onNodeWithText("照片壁纸").performClick()
            compose.mainClock.advanceTimeBy(100)
            compose.onNodeWithTag("photo-aod-styles").assertExists()
            compose.onNodeWithTag("alive-picker-grid").assertExists()
            compose.mainClock.advanceTimeBy(400)
            compose.onNodeWithTag("alive-picker-grid").assertDoesNotExist()
        } finally { compose.mainClock.autoAdvance=true;host.pause().stop().destroy() }
    }

    @Test fun sceneTransitionCoversReplacementAndWaitsForNewFrame() {
        val host=Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            val transition=PreviewSceneTransition();val frame=CompletableDeferred<Unit>()
            var replaced=false
            host.get().setContent {
                val scope=rememberCoroutineScope()
                MiuixTheme(controller=remember { ThemeController(ColorSchemeMode.Dark) },textStyles=HyperTextStyles) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=transition.shade.value))) {
                        TextButton("Switch",onClick={scope.launch {
                            transition.switch {
                                assertEquals(1f,transition.shade.value,0f);replaced=true;frame.await()
                            }
                        }})
                    }
                }
            }
            compose.waitForIdle()
            compose.mainClock.autoAdvance=false
            compose.onNodeWithText("Switch").performClick()
            compose.mainClock.advanceTimeBy(80)
            compose.runOnIdle {
                assertTrue("Transition not running; shade=${transition.shade.value}",transition.running)
                assertFalse("Renderer replaced before fade-out",replaced)
                assertTrue("Fade-out midpoint=${transition.shade.value}",transition.shade.value>0 && transition.shade.value<1)
            }
            compose.mainClock.advanceTimeBy(160)
            compose.runOnIdle { assertTrue(replaced);assertEquals(1f,transition.shade.value,0f) }
            compose.mainClock.advanceTimeBy(500)
            compose.runOnIdle { assertEquals(1f,transition.shade.value,0f);frame.complete(Unit) }
            compose.mainClock.advanceTimeBy(140)
            compose.runOnIdle { assertTrue(transition.shade.value>0 && transition.shade.value<1) }
            compose.mainClock.advanceTimeBy(300)
            compose.runOnIdle { assertFalse(transition.running);assertEquals(0f,transition.shade.value,0f) }
        } finally { compose.mainClock.autoAdvance=true;host.pause().stop().destroy() }
    }

    @Test fun cancelledSceneTransitionClearsCoverAndAllowsNextSwitch() {
        val host=Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            val transition=PreviewSceneTransition();var job:Job?=null;var replaced=false
            host.get().setContent {
                val scope=rememberCoroutineScope()
                MiuixTheme(controller=remember { ThemeController(ColorSchemeMode.Dark) },textStyles=HyperTextStyles) {
                    TextButton("Switch",onClick={job=scope.launch {
                        transition.switch { replaced=true;CompletableDeferred<Unit>().await() }
                    }})
                }
            }
            compose.waitForIdle();compose.mainClock.autoAdvance=false
            for(waitForFrame in listOf(false,true)) {
                compose.onNodeWithText("Switch").performClick()
                compose.mainClock.advanceTimeBy(if(waitForFrame) 300 else 80)
                compose.runOnIdle {
                    assertTrue(transition.running);assertEquals(waitForFrame,replaced);job!!.cancel()
                }
                compose.mainClock.advanceTimeBy(32)
                compose.runOnIdle { assertFalse(transition.running);assertEquals(0f,transition.shade.value,0f) }
            }
        } finally { compose.mainClock.autoAdvance=true;host.pause().stop().destroy() }
    }

    @Test fun reducedMotionStillWaitsForFirstFrameAndFailureRestoresControls() {
        val host=Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            val transition=PreviewSceneTransition();val frame=CompletableDeferred<Unit>()
            val noMotion=object:MotionDurationScale { override val scaleFactor=0f }
            var replaced=false;var failure=""
            host.get().setContent {
                val scope=rememberCoroutineScope()
                MiuixTheme(controller=remember { ThemeController(ColorSchemeMode.Dark) },textStyles=HyperTextStyles) {
                    TextButton("Switch",onClick={scope.launch(noMotion) {
                        try { transition.switch { replaced=true;frame.await();error("Synthetic renderer failure") } }
                        catch(e:IllegalStateException) { failure=e.message.orEmpty() }
                    }})
                }
            }
            compose.waitForIdle();compose.mainClock.autoAdvance=false
            compose.onNodeWithText("Switch").performClick();compose.mainClock.advanceTimeBy(100)
            compose.runOnIdle { assertTrue(replaced);assertTrue(transition.running);assertEquals(1f,transition.shade.value,0f) }
            compose.mainClock.advanceTimeBy(500)
            compose.runOnIdle { assertTrue(transition.running);frame.complete(Unit) }
            compose.mainClock.advanceTimeBy(100)
            compose.runOnIdle {
                assertFalse(transition.running);assertEquals(0f,transition.shade.value,0f)
                assertEquals("Synthetic renderer failure",failure)
            }
        } finally { compose.mainClock.autoAdvance=true;host.pause().stop().destroy() }
    }
}
