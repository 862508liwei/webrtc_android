# 本地音视频录制功能实现过程文档

本文档描述了在原有代码基础上，实现通话过程中音频与视频的本地录制与导出功能的详细步骤。

## 1. 需求分析

主要需求是在音视频通话过程中，用户能够启动和停止本地媒体（自己这一侧的音频和视频）的录制，并将录制内容保存为媒体文件（如 MP4），用户后续可以访问和播放这些文件。

## 2. 技术方案

*   **媒体捕获与编码**: 利用 Android 平台的 `MediaRecorder` 类来处理音视频的录制和编码。`MediaRecorder` 可以从音频源（如 `VOICE_COMMUNICATION`）和视频源（如 Surface）获取数据，并将其编码为常见的媒体格式。
*   **WebRTC 集成**:
    *   音频: 直接使用 `MediaRecorder` 配置的音频源。
    *   视频: 将 WebRTC 本地视频轨道 (`_localVideoTrack`) 的内容渲染到一个 `Surface` 上，这个 `Surface` 由 `MediaRecorder`提供。这通过为 `_localVideoTrack` 添加一个新的 `VideoSink` (具体为 `ProxyVideoSink`，其目标是 `mediaRecorder.getSurface()`) 来实现。
*   **文件存储**: 录制的文件将保存在应用外部存储的公共媒体目录中（视频在 `Movies` 目录，音频在 `Music` 目录），以便用户可以通过相册、音乐播放器或文件管理器轻松访问。
*   **用户界面**: 在通话界面（区分视频通话和音频通话）中添加录制按钮和状态指示器（如计时器）。
*   **权限管理**: 确保应用请求并获得了必要的权限（`RECORD_AUDIO`, `CAMERA`, `WRITE_EXTERNAL_STORAGE`）。
*   **用户反馈**: 录制结束后，通过系统通知告知用户文件已保存，并允许用户点击通知直接打开文件。

## 3. 实现步骤

### 步骤 1: 在 `WebRTCEngine.java` 中添加录制核心逻辑

*   **目标文件**: `rtc-chat/src/main/java/com/dds/skywebrtc/engine/webrtc/WebRTCEngine.java`
*   **修改内容**:
    *   添加成员变量: `mediaRecorder` (MediaRecorder 实例), `mediaRecorderSurface` (用于视频录制的 Surface), `currentRecordingFilePath` (当前录制文件路径), `isRecording` (录制状态标志), `mediaRecorderVideoSink` (用于将 WebRTC 视频连接到 MediaRecorder 的 VideoSink)。
    *   实现 `startRecording(String filePath)` 方法:
        *   初始化和配置 `MediaRecorder`：设置音频源 (`MediaRecorder.AudioSource.VOICE_COMMUNICATION`)、视频源 (`MediaRecorder.VideoSource.SURFACE`)、输出格式 (`MediaRecorder.OutputFormat.MPEG_4`)、输出文件、音频编码器 (`MediaRecorder.AudioEncoder.AAC`)、视频编码器 (`MediaRecorder.VideoEncoder.H264`)、视频尺寸、帧率和比特率。
        *   通过 `mediaRecorder.getSurface()` 获取 `Surface`，并创建一个 `ProxyVideoSink` 将其连接到本地视频轨道 `_localVideoTrack`。
        *   调用 `mediaRecorder.prepare()` 和 `mediaRecorder.start()`。
    *   实现 `stopRecording()` 方法:
        *   调用 `mediaRecorder.stop()`, `mediaRecorder.reset()`, `mediaRecorder.release()`。
        *   从 `_localVideoTrack` 移除 `mediaRecorderVideoSink`。
    *   在 `stopPreview()` 和 `release()` 方法中添加必要的清理逻辑，以防资源泄漏。

### 步骤 2: 在 `SkyEngineKit.java` 中暴露录制接口

*   **目标文件**:
    *   `rtc-chat/src/main/java/com/dds/skywebrtc/engine/IEngine.java` (接口)
    *   `rtc-chat/src/main/java/com/dds/skywebrtc/CallSession.java` (实现调用)
    *   `rtc-chat/src/main/java/com/dds/skywebrtc/SkyEngineKit.java` (最终暴露)
*   **修改内容**:
    *   在 `IEngine.java` 接口中添加 `void startRecording(String filePath);` 和 `void stopRecording();`。
    *   在 `WebRTCEngine.java` 中实现这些接口方法（已在步骤 1 中完成）。
    *   在 `CallSession.java` 中添加相应方法，通过其持有的 `IEngine` 实例调用录制方法。
    *   在 `SkyEngineKit.java` 中添加 `startRecording(String filePath)` 和 `stopRecording()` 方法，通过其持有的 `CallSession` 实例调用，从而使 UI 层可以访问。

### 步骤 3: 更新通话界面的 UI 和交互逻辑

*   **目标文件 (视频通话)**:
    *   Fragment: `app/src/main/java/com/dds/core/voip/FragmentVideo.java`
    *   Layout: `app/src/main/res/layout/av_p2p_video_connected_action.xml` (此为 `FragmentVideo` 中连接状态下的控制按钮布局)
*   **目标文件 (音频通话)**:
    *   Fragment: `app/src/main/java/com/dds/core/voip/FragmentAudio.java`
    *   Layout: `app/src/main/res/layout/av_p2p_audio_outgoing.xml` (此为 `FragmentAudio` 中通话状态下的控制按钮布局，具体布局文件名根据实际情况调整，这里假设是这个)
*   **修改内容 (针对视频和音频 Fragment 分别进行)**:
    *   **布局文件**:
        *   在通话控制按钮区域（如 `connectedActionContainer`）添加 `ImageButton` (用于开始/停止录制) 和 `Chronometer` (用于显示录制时长)。
        *   为 `ImageButton` 设置 ID (如 `recordButtonVideo`, `recordButtonAudio`) 和初始图标。
        *   为 `Chronometer` 设置 ID (如 `recordingTimerVideo`, `recordingTimerAudio`) 并初始隐藏。
    *   **Fragment Java 文件**:
        *   添加成员变量: 录制按钮、计时器、`isRecording` 状态标志、`currentRecordingFilePath`。
        *   在 `initView()` 中获取按钮和计时器实例，并为按钮设置点击监听器。
        *   实现点击事件处理逻辑 (`onClick` 或专门的处理方法如 `handleRecordButtonVideoClick`):
            *   如果未在录制:
                *   生成唯一文件路径 (视频保存到 `Environment.DIRECTORY_MOVIES`，音频到 `Environment.DIRECTORY_MUSIC`)。
                *   调用 `SkyEngineKit.Instance().startRecording(filePath)`。
                *   更新按钮图标为“停止”状态，启动并显示计时器。
                *   设置 `isRecording = true`。
            *   如果在录制:
                *   调用 `SkyEngineKit.Instance().stopRecording()`。
                *   更新按钮图标为“开始”状态，停止并隐藏（或重置）计时器。
                *   设置 `isRecording = false`。
                *   调用后续步骤实现的通知方法，并显示 Toast 提示文件保存路径。
        *   在 `onDestroyView()` 中增加判断，如果仍在录制，则停止录制。

### 步骤 4: 实现文件导出与用户反馈 (通过通知)

*   **目标文件**: `FragmentVideo.java`, `FragmentAudio.java`
*   **修改内容**:
    *   **`FileProvider` 配置**:
        *   在 `AndroidManifest.xml` 中注册 `androidx.core.content.FileProvider`。
        *   在 `res/xml/` 目录下创建 `file_paths.xml`，定义外部文件共享路径 (指向 `Movies/` 和 `Music/`)。
    *   在两个 Fragment 中实现 `showSaveNotification(String filePath, boolean isVideo)` 方法:
        *   创建通知渠道 (Android 8.0+)。
        *   使用 `FileProvider.getUriForFile()` 为保存的文件创建 `content URI`。
        *   创建 `Intent.ACTION_VIEW` 指向该 URI，并设置正确的 MIME 类型 (`video/mp4` 或 `audio/mp3`)。
        *   创建 `PendingIntent`。
        *   构建并显示系统通知，告知用户文件已保存，点击可查看。
    *   在停止录制并确认文件保存后，调用此 `showSaveNotification` 方法。

### 步骤 5: 处理权限

*   **目标文件**:
    *   `app/src/main/java/com/dds/core/voip/CallSingleActivity.java`
    *   `app/src/main/AndroidManifest.xml`
*   **修改内容**:
    *   **`AndroidManifest.xml`**:
        *   确保声明了以下权限:
            *   `<uses-permission android:name="android.permission.RECORD_AUDIO" />`
            *   `<uses-permission android:name="android.permission.CAMERA" />`
            *   `<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />` (移除了 `maxSdkVersion` 属性以兼容各版本，确保 `FileProvider` 和文件保存的可靠性)。
    *   **`CallSingleActivity.java`**:
        *   在原有的权限请求逻辑中，为视频通话和音频通话场景均加入 `Manifest.permission.WRITE_EXTERNAL_STORAGE` 权限的请求。

### 步骤 6: 测试

*   **主要方式**: 手动测试。
*   **测试点**:
    *   在视频通话和音频通话中分别启动和停止录制。
    *   验证录制按钮状态和计时器是否按预期工作。
    *   录制完成后，检查通知是否弹出，点击通知是否能正确打开对应的录制文件。
    *   通过文件管理器或媒体库检查录制文件是否存在于正确的目录 (`Movies/` 或 `Music/`)。
    *   播放录制的音频和视频文件，确认内容完整、清晰、可播放。
    *   测试权限请求流程：首次安装应用，检查是否弹出权限请求对话框；拒绝权限后，检查录制功能是否不可用或有相应提示。
    *   在不同 Android 版本上测试（如果条件允许）。

## 4. 总结

通过以上步骤，成功为应用添加了本地音视频录制功能。用户现在可以在通话时方便地录制通话内容，并通过系统标准途径访问这些录音录像。
